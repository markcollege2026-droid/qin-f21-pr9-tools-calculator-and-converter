package com.qin.f21.toolbox.core.hardware

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** A single measurement frame of the sound level meter. */
data class SoundLevel(
    val rms: Double,
    val peak: Double,
    val dbfs: Double,
    val decibels: Double,
    val bands: List<Float>
)

/**
 * Microphone level meter and spectrum analyser.
 *
 * Uses [AudioRecord] directly (no third-party audio library). The absolute decibel value is
 * an estimate: phone microphones are not calibrated, so the reading is expressed relative to
 * full scale (dBFS) plus a user adjustable calibration offset.
 */
class SoundMeter(private val calibrationOffsetDb: Double = 94.0) {

    companion object {
        const val SAMPLE_RATE = 44100
        val BANDS = intArrayOf(60, 150, 400, 1000, 2400, 5000, 10000)
    }

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile
    private var running = false

    @Volatile
    var latest: SoundLevel? = null
        private set

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(onFrame: ((SoundLevel) -> Unit)? = null): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 10 * 2)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            try {
                rec.release()
            } catch (e: Exception) {
                // ignore
            }
            return false
        }
        recorder = rec
        running = true
        rec.startRecording()
        thread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (running) {
                val read = try {
                    rec.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    -1
                }
                if (read <= 0) continue
                val level = analyse(buffer, read)
                latest = level
                onFrame?.invoke(level)
            }
        }.apply {
            name = "sound-meter"
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        try {
            thread?.join(400)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // ignore
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
    }

    private fun analyse(buffer: ShortArray, length: Int): SoundLevel {
        var sumSquares = 0.0
        var peak = 0
        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            sumSquares += (sample.toDouble() * sample.toDouble())
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        val rms = if (length == 0) 0.0 else sqrt(sumSquares / length) / 32768.0
        val dbfs = if (rms > 0) 20.0 * log10(rms) else -120.0
        val decibels = dbfs + calibrationOffsetDb
        val bands = ArrayList<Float>(BANDS.size)
        for (band in BANDS) {
            bands.add(goertzel(buffer, length, band))
        }
        return SoundLevel(rms, peak / 32768.0, dbfs, decibels, bands)
    }

    /** Goertzel filter: magnitude of one frequency bin. */
    private fun goertzel(buffer: ShortArray, length: Int, frequency: Int): Float {
        if (length == 0) return 0f
        val omega = 2.0 * Math.PI * frequency / SAMPLE_RATE
        val coefficient = 2.0 * cos(omega)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until length) {
            s0 = buffer[i].toDouble() / 32768.0 + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coefficient * s1 * s2
        val magnitude = sqrt(abs(power)) * 2.0 / length
        // convert to a 0..1 scale with a 60 dB dynamic range
        val db = 20.0 * log10(magnitude.coerceAtLeast(1e-9)) + 60.0
        return db.coerceIn(0.0, 60.0).toFloat() / 60.0f
    }

    private fun cos(value: Double): Double = kotlin.math.cos(value)
}
