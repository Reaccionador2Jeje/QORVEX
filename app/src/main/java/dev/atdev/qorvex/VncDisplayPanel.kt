package dev.atdev.qorvex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.layout.ContentScale
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
    DisposableEffect(client) {
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
                val currentBitmap = bitmap!!
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Guest screen. Move your finger or mouse over the screen to control the guest pointer.",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color.Black)
                        .pointerInput(client, currentBitmap) {
                            // Keep one pointer event loop so taps, drags, and mouse hover
                            // don't compete with separate gesture detectors.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    val x = (change.position.x / size.width * client.framebufferWidth).toInt()
                                    val y = (change.position.y / size.height * client.framebufferHeight).toInt()

                                    // RFB button mask: left=1, middle=2, right=4.
                                    // Compose's primary/tertiary/secondary map to left/middle/right.
                                    val buttons =
                                        (if (event.buttons.isPrimaryPressed) 1 else 0) or
                                        (if (event.buttons.isTertiaryPressed) 2 else 0) or
                                        (if (event.buttons.isSecondaryPressed) 4 else 0)
                                    client.sendPointer(x, y, buttons)
                                }
                            }
                        }
                )
            } else {
                Text(
                    "The guest framebuffer appears here after its display initializes.",
                    color = Color(0xFF94A3B8)
                )
            }

            OutlinedTextField(
                value = typedText,
                onValueChange = { next ->
                    val old = typedText
                    var prefix = 0
                    while (prefix < old.length && prefix < next.length && old[prefix] == next[prefix]) {
                        prefix++
                    }
                    // If text was deleted or replaced, send Backspace for the old suffix.
                    repeat(old.length - prefix) { sendKeyTap(client, KEY_BACKSPACE) }
                    // Send newly typed characters immediately, including text from Android IME.
                    next.substring(prefix).codePoints().forEach { codePoint ->
                        sendKeyTap(client, unicodeToKeysym(codePoint))
                    }
                    typedText = next
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Android keyboard → guest") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { sendKeyTap(client, KEY_ENTER) }) { Text("Enter") }
                Button(onClick = { sendKeyTap(client, KEY_BACKSPACE) }) { Text("Backspace") }
                Button(onClick = { sendKeyTap(client, KEY_TAB) }) { Text("Tab") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { sendKeyTap(client, KEY_ESCAPE) }) { Text("Esc") }
                Button(onClick = { sendKeyTap(client, KEY_UP) }) { Text("↑") }
                Button(onClick = { sendKeyTap(client, KEY_DOWN) }) { Text("↓") }
                Button(onClick = { sendKeyTap(client, KEY_LEFT) }) { Text("←") }
                Button(onClick = { sendKeyTap(client, KEY_RIGHT) }) { Text("→") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    // Send the Windows secure-attention key chord as a single ordered sequence.
                    client.sendKey(KEY_CTRL_LEFT, true)
                    client.sendKey(KEY_ALT_LEFT, true)
                    client.sendKey(KEY_DELETE, true)
                    client.sendKey(KEY_DELETE, false)
                    client.sendKey(KEY_ALT_LEFT, false)
                    client.sendKey(KEY_CTRL_LEFT, false)
                }) { Text("Ctrl + Alt + Del") }
            }
            Text(
                "Touch/mouse move = guest pointer • Touch press = click • Hold and move = drag",
                color = Color(0xFF718096)
            )
        }
    }
}

private fun sendKeyTap(client: VncDisplayClient, keySym: Int) {
    client.sendKey(keySym, true)
    client.sendKey(keySym, false)
}

private fun unicodeToKeysym(codePoint: Int): Int =
    if (codePoint <= 0xff) codePoint else 0x01000000 or codePoint

private const val KEY_BACKSPACE = 0xff08
private const val KEY_TAB = 0xff09
private const val KEY_ENTER = 0xff0d
private const val KEY_ESCAPE = 0xff1b
private const val KEY_UP = 0xff52
private const val KEY_DOWN = 0xff54
private const val KEY_LEFT = 0xff51
private const val KEY_RIGHT = 0xff53
private const val KEY_CTRL_LEFT = 0xffe3
private const val KEY_ALT_LEFT = 0xffe9
private const val KEY_DELETE = 0xffff
