package com.qin.f21.toolbox.core.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.ConsumerIrManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator

/** Availability of a hardware feature on the running device. */
enum class SupportState {
    /** Detected and usable right now. */
    AVAILABLE,

    /** The hardware is physically absent on this model. */
    NOT_PRESENT,

    /** The hardware exists but the app still needs a runtime permission. */
    PERMISSION_REQUIRED,

    /** The hardware exists but the platform refuses to expose it (e.g. policy restrictions). */
    DISABLED
}

data class Capability(
    val id: String,
    val title: String,
    val state: SupportState,
    val detail: String
) {
    val usable: Boolean get() = state == SupportState.AVAILABLE
}

/**
 * Runtime hardware detection.
 *
 * Nothing in the toolbox assumes that a sensor, camera flash, IR emitter or GPS exists:
 * every feature asks this registry first and shows a clear "not available on this device"
 * state when the hardware is missing. On the Qin F21 Pro the only sensors reported by the
 * vendor are a light and a proximity sensor, so the accelerometer based tools stay hidden.
 */
class HardwareCapabilities private constructor(
    private val appContext: Context,
    val cameraIds: List<String>,
    val cameraLabels: Map<String, String>,
    val camerasWithFlash: List<String>,
    val frontFacingCameras: List<String>,
    val sensorTypes: List<Int>,
    val sensorsByType: Map<Int, Sensor>,
    val hasIrEmitter: Boolean,
    val hasGpsProvider: Boolean,
    val hasMicrophone: Boolean,
    val hasWifi: Boolean,
    val hasCellular: Boolean,
    val hasBluetooth: Boolean,
    val hasVibrator: Boolean,
    val hasBarometer: Boolean,
    val hasMicrophonePermission: Boolean,
    val hasCameraPermission: Boolean,
    val hasLocationPermission: Boolean
) {

    val hasCamera: Boolean get() = cameraIds.isNotEmpty()
    val hasTorch: Boolean get() = camerasWithFlash.isNotEmpty()
    val hasAccelerometer: Boolean get() = sensorsByType.containsKey(Sensor.TYPE_ACCELEROMETER)
    val hasGyroscope: Boolean get() = sensorsByType.containsKey(Sensor.TYPE_GYROSCOPE)
    val hasCompass: Boolean get() = sensorsByType.containsKey(Sensor.TYPE_MAGNETIC_FIELD) ||
        sensorsByType.containsKey(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    val hasLightSensor: Boolean get() = sensorsByType.containsKey(Sensor.TYPE_LIGHT)
    val hasProximitySensor: Boolean get() = sensorsByType.containsKey(Sensor.TYPE_PROXIMITY)
    val hasPressureSensor: Boolean get() = hasBarometer
    val hasMicrophoneHardware: Boolean get() = hasMicrophone

    /** Every sensor the device reports, in a stable order. */
    val sensorInventory: List<Sensor> get() = sensorsByType.values.sortedBy { it.type }

    fun capability(id: String, title: String, state: SupportState, detail: String) =
        Capability(id, title, state, detail)

    fun list(): List<Capability> = listOf(
        Capability("camera", "Camera", cameraState(), cameraDetail()),
        Capability("torch", "Flashlight", cameraState(), torchDetail()),
        Capability("microphone", "Microphone", micState(), micDetail()),
        Capability("gps", "GPS", gpsState(), gpsDetail()),
        Capability("ir", "Infrared blaster", irState(), irDetail()),
        Capability("accelerometer", "Accelerometer", sensorState(Sensor.TYPE_ACCELEROMETER, "Accelerometer"), sensorDetail(Sensor.TYPE_ACCELEROMETER)),
        Capability("gyroscope", "Gyroscope", sensorState(Sensor.TYPE_GYROSCOPE, "Gyroscope"), sensorDetail(Sensor.TYPE_GYROSCOPE)),
        Capability("compass", "Magnetometer", sensorState(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer"), sensorDetail(Sensor.TYPE_MAGNETIC_FIELD)),
        Capability("light", "Light sensor", sensorState(Sensor.TYPE_LIGHT, "Light sensor"), sensorDetail(Sensor.TYPE_LIGHT)),
        Capability("proximity", "Proximity sensor", sensorState(Sensor.TYPE_PROXIMITY, "Proximity sensor"), sensorDetail(Sensor.TYPE_PROXIMITY)),
        Capability("barometer", "Barometer", sensorState(Sensor.TYPE_PRESSURE, "Barometer"), sensorDetail(Sensor.TYPE_PRESSURE)),
        Capability("wifi", "Wi-Fi", networkState(), networkDetail()),
        Capability("bluetooth", "Bluetooth", if (hasBluetooth) SupportState.AVAILABLE else SupportState.NOT_PRESENT, if (hasBluetooth) "Bluetooth radio present" else "No Bluetooth radio"),
        Capability("vibrator", "Vibrator", if (hasVibrator) SupportState.AVAILABLE else SupportState.NOT_PRESENT, if (hasVibrator) "Vibration motor present" else "No vibration motor")
    )

    private fun cameraState(): SupportState = when {
        !hasCamera -> SupportState.NOT_PRESENT
        !hasCameraPermission -> SupportState.PERMISSION_REQUIRED
        else -> SupportState.AVAILABLE
    }

    private fun cameraDetail(): String = when {
        !hasCamera -> "No camera reported by the platform"
        !hasCameraPermission -> "Camera permission has not been granted"
        else -> "${cameraIds.size} camera(s): ${cameraLabels.values.joinToString(", ")}"
    }

    private fun torchDetail(): String = when {
        !hasTorch -> "No camera with a flash unit was found"
        !hasCameraPermission -> "Camera permission has not been granted"
        else -> "Flash unit available on ${camerasWithFlash.size} camera(s)"
    }

    private fun micState(): SupportState = when {
        !hasMicrophone -> SupportState.NOT_PRESENT
        !hasMicrophonePermission -> SupportState.PERMISSION_REQUIRED
        else -> SupportState.AVAILABLE
    }

    private fun micDetail(): String = when {
        !hasMicrophone -> "No microphone reported by the platform"
        !hasMicrophonePermission -> "Microphone permission has not been granted"
        else -> "Microphone ready"
    }

    private fun gpsState(): SupportState = when {
        !hasGpsProvider -> SupportState.NOT_PRESENT
        !hasLocationPermission -> SupportState.PERMISSION_REQUIRED
        else -> SupportState.AVAILABLE
    }

    private fun gpsDetail(): String = when {
        !hasGpsProvider -> "No GPS provider on this device"
        !hasLocationPermission -> "Location permission has not been granted"
        else -> "GNSS provider active"
    }

    private fun irState(): SupportState = when {
        !hasIrEmitter -> SupportState.NOT_PRESENT
        else -> SupportState.AVAILABLE
    }

    private fun irDetail(): String = when {
        !hasIrEmitter -> "This model has no infrared emitter"
        else -> "Infrared emitter present"
    }

    private fun sensorState(type: Int, label: String): SupportState =
        if (sensorsByType.containsKey(type)) SupportState.AVAILABLE else SupportState.NOT_PRESENT

    private fun sensorDetail(type: Int): String {
        val sensor = sensorsByType[type] ?: return "Not reported by this model"
        return "${sensor.name} (${sensor.vendor})"
    }

    private fun networkState(): SupportState =
        if (hasWifi || hasCellular) SupportState.AVAILABLE else SupportState.NOT_PRESENT

    private fun networkDetail(): String = when {
        hasWifi && hasCellular -> "Wi-Fi and mobile data available"
        hasWifi -> "Wi-Fi available"
        hasCellular -> "Mobile data available"
        else -> "No active network"
    }

    fun vibrate(millis: Long = 40) {
        if (!hasVibrator) return
        try {
            @Suppress("DEPRECATION")
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(millis)
            }
        } catch (e: Exception) {
            // vibration is a nice-to-have, never fatal
        }
    }

    companion object {
        @Volatile
        private var instance: HardwareCapabilities? = null

        fun get(context: Context): HardwareCapabilities {
            return instance ?: synchronized(this) {
                instance ?: detect(context.applicationContext).also { instance = it }
            }
        }

        /** Re-runs detection, e.g. after the user grants a permission. */
        fun refresh(context: Context): HardwareCapabilities = synchronized(this) {
            detect(context.applicationContext).also { instance = it }
        }

        private fun detect(context: Context): HardwareCapabilities {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraIds = try {
                cameraManager?.cameraIdList?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val labels = HashMap<String, String>()
            val withFlash = ArrayList<String>()
            val front = ArrayList<String>()
            for (id in cameraIds) {
                val chars = try {
                    cameraManager?.getCameraCharacteristics(id)
                } catch (e: Exception) {
                    null
                }
                val facing = chars?.get(CameraCharacteristics.LENS_FACING)
                val flash = chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val label = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    else -> "External"
                }
                labels[id] = label
                if (flash) withFlash.add(id)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) front.add(id)
            }

            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensors = try {
                sensorManager?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val byType = LinkedHashMap<Int, Sensor>()
            for (sensor in sensors) {
                if (!byType.containsKey(sensor.type)) byType[sensor.type] = sensor
            }

            val irManager = context.getSystemService("consumer_ir") as? ConsumerIrManager
            val hasIr = try {
                irManager?.hasIrEmitter() == true
            } catch (e: Exception) {
                false
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val hasGps = try {
                locationManager?.getProviders(true)?.contains(LocationManager.GPS_PROVIDER) == true
            } catch (e: Exception) {
                false
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val hasMic = try {
                audioManager?.getMicrophones()?.isNotEmpty() == true ||
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            } catch (e: Exception) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            }

            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            var wifi = false
            var cellular = false
            try {
                val network = connectivity?.activeNetwork
                val caps = network?.let { connectivity.getNetworkCapabilities(it) }
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            } catch (e: Exception) {
                // ignore
            }

            val hasBluetooth = try {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            } catch (e: Exception) {
                false
            }

            val hasVibrator = context.packageManager.hasSystemFeature("android.hardware.vibrator")
            val hasBarometer = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)

            return HardwareCapabilities(
                appContext = context,
                cameraIds = cameraIds,
                cameraLabels = labels,
                camerasWithFlash = withFlash,
                frontFacingCameras = front,
                sensorTypes = sensors.map { it.type },
                sensorsByType = byType,
                hasIrEmitter = hasIr,
                hasGpsProvider = hasGps,
                hasMicrophone = hasMic,
                hasWifi = wifi,
                hasCellular = cellular,
                hasBluetooth = hasBluetooth,
                hasVibrator = hasVibrator,
                hasBarometer = hasBarometer,
                hasMicrophonePermission = context.hasPermission(Manifest.permission.RECORD_AUDIO),
                hasCameraPermission = context.hasPermission(Manifest.permission.CAMERA),
                hasLocationPermission = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
