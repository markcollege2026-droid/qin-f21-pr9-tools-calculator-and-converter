package com.qin.f21.toolbox.core.hardware

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Records mono 16-bit PCM audio and writes a standard RIFF/WAVE file.
 *
 * The recording stays on the device (app-specific external storage) - nothing is uploaded.
 */
class VoiceRecorder {

    companion object {
        const val SAMPLE_RATE = 44100
    }

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var file: File? = null
    private var dataLength = 0L

    @Volatile
    private var running = false

    @Volatile
    var amplitude: Double = 0.0
        private set

    val isRecording: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(target: File): Boolean {
        if (running) return false
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2)
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
        try {
            target.parentFile?.mkdirs()
        } catch (e: Exception) {
            // ignore
        }
        file = target
        dataLength = 0
        recorder = rec
        running = true
        rec.startRecording()
        thread = Thread {
            val buffer = ByteArray(bufferSize)
            try {
                target.outputStream().use { out ->
                    // placeholder header, rewritten on stop
                    out.write(ByteArray(44))
                    while (running) {
                        val read = try {
                            rec.read(buffer, 0, buffer.size)
                        } catch (e: Exception) {
                            -1
                        }
                        if (read <= 0) continue
                        out.write(buffer, 0, read)
                        dataLength += read
                        amplitude = amplitudeOf(buffer, read)
                    }
                }
            } catch (e: Exception) {
                // recording aborted
            }
        }.apply {
            name = "voice-recorder"
            isDaemon = true
            start()
        }
        return true
    }

    /** Stops the recording and finalises the WAV header. */
    fun stop(): File? {
        if (!running) return null
        running = false
        try {
            thread?.join(800)
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
        val target = file
        if (target != null) {
            writeWavHeader(target, dataLength)
        }
        return target
    }

    private fun amplitudeOf(buffer: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val value = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += (value.toDouble() * value.toDouble())
            count++
            i += 2
        }
        if (count == 0) return 0.0
        return (sqrt(sum / count) / 32768.0).coerceIn(0.0, 1.0)
    }

    private fun writeWavHeader(target: File, dataLength: Long) {
        try {
            val byteRate = SAMPLE_RATE * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt((36 + dataLength).toInt())
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)                       // PCM
            header.putShort(1)                       // mono
            header.putInt(SAMPLE_RATE)
            header.putInt(byteRate)
            header.putShort(2)                       // block align
            header.putShort(16)                      // bits per sample
            header.put("data".toByteArray())
            header.putInt(dataLength.toInt())
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(0)
                raf.write(header.array())
            }
        } catch (e: Exception) {
            // keep the raw file even if the header cannot be written
        }
    }
}
