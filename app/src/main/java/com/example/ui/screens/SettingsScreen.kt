package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCoral
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeMint
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isCheckingCredits by viewModel.isCheckingCredits.collectAsStateWithLifecycle()

    var newElevenKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var openRouterKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var customBaseUrl by remember { mutableStateOf(settings.customBaseUrl) }
    var customModel by remember { mutableStateOf(settings.customModel) }
    var customApiKey by remember { mutableStateOf(settings.customApiKey) }
    var aiHordeKey by remember { mutableStateOf(settings.aiHordeKey) }
    var allowOfflineTts by remember { mutableStateOf(settings.allowOfflineTtsFallback) }

    LaunchedEffect(settings) {
        groqKey = settings.groqApiKey
        openRouterKey = settings.openRouterApiKey
        customBaseUrl = settings.customBaseUrl
        customModel = settings.customModel
        customApiKey = settings.customApiKey
        aiHordeKey = settings.aiHordeKey
        allowOfflineTts = settings.allowOfflineTtsFallback
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "API & Studio Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure ElevenLabs TTS rotation pool & optional LLM routers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. ELEVENLABS KEY POOL MANAGER
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = ForgeAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "ElevenLabs API Key Pool",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkAllElevenLabsCredits() },
                            enabled = !isCheckingCredits,
                            modifier = Modifier.testTag("check_credits_button")
                        ) {
                            if (isCheckingCredits) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Check Credits",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Check Credits", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Text(
                        text = "Keys rotate automatically on quota exhaustion or 401/429 errors using eleven_multilingual_v2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newElevenKey,
                            onValueChange = { newElevenKey = it },
                            label = { Text("Add ElevenLabs xi-api-key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("elevenlabs_key_input"),
                            colors = studioTextFieldColors()
                        )
                        Button(
                            onClick = {
                                viewModel.addElevenLabsKey(newElevenKey)
                                newElevenKey = ""
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("add_elevenlabs_key_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add key")
                        }
                    }

                    if (settings.elevenLabsKeys.isEmpty()) {
                        Surface(
                            color = StudioSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No ElevenLabs keys added yet. Add a key above for studio-grade Tagalog voiceover with word-level alignment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        settings.elevenLabsKeys.forEach { entry ->
                            Surface(
                                color = StudioSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioOutline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.maskedKey,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val statusText = when {
                                            entry.lastError != null -> "Status: ${entry.lastError}"
                                            entry.remainingChars != null ->
                                                "${entry.remainingChars} / ${entry.limitChars ?: "?"} chars left (${entry.tier ?: "free"})"
                                            else -> "Tap Check Credits to verify quota"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (entry.lastError != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                ForgeMint
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeElevenLabsKey(entry.key) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Remove Key",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Android TTS Fallback",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Use device TTS if key pool is empty or exhausted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowOfflineTts,
                            onCheckedChange = { allowOfflineTts = it }
                        )
                    }
                }
            }

            // 2. OPTIONAL OPEN-SOURCE LLM PROVIDERS + AI HORDE DEFAULT
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = ForgeCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "LLM Story Director Providers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = ForgeCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Optional — defaults to free keyless AI Horde",
                            style = MaterialTheme.typography.labelMedium,
                            color = ForgeCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = { groqKey = it },
                        label = { Text("Groq API Key (gsk_...) — Optional") },
                        supportingText = { Text("Optional — defaults to free keyless AI Horde (qwen/qwen3-32b)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("groq_key_input"),
                        colors = studioTextFieldColors()
                    )

                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = { openRouterKey = it },
                        label = { Text("OpenRouter API Key (sk-or-...) — Optional") },
                        supportingText = { Text("Optional — defaults to free keyless AI Horde (qwen3-235b / deepseek-r1:free)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("openrouter_key_input"),
                        colors = studioTextFieldColors()
                    )

                    Text(
                        text = "Custom OpenAI-Compatible Endpoint (Optional — defaults to free keyless AI Horde)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("Base URL (e.g. https://api.gonkarouter.com/v1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = studioTextFieldColors()
                    )

                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text("Model ID (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = studioTextFieldColors()
                    )

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("Custom Endpoint API Key (Optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = studioTextFieldColors()
                    )

                    OutlinedTextField(
                        value = aiHordeKey,
                        onValueChange = { aiHordeKey = it },
                        label = { Text("AI Horde API Key (Default: 0000000000)") },
                        supportingText = { Text("Used for both keyless LLM Text fallback & FLUX/SDXL backdrop art") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("aihorde_key_input"),
                        colors = studioTextFieldColors()
                    )

                    Button(
                        onClick = {
                            viewModel.saveLlmSettings(
                                groqApiKey = groqKey,
                                openRouterApiKey = openRouterKey,
                                customBaseUrl = customBaseUrl,
                                customModel = customModel,
                                customApiKey = customApiKey,
                                aiHordeKey = aiHordeKey,
                                allowOfflineTtsFallback = allowOfflineTts
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeCoral)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save Settings")
                        Spacer(Modifier.width(8.dp))
                        Text("Save API & Studio Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
