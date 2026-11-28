package dev.atdev.qorvex.runtime

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/** Small QMP client for requesting a guest-initiated ACPI powerdown. */
internal object QmpClient {
    fun requestPowerdown(socketFile: File): Boolean {
        if (!socketFile.exists()) return false
        val socket = LocalSocket()
        return try {
            socket.soTimeout = IO_TIMEOUT_MS
            socket.connect(
                LocalSocketAddress(
                    socketFile.absolutePath,
                    LocalSocketAddress.Namespace.FILESYSTEM
                )
            )
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

            // QMP sends a greeting immediately after the connection is accepted.
            val greeting = reader.readLine() ?: return false
            JSONObject(greeting).optJSONObject("QMP") ?: return false

            if (!execute(reader, writer, "qmp_capabilities", "capabilities")) return false
            // system_powerdown asks the guest OS to shut down; unlike `quit`, it
            // does not terminate QEMU before the guest can flush its filesystems.
            execute(reader, writer, "system_powerdown", "powerdown")
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Skip asynchronous QMP events until the reply for this command arrives. */
    private fun execute(
        reader: BufferedReader,
        writer: BufferedWriter,
        command: String,
        requestId: String
    ): Boolean {
        writer.write("{\"execute\":\"$command\",\"id\":\"$requestId\"}\n")
        writer.flush()
        while (true) {
            val line = reader.readLine() ?: return false
            val response = JSONObject(line)
            if (response.optString("id") != requestId) continue
            return !response.has("error") && response.has("return")
        }
    }

    private const val IO_TIMEOUT_MS = 1500
}
