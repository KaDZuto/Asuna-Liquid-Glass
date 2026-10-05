@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vectorheart.asuna.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectorheart.asuna.memory.MemoryCloudSync
import com.vectorheart.asuna.memory.MemoryManager
import com.vectorheart.asuna.ui.theme.LiquidGlassColors
import com.vectorheart.asuna.ui.theme.liquidGlassCapsule
import com.vectorheart.asuna.ui.theme.liquidGlassGlow
import com.vectorheart.asuna.ui.theme.liquidGlassSurface
import kotlinx.coroutines.launch

/**
 * Memory viewer and sync modal styled with iOS 26 Liquid Glass design.
 */
@Composable
fun MemorySheetContent(
    memoryManager: MemoryManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var memories by remember { mutableStateOf(memoryManager.loadMemories()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val count = memoryManager.importFromUri(context, uri)
                        memories = memoryManager.loadMemories()
                        Toast.makeText(context, "Импортировано записей: $count", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LiquidGlassColors.AsunaLavenderGlow, CircleShape)
                    .liquidGlassGlow(LiquidGlassColors.AsunaLavenderGlow, radius = 6.dp, alpha = 0.9f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "🧠 Память Асуны",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF7EFE9)
            )
        }
        Text(
            "Выжимки прошлых бесед. Импортируй JSON из ПК-версии, чтобы Асуна помнила тебя.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFC9BCB0)
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Импорт JSON")
            }
            OutlinedButton(onClick = {
                memoryManager.clearMemories()
                memories = emptyList()
                Toast.makeText(context, "Память очищена", Toast.LENGTH_SHORT).show()
            }) {
                Text("Очистить")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val n = MemoryCloudSync.pushMemories(context, memoryManager.loadMemories())
                            Toast.makeText(context, "В Firebase выгружено: $n", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Ошибка Firebase", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("☁️ В Firebase") }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val remote = MemoryCloudSync.pullMemories(context)
                            memoryManager.mergeMemories(remote)
                            memories = memoryManager.loadMemories()
                            Toast.makeText(context, "Из Firebase загружено: ${remote.size}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Ошибка Firebase", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) { Text("☁️ Из Firebase") }
        }

        Spacer(Modifier.height(12.dp))
        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        tintColor = LiquidGlassColors.ObsidianElevated,
                        frostedAlpha = 0.60f,
                        specularAlpha = 0.75f,
                        borderWidth = 1.dp
                    )
                    .padding(14.dp)
            ) {
                Text("Память пока пуста.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC9BCB0))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories.reversed()) { m ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(16.dp),
                                tintColor = LiquidGlassColors.ObsidianElevated,
                                frostedAlpha = 0.70f,
                                specularAlpha = 0.88f,
                                borderWidth = 1.dp,
                                sheenIntensity = 0.5f,
                                highlightColor = Color.White,
                                bounceColor = LiquidGlassColors.AsunaLavenderBorder.copy(alpha = 0.35f)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(m.dateRu, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LiquidGlassColors.AsunaLavenderGlow)
                            Spacer(Modifier.height(3.dp))
                            Text(m.summary, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF7EFE9))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCapsule(
                    tintColor = LiquidGlassColors.WarmPeachTint,
                    frostedAlpha = 0.88f,
                    specularAlpha = 1.0f,
                    borderWidth = 1.2.dp,
                    glowColor = LiquidGlassColors.WarmPeachGlow,
                    glowRadius = 8.dp,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.WarmPeachBorder
                )
                .clickable { onClose() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Готово", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A0F1F))
        }
    }
}
