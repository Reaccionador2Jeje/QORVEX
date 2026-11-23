package dev.atdev.qorvex

import android.os.Bundle
import android.content.Intent
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
import dev.atdev.qorvex.runtime.NativeQemuBridge
import dev.atdev.qorvex.storage.InstallationMediaReader
import dev.atdev.qorvex.storage.InstallationMediaManager
import dev.atdev.qorvex.storage.VirtualDiskManager
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
            primary = Color(0xFF67E8F9),
            secondary = Color(0xFF818CF8),
            background = Color(0xFF080D18),
            surface = Color(0xFF111827)
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
    var notice by remember { mutableStateOf<String?>(null) }
    var mediaTargetId by remember { mutableStateOf<String?>(null) }
    val diskManager = remember { VirtualDiskManager(context) }
    val mediaManager = remember { InstallationMediaManager(context) }
    val scope = rememberCoroutineScope()
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
        mediaTargetId = null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080D18)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("QORVEX", color = Color(0xFF67E8F9), fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("ATDEV • VIRTUAL PC", color = Color(0xFF94A3B8), fontSize = 11.sp, letterSpacing = 2.sp)
                }
                Text("0.3.0-dev", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13243A))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Your pocket-sized PC platform", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Create and organize virtual computer profiles. QEMU boot support is being developed separately.",
                        color = Color(0xFFB6C7D9), fontSize = 14.sp,
                        style = androidx.compose.ui.text.TextStyle(lineHeight = 21.sp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { showCreateDialog = true }) { Text("＋ Create virtual machine") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("YOUR MACHINES", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("${machines.size} saved", color = Color(0xFF67E8F9), fontSize = 12.sp)
            }

            if (machines.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No virtual machines yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Create a profile to define its guest architecture, memory, CPU and virtual disk size.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            } else {
                machines.forEach { machine ->
                    val mediaName = mediaManager.inspect(machine)?.let { imported ->
                        "${imported.displayName} • ${formatBytes(imported.sizeBytes)} • SHA-256 verified"
                    } ?: machine.installationMediaUri?.let { raw ->
                        runCatching { InstallationMediaReader.describe(context, Uri.parse(raw)).displayName }
                            .getOrDefault("Legacy ISO reference (re-import required)")
                    } ?: "No installation media"
                    MachineCard(
                        machine = machine,
                        mediaName = mediaName,
                        diskInfo = runCatching { diskManager.inspect(machine) }.getOrNull(),
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
                        onStart = { notice = "Cannot boot yet: JNI bridge is present, but the QEMU engine is not linked." }
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
                        TextButton(onClick = { notice = null }) { Text("OK") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NATIVE BRIDGE", color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(if (NativeQemuBridge.isLoaded) "JNI library loaded" else "JNI library unavailable", color = Color.White, fontSize = 13.sp)
                    Text(NativeQemuBridge.status, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text("QEMU engine: not bundled • Guest boot: disabled", color = Color(0xFFFBBF24), fontSize = 11.sp)
                }
            }
            Text("Built by AtDev", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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
}

@Composable
private fun MachineCard(
    machine: VirtualMachine,
    mediaName: String,
    diskInfo: dev.atdev.qorvex.storage.VirtualDiskInfo?,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onSelectMedia: () -> Unit,
    onCreateDisk: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Button(onClick = onStart) { Text("Start") }
                OutlinedButton(onClick = onSelectMedia) { Text("Select ISO") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreateDisk) { Text(if (diskInfo != null) "Verify disk" else "Create disk") }
                OutlinedButton(onClick = onDelete) { Text("Delete profile") }
            }
        }
    }
}

@Composable
private fun CreateMachineDialog(onDismiss: () -> Unit, onCreate: (VirtualMachine) -> Unit) {
    var name by remember { mutableStateOf("My first PC") }
    var memory by remember { mutableStateOf("2048") }
    var cores by remember { mutableStateOf("2") }
    var disk by remember { mutableStateOf("16") }
    var architecture by remember { mutableStateOf(GuestArchitecture.AARCH64) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create virtual machine") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Machine name") }, singleLine = true)
                Text("Guest architecture", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (architecture == GuestArchitecture.AARCH64) {
                        Button(onClick = { architecture = GuestArchitecture.AARCH64 }) { Text("ARM64") }
                    } else {
                        OutlinedButton(onClick = { architecture = GuestArchitecture.AARCH64 }) { Text("ARM64") }
                    }
                    if (architecture == GuestArchitecture.X86_64) {
                        Button(onClick = { architecture = GuestArchitecture.X86_64 }) { Text("x86_64") }
                    } else {
                        OutlinedButton(onClick = { architecture = GuestArchitecture.X86_64 }) { Text("x86_64") }
                    }
                }
                NumberField("Memory (MiB)", memory, { memory = it; error = null })
                NumberField("CPU cores", cores, { cores = it; error = null })
                NumberField("Virtual disk (GiB)", disk, { disk = it; error = null })
                error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
                Text("This creates a profile. Create its disk and select installation media separately; boot requires the QEMU engine.", color = Color(0xFF94A3B8), fontSize = 11.sp)
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
