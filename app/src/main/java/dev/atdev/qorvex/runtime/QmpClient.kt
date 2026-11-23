package dev.atdev.qorvex.runtime

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * Small QMP client used for a graceful QEMU shutdown.
 * Each command uses a short-lived local-domain socket connection.
 */
internal object QmpClient {
    fun requestQuit(socketFile: File): Boolean {
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

            writer.write("{\"execute\":\"qmp_capabilities\"}\n")
            writer.flush()
            val capabilitiesReply = reader.readLine() ?: return false
            if (JSONObject(capabilitiesReply).has("error")) return false

            writer.write("{\"execute\":\"quit\"}\n")
            writer.flush()
            val quitReply = reader.readLine() ?: return false
            !JSONObject(quitReply).has("error")
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private const val IO_TIMEOUT_MS = 1500
}
