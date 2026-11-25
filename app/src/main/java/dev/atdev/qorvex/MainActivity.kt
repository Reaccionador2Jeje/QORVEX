package dev.atdev.qorvex

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import dev.atdev.qorvex.runtime.QorvexRuntimeService
import dev.atdev.qorvex.runtime.RuntimeAvailability
import dev.atdev.qorvex.runtime.RuntimeState
import dev.atdev.qorvex.storage.InstallationMediaReader
import dev.atdev.qorvex.storage.InstallationMediaManager
import dev.atdev.qorvex.storage.VirtualDiskManager
import dev.atdev.qorvex.storage.VirtualDiskSnapshot
import dev.atdev.qorvex.storage.VirtualDiskSnapshotManager
import dev.atdev.qorvex.vm.GuestArchitecture
import dev.atdev.qorvex.vm.VirtualMachine
import dev.atdev.qorvex.vm.VirtualMachineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QorvexTheme { QorvexDashboard() } }
    }
}

@Composable
private fun QorvexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF65E6D5),
            secondary = Color(0xFF9AA8B5),
            background = Color(0xFF101820),
            surface = Color(0xFF1B252D)
        ),
        content = content
    )
}

@Composable
private fun QorvexDashboard() {
    val context = LocalContext.current
    val repository = remember { VirtualMachineRepository(context) }
    val machines = remember { mutableStateListOf<VirtualMachine>().apply { addAll(repository.getAll()) } }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf("machines") }
    var notice by remember { mutableStateOf<String?>(null) }
    var mediaTargetId by remember { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<Pair<VirtualMachine, String>?>(null) }
    var snapshotManagerTarget by remember { mutableStateOf<VirtualMachine?>(null) }
    var snapshotNameTarget by remember { mutableStateOf<VirtualMachine?>(null) }
    var snapshotName by remember { mutableStateOf("Before changes") }
    var snapshotRevision by remember { mutableStateOf(0) }
    val diskManager = remember { VirtualDiskManager(context) }
    val snapshotManager = remember { VirtualDiskSnapshotManager(context) }
    val mediaManager = remember { InstallationMediaManager(context) }
    val scope = rememberCoroutineScope()
    val runtime = remember { QorvexRuntimeService.getRuntime(context) }
    val runtimeState by runtime.state.collectAsState()
    LaunchedEffect(runtimeState) {
        if (runtimeState == RuntimeState.FAILED) {
            notice = "QEMU failed or crashed. Check the serial console below for the exit code and details."
        } else if (runtimeState == RuntimeState.EXITED) {
            notice = "The virtual machine has exited. Check the serial console for its final output."
        }
    }
    val activeMachineId by runtime.activeMachineId.collectAsState()
    val consoleText by runtime.consoleText.collectAsState()
    val runtimeCapabilities = remember { runtime.getCapabilities() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val targetId = mediaTargetId
        if (uri != null && targetId != null) {
            val machine = machines.firstOrNull { it.id == targetId }
            if (machine == null) {
                notice = "VM profile no longer exists."
            } else {
                notice = "Importing installation media into QORVEX storage..."
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            mediaManager.importMedia(machine, uri)
                        }
                    }.onSuccess { imported ->
                        val index = machines.indexOfFirst { it.id == targetId }
                        if (index >= 0) {
                            val updated = machine.copy(
                                installationMediaUri = null,
                                installationMediaName = imported.displayName,
                                installationMediaSha256 = imported.sha256
                            )
                            runCatching {
                                repository.save(updated)
                                machines[index] = updated
                                notice = "ISO imported: ${imported.displayName} (${formatBytes(imported.sizeBytes)}). SHA-256: ${imported.sha256.take(16)}..."
                            }.onFailure { notice = "ISO copied, but profile metadata could not be saved: ${it.message}" }
                        }
                    }.onFailure { notice = "ISO import failed: ${it.message}" }
                }
            }
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101820)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("QORVEX", color = Color(0xFF65E6D5), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(if (selectedSection == "machines") "Virtual machines" else "Settings", color = Color(0xFF9AA8B5), fontSize = 13.sp)
                }
                if (selectedSection == "machines") Button(onClick = { showCreateDialog = true }) { Text("＋ QuickStart") }
            }
            if (activeMachineId != null) VncDisplayPanel()
            Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedSection == "machines") {
            if (machines.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B252D))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No virtual machines yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Create a profile to define its guest architecture, memory, CPU and virtual disk size.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            } else {
                machines.forEach { machine ->
                    val snapshots = remember(machine.id, snapshotRevision) {
                        runCatching { snapshotManager.listSnapshots(machine) }.getOrDefault(emptyList())
                    }
                    val latestSnapshot = snapshots.firstOrNull()
                    val mediaName = mediaManager.inspect(machine)?.let { imported ->
                        "${imported.displayName} • ${formatBytes(imported.sizeBytes)} • SHA-256 recorded"
                    } ?: machine.installationMediaUri?.let { raw ->
                        runCatching { InstallationMediaReader.describe(context, Uri.parse(raw)).displayName }
                            .getOrDefault("Legacy ISO reference (re-import required)")
                    } ?: "No installation media"
                    MachineCard(
                        machine = machine,
                        mediaName = mediaName,
                        diskInfo = runCatching { diskManager.inspect(machine) }.getOrNull(),
                        snapshotCount = snapshots.size,
                        latestSnapshot = latestSnapshot,
                        snapshotsEnabled = activeMachineId == null && runtimeState != RuntimeState.STARTING && runtimeState != RuntimeState.STOPPING,
                        onCreateSnapshot = {
                            snapshotName = "Before changes"
                            snapshotNameTarget = machine
                        },
                        onManageSnapshots = { snapshotManagerTarget = machine },
                        onSelectMedia = {
                            mediaTargetId = machine.id
                            mediaPicker.launch(arrayOf("application/x-iso9660-image", "application/octet-stream", "*/*"))
                        },
                        onCreateDisk = {
                            runCatching { diskManager.createSparseDisk(machine) }
                                .onSuccess { notice = "Sparse RAW disk created: ${it.file.name} (${machine.diskSizeGiB} GiB virtual capacity)." }
                                .onFailure { notice = "Disk creation failed: ${it.message}" }
                        },
                        onDelete = {
                            repository.delete(machine.id)
                            machines.remove(machine)
                            notice = "${machine.name} profile deleted."
                        },
                        isRunning = activeMachineId == machine.id,
                        onStart = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                runCatching {
                                    QorvexRuntimeService.start(context)
                                    runtime.start(machine)
                                }.onSuccess { result ->
                                    notice = when (result) {
                                        is dev.atdev.qorvex.runtime.RuntimeResult.Success ->
                                            "QEMU started for ${machine.name}. It will keep running when the dashboard is in the background."
                                        is dev.atdev.qorvex.runtime.RuntimeResult.Failure -> {
                                            QorvexRuntimeService.stop(context)
                                            result.reason
                                        }
                                    }
                                }.onFailure { error ->
                                    QorvexRuntimeService.stop(context)
                                    notice = "Could not start the QORVEX background service: ${error.message}"
                                }
                            }
                        },
                        onStop = {
                            QorvexRuntimeService.requestStop(context, machine.id)
                            notice = "Sending graceful stop request to QEMU..."
                        }
                    )
                }
            }

            notice?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF263449))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val fullError = buildString {
                                appendLine("QORVEX error")
                                appendLine(message)
                                if (consoleText.isNotBlank()) {
                                    appendLine()
                                    appendLine("QEMU console:")
                                    append(consoleText)
                                }
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("QORVEX error", fullError))
                            notice = "Error copied to clipboard."
                        }) { Text("Copy error") }
                        TextButton(onClick = { notice = null }) { Text("OK") }
                    }
                }
            }


                } else {
                    Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QEMU ENGINE", color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(
                        if (runtimeCapabilities.availability == RuntimeAvailability.READY) "QEMU ARM64 packaged"
                        else "QEMU runtime unavailable",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(runtimeCapabilities.detail, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text("Runtime state: ${runtimeState.name}", color = if (runtimeState == RuntimeState.RUNNING) Color(0xFF86EFAC) else Color(0xFFCBD5E1), fontSize = 11.sp)
                }
            }
            if (activeMachineId != null || consoleText.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF050A12))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SERIAL CONSOLE", color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            consoleText.lines().takeLast(18).joinToString("\n").ifBlank { "Waiting for guest serial output..." },
                            color = Color(0xFFB7F7D8),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            maxLines = 18
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val report = buildString {
                                        appendLine("QORVEX diagnostic report")
                                        appendLine("Runtime state: ${runtimeState.name}")
                                        appendLine("Active machine: ${activeMachineId ?: "none"}")
                                        appendLine()
                                        appendLine("QEMU console:")
                                        append(consoleText.ifBlank { "No console output recorded." })
                                    }
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QORVEX diagnostic report", report))
                                    notice = "Console and runtime details copied to clipboard."
                                }
                            ) { Text("Copy console") }
                        }
                    }
                }
            }
            Text("Built by AtDev", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                }
                Spacer(Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedSection = "machines" }) { Text(if (selectedSection == "machines") "▣  Machines" else "Machines", color = if (selectedSection == "machines") Color(0xFF65E6D5) else Color(0xFF9AA8B5)) }
                TextButton(onClick = { selectedSection = "settings" }) { Text(if (selectedSection == "settings") "⚙  Settings" else "Settings", color = if (selectedSection == "settings") Color(0xFF65E6D5) else Color(0xFF9AA8B5)) }
            }
        }
    }
    if (showCreateDialog) {
        CreateMachineDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { machine ->
                repository.save(machine)
                machines.add(machine)
                showCreateDialog = false
                notice = "${machine.name} profile created and saved on this device."
            }
        )
    }
    snapshotNameTarget?.let { machine ->
        AlertDialog(
            onDismissRequest = { snapshotNameTarget = null },
            title = { Text("Create disk snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The VM must be stopped. This creates a separate disk copy and may use additional storage.", color = Color(0xFF94A3B8))
                    OutlinedTextField(
                        value = snapshotName,
                        onValueChange = { snapshotName = it.take(64) },
                        label = { Text("Snapshot name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = snapshotName.trim()
                        if (name.isNotEmpty()) {
                            snapshotNameTarget = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        snapshotManager.createSnapshot(machine, name)
                                    }
                                }.onSuccess { created ->
                                    snapshotRevision++
                                    notice = "Snapshot created: ${created.displayName} • ${formatBytes(created.capacityBytes)} logical capacity."
                                }.onFailure { notice = "Snapshot failed: ${it.message}" }
                            }
                        }
                    },
                    enabled = snapshotName.isNotBlank() && activeMachineId == null &&
                        runtimeState != RuntimeState.STARTING && runtimeState != RuntimeState.STOPPING
                ) { Text("Create snapshot") }
            },
            dismissButton = {
                TextButton(onClick = { snapshotNameTarget = null }) { Text("Cancel") }
            }
        )
    }
    snapshotManagerTarget?.let { machine ->
        val snapshots = remember(machine.id, snapshotRevision) {
            runCatching { snapshotManager.listSnapshots(machine) }.getOrDefault(emptyList())
        }
        SnapshotManagerDialog(
            machine = machine,
            snapshots = snapshots,
            actionsEnabled = activeMachineId == null && runtimeState != RuntimeState.STARTING && runtimeState != RuntimeState.STOPPING,
            onDismiss = { snapshotManagerTarget = null },
            onRestore = { snapshot ->
                snapshotManagerTarget = null
                restoreTarget = machine to snapshot.id
            },
            onDelete = { snapshot ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { snapshotManager.deleteSnapshot(machine, snapshot.id) }
                    }.onSuccess { deleted ->
                        snapshotRevision++
                        notice = if (deleted) "Snapshot deleted." else "Could not delete snapshot files."
                    }.onFailure { notice = "Snapshot deletion failed: ${it.message}" }
                }
            }
        )
    }
    restoreTarget?.let { (machine, snapshotId) ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore disk snapshot?") },
            text = { Text("The current disk contents of ${machine.name} will be replaced by this saved snapshot. The VM must remain stopped. This cannot be undone unless another snapshot exists.") },
            confirmButton = {
                Button(onClick = {
                    restoreTarget = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { snapshotManager.restoreSnapshot(machine, snapshotId) } }
                            .onSuccess {
                                snapshotRevision++
                                notice = "Snapshot restored. The VM disk now contains the selected saved state."
                            }
                            .onFailure { notice = "Snapshot restore failed: ${it.message}" }
                    }
                }) { Text("Restore disk") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MachineCard(
    machine: VirtualMachine,
    mediaName: String,
    diskInfo: dev.atdev.qorvex.storage.VirtualDiskInfo?,
    snapshotCount: Int,
    latestSnapshot: VirtualDiskSnapshot?,
    snapshotsEnabled: Boolean,
    onCreateSnapshot: () -> Unit,
    onManageSnapshots: () -> Unit,
    onDelete: () -> Unit,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelectMedia: () -> Unit,
    onCreateDisk: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B252D))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(machine.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("SAVED", color = Color(0xFF67E8F9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(machine.architecture.label, color = Color(0xFF94A3B8), fontSize = 13.sp)
            Text("${machine.memoryMiB} MiB RAM  •  ${machine.cpuCores} CPU cores  •  ${machine.diskSizeGiB} GiB disk", color = Color(0xFFCBD5E1), fontSize = 12.sp)
            Text("Media: $mediaName", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 2)
            if (diskInfo != null) {
                Text(
                    "Virtual disk: ${formatBytes(diskInfo.capacityBytes)} capacity • ${formatBytes(diskInfo.allocatedBytes)} allocated",
                    color = Color(0xFF86EFAC),
                    fontSize = 11.sp
                )
            } else {
                Text("Virtual disk: not created", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (isRunning) onStop else onStart,
                    enabled = true
                ) { Text(if (isRunning) "Stop" else "Start") }
                OutlinedButton(onClick = onSelectMedia, enabled = !isRunning) { Text("Select ISO") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreateDisk, enabled = snapshotsEnabled) { Text(if (diskInfo != null) "Verify disk" else "Create disk") }
                OutlinedButton(onClick = onDelete, enabled = !isRunning && snapshotsEnabled) { Text("Delete profile") }
            }
            Text("Snapshots: $snapshotCount", color = Color(0xFF9AA8B5), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreateSnapshot, enabled = snapshotsEnabled && diskInfo != null) { Text("＋ Snapshot") }
                OutlinedButton(onClick = onManageSnapshots, enabled = snapshotCount > 0) { Text("Manage snapshots") }
            }
            if (latestSnapshot != null) {
                Text("Latest: ${latestSnapshot.displayName} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(latestSnapshot.createdAtEpochMs))}", color = Color(0xFF718096), fontSize = 10.sp)
            }
        }
    }
}


