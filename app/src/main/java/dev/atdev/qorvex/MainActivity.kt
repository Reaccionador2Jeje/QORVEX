package dev.atdev.qorvex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080D18)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                Text("0.1.0", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13243A))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Your pocket-sized PC platform", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Create virtual machines, configure hardware, and run guest operating systems from Android.",
                        color = Color(0xFFB6C7D9), fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { }) { Text("Create your first VM") }
                }
            }

            Text("YOUR MACHINES", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No virtual machines yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Your configured computers will appear here.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.weight(1f))
            Text("QEMU runtime integration is not connected yet.", color = Color(0xFFFBBF24), fontSize = 12.sp)
            Text("Built by AtDev", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
