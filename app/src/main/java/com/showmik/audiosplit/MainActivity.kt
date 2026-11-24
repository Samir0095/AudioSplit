package com.showmik.audiosplit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import android.app.Activity
import kotlin.math.sqrt

class MainActivity : Activity() {
    private lateinit var audioManager: AudioManager
    private lateinit var statusText: TextView
    private lateinit var devicesText: TextView
    private lateinit var micText: TextView
    private lateinit var routeText: TextView
    private lateinit var refreshButton: Button
    private lateinit var micButton: Button
    private lateinit var routeButton: Button
    private lateinit var clearRouteButton: Button
    private var recorder: AudioRecord? = null
    private var micRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var routeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildUi()
        refreshDevices()
        askMicPermission()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        val title = TextView(this).apply { text = "AudioSplit"; textSize = 30f; setTextColor(0xFF172033.toInt()); setPadding(0,0,0,6) }
        val subtitle = TextView(this).apply { text = "Experimental audio device routing"; textSize = 15f; setTextColor(0xFF68738A.toInt()) }
        content.addView(title); content.addView(subtitle)
        content.addView(space(20))
        statusText = cardText("Status: Checking audio devices…", 17f, 0xFF172033.toInt())
        content.addView(statusText)
        content.addView(space(12))
        devicesText = cardText("Connected devices will appear here.", 15f, 0xFF29344A.toInt())
        content.addView(devicesText)
        content.addView(space(12))
        refreshButton = button("Refresh device list") { refreshDevices() }
        content.addView(refreshButton)
        content.addView(space(20))
        micText = cardText("Microphone test: not running", 15f, 0xFF29344A.toInt())
        content.addView(micText)
        content.addView(space(8))
        micButton = button("Start microphone test") { toggleMicTest() }
        content.addView(micButton)
        content.addView(space(20))
        routeText = cardText("Experimental route: inactive\nThis controls AudioSplit's audio route only; it cannot guarantee control over WhatsApp.", 15f, 0xFF29344A.toInt())
        content.addView(routeText)
        content.addView(space(8))
        routeButton = button("Try Bluetooth communication route") { tryBluetoothRoute() }
        content.addView(routeButton)
        content.addView(space(8))
        clearRouteButton = button("Clear AudioSplit route") { clearRoute() }
        content.addView(clearRouteButton)
        content.addView(space(24))
        val note = TextView(this).apply {
            text = "Important: Android lets an app select a communication OUTPUT device, and the matching microphone is normally chosen automatically. AudioSplit can test your phone's behavior, but ordinary apps cannot force WhatsApp to use a different microphone."
            textSize = 13f; setTextColor(0xFF657089.toInt())
        }
        content.addView(note)
        root.addView(scroll)
        setContentView(root)
    }

    private fun space(dp: Int) = TextView(this).apply { height = (dp * resources.displayMetrics.density).toInt() }
    private fun cardText(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(20, 18, 20, 18)
        setBackgroundColor(0xFFFFFFFF.toInt())
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun askMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputLines = inputs.map { "• ${deviceName(it)} (input)" }
        val outputLines = outputs.map { "• ${deviceName(it)} (output)" }
        devicesText.text = "INPUT DEVICES\n${inputLines.joinToString("\n").ifEmpty { "• None detected" }}\n\nOUTPUT DEVICES\n${outputLines.joinToString("\n").ifEmpty { "• None detected" }}"
        val wiredMic = inputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        val btOut = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) }
        statusText.text = "Device status\n${if (wiredMic) "✓ Wired/USB microphone detected" else "• Wired microphone not detected"}\n${if (btOut) "✓ Bluetooth output detected" else "• Bluetooth headphones not detected"}\n\nTarget: BOYA mic + HOCO headphones"
    }

    private fun deviceName(device: AudioDeviceInfo): String {
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset/mic"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth headphones (media)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset (calls)"
            else -> "Audio device type ${device.type}"
        }
        return "${device.productName} — $type"
    }

    private fun toggleMicTest() {
        if (micRunning) stopMicTest() else startMicTest()
    }

    @SuppressLint("MissingPermission")
    private fun startMicTest() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { askMicPermission(); return }
        val min = AudioRecord.getMinBufferSize(16000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { micText.text = "Mic test failed: device audio configuration unavailable"; return }
        try {
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, min * 2)
            val rec = recorder!!
            rec.startRecording()
            micRunning = true
            micButton.text = "Stop microphone test"
            micText.text = "Mic test running… speak into your BOYA mic."
            Thread {
                val buffer = ShortArray(min / 2)
                while (micRunning) {
                    val count = rec.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        var sum = 0.0
                        for (i in 0 until count) { val v = buffer[i].toDouble(); sum += v * v }
                        val rms = sqrt(sum / count)
                        val db = if (rms > 0) 20 * kotlin.math.log10(rms) else -90.0
                        handler.post { micText.text = "Mic test running\nInput level: ${db.toInt()} dB\nSpeak into your BOYA mic. If the level changes, microphone input is detected." }
                    }
                }
            }.start()
        } catch (e: Exception) { micText.text = "Mic test error: ${e.message}"; stopMicTest() }
    }

    private fun stopMicTest() {
        micRunning = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        micButton.text = "Start microphone test"
        micText.text = "Microphone test stopped."
    }

    private fun tryBluetoothRoute() {
        if (Build.VERSION.SDK_INT < 31) { routeText.text = "Communication-device routing requires Android 12 or newer."; return }
        val candidates = audioManager.availableCommunicationDevices
        val bluetooth = candidates.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) }
        if (bluetooth == null) { routeText.text = "No Bluetooth communication output available. Connect your HOCO headphones and retry."; return }
        val success = audioManager.setCommunicationDevice(bluetooth)
        routeActive = success
        routeText.text = if (success) "Route request accepted for ${bluetooth.productName}.\nAndroid automatically chooses the matching input for this communication route. This does NOT force WhatsApp to use the BOYA mic." else "Android rejected the route request. WhatsApp or the system may currently own communication audio."
    }

    private fun clearRoute() {
        if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
        routeActive = false
        routeText.text = "Experimental route: cleared."
    }

    override fun onDestroy() {
        stopMicTest()
        if (Build.VERSION.SDK_INT >= 31 && routeActive) audioManager.clearCommunicationDevice()
        super.onDestroy()
    }
}
