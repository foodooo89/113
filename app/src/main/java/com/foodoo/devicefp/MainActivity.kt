package com.foodoo.devicefp

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvOutput: TextView
    private var lastReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.tvOutput)
        findViewById<android.widget.Button>(R.id.btnRefresh).setOnClickListener { generateReport() }
        findViewById<android.widget.Button>(R.id.btnCopy).setOnClickListener { copyToClipboard() }
        findViewById<android.widget.Button>(R.id.btnShare).setOnClickListener { shareReport() }

        // Xin các quyền cần thiết (không bắt buộc, app vẫn chạy nếu bị từ chối)
        val perms = mutableListOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }

        generateReport()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        generateReport()
    }

    private fun sb(title: String, block: StringBuilder.() -> Unit): String {
        val s = StringBuilder()
        s.append("\n===== $title =====\n")
        s.block()
        return s.toString()
    }

    @Suppress("DEPRECATION")
    private fun generateReport() {
        val out = StringBuilder()
        out.append("DEVICE FINGERPRINT REPORT\n")
        out.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")

        // ===== BUILD / SOFTWARE =====
        out.append(sb("BUILD (SOFTWARE)") {
            appendLine("FINGERPRINT     : ${Build.FINGERPRINT}")
            appendLine("MODEL           : ${Build.MODEL}")
            appendLine("BRAND           : ${Build.BRAND}")
            appendLine("MANUFACTURER    : ${Build.MANUFACTURER}")
            appendLine("PRODUCT         : ${Build.PRODUCT}")
            appendLine("DEVICE          : ${Build.DEVICE}")
            appendLine("BOARD           : ${Build.BOARD}")
            appendLine("HARDWARE        : ${Build.HARDWARE}")
            appendLine("BOOTLOADER      : ${Build.BOOTLOADER}")
            appendLine("DISPLAY         : ${Build.DISPLAY}")
            appendLine("ID              : ${Build.ID}")
            appendLine("TAGS            : ${Build.TAGS}")
            appendLine("TYPE            : ${Build.TYPE}")
            appendLine("USER            : ${Build.USER}")
            appendLine("HOST            : ${Build.HOST}")
            appendLine("TIME            : ${Build.TIME}")
            appendLine("RADIO_VERSION   : ${Build.getRadioVersion()}")
            appendLine("SERIAL          : ${getSerial()}")
            appendLine("SUPPORTED_ABIS  : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("SUPPORTED_32ABI : ${Build.SUPPORTED_32_BIT_ABIS.joinToString()}")
            appendLine("SUPPORTED_64ABI : ${Build.SUPPORTED_64_BIT_ABIS.joinToString()}")
            appendLine("SDK_INT         : ${Build.VERSION.SDK_INT}")
            appendLine("RELEASE (OS ver): ${Build.VERSION.RELEASE}")
            appendLine("CODENAME        : ${Build.VERSION.CODENAME}")
            appendLine("INCREMENTAL     : ${Build.VERSION.INCREMENTAL}")
            appendLine("SECURITY_PATCH  : ${Build.VERSION.SECURITY_PATCH}")
            appendLine("BASE_OS         : ${Build.VERSION.BASE_OS}")
            appendLine("PREVIEW_SDK_INT : ${Build.VERSION.PREVIEW_SDK_INT}")
        })

        // ===== IDENTIFIERS =====
        out.append(sb("IDENTIFIERS") {
            appendLine("ANDROID_ID      : ${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}")
            appendLine("IMEI/DeviceId   : ${getDeviceId()}")
        })

        // ===== TELEPHONY / SIM =====
        out.append(sb("TELEPHONY") {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                appendLine("Network Operator     : ${tm.networkOperatorName}")
                appendLine("SIM Operator         : ${tm.simOperatorName}")
                appendLine("SIM Country          : ${tm.simCountryIso}")
                appendLine("SIM State            : ${simStateToString(tm.simState)}")
                appendLine("Phone Type           : ${phoneTypeToString(tm.phoneType)}")
                appendLine("Network Type         : ${tm.networkType}")
                appendLine("Is Network Roaming   : ${tm.isNetworkRoaming}")
            } catch (e: SecurityException) {
                appendLine("(Không đủ quyền để đọc đầy đủ thông tin SIM)")
            }
        })

        // ===== CPU =====
        out.append(sb("CPU") {
            appendLine("Số nhân (cores) : ${Runtime.getRuntime().availableProcessors()}")
            appendLine(readProcCpuInfo())
        })

        // ===== RAM / STORAGE =====
        out.append(sb("RAM & STORAGE") {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            appendLine("Tổng RAM        : ${mi.totalMem / (1024 * 1024)} MB")
            appendLine("RAM khả dụng    : ${mi.availMem / (1024 * 1024)} MB")
            appendLine("Low Memory      : ${mi.lowMemory}")

            val statFs = StatFs(Environment.getDataDirectory().path)
            val total = statFs.blockCountLong * statFs.blockSizeLong
            val free = statFs.availableBlocksLong * statFs.blockSizeLong
            appendLine("Tổng bộ nhớ trong: ${total / (1024 * 1024)} MB")
            appendLine("Còn trống       : ${free / (1024 * 1024)} MB")
        })

        // ===== DISPLAY =====
        out.append(sb("DISPLAY") {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
            appendLine("Resolution      : ${dm.widthPixels} x ${dm.heightPixels}")
            appendLine("Density         : ${dm.density} (dpi=${dm.densityDpi})")
            appendLine("ScaledDensity   : ${dm.scaledDensity}")
            appendLine("XDPI / YDPI     : ${dm.xdpi} / ${dm.ydpi}")
            val refreshRate = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
            appendLine("Refresh Rate    : $refreshRate Hz")
        })

        // ===== SENSORS =====
        out.append(sb("SENSORS") {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            appendLine("Số cảm biến     : ${sensors.size}")
            for (s in sensors) {
                appendLine("  - ${s.name} (${s.vendor}) v${s.version}")
            }
        })

        // ===== NETWORK / WIFI =====
        out.append(sb("NETWORK") {
            try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wm.connectionInfo
                appendLine("WiFi MAC        : ${info.macAddress}")
                appendLine("WiFi SSID       : ${info.ssid}")
                appendLine("IP Address(int) : ${info.ipAddress}")
            } catch (e: Exception) {
                appendLine("(Không đọc được WiFi info: ${e.message})")
            }
        })

        // ===== KERNEL =====
        out.append(sb("KERNEL / UNAME") {
            appendLine(System.getProperty("os.version") ?: "N/A")
            appendLine(runShell("uname -a"))
        })

        // ===== FULL GETPROP DUMP =====
        out.append(sb("GETPROP (FULL SYSTEM PROPERTIES)") {
            appendLine(runShell("getprop"))
        })

        // ===== HARDWARE FEATURES (PackageManager) =====
        out.append(sb("HARDWARE / SOFTWARE FEATURES") {
            try {
                val features = packageManager.systemAvailableFeatures
                    .mapNotNull { it.name }
                    .sorted()
                appendLine("Tổng số feature: ${features.size}")
                for (f in features) appendLine("  - $f")
            } catch (e: Exception) {
                appendLine("(Lỗi đọc features: ${e.message})")
            }
        })

        // ===== CAMERA =====
        out.append(sb("CAMERA") {
            try {
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                for (id in cm.cameraIdList) {
                    val ch = cm.getCameraCharacteristics(id)
                    val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                        else -> "UNKNOWN"
                    }
                    val sensorSize = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val pixelArray = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    val focalLengths = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val hwLevel = ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    appendLine("Camera ID $id: facing=$facing, sensorSize=$sensorSize, pixelArray=$pixelArray, focalLengths=${focalLengths?.joinToString()}, hwLevel=$hwLevel")
                }
            } catch (e: Exception) {
                appendLine("(Lỗi đọc camera: ${e.message})")
            }
        })

        // ===== BLUETOOTH =====
        out.append(sb("BLUETOOTH") {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    appendLine("Thiết bị không có Bluetooth adapter")
                } else {
                    appendLine("Name    : ${adapter.name}")
                    appendLine("Address : ${adapter.address}")
                    appendLine("Enabled : ${adapter.isEnabled}")
                }
            } catch (e: Exception) {
                appendLine("(Lỗi đọc bluetooth: ${e.message})")
            }
        })

        // ===== BATTERY =====
        out.append(sb("BATTERY") {
            try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                appendLine("Capacity        : ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%")
                val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val health = status?.getIntExtra("health", -1)
                val tech = status?.getStringExtra("technology")
                val temp = status?.getIntExtra("temperature", -1)
                val voltage = status?.getIntExtra("voltage", -1)
                appendLine("Technology      : $tech")
                appendLine("Temperature     : ${(temp ?: -1) / 10.0} °C")
                appendLine("Voltage         : ${voltage} mV")
                appendLine("Health code     : $health")
            } catch (e: Exception) {
                appendLine("(Lỗi đọc battery: ${e.message})")
            }
        })

        // ===== NETWORK INTERFACES =====
        out.append(sb("NETWORK INTERFACES") {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses).joinToString { it.hostAddress ?: "" }
                    val mac = intf.hardwareAddress?.joinToString(":") { String.format("%02X", it) } ?: "N/A"
                    appendLine("${intf.name}: MAC=$mac, IP=[$addrs], up=${intf.isUp}, loopback=${intf.isLoopback}")
                }
            } catch (e: Exception) {
                appendLine("(Lỗi đọc network interfaces: ${e.message})")
            }
        })

        // ===== STORAGE PARTITIONS (/proc/mounts) =====
        out.append(sb("STORAGE PARTITIONS (/proc/mounts)") {
            appendLine(runShell("cat /proc/mounts"))
        })

        // ===== CPU INFO CHI TIẾT HƠN (/proc/cpuinfo full + /sys freq) =====
        out.append(sb("CPU FREQUENCY (nếu đọc được)") {
            appendLine(runShell("cat /sys/devices/system/cpu/possible"))
            appendLine(runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"))
            appendLine(runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"))
        })

        // ===== SYSTEM PROPERTIES (java) =====
        out.append(sb("JAVA SYSTEM PROPERTIES") {
            appendLine("java.vm.version : ${System.getProperty("java.vm.version")}")
            appendLine("os.arch         : ${System.getProperty("os.arch")}")
            appendLine("user.timezone   : ${System.getProperty("user.timezone")}")
            appendLine("Locale          : ${Locale.getDefault()}")
        })

        lastReport = out.toString()
        tvOutput.text = lastReport
    }

    private fun getSerial(): String {
        return try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else Build.SERIAL
        } catch (e: SecurityException) {
            "PERMISSION_DENIED"
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun getDeviceId(): String {
        return try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.deviceId ?: "N/A (null)"
            } else {
                "PERMISSION_NOT_GRANTED"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun simStateToString(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
        TelephonyManager.SIM_STATE_READY -> "READY"
        TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
        else -> "OTHER($state)"
    }

    private fun phoneTypeToString(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_NONE -> "NONE"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        else -> "OTHER($type)"
    }

    private fun readProcCpuInfo(): String {
        return try {
            val f = File("/proc/cpuinfo")
            if (!f.exists()) return "(/proc/cpuinfo không truy cập được)"
            val br = BufferedReader(FileReader(f))
            val text = br.readText()
            br.close()
            // Lấy các dòng quan trọng, tránh in quá dài nếu nhiều core
            text.lineSequence()
                .filter { it.startsWith("Hardware") || it.startsWith("Processor") || it.startsWith("model name") }
                .distinct()
                .joinToString("\n")
                .ifBlank { text.take(500) }
        } catch (e: Exception) {
            "(Lỗi đọc cpuinfo: ${e.message})"
        }
    }

    private fun runShell(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.ifBlank { "(không có output)" }
        } catch (e: Exception) {
            "(Không chạy được lệnh: ${e.message})"
        }
    }

    private fun copyToClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("fingerprint", lastReport))
        Toast.makeText(this, "Đã copy report", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, lastReport)
        startActivity(Intent.createChooser(intent, "Share report"))
    }
}
