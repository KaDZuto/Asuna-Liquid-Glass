@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vectorheart.asuna.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectorheart.asuna.localai.LocalModelManager
import com.vectorheart.asuna.pickup.PickupMode
import com.vectorheart.asuna.tts.TtsManager
import com.vectorheart.asuna.ui.theme.LiquidGlassColors
import com.vectorheart.asuna.ui.theme.liquidGlassBorder
import com.vectorheart.asuna.ui.theme.liquidGlassCapsule
import com.vectorheart.asuna.ui.theme.liquidGlassGlow
import com.vectorheart.asuna.ui.theme.liquidGlassSurface
import java.io.File

/**
 * Settings modal sheet styled with iOS 26 Liquid Glass design.
 * Features translucent frosted glass cards, dual-tone specular borders,
 * glowing neon toggles, and responsive sliders.
 */
@Composable
fun SettingsSheetContent(
    apiType: String,
    onApiTypeChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    ttsMode: String,
    onTtsModeChange: (String) -> Unit,
    sileroUrl: String,
    onSileroUrlChange: (String) -> Unit,
    ttsRate: Float,
    onTtsRateChange: (Float) -> Unit,
    ttsPitch: Float,
    onTtsPitchChange: (Float) -> Unit,
    onTestSpeech: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onClose: () -> Unit,
    onOpenMemorySheet: () -> Unit = {},
    onBackgroundChanged: () -> Unit = {},
    onOpenLocalModels: () -> Unit = {},
    isPickupMode: Boolean = false,
    pickupScenario: PickupMode.Scenario? = null,
    onTogglePickupMode: () -> Unit = {},
    localModelManager: LocalModelManager? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("asuna_settings", Context.MODE_PRIVATE) }
    var personaUserName by remember { mutableStateOf(prefs.getString("persona_user_name", "") ?: "") }
    var personaExtra by remember { mutableStateOf(prefs.getString("persona_extra", "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Modal Header with Glass Pill Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LiquidGlassColors.WarmPeachGlow, CircleShape)
                    .liquidGlassGlow(LiquidGlassColors.WarmPeachGlow, radius = 6.dp, alpha = 0.9f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Настройки ИИ и Синтеза Речи",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF7EFE9)
            )
        }

        // ================= Section 1: AI Provider Glass Card =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.70f,
                    specularAlpha = 0.90f,
                    borderWidth = 1.1.dp,
                    sheenIntensity = 0.6f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.35f)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    "ИИ Провайдер",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiquidGlassColors.WarmPeachGlow
                )
                Spacer(Modifier.height(8.dp))

                val providers = listOf(
                    "gemini" to "✨ Gemini Pro / AI Studio",
                    "openai-compatible" to "🌐 OpenRouter",
                    "deepseek" to "🐋 DeepSeek V3/R1",
                    "antigravity-bridge" to "⚡ Antigravity CLI (ПК)",
                    "local-termux" to "📱 Офлайн (Mi 11T / Termux)",
                    "local-ondevice" to "🧩 On-Device (.task / .litertlm)",
                    "openai" to "OpenAI (GPT-4o)",
                    "anthropic" to "Anthropic (Claude)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    providers.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (id, label) ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ChipToggle(
                                        selected = (id == "antigravity-bridge" && baseUrl.contains("8080") && model.contains("gemini-3")) ||
                                                (id == "local-termux" && baseUrl.contains("127.0.0.1:8080")) ||
                                                apiType == id,
                                        label = label,
                                        onClick = { onApiTypeChange(id) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Base URL", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE2D6CA))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))
                Text("Модель", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE2D6CA))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))
                Text("API-ключ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE2D6CA))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Section 2: Speech Synthesis (TTS) Glass Card =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.70f,
                    specularAlpha = 0.90f,
                    borderWidth = 1.1.dp,
                    sheenIntensity = 0.6f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.AsunaLavenderBorder.copy(alpha = 0.35f)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    "Синтез Речи (TTS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiquidGlassColors.AsunaLavenderGlow
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ChipToggle(
                            selected = ttsMode == TtsManager.TTS_MODE_SYSTEM,
                            label = "Android System TTS",
                            onClick = { onTtsModeChange(TtsManager.TTS_MODE_SYSTEM) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ChipToggle(
                            selected = ttsMode == TtsManager.TTS_MODE_SILERO_HTTP,
                            label = "Silero HTTP (ПК/Baya)",
                            onClick = { onTtsModeChange(TtsManager.TTS_MODE_SILERO_HTTP) }
                        )
                    }
                }

                if (ttsMode == TtsManager.TTS_MODE_SILERO_HTTP) {
                    Spacer(Modifier.height(8.dp))
                    Text("URL сервера Silero TTS (ПК-версия)", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE2D6CA))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = sileroUrl,
                        onValueChange = onSileroUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("http://192.168.1.100:8000") },
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Скорость речи: ${String.format("%.2f", ttsRate)}x", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2D6CA))
                Slider(value = ttsRate, onValueChange = onTtsRateChange, valueRange = 0.7f..1.5f)

                Text("Тон голоса (Pitch): ${String.format("%.2f", ttsPitch)}x", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2D6CA))
                Slider(value = ttsPitch, onValueChange = onTtsPitchChange, valueRange = 0.8f..1.4f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTestSpeech,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Тест голоса")
                    }
                    OutlinedButton(
                        onClick = onOpenTtsSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Голоса в системе")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Section 3: Persona & Memory Glass Card =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.70f,
                    specularAlpha = 0.90f,
                    borderWidth = 1.1.dp,
                    sheenIntensity = 0.6f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.35f)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    "Личность и память",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiquidGlassColors.WarmPeachGlow
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = personaUserName,
                    onValueChange = { personaUserName = it; prefs.edit().putString("persona_user_name", it).apply() },
                    label = { Text("Ваше имя (Асуна будет так к вам обращаться)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = personaExtra,
                    onValueChange = { personaExtra = it; prefs.edit().putString("persona_extra", it).apply() },
                    label = { Text("Доп. правила личности (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onClose()
                            onOpenLocalModels()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⬇ Скачать модель для Асуны")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenMemorySheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧠 Память Асуны (просмотр / импорт JSON)")
                }
                Spacer(Modifier.height(8.dp))

                val bgLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                            val out = File(context.filesDir, "chat_background.img")
                            input?.use { it.copyTo(out.outputStream()) }
                            onBackgroundChanged()
                            Toast.makeText(context, "Фон установлен", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { bgLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Text("🖼 Сменить фон чата")
                    }
                    OutlinedButton(onClick = {
                        val f = File(context.filesDir, "chat_background.img")
                        if (f.exists()) f.delete()
                        onBackgroundChanged()
                    }) {
                        Text("Сбросить")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ================= Section 4: Pickup Trainer Glass Card =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    tintColor = if (isPickupMode) LiquidGlassColors.RubyErrorTint else LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.72f,
                    specularAlpha = 0.92f,
                    borderWidth = 1.1.dp,
                    glowColor = if (isPickupMode) LiquidGlassColors.RubyErrorGlow else Color.Transparent,
                    glowRadius = if (isPickupMode) 10.dp else 0.dp,
                    sheenIntensity = 0.6f,
                    highlightColor = Color.White,
                    bounceColor = if (isPickupMode) LiquidGlassColors.RubyErrorBorder else LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.35f)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    "Тренажёр общения и знакомств",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPickupMode) LiquidGlassColors.RubyErrorGlow else LiquidGlassColors.WarmPeachGlow
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Тренировка флирта и спонтанных диалогов с Асуной в случайных жизненных ситуациях.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC9BCB0)
                )
                Spacer(Modifier.height(8.dp))

                if (isPickupMode && pickupScenario != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCapsule(
                                tintColor = LiquidGlassColors.RubyErrorTint,
                                frostedAlpha = 0.85f,
                                specularAlpha = 0.95f,
                                borderWidth = 1.dp,
                                glowColor = LiquidGlassColors.RubyErrorGlow,
                                glowRadius = 4.dp
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text("Активен сценарий: ${pickupScenario.location}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFFD4BD))
                            Text(pickupScenario.situation, fontSize = 11.sp, color = Color(0xFFF0E0D6))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onTogglePickupMode,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Выйти из режима тренажёра")
                    }
                } else {
                    OutlinedButton(
                        onClick = onTogglePickupMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Запустить сценарий знакомства")
                    }
                }
            }
        }

        // ================= Section 5: Lite LLM & On-Device Optimization Glass Card =================
        if (localModelManager != null) {
            Spacer(Modifier.height(14.dp))
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
                        bounceColor = LiquidGlassColors.CyberCyanBorder.copy(alpha = 0.45f)
                    )
                    .padding(14.dp)
            ) {
                val freeRamMb = localModelManager.getAvailableRamBytes().let { if (it > 0) it / 1024 / 1024 else -1L }
                var isLite by remember { mutableStateOf(localModelManager.isLiteLlmEnabled) }

                Column {
                    Text(
                        "Оптимизация On-Device и Lite LLM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlassColors.CyberCyanGlow
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ Режим Lite LLM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF5EBE0))
                            Text(
                                "Сжимает системный промпт в 10 раз (~100 токенов вместо 1200). Эмоции и движения подбираются автоматически.",
                                fontSize = 10.sp,
                                color = Color(0xFFB0C4DE)
                            )
                        }
                        Switch(
                            checked = isLite,
                            onCheckedChange = {
                                isLite = it
                                localModelManager.isLiteLlmEnabled = it
                            }
                        )
                    }

                    if (freeRamMb > 0) {
                        Spacer(Modifier.height(6.dp))
                        val eff = localModelManager.getEffectiveContextLimit()
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
                                "Свободно RAM: $freeRamMb МБ • Контекст: $eff токенов",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LiquidGlassColors.CyberCyanGlow
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Done button styled as Liquid Glass Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCapsule(
                    tintColor = LiquidGlassColors.WarmPeachTint,
                    frostedAlpha = 0.88f,
                    specularAlpha = 1.0f,
                    borderWidth = 1.2.dp,
                    glowColor = LiquidGlassColors.WarmPeachGlow,
                    glowRadius = 10.dp,
                    sheenIntensity = 0.8f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.WarmPeachBorder
                )
                .clickable { onClose() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Готово",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1A0F1F)
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}