@Composable
private fun SnapshotManagerDialog(
    machine: VirtualMachine,
    snapshots: List<VirtualDiskSnapshot>,
    actionsEnabled: Boolean,
    onDismiss: () -> Unit,
    onRestore: (VirtualDiskSnapshot) -> Unit,
    onDelete: (VirtualDiskSnapshot) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Snapshots • ${machine.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (snapshots.isEmpty()) {
                    Text("No valid snapshots found. Incomplete or damaged snapshot metadata is hidden.", color = Color(0xFF94A3B8))
                }
                snapshots.forEach { snapshot ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF24313A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(snapshot.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(snapshot.createdAtEpochMs)),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text("${formatBytes(snapshot.capacityBytes)} logical disk image", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRestore(snapshot) }, enabled = actionsEnabled) { Text("Restore") }
                                TextButton(onClick = { onDelete(snapshot) }, enabled = actionsEnabled) {
                                    Text("Delete", color = Color(0xFFFCA5A5))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CreateMachineDialog(onDismiss: () -> Unit, onCreate: (VirtualMachine) -> Unit) {
    var name by remember { mutableStateOf("My first PC") }
    var memory by remember { mutableStateOf("512") }
    var cores by remember { mutableStateOf("2") }
    var disk by remember { mutableStateOf("8") }
    var architecture by remember { mutableStateOf(GuestArchitecture.AARCH64) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QuickStart • Create VM") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Machine name") }, singleLine = true)
                Text("Quick setup", color = Color(0xFF9AA8B5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        memory = "512"
                        cores = "1"
                        disk = "8"
                    }) { Text("Lightweight") }
                    TextButton(onClick = {
                        memory = "1024"
                        cores = "2"
                        disk = "16"
                    }) { Text("Balanced") }
                    TextButton(onClick = {
                        memory = "2048"
                        cores = "4"
                        disk = "32"
                    }) { Text("Custom+") }
                }
                Text("Presets are starting points. Actual safe RAM depends on free memory on this device.", color = Color(0xFF94A3B8), fontSize = 10.sp)
                Text("Guest architecture", color = Color(0xFF9AA8B5), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { architecture = GuestArchitecture.AARCH64 }) {
                        Text(if (architecture == GuestArchitecture.AARCH64) "✓ ARM64" else "ARM64")
                    }
                    TextButton(onClick = { architecture = GuestArchitecture.X86_64 }) {
                        Text(if (architecture == GuestArchitecture.X86_64) "✓ x86_64 PC" else "x86_64 PC")
                    }
                }
                NumberField("Memory (MiB)", memory, { memory = it; error = null })
                NumberField("CPU cores", cores, { cores = it; error = null })
                NumberField("Virtual disk (GiB)", disk, { disk = it; error = null })
                error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
                Text("QEMU uses software emulation. VM startup needs enough free Android RAM.", color = Color(0xFF9AA8B5), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedMemory = memory.toIntOrNull()
                val parsedCores = cores.toIntOrNull()
                val parsedDisk = disk.toIntOrNull()
                when {
                    name.isBlank() -> error = "Enter a name for this machine."
                    parsedMemory == null || parsedMemory !in 256..65536 -> error = "Memory must be 256–65536 MiB."
                    parsedCores == null || parsedCores !in 1..32 -> error = "CPU cores must be 1–32."
                    parsedDisk == null || parsedDisk !in 1..2048 -> error = "Disk size must be 1–2048 GiB."
                    else -> onCreate(
                        VirtualMachine(
                            name = name.trim(),
                            architecture = architecture,
                            memoryMiB = parsedMemory,
                            cpuCores = parsedCores,
                            diskSizeGiB = parsedDisk
                        )
                    )
                }
            }) { Text("Save profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.all(Char::isDigit) && input.length <= 5) onValueChange(input) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
