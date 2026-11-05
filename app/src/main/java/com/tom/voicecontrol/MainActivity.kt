package com.tom.voicecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val TomBackground = Color(0xFF0B1020)
private val TomSurface = Color(0xFF151D32)
private val TomAccent = Color(0xFF78E0C3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var hasMicPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> hasMicPermission = granted }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = TomAccent,
                background = TomBackground,
                surface = TomSurface,
                onPrimary = TomBackground,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                TomHome(
                    hasMicPermission = hasMicPermission,
                    onRequestMic = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }
}

@Composable
private fun TomHome(
    hasMicPermission: Boolean,
    onRequestMic: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TomBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOM", color = TomAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Your offline voice assistant", color = Color.LightGray, fontSize = 12.sp)
                }
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
            }

            Spacer(Modifier.height(54.dp))
            Box(
                modifier = Modifier
                    .background(TomSurface, CircleShape)
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(TomAccent.copy(alpha = 0.14f), CircleShape)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = TomAccent,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Hello, I'm Tom.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasMicPermission) "Ready when you are." else "Microphone permission is needed for voice input.",
                color = Color.LightGray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onRequestMic,
                colors = ButtonDefaults.buttonColors(containerColor = TomAccent),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = TomBackground)
                Text(
                    if (hasMicPermission) "  Microphone ready" else "  Allow microphone",
                    color = TomBackground,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(36.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TomSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("LANGUAGES", color = TomAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("বাংলা  •  English", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Offline-first design. Speech models and command engine are the next implementation phase.", color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Tom • Native Android • Privacy-first", color = Color.Gray, fontSize = 11.sp)
        }
    }
}
