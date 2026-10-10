@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vectorheart.asuna.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectorheart.asuna.localai.LiteRtLmModelInspector
import com.vectorheart.asuna.localai.LocalModelManager
import com.vectorheart.asuna.ui.theme.LiquidGlassColors
import com.vectorheart.asuna.ui.theme.liquidGlassBorder
import com.vectorheart.asuna.ui.theme.liquidGlassCapsule
import com.vectorheart.asuna.ui.theme.liquidGlassGlow
import com.vectorheart.asuna.ui.theme.liquidGlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local AI Models Modal Sheet styled with iOS 26 Liquid Glass design.
 * Features translucent frosted glass cards, glowing offline server status pills,
 * memory optimization toggles, and animated download bars.
 */
@Composable
fun LocalModelsSheetContent(
    localModelManager: LocalModelManager,
    onPickModelFile: () -> Unit,
    onActivateLocalServer: () -> Unit,
    onActivateOnDevice: () -> Unit = {},
    onActivateModel: (LocalModelManager.ImportedModel) -> Unit = {},
    onCopyText: (String) -> Unit
) {
    var importedModels by remember { mutableStateOf(localModelManager.getImportedModels()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isServerOnline by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isServerOnline = localModelManager.checkLocalServerHealth("http://127.0.0.1:8080")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Modal Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LiquidGlassColors.WarmPeachGlow, CircleShape)
                    .liquidGlassGlow(LiquidGlassColors.WarmPeachGlow, radius = 6.dp, alpha = 0.9f)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Локальные ИИ Модели (Офлайн)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF7EFE9)
                )
                Text(
                    "Оптимизировано для Xiaomi Mi 11T (Dimensity 1200, 8GB RAM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiquidGlassColors.WarmPeachGlow
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Import Button (Liquid Glass styled)
        Button(
            onClick = onPickModelFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Импортировать модель (.gguf / .task / .litertlm)")
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Импортированные модели (${importedModels.size})",
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF7EFE9),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(6.dp))

        if (importedModels.isEmpty()) {
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
                    .padding(12.dp)
            ) {
                Text(
                    "Файлы моделей ещё не импортированы. Скачайте .task или .litertlm модель на телефон и нажмите кнопку выше.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC9BCB0)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                importedModels.forEach { m ->
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
                                bounceColor = LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.35f)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF7EFE9))
                                Text(
                                    "${m.sizeBytes / (1024 * 1024)} МБ • 4 потока • ${m.format}",
                                    fontSize = 12.sp,
                                    color = Color(0xFFC9BCB0)
                                )
                                // Разрядность весов читаем из самого .litertlm:
                                // INT2 — это троичные {-a,0,+a} (вдвое меньше
                                // INT4, но заметно грубее), и пользователю
                                // полезно видеть, что он запустил. Файл на
                                // сотни МБ, поэтому читаем не в UI-потоке.
                                val weights by produceState<String?>(null, m.filePath) {
                                    value = withContext(Dispatchers.IO) { weightLabel(m) }
                                }
                                val weightsLabel = weights
                                if (weightsLabel != null) {
                                    Text(
                                        weightsLabel,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB0C4DE)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = { onActivateModel(m) }) {
                                    Text(
                                        when {
                                            m.fileName.endsWith(".litertlm", ignoreCase = true) || m.format == "LiteRT" ->
                                                "🧩 В чате (LiteRT-LM)"
                                            m.fileName.endsWith(".task", ignoreCase = true) || m.format == "MediaPipeTask" ->
                                                "🧩 В чате (MediaPipe)"
                                            else -> "📱 В чате (Termux)"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(onClick = {
                                localModelManager.removeModel(m.id)
                                importedModels = localModelManager.getImportedModels()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Offline Server Status Glass Card =================
        val isOnline = isServerOnline == true
        val statusGlowColor = when (isServerOnline) {
            true -> LiquidGlassColors.NeonMintGlow
            false -> LiquidGlassColors.RubyErrorGlow
            null -> LiquidGlassColors.WarmPeachGlow
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.72f,
                    specularAlpha = 0.92f,
                    borderWidth = 1.2.dp,
                    glowColor = statusGlowColor,
                    glowRadius = 6.dp,
                    sheenIntensity = 0.65f,
                    highlightColor = Color.White,
                    bounceColor = statusGlowColor.copy(alpha = 0.4f)
                )
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Статус офлайн сервера: ", fontWeight = FontWeight.Bold, color = Color(0xFFF7EFE9), fontSize = 13.sp)

                    // Glass Status Badge
                    Box(
                        modifier = Modifier
                            .liquidGlassCapsule(
                                tintColor = LiquidGlassColors.ObsidianDark,
                                frostedAlpha = 0.85f,
                                specularAlpha = 0.90f,
                                borderWidth = 1.dp,
                                glowColor = statusGlowColor,
                                glowRadius = 4.dp
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusGlowColor, CircleShape)
                                    .liquidGlassGlow(statusGlowColor, radius = 4.dp, alpha = 0.9f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (isServerOnline) {
                                    true -> "Работает (8080)"
                                    false -> "Не запущен"
                                    null -> "Проверка..."
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = statusGlowColor
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onActivateLocalServer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Подключить к Асуне")
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isServerOnline = localModelManager.checkLocalServerHealth()
                            }
                        }
                    ) {
                        Text("Проверить")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onActivateOnDevice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("🧩 Встроить On-Device (LiteRT) и подключить")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Section: Context & Lite LLM Glass Card =================
        val freeRamMb = localModelManager.getAvailableRamBytes().let { if (it > 0) it / 1024 / 1024 else -1L }
        val totalRamMb = localModelManager.getTotalRamBytes().let { if (it > 0) it / 1024 / 1024 else -1L }
        var contextMode by remember { mutableStateOf(localModelManager.onDeviceContextMode) }
        var manualContext by remember { mutableStateOf(localModelManager.onDeviceManualContext) }
        var isLiteLlm by remember { mutableStateOf(localModelManager.isLiteLlmEnabled) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = LiquidGlassColors.CyberCyanTint,
                    frostedAlpha = 0.68f,
                    specularAlpha = 0.92f,
                    borderWidth = 1.2.dp,
                    glowColor = LiquidGlassColors.CyberCyanGlow,
                    glowRadius = 6.dp,
                    sheenIntensity = 0.65f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.CyberCyanBorder.copy(alpha = 0.4f)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    "⚙ Контекст и Lite LLM (Оптимизация RAM)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = LiquidGlassColors.CyberCyanGlow
                )
                Spacer(Modifier.height(6.dp))

                if (freeRamMb > 0) {
                    Box(
                        modifier = Modifier
                            .liquidGlassCapsule(
                                tintColor = LiquidGlassColors.CyberCyanTint,
                                frostedAlpha = 0.80f,
                                specularAlpha = 0.85f,
                                borderWidth = 0.8.dp,
                                glowColor = LiquidGlassColors.CyberCyanGlow,
                                glowRadius = 4.dp
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Свободно RAM: $freeRamMb МБ / $totalRamMb МБ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiquidGlassColors.CyberCyanGlow
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Text("Размер контекста On-Device модели:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF7EFE9))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ChipToggle(
                            selected = contextMode == "auto",
                            label = "Авто (по RAM)",
                            onClick = {
                                contextMode = "auto"
                                localModelManager.onDeviceContextMode = "auto"
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ChipToggle(
                            selected = contextMode == "manual",
                            label = "Вручную ($manualContext)",
                            onClick = {
                                contextMode = "manual"
                                localModelManager.onDeviceContextMode = "manual"
                            }
                        )
                    }
                }
                if (contextMode == "manual") {
                    Spacer(Modifier.height(4.dp))
                    Text("Выберите лимит токенов:", fontSize = 11.sp, color = Color(0xFFB0C4DE))
                    val presets = listOf(1024, 1280, 2048, 3072, 4096)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        presets.forEach { tokens ->
                            Box(modifier = Modifier.weight(1f)) {
                                ChipToggle(
                                    selected = manualContext == tokens,
                                    label = "$tokens",
                                    onClick = {
                                        manualContext = tokens
                                        localModelManager.onDeviceManualContext = tokens
                                    }
                                )
                            }
                        }
                    }
                }
                val effectiveTokens = localModelManager.getEffectiveContextLimit()
                Text("Итоговый контекст: $effectiveTokens токенов", fontSize = 11.sp, color = Color(0xFFB0C4DE))

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ Режим Lite LLM (Текст + авто-эмоции)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF5EBE0))
                        Text(
                            "Сжимает системный промпт с 1200 до ~100 токенов, а эмоции Асуны оцениваются автоматически. Идеально для 0.5B и 1.5B моделей.",
                            fontSize = 10.sp,
                            color = Color(0xFFB0C4DE)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isLiteLlm,
                        onCheckedChange = {
                            isLiteLlm = it
                            localModelManager.isLiteLlmEnabled = it
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ================= Section: LiteRT-LM (.litertlm) Glass Card =================
        // Настройки нужны только движку LiteRT-LM, поэтому карточка появляется,
        // когда в списке есть хотя бы одна .litertlm-модель.
        if (importedModels.any { it.fileName.endsWith(".litertlm", ignoreCase = true) }) {
            var useGpu by remember { mutableStateOf(localModelManager.useLiteRtLmGpu) }
            var speculative by remember { mutableStateOf(localModelManager.useLiteRtLmSpeculative) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        tintColor = LiquidGlassColors.NeonMintTint,
                        frostedAlpha = 0.68f,
                        specularAlpha = 0.92f,
                        borderWidth = 1.2.dp,
                        glowColor = LiquidGlassColors.NeonMintGlow,
                        glowRadius = 6.dp,
                        sheenIntensity = 0.65f,
                        highlightColor = Color.White,
                        bounceColor = LiquidGlassColors.NeonMintBorder.copy(alpha = 0.4f)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        "🧠 Движок LiteRT-LM (.litertlm)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = LiquidGlassColors.NeonMintGlow
                    )
                    Text(
                        "Эти два переключателя действуют только на .litertlm-модели. " +
                            "При смене настройки модель перезагрузится при следующем сообщении.",
                        fontSize = 11.sp,
                        color = Color(0xFFB0C4DE)
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPU-бэкенд", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF5EBE0))
                            Text(
                                "Быстрее, но на Mali-G77 не проверен. Если движок не поднимется, он сам вернётся на CPU.",
                                fontSize = 10.sp,
                                color = Color(0xFFB0C4DE)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = useGpu,
                            onCheckedChange = {
                                useGpu = it
                                localModelManager.useLiteRtLmGpu = it
                            }
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Спекулятивное декодирование", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF5EBE0))
                            Text(
                                "Ускоряет генерацию, но требует модель, собранную после 5 мая 2026. Если ответы станут странными — выключи.",
                                fontSize = 10.sp,
                                color = Color(0xFFB0C4DE)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = speculative,
                            onCheckedChange = {
                                speculative = it
                                localModelManager.useLiteRtLmSpeculative = it
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ================= Section: Download Direct Glass Card =================
        Text("⬇ Скачать модель прямо из приложения", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF7EFE9))
        val freeBytes = localModelManager.freeDiskBytes()
        if (freeBytes > 0) {
            Text(
                "Свободно на телефоне: ${freeBytes / 1024 / 1024} МБ",
                fontSize = 11.sp,
                color = Color(0xFFC9BCB0)
            )
        }

        // HF Token
        var hfToken by remember { mutableStateOf(localModelManager.hfToken) }
        var verifyingToken by remember { mutableStateOf(false) }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = hfToken,
            onValueChange = {
                hfToken = it
                localModelManager.hfToken = it
            },
            label = { Text("Токен HuggingFace (необязательно, только для Gemma 3)", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        Text(
            "Gemma 4, Qwen и Phi открыты и скачиваются БЕЗ токена. Токен нужен только для gated-модели Gemma 3.",
            fontSize = 10.sp,
            color = Color(0xFFC9BCB0),
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            OutlinedButton(onClick = {
                localModelManager.hfToken = hfToken
                Toast.makeText(context, "Токен сохранён", Toast.LENGTH_SHORT).show()
            }) { Text("Сохранить", fontSize = 12.sp) }
            OutlinedButton(
                enabled = !verifyingToken && hfToken.isNotBlank(),
                onClick = {
                    verifyingToken = true
                    coroutineScope.launch {
                        val res = localModelManager.verifyHfToken(hfToken)
                        verifyingToken = false
                        res.onSuccess { msg -> Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show() }
                        res.onFailure { err -> Toast.makeText(context, "❌ ${err.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            ) { Text(if (verifyingToken) "Проверка..." else "Проверить", fontSize = 12.sp) }
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                context.startActivity(intent)
            }) { Text("Как получить", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(10.dp))
        var downloading by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableStateOf<Int?>(null) }

        localModelManager.onDeviceRecommendations.forEach { rec ->
            val already = localModelManager.downloadedModelBytes(rec)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        tintColor = LiquidGlassColors.ObsidianElevated,
                        frostedAlpha = 0.70f,
                        specularAlpha = 0.88f,
                        borderWidth = 1.dp,
                        sheenIntensity = 0.5f,
                        highlightColor = Color.White,
                        bounceColor = LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.3f)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(rec.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF7EFE9))
                    Text("${rec.sizeFormatted} • ${rec.ramUsage} • ${rec.speedMi11T}", fontSize = 11.sp, color = LiquidGlassColors.WarmPeachGlow)
                    Text(rec.description, fontSize = 11.sp, color = Color(0xFFC9BCB0))

                    if (already != null) {
                        Spacer(Modifier.height(2.dp))
                        Text("✅ Уже скачано (${already / 1024 / 1024} МБ)", fontSize = 11.sp, color = LiquidGlassColors.NeonMintGlow)
                    }
                    if (rec.requiresHfToken && hfToken.isBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("⚠ Нужен токен HuggingFace", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    val isDownloading = downloading == rec.filename
                    if (isDownloading) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (downloadProgress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Скачивание… ${downloadProgress ?: 0}%", fontSize = 11.sp, color = Color(0xFFF5EBE0))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = downloading == null && rec.downloadUrl.isNotBlank(),
                            onClick = {
                                downloading = rec.filename
                                downloadProgress = 0
                                coroutineScope.launch {
                                    try {
                                        val m = localModelManager.downloadModel(rec) { p -> downloadProgress = p }
                                        importedModels = localModelManager.getImportedModels()
                                        Toast.makeText(context, "Скачано: ${m.displayName}. Подключаю к Асуне…", Toast.LENGTH_LONG).show()
                                        onActivateModel(m)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        downloading = null
                                        downloadProgress = null
                                    }
                                }
                            }
                        ) {
                            Text(
                                when {
                                    isDownloading -> "Скачиваем…"
                                    already != null -> "↻ Проверить"
                                    rec.downloadUrl.isNotBlank() -> "⬇ Скачать"
                                    else -> "🔗 Открыть HF"
                                },
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rec.huggingFaceUrl))
                            context.startActivity(intent)
                        }) { Text("Страница", fontSize = 12.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Section: Termux Command Glass Card =================
        Text("Команда для запуска в Termux на Xiaomi Mi 11T:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF7EFE9))
        val termuxCmd = localModelManager.getTermuxServerCommand(importedModels.firstOrNull())
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(14.dp),
                    tintColor = Color(0x60000000),
                    frostedAlpha = 0.85f,
                    specularAlpha = 0.70f,
                    borderWidth = 1.dp,
                    highlightColor = Color(0x88FFFFFF)
                )
                .clickable { onCopyText(termuxCmd) }
                .padding(12.dp)
        ) {
            Column {
                Text(termuxCmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF80FFCE))
                Spacer(Modifier.height(4.dp))
                Text("📋 Нажмите, чтобы скопировать команду", fontSize = 10.sp, color = LiquidGlassColors.WarmPeachGlow)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Рекомендованные модели для Mi 11T (8GB RAM):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF7EFE9))
        Spacer(Modifier.height(4.dp))
        localModelManager.recommendations.forEach { r ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        tintColor = LiquidGlassColors.ObsidianElevated,
                        frostedAlpha = 0.65f,
                        specularAlpha = 0.85f,
                        borderWidth = 1.dp
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(r.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF7EFE9))
                    Text("${r.sizeFormatted} • ОЗУ: ${r.ramUsage} • Скорость: ${r.speedMi11T}", fontSize = 11.sp, color = LiquidGlassColors.WarmPeachGlow)
                    Text(r.description, fontSize = 11.sp, color = Color(0xFFC9BCB0))
                }
            }
        }
    }
}

/**
 * Короткая подпись о разрядности весов `.litertlm` — или `null`, если это не
 * LiteRT-LM-модель либо файл не читается.
 *
 * Троичные веса `{-a, 0, +a}` лежат в `.litertlm` как `INT2` (2 бита на вес,
 * без потерь — три уровня кодируются кодами `{-1, 0, +1}`), а обычные
 * 4-битные — как `INT4`. Разница вдвое и по размеру, и по качеству, поэтому
 * показываем её явно.
 */
private fun weightLabel(m: LocalModelManager.ImportedModel): String? {
    if (!m.fileName.endsWith(".litertlm", ignoreCase = true)) return null
    val info = LiteRtLmModelInspector.inspect(m.filePath)
    if (!info.formatOk || info.weightTypes.isEmpty()) return null
    val hint = when {
        info.hasInt2Weights && !info.hasInt4Weights -> "троичные"
        info.hasInt2Weights -> "троичные + 4-битные"
        info.hasInt4Weights -> "4-битные"
        else -> null
    }
    return if (hint != null) "Веса: ${info.weightTypes.joinToString(", ")} — $hint" else null
}
