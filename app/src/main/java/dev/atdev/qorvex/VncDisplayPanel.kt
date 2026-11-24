package dev.atdev.qorvex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.atdev.qorvex.runtime.VncDisplayClient
import kotlinx.coroutines.delay

@Composable
internal fun VncDisplayPanel() {
    val client = remember { VncDisplayClient() }
    val bitmap by client.framebuffer.collectAsState()
    var connectionLabel by remember { mutableStateOf("Connecting to guest display…") }
    var typedText by remember { mutableStateOf("") }

    LaunchedEffect(client) {
        var connected = false
        repeat(12) {
            if (!connected) {
                runCatching { client.connect() }
                    .onSuccess { connected = true; connectionLabel = "Guest display connected" }
                    .onFailure { connectionLabel = "Waiting for QEMU display…" }
                if (!connected) delay(500)
            }
        }
        if (!connected) connectionLabel = "Display unavailable. Check that the guest VM is still running."
    }
    androidx.compose.runtime.DisposableEffect(client) {
        onDispose { client.close() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111B24))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("GUEST DISPLAY", color = Color(0xFF65E6D5))
            Text(connectionLabel, color = Color(0xFF9AA8B5))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Virtual machine framebuffer. Touch to click and drag to move the pointer.",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(Color.Black)
                        .pointerInput(client, bitmap) {
                            detectTapGestures { position ->
                                val x = (position.x / size.width * client.framebufferWidth).toInt()
                                val y = (position.y / size.height * client.framebufferHeight).toInt()
                                client.sendPointer(x, y, 1)
                                client.sendPointer(x, y, 0)
                            }
                        }
                        .pointerInput(client, bitmap) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    val x = (position.x / size.width * client.framebufferWidth).toInt()
                                    val y = (position.y / size.height * client.framebufferHeight).toInt()
                                    client.sendPointer(x, y, 1)
                                },
                                onDrag = { change, _ ->
                                    val x = (change.position.x / size.width * client.framebufferWidth).toInt()
                                    val y = (change.position.y / size.height * client.framebufferHeight).toInt()
                                    client.sendPointer(x, y, 1)
                                },
                                onDragEnd = { client.sendPointer(0, 0, 0) },
                                onDragCancel = { client.sendPointer(0, 0, 0) }
                            )
                        }
                )
            } else {
                Text(
                    "The framebuffer will appear here when the guest firmware initializes its display.",
                    color = Color(0xFF94A3B8)
                )
            }

            OutlinedTextField(
                value = typedText,
                onValueChange = { typedText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type into the virtual machine") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    typedText.codePoints().forEach { codePoint ->
                        val keySym = if (codePoint <= 0xff) codePoint else 0x01000000 or codePoint
                        client.sendKey(keySym, true)
                        client.sendKey(keySym, false)
                    }
                    typedText = ""
                }, enabled = typedText.isNotEmpty() && bitmap != null) { Text("Send keys") }
                Button(onClick = {
                    client.sendKey(0xff08, true)
                    client.sendKey(0xff08, false)
                }, enabled = bitmap != null) { Text("Backspace") }
                Button(onClick = {
                    client.sendKey(0xff0d, true)
                    client.sendKey(0xff0d, false)
                }, enabled = bitmap != null) { Text("Enter") }
            }
            Text(
                "Touch = click • Drag = pointer • Text field = Android keyboard",
                color = Color(0xFF718096)
            )
        }
    }
}
