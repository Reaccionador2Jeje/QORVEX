package dev.atdev.qorvex.runtime

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal RFB 3.8 client for QEMU's loopback-only VNC display.
 *
 * Supported framebuffer encoding: Raw 32-bit true-colour.
 * Input: RFB key events and pointer events (touch/mouse).
 * This class deliberately connects only to 127.0.0.1:5900.
 */
class VncDisplayClient : Closeable {
    private val socket = Socket()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val writeLock = Any()

    private val mutableFramebuffer = MutableStateFlow<Bitmap?>(null)
    val framebuffer: StateFlow<Bitmap?> = mutableFramebuffer.asStateFlow()

    var framebufferWidth: Int = 0
        private set
    var framebufferHeight: Int = 0
        private set

    suspend fun connect() = withContext(Dispatchers.IO) {
        check(!socket.isConnected) { "VNC client is already connected." }
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress("127.0.0.1", VNC_PORT), CONNECT_TIMEOUT_MS)
        socket.soTimeout = 0
        input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        handshake()
        requestFramebufferUpdate(incremental = false)
        ioScope.launch { runCatching { readLoop() }.onFailure { if (!closed.get()) close() } }
    }

    private fun handshake() {
        val version = ByteArray(12)
        input.readFully(version)
        require(String(version, Charsets.US_ASCII).startsWith("RFB 003.")) {
            "Unsupported VNC server protocol."
        }
        output.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        output.flush()

        val securityCount = input.readUnsignedByte()
        require(securityCount > 0) { "QEMU VNC server rejected the connection." }
        val securityTypes = ByteArray(securityCount)
        input.readFully(securityTypes)
        require(securityTypes.any { it.toInt() == SECURITY_NONE }) {
            "QEMU VNC server requested an unsupported authentication method."
        }
        output.writeByte(SECURITY_NONE)
        output.flush()
        val securityResult = input.readInt()
        check(securityResult == 0) { "VNC security handshake failed." }

        // Shared connection; QORVEX is the only local client expected.
        output.writeByte(1)
        output.flush()

        framebufferWidth = input.readUnsignedShort()
        framebufferHeight = input.readUnsignedShort()
        require(framebufferWidth in 1..MAX_DIMENSION && framebufferHeight in 1..MAX_DIMENSION) {
            "Guest framebuffer dimensions are invalid: ${framebufferWidth}x${framebufferHeight}."
        }
        val nameLength = input.readInt()
        require(nameLength in 0..MAX_SERVER_NAME_BYTES) { "Invalid VNC server name." }
        val name = ByteArray(nameLength)
        input.readFully(name)

        // 32 bpp, 24-bit depth, little-endian, true-colour RGB.
        synchronized(writeLock) {
            output.writeByte(0) // SetPixelFormat
            output.writeByte(0); output.writeByte(0); output.writeByte(0)
            output.writeByte(32); output.writeByte(24); output.writeByte(1); output.writeByte(1)
            output.writeShort(255); output.writeShort(255); output.writeShort(255)
            output.writeByte(16); output.writeByte(8); output.writeByte(0)
            output.write(ByteArray(3))
            output.writeByte(2) // SetEncodings
            output.writeByte(0)
            output.writeShort(1)
            output.writeInt(ENCODING_RAW)
            output.flush()
        }
    }

    private fun readLoop() {
        while (!closed.get()) {
            when (input.readUnsignedByte()) {
                0 -> readFramebufferUpdate()
                1 -> skipColourMapUpdate()
                2 -> Unit // Bell
                3 -> skipServerCutText()
                else -> error("Unknown VNC server message.")
            }
        }
    }

    private fun readFramebufferUpdate() {
        input.readUnsignedByte() // padding
        val rectangles = input.readUnsignedShort()
        var current = mutableFramebuffer.value
            ?: Bitmap.createBitmap(framebufferWidth, framebufferHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(framebufferWidth * framebufferHeight)
        current.getPixels(pixels, 0, framebufferWidth, 0, 0, framebufferWidth, framebufferHeight)

        repeat(rectangles) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val width = input.readUnsignedShort()
            val height = input.readUnsignedShort()
            val encoding = input.readInt()
            require(x + width <= framebufferWidth && y + height <= framebufferHeight) {
                "VNC rectangle is outside the guest framebuffer."
            }
            when (encoding) {
                ENCODING_RAW -> {
                    val row = ByteArray(width * 4)
                    repeat(height) { rowIndex ->
                        input.readFully(row)
                        for (column in 0 until width) {
                            val offset = column * 4
                            val blue = row[offset].toInt() and 0xff
                            val green = row[offset + 1].toInt() and 0xff
                            val red = row[offset + 2].toInt() and 0xff
                            pixels[(y + rowIndex) * framebufferWidth + x + column] =
                                Color.rgb(red, green, blue)
                        }
                    }
                }
                else -> error("QEMU sent an unsupported VNC framebuffer encoding: $encoding")
            }
        }
        current = Bitmap.createBitmap(pixels, framebufferWidth, framebufferHeight, Bitmap.Config.ARGB_8888)
        mutableFramebuffer.value = current
        requestFramebufferUpdate(incremental = true)
    }

    private fun skipColourMapUpdate() {
        input.readUnsignedByte()
        input.readUnsignedShort()
        val count = input.readUnsignedShort()
        repeat(count) { input.readUnsignedShort(); input.readUnsignedShort(); input.readUnsignedShort() }
    }

    private fun skipServerCutText() {
        input.skipBytes(3)
        val length = input.readInt()
        require(length in 0..MAX_SERVER_NAME_BYTES) { "VNC clipboard payload is too large." }
        input.skipBytes(length)
    }

    fun sendKey(keySym: Int, pressed: Boolean) {
        if (closed.get()) return
        synchronized(writeLock) {
            output.writeByte(4) // KeyEvent
            output.writeByte(if (pressed) 1 else 0)
            output.writeShort(0)
            output.writeInt(keySym)
            output.flush()
        }
    }

    /** buttons uses RFB bit 0=left, bit 1=middle, bit 2=right. */
    fun sendPointer(x: Int, y: Int, buttons: Int = 0) {
        if (closed.get()) return
        val px = x.coerceIn(0, (framebufferWidth - 1).coerceAtLeast(0))
        val py = y.coerceIn(0, (framebufferHeight - 1).coerceAtLeast(0))
        synchronized(writeLock) {
            output.writeByte(5) // PointerEvent
            output.writeByte(buttons and 0xff)
            output.writeShort(px)
            output.writeShort(py)
            output.flush()
        }
    }

    private fun requestFramebufferUpdate(incremental: Boolean) {
        synchronized(writeLock) {
            output.writeByte(3) // FramebufferUpdateRequest
            output.writeByte(if (incremental) 1 else 0)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(framebufferWidth)
            output.writeShort(framebufferHeight)
            output.flush()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        mutableFramebuffer.value = null
    }

    private companion object {
        const val VNC_PORT = 5900
        const val CONNECT_TIMEOUT_MS = 3_000
        const val SECURITY_NONE = 1
        const val ENCODING_RAW = 0
        const val MAX_DIMENSION = 8192
        const val MAX_SERVER_NAME_BYTES = 4096
    }
}
