package in.trustwbhmai.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var networkText: TextView
    private lateinit var attendanceButton: Button
    private lateinit var printerButton: Button
    private lateinit var chatButton: Button

    private val attendanceUrl = "https://66wbshpc26.trustwbhmai.in/attendance"
    private val chatUrl = "http://66wbshpc26.trustwbhmai.in/chat"

    private var printerSocket: BluetoothSocket? = null
    private var printerOutput: OutputStream? = null
    private var serverThread: Thread? = null
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissionsIfNeeded()
        buildShell()
        showAttendance()
        updateNetworkStatus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        networkText = TextView(this).apply {
            text = "  Network: checking…"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.DKGRAY)
            setBackgroundResource(R.drawable.status_bg)
            layoutParams = LinearLayout.LayoutParams(-1, dp(42))
        }
        root.addView(networkText)

        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(content)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.rgb(74, 120, 200))
        }

        attendanceButton = navButton("Attendance")
        printerButton = navButton("Printer Connection")
        chatButton = navButton("Chat")

        nav.addView(attendanceButton, LinearLayout.LayoutParams(0, dp(64), 1.0f).apply { marginEnd = dp(6) })
        nav.addView(printerButton, LinearLayout.LayoutParams(0, dp(64), 1.35f).apply { marginEnd = dp(6) })
        nav.addView(chatButton, LinearLayout.LayoutParams(0, dp(64), 0.8f))
        root.addView(nav)

        setContentView(root)

        attendanceButton.setOnClickListener { showAttendance() }
        printerButton.setOnClickListener { showPrinter() }
        chatButton.setOnClickListener { showChat() }
    }

    private fun navButton(label: String): Button =
        Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setBackgroundResource(R.drawable.bottom_button)
            stateListAnimator = null
        }

    private fun select(button: Button) {
        listOf(attendanceButton, printerButton, chatButton).forEach {
            it.setBackgroundResource(R.drawable.bottom_button)
        }
        button.setBackgroundResource(R.drawable.bottom_button_selected)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webPage(url: String): WebView =
        WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }

    private fun showAttendance() {
        select(attendanceButton)
        content.removeAllViews()
        content.addView(webPage(attendanceUrl), FrameLayout.LayoutParams(-1, -1))
    }

    private fun showChat() {
        select(chatButton)
        content.removeAllViews()
        content.addView(webPage(chatUrl), FrameLayout.LayoutParams(-1, -1))
    }

    private fun showPrinter() {
        select(printerButton)
        content.removeAllViews()

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(box)

        val title = TextView(this).apply {
            text = "Printer Connection"
            textSize = 25f
            setTextColor(Color.rgb(49, 95, 174))
            setPadding(0, 0, 0, dp(8))
        }
        box.addView(title)

        val info = TextView(this).apply {
            text = "LD0801 Bluetooth thermal printer"
            textSize = 17f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(18))
        }
        box.addView(info)

        val deviceSpinner = Spinner(this)
        box.addView(deviceSpinner, LinearLayout.LayoutParams(-1, dp(52)))

        val refresh = Button(this).apply {
            text = "Refresh Paired Printers"
            isAllCaps = false
        }
        box.addView(refresh)

        val connect = Button(this).apply {
            text = "Connect Printer"
            isAllCaps = false
        }
        box.addView(connect)

        val server = Button(this).apply {
            text = "Start Local Print Server"
            isAllCaps = false
        }
        box.addView(server)

        val status = TextView(this).apply {
            text = "Status: Not connected"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(18), 0, 0)
        }
        box.addView(status)

        val endpoint = TextView(this).apply {
            text = "Local print endpoint: http://127.0.0.1:9101/print"
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(endpoint)

        val names = mutableListOf<String>()
        val devices = mutableListOf<BluetoothDevice>()
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

        fun refreshDevices() {
            names.clear()
            devices.clear()
            try {
                if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    status.text = "Status: Bluetooth permission required"
                    return
                }
                adapter?.bondedDevices?.forEach { d ->
                    names.add("${d.name ?: "Unknown"}\n${d.address}")
                    devices.add(d)
                }
            } catch (e: Exception) {
                status.text = "Status: ${e.message}"
            }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }

        refresh.setOnClickListener { refreshDevices() }

        connect.setOnClickListener {
            if (devices.isEmpty()) {
                Toast.makeText(this, "Pair the LD0801 in Android Bluetooth settings first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val device = devices[deviceSpinner.selectedItemPosition]
            thread {
                try {
                    if (Build.VERSION.SDK_INT >= 31 &&
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        runOnUiThread { status.text = "Status: Bluetooth permission required" }
                        return@thread
                    }
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    val socket = device.createRfcommSocketToServiceRecord(uuid)
                    adapter?.cancelDiscovery()
                    socket.connect()
                    printerSocket = socket
                    printerOutput = socket.outputStream
                    runOnUiThread { status.text = "Status: Connected to ${device.name ?: device.address}" }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Status: Connection failed — ${e.message}" }
                }
            }
        }

        server.setOnClickListener {
            if (serverRunning) {
                serverRunning = false
                server.text = "Start Local Print Server"
                status.text = "Status: Print server stopped"
            } else {
                if (printerOutput == null) {
                    Toast.makeText(this, "Connect the printer first.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startLocalPrintServer(status)
                server.text = "Stop Local Print Server"
            }
        }

        refreshDevices()
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun startLocalPrintServer(status: TextView) {
        serverRunning = true
        status.text = "Status: Local print server running on 127.0.0.1:9101"
        serverThread = thread {
            try {
                val server = java.net.ServerSocket(9101, 20, java.net.InetAddress.getByName("127.0.0.1"))
                while (serverRunning) {
                    val client = server.accept()
                    thread {
                        try {
                            val input = client.getInputStream()
                            val request = input.readBytes()
                            val separator = request.indexOfSequence("\r\n\r\n".toByteArray())
                            val body = if (separator >= 0) request.copyOfRange(separator + 4, request.size) else request
                            printerOutput?.write(body)
                            printerOutput?.flush()
                            client.getOutputStream().use {
                                it.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray())
                            }
                        } catch (_: Exception) {
                        } finally {
                            client.close()
                        }
                    }
                }
                server.close()
            } catch (e: Exception) {
                runOnUiThread { status.text = "Status: Server error — ${e.message}" }
            }
        }
    }

    private fun ByteArray.indexOfSequence(seq: ByteArray): Int {
        outer@ for (i in 0..size - seq.size) {
            for (j in seq.indices) if (this[i + j] != seq[j]) continue@outer
            return i
        }
        return -1
    }

    private fun updateNetworkStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        networkText.text = if (online) "  Network: Connected" else "  Network: Offline"
        networkText.setTextColor(if (online) Color.rgb(46, 155, 89) else Color.rgb(217, 83, 79))
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                1001
            )
        }
    }

    override fun onDestroy() {
        serverRunning = false
        try { printerSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}