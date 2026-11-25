package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActorRole
import com.example.model.ImportedClipItem
import com.example.model.ProductionMode
import com.example.model.PronunciationEntry
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCoral
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeMint
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight

@Composable
fun ProductionModeSelectorCard(
    selectedMode: ProductionMode,
    onSelectMode: (ProductionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = "Production Mode",
                        tint = ForgeCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Studio Pro Production Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = ForgeCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = selectedMode.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductionMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    val accent = when (mode) {
                        ProductionMode.FULL_AI_STORY -> ForgeCoral
                        ProductionMode.CLIP_AUTO_EDITOR -> ForgeAmber
                        ProductionMode.CROSS_SECTION_EXPLAINER -> ForgeCyan
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMode(mode) }
                            .testTag("mode_card_${mode.name}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) accent.copy(alpha = 0.16f) else StudioSurfaceElevated
                        ),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) accent else StudioOutline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClipAutoEditorPickerCard(
    clips: List<ImportedClipItem>,
    onAddClips: (List<Uri>) -> Unit,
    onRemoveClip: (String) -> Unit,
    onClearClips: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onAddClips(uris)
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, ForgeAmber.copy(alpha = 0.5f)),
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
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = "Clip Auto-HUD Finisher",
                        tint = ForgeAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Clip + Auto-HUD Finisher",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (clips.isNotEmpty()) {
                    TextButton(
                        onClick = onClearClips,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear (${clips.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                text = "Select multiple .mp4 clips or .jpg/.png photos. Clips are center-cropped to 1080x1920, looped automatically, and layered with Tagalog voiceover, GOAL/PROBLEM/FIX pills, HUD gauges & karaoke captions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { clipsLauncher.launch(arrayOf("video/*", "image/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = ForgeAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("select_clips_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = "Select clips",
                    tint = Color.Black
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (clips.isEmpty()) "Select MP4 Clips / Images" else "Add More Clips (${clips.size} ready)",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            if (clips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    clips.forEachIndexed { idx, clip ->
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StudioOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (clip.isVideo) Icons.Rounded.Movie else Icons.Rounded.Image,
                                        contentDescription = if (clip.isVideo) "Video clip" else "Image clip",
                                        tint = if (clip.isVideo) ForgeCyan else ForgeMint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Scene ${idx + 1}: ${clip.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveClip(clip.uriString) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove clip",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterSpriteManagerCard(
    customSpritePaths: Map<String, String>,
    previewSprites: Map<ActorRole, Bitmap>,
    stickerOutlineEnabled: Boolean,
    onImportSprite: (ActorRole, Uri) -> Unit,
    onRemoveSprite: (ActorRole) -> Unit,
    onToggleStickerOutline: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRole by remember { mutableStateOf(ActorRole.NARRATOR) }
    val spriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) onImportSprite(selectedRole, uri)
        }
    )

    val hasCustom = customSpritePaths.containsKey(selectedRole.name)
    val previewBmp = previewSprites[selectedRole]

    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Rounded.Face,
                        contentDescription = "Character Sprite Manager",
                        tint = ForgeMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Custom PNG Character Sprites",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = ForgeMint.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${customSpritePaths.size}/5 Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeMint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Upload transparent PNG cutouts per character role. Auto-strips 1.5px edge halos and animates speech bounce, 3/4 tilt & ground shadow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActorRole.entries.forEach { role ->
                    val assigned = customSpritePaths.containsKey(role.name)
                    FilterChip(
                        selected = role == selectedRole,
                        onClick = { selectedRole = role },
                        label = {
                            Text(
                                text = if (assigned) "${role.name} ✓" else role.name,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForgeMint.copy(alpha = 0.2f),
                            selectedLabelColor = ForgeMint
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioSurfaceHighlight)
                        .border(1.dp, StudioOutline, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBmp != null && !previewBmp.isRecycled) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Custom sprite preview for ${selectedRole.name}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(4.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Face,
                            contentDescription = "Default vector puppet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { spriteLauncher.launch(arrayOf("image/png", "image/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_sprite_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeMint)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = "Import PNG",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasCustom) "Replace PNG" else "Import PNG Cutout",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (hasCustom) {
                        OutlinedButton(
                            onClick = { onRemoveSprite(selectedRole) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clear_sprite_button"),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Restore default puppet",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Use Built-in Vector Puppet",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
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
                        text = "8px White Sticker Outline",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Adds crisp paper-cut border around imported PNG cutouts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = stickerOutlineEnabled,
                    onCheckedChange = onToggleStickerOutline,
                    modifier = Modifier.testTag("sprite_outline_switch")
                )
            }
        }
    }
}

@Composable
fun CustomBgmCard(
    customBgmName: String,
    onImportBgm: (Uri) -> Unit,
    onClearBgm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) onImportBgm(uri)
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Audio BGM Studio",
                        tint = ForgeAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Background Music & Sidechain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = ForgeAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Ducking: 10% / 28%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = if (customBgmName.isNotBlank()) ForgeMint else ForgeCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = customBgmName.ifBlank { "Built-in Ambient Lo-Fi Synth (84 BPM)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (customBgmName.isNotBlank()) {
                        IconButton(
                            onClick = onClearBgm,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Remove custom BGM",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { bgmLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_bgm_button"),
                border = BorderStroke(1.dp, ForgeAmber.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = "Import custom BGM",
                    tint = ForgeAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Import Custom BGM (.mp3 / .wav)",
                    color = ForgeAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PronunciationDictionaryCard(
    entries: List<PronunciationEntry>,
    onUpsertEntry: (String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wordInput by remember { mutableStateOf("") }
    var spokenInput by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = "Tagalog Pronunciation Dictionary",
                        tint = ForgeCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Tagalog TTS Pronunciation Dictionary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("reset_pronunciation_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset defaults",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Defaults")
                }
            }

            Text(
                text = "Applies only to ElevenLabs voice synthesis while keeping clean original spelling (e.g. Nike, WiFi, ₱, %) in video captions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = wordInput,
                    onValueChange = { wordInput = it },
                    label = { Text("Word / Symbol") },
                    placeholder = { Text("Nike") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronunciation_word_input"),
                    colors = studioTextFieldColors()
                )
                OutlinedTextField(
                    value = spokenInput,
                    onValueChange = { spokenInput = it },
                    label = { Text("Spoken Form") },
                    placeholder = { Text("Nayk") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronunciation_spoken_input"),
                    colors = studioTextFieldColors()
                )
                Button(
                    onClick = {
                        onUpsertEntry(wordInput, spokenInput)
                        if (wordInput.isNotBlank() && spokenInput.isNotBlank()) {
                            wordInput = ""
                            spokenInput = ""
                        }
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("add_pronunciation_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Save pronunciation rule")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.take(12).forEach { entry ->
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                wordInput = entry.original
                                spokenInput = entry.spoken
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = entry.original,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ForgeAmber
                                )
                                Text(
                                    text = "→",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = entry.spoken,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForgeMint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteEntry(entry.original) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete pronunciation entry",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
