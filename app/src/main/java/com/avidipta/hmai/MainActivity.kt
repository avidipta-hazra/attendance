package com.avidipta.hmai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.view.Gravity
import android.graphics.Color
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var networkText: TextView
    private lateinit var attendanceButton: Button
    private lateinit var printerButton: Button
    private lateinit var chatButton: Button

    private var status: TextView? = null
    private var deviceSpinner: Spinner? = null
    private var connectButton: Button? = null
    private var serverButton: Button? = null

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var serverSocket: ServerSocket? = null

    private val printExecutor = Executors.newSingleThreadExecutor()
    private val httpExecutor = Executors.newCachedThreadPool()

    private val sppUuid =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val devices = mutableListOf<BluetoothDevice>()

    companion object {
        private const val REQ_BT = 1001
        private const val PORT = 9101
        private const val WIDTH_DOTS = 400
        private const val WIDTH_BYTES = 50
        private const val HEIGHT_DOTS = 240

        private const val ATTENDANCE_URL =
            "https://66wbshpc26.trustwbhmai.in/attendance"
        private const val CHAT_URL =
            "http://66wbshpc26.trustwbhmai.in/chat"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        requestBluetoothPermissions()
        updateNetworkStatus()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        networkText = TextView(this).apply {
            text = "  Network: checking..."
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.DKGRAY)
            setBackgroundResource(R.drawable.status_bg)
        }
        root.addView(networkText, LinearLayout.LayoutParams(-1, dp(42)))

        content = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.rgb(74, 120, 200))
        }

        attendanceButton = navButton("Attendance")
        printerButton = navButton("Printer Connection")
        chatButton = navButton("Chat")

        nav.addView(attendanceButton,
            LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(6) })
        nav.addView(printerButton,
            LinearLayout.LayoutParams(0, dp(64), 1.35f).apply { marginEnd = dp(6) })
        nav.addView(chatButton,
            LinearLayout.LayoutParams(0, dp(64), .8f))

        root.addView(nav)
        setContentView(root)

        attendanceButton.setOnClickListener { showAttendance() }
        printerButton.setOnClickListener { showPrinter() }
        chatButton.setOnClickListener { showChat() }

        showAttendance()
    }

    private fun navButton(label: String) = Button(this).apply {
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
    private fun makeWebView(url: String): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            // Attendance is HTTPS while the printer bridge is on
            // http://127.0.0.1:9101. The WebView must be allowed to
            // call the local HTTP bridge.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }

    private fun showAttendance() {
        select(attendanceButton)
        content.removeAllViews()
        content.addView(makeWebView(ATTENDANCE_URL),
            FrameLayout.LayoutParams(-1, -1))
    }

    private fun showChat() {
        select(chatButton)
        content.removeAllViews()
        content.addView(makeWebView(CHAT_URL),
            FrameLayout.LayoutParams(-1, -1))
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

        box.addView(TextView(this).apply {
            text = "Printer Connection"
            textSize = 25f
            setTextColor(Color.rgb(49, 95, 174))
            setPadding(0, 0, 0, dp(8))
        })

        box.addView(TextView(this).apply {
            text = "LD0801 Bluetooth thermal printer"
            textSize = 17f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(18))
        })

        val spinner = Spinner(this)
        deviceSpinner = spinner
        box.addView(spinner, LinearLayout.LayoutParams(-1, dp(52)))

        val refresh = Button(this).apply {
            text = "Refresh Paired Printers"
            isAllCaps = false
        }
        box.addView(refresh)

        val connect = Button(this).apply {
            text = "Connect Printer"
            isAllCaps = false
        }
        connectButton = connect
        box.addView(connect)

        val server = Button(this).apply {
            text = if (serverSocket != null) "Stop Local Print Server"
                    else "Start Local Print Server"
            isAllCaps = false
        }
        serverButton = server
        box.addView(server)

        val stat = TextView(this).apply {
            text = if (socket?.isConnected == true)
                "Status: Printer connected"
            else if (serverSocket != null)
                "Status: Local print server running on :$PORT"
            else
                "Status: Not connected"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(18), 0, 0)
        }
        status = stat
        box.addView(stat)

        box.addView(TextView(this).apply {
            text = "Local print endpoint: http://127.0.0.1:9101/print"
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        })

        refresh.setOnClickListener { refreshBondedDevices() }
        connect.setOnClickListener { connectSelectedPrinter() }
        server.setOnClickListener {
            if (serverSocket == null) startLocalServer()
            else stopLocalServer()
        }

        refreshBondedDevices()
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), REQ_BT)
            }
        } else {
            refreshBondedDevices()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshBondedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        if (!hasBluetoothPermissions()) return

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            status?.text = "Status: Bluetooth is not available"
            return
        }

        try {
            devices.clear()
            devices.addAll(adapter.bondedDevices.sortedBy { it.name ?: it.address })

            val names = devices.map {
                "${it.name ?: "Unnamed"} (${it.address})"
            }

            deviceSpinner?.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names.ifEmpty { listOf("No paired Bluetooth devices") }
            )

            if (devices.isNotEmpty()) {
                status?.text = "Status: Select LD0801 and connect."
            }
        } catch (e: Exception) {
            status?.text = "Status: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSelectedPrinter() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        if (devices.isEmpty()) {
            refreshBondedDevices()
            Toast.makeText(
                this,
                "Pair the LD0801 in Android Bluetooth settings first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val index = deviceSpinner?.selectedItemPosition ?: 0
        val device = devices[index.coerceIn(0, devices.lastIndex)]

        status?.text = "Status: Connecting to ${device.name ?: device.address}..."

        Thread {
            try {
                // Always close the old socket before creating a new one.
                try { output?.flush() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}

                socket = null
                output = null

                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()

                socket = s
                output = BufferedOutputStream(s.outputStream)

                runOnUiThread {
                    status?.text =
                        "Status: CONNECTED — ${device.name ?: device.address}"
                    connectButton?.text = "Reconnect Printer"
                }
            } catch (e: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                output = null

                runOnUiThread {
                    status?.text =
                        "Status: Connection failed — ${e.message ?: "unknown error"}"
                    connectButton?.text = "Connect Printer"
                }
            }
        }.start()
    }

    private fun startLocalServer() {
        // Prevent duplicate binds. This fixes the EADDRINUSE shown in
        // the screenshot.
        if (serverSocket != null) {
            status?.text = "Status: Print server already running on :$PORT"
            return
        }

        httpExecutor.execute {
            var localServer: ServerSocket? = null
            try {
                localServer = ServerSocket(
                    PORT,
                    50,
                    java.net.InetAddress.getByName("127.0.0.1")
                )

                synchronized(this) {
                    serverSocket = localServer
                }

                runOnUiThread {
                    status?.text =
                        "Status: READY — printer + local server on :$PORT"
                    serverButton?.text = "Stop Local Print Server"
                }

                while (true) {
                    val ss = synchronized(this) { serverSocket } ?: break
                    if (ss.isClosed) break

                    val client = try {
                        ss.accept()
                    } catch (_: IOException) {
                        break
                    }

                    httpExecutor.execute {
                        handleHttp(client)
                    }
                }
            } catch (e: Exception) {
                synchronized(this) {
                    if (serverSocket === localServer) serverSocket = null
                }
                try { localServer?.close() } catch (_: Exception) {}

                runOnUiThread {
                    status?.text =
                        "Status: Server error — ${e.message ?: "unknown error"}"
                    serverButton?.text = "Start Local Print Server"
                }
            }
        }
    }

    private fun stopLocalServer() {
        val ss = synchronized(this) {
            val current = serverSocket
            serverSocket = null
            current
        }

        try { ss?.close() } catch (_: Exception) {}

        runOnUiThread {
            status?.text = if (socket?.isConnected == true)
                "Status: Printer connected; local server stopped"
            else
                "Status: Local print server stopped"
            serverButton?.text = "Start Local Print Server"
        }
    }

    private fun handleHttp(client: Socket) {
        client.use { clientSocket ->
            clientSocket.soTimeout = 30000

            val input = BufferedInputStream(clientSocket.getInputStream())
            val out = BufferedOutputStream(clientSocket.getOutputStream())

            val headerBytes = readUntilHeaderEnd(input)
            if (headerBytes == null) {
                writeHttp(out, 400, """{"error":"Bad HTTP request"}""")
                return
            }

            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val firstLine = headerText.substringBefore("\r\n")
            val method = firstLine.substringBefore(" ")
            val path = firstLine.substringAfter(" ").substringBefore(" ")

            if (method.equals("OPTIONS", true)) {
                writeHttp(out, 200, """{"ok":true}""")
                return
            }

            if (method.equals("GET", true) && path == "/") {
                writeHttp(
                    out, 200,
                    """{"service":"LD0801 Native Bridge","status":"ok"}"""
                )
                return
            }

            if (!method.equals("POST", true) || path != "/print") {
                writeHttp(out, 404, """{"error":"Use POST /print"}""")
                return
            }

            val contentLength =
                Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$")
                    .find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            if (contentLength <= 0 || contentLength > 2_000_000) {
                writeHttp(out, 400, """{"error":"Invalid Content-Length"}""")
                return
            }

            val body = ByteArray(contentLength)
            var read = 0

            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n < 0) break
                read += n
            }

            if (read != contentLength) {
                writeHttp(out, 400, """{"error":"Incomplete request body"}""")
                return
            }

            try {
                val json = org.json.JSONObject(String(body, Charsets.UTF_8))
                val content = json.optJSONArray("content")
                    ?: throw IllegalArgumentException("content array missing")

                var base64: String? = null

                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "image") {
                        base64 = item.optString("base64")
                        break
                    }
                }

                if (base64.isNullOrBlank()) {
                    throw IllegalArgumentException("image base64 missing")
                }

                val future: Future<*> = printExecutor.submit {
                    printBase64Png(base64!!)
                }

                future.get(60, TimeUnit.SECONDS)

                runOnUiThread {
                    status?.text = "Status: PRINTED successfully"
                }

                writeHttp(out, 200, """{"status":"printed"}""")
            } catch (e: Exception) {
                runOnUiThread {
                    status?.text =
                        "Status: Print failed — ${e.message ?: "unknown error"}"
                }

                writeHttp(
                    out,
                    500,
                    """{"status":"error","error":${
                        org.json.JSONObject.quote(
                            e.message ?: "Print failed"
                        )
                    }}"""
                )
            }
        }
    }

    private fun readUntilHeaderEnd(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var state = 0

        while (buffer.size() < 65536) {
            val b = input.read()
            if (b < 0) return null

            buffer.write(b)

            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                else -> 0
            }

            if (state == 4) return buffer.toByteArray()
        }

        return null
    }

    private fun writeHttp(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)

        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }

        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)

        out.write(headers)
        out.write(bytes)
        out.flush()
    }

    private fun printBase64Png(base64: String) {
        val bytes = Base64.getDecoder().decode(base64)

        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Invalid PNG image")

        val bitmap =
            if (source.width == WIDTH_DOTS && source.height == HEIGHT_DOTS) {
                source
            } else {
                Bitmap.createScaledBitmap(
                    source,
                    WIDTH_DOTS,
                    HEIGHT_DOTS,
                    true
                )
            }

        try {
            sendDeTongLabel(bitmap)
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    private fun sendDeTongLabel(bitmap: Bitmap) {
        val out = output
            ?: throw IOException("Printer is not connected")

        if (socket?.isConnected != true) {
            throw IOException("Bluetooth printer socket is not connected")
        }

        // Original LD0801 / DeTong protocol.
        val header = byteArrayOf(
            0x1B, 0x40,
            0x1F, 0x27, 0x01,
            WIDTH_BYTES.toByte(),
            0x88.toByte()
        )

        out.write(header)

        val row = ByteArray(WIDTH_BYTES)

        for (y in 0 until HEIGHT_DOTS) {
            Arrays.fill(row, 0)

            for (x in 0 until WIDTH_DOTS) {
                val pixel = bitmap.getPixel(x, y)

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel ushr 24) and 0xFF

                val gray =
                    (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()

                if (a > 20 && gray < 180) {
                    row[x / 8] =
                        (row[x / 8].toInt() or
                            (0x80 shr (x % 8))).toByte()
                }
            }

            val command = byteArrayOf(
                0x1F,
                0x2A,
                (WIDTH_DOTS and 0xFF).toByte(),
                ((WIDTH_DOTS shr 8) and 0xFF).toByte()
            )

            out.write(command)
            out.write(row)

            if (y % 16 == 15) {
                out.flush()
                Thread.sleep(8)
            }
        }

        // Move to the next physical die-cut label.
        out.write(byteArrayOf(0x0C))
        out.flush()

        Thread.sleep(120)
    }

    private fun updateNetworkStatus() {
        val cm =
            getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }

        val online = caps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) == true

        networkText.text =
            if (online) "  Network: Connected"
            else "  Network: Offline"

        networkText.setTextColor(
            if (online) Color.rgb(46, 155, 89)
            else Color.rgb(217, 83, 79)
        )
    }

    override fun onDestroy() {
        try { stopLocalServer() } catch (_: Exception) {}
        try { output?.flush() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}

        output = null
        socket = null

        printExecutor.shutdownNow()
        httpExecutor.shutdownNow()

        super.onDestroy()
    }
}
