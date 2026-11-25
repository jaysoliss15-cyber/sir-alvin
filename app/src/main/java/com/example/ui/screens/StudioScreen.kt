package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BuiltInPrompts
import com.example.model.ActorAction
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.HudWidget
import com.example.model.PipelineStep
import com.example.model.ProductionMode
import com.example.model.ShotFraming
import com.example.model.StatusPill
import com.example.model.WorldFx
import com.example.render.SceneCompositor
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCoral
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeMint
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.viewmodel.MainViewModel

@Composable
fun StudioScreen(
    viewModel: MainViewModel,
    onOpenVideosTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val topic by viewModel.topic.collectAsStateWithLifecycle()
    val productionMode by viewModel.productionMode.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val importedClips by viewModel.importedClips.collectAsStateWithLifecycle()
    val previewCustomSprites by viewModel.previewCustomSprites.collectAsStateWithLifecycle()
    val selectedDurationSec by viewModel.selectedDurationSec.collectAsStateWithLifecycle()
    val previewPlaying by viewModel.previewPlaying.collectAsStateWithLifecycle()
    val previewTimeMs by viewModel.previewTimeMs.collectAsStateWithLifecycle()
    val demoRole by viewModel.demoActorRole.collectAsStateWithLifecycle()
    val demoAction by viewModel.demoActorAction.collectAsStateWithLifecycle()
    val demoFx by viewModel.demoWorldFx.collectAsStateWithLifecycle()
    val demoPill by viewModel.demoStatusPill.collectAsStateWithLifecycle()
    val demoHudWidget by viewModel.demoHudWidget.collectAsStateWithLifecycle()
    val demoShotFraming by viewModel.demoShotFraming.collectAsStateWithLifecycle()

    val activeTemplate = settings.activeTemplate
    var showPromptEditor by remember { mutableStateOf(false) }
    var templateDropdownOpen by remember { mutableStateOf(false) }
    var editableTemplateName by remember(activeTemplate?.id) {
        mutableStateOf(activeTemplate?.name.orEmpty())
    }
    var editableTemplateBody by remember(activeTemplate?.id, activeTemplate?.content) {
        mutableStateOf(activeTemplate?.content.orEmpty())
    }
    var voiceInput by remember(settings.selectedVoiceId) {
        mutableStateOf(settings.selectedVoiceId)
    }

    LaunchedEffect(activeTemplate) {
        if (activeTemplate != null) {
            editableTemplateName = activeTemplate.name
            editableTemplateBody = activeTemplate.content
        }
    }

    val txtImporterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importTxtPrompt(uri)
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            // Start generation whether granted or denied (service still runs foreground)
            viewModel.startGeneration()
        }
    )

    fun triggerGenerate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startGeneration()
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
            // Studio Header Banner
            StudioTopHeader()

            // 0. THREE STUDIO PRO PRODUCTION MODES + CLIP AUTO-HUD PICKER
            ProductionModeSelectorCard(
                selectedMode = productionMode,
                onSelectMode = { viewModel.selectProductionMode(it) }
            )

            AnimatedVisibility(visible = productionMode == ProductionMode.CLIP_AUTO_EDITOR) {
                ClipAutoEditorPickerCard(
                    clips = importedClips,
                    onAddClips = { viewModel.addImportedClips(it) },
                    onRemoveClip = { viewModel.removeImportedClip(it) },
                    onClearClips = { viewModel.clearImportedClips() }
                )
            }

            // 1. MASTER PROMPT TEMPLATE BAR + .TXT IMPORTER + INLINE EDITOR
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = "Master prompt",
                                tint = ForgeAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Master System Prompt",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = ForgeMint.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "JSON Schema Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeMint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Template selector dropdown + Import .txt + Edit Prompt
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { templateDropdownOpen = true }
                                    .testTag("prompt_template_selector"),
                                color = StudioSurfaceElevated,
                                border = BorderStroke(1.dp, StudioOutline),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeTemplate?.name ?: "Select Template",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Choose prompt template",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = templateDropdownOpen,
                                onDismissRequest = { templateDropdownOpen = false }
                            ) {
                                settings.promptTemplates.forEach { tpl ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = tpl.name,
                                                    fontWeight = if (tpl.id == activeTemplate?.id) {
                                                        FontWeight.Bold
                                                    } else FontWeight.Normal
                                                )
                                                if (!tpl.isBuiltIn) {
                                                    Text(
                                                        text = ".TXT",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ForgeCyan
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectPromptTemplate(tpl.id)
                                            templateDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { txtImporterLauncher.launch(arrayOf("text/plain")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, ForgeCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("import_txt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = "Import .txt",
                                tint = ForgeCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Import .txt",
                                style = MaterialTheme.typography.labelMedium,
                                color = ForgeCyan
                            )
                        }

                        OutlinedButton(
                            onClick = { showPromptEditor = !showPromptEditor },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (showPromptEditor) ForgeCoral else StudioOutline
                            ),
                            modifier = Modifier.testTag("edit_prompt_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Prompt",
                                tint = if (showPromptEditor) ForgeCoral else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Prompt",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (showPromptEditor) ForgeCoral else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Expandable Inline System Prompt Editor
                    AnimatedVisibility(visible = showPromptEditor && activeTemplate != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editableTemplateName,
                                onValueChange = { editableTemplateName = it },
                                label = { Text("Template Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_name_input"),
                                colors = studioTextFieldColors()
                            )
                            OutlinedTextField(
                                value = editableTemplateBody,
                                onValueChange = { editableTemplateBody = it },
                                label = { Text("Creative Storytelling Instructions (Tagalog Tone / Rules)") },
                                minLines = 5,
                                maxLines = 10,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_body_input"),
                                colors = studioTextFieldColors()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            activeTemplate?.let { tpl ->
                                                if (tpl.isBuiltIn) {
                                                    viewModel.resetPromptTemplate(tpl.id)
                                                } else {
                                                    editableTemplateName = tpl.name
                                                    editableTemplateBody = tpl.content
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("reset_prompt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Reset",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reset")
                                    }
                                    if (activeTemplate != null && !activeTemplate.isBuiltIn) {
                                        TextButton(
                                            onClick = { viewModel.deletePromptTemplate(activeTemplate.id) },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.testTag("delete_prompt_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Delete")
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        activeTemplate?.let {
                                            viewModel.savePromptTemplate(
                                                it.id,
                                                editableTemplateName,
                                                editableTemplateBody
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("save_prompt_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Save,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }

            // 2. TOPIC INPUT + RANDOM VIRAL TAGALOG IDEA
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Story Topic / Hook",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { viewModel.updateTopic(it) },
                        placeholder = { Text("e.g. Bakit laging ubos ang sahod...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("topic_input"),
                        colors = studioTextFieldColors()
                    )
                    OutlinedButton(
                        onClick = { viewModel.pickRandomIdea() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("random_idea_button"),
                        border = BorderStroke(1.dp, ForgeAmber.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Random Idea",
                            tint = ForgeAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Random Viral Tagalog Idea",
                            color = ForgeAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. HORIZONTAL ANIMATION STYLE PRESET CARDS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Animation Style Preset (${AnimationStyle.entries.size} Engines)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(AnimationStyle.entries) { style ->
                        val isSelected = style == selectedStyle
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { viewModel.selectStyle(style) }
                                .testTag("style_card_${style.name}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    style.accentColor.copy(alpha = 0.14f)
                                } else {
                                    StudioSurface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) style.accentColor else StudioOutline
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = style.accentColor.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = style.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = style.accentColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = style.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Captions: ${style.captionStyleLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeAmber.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 4. DURATION & VOICE ID SELECTOR
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Target Duration & Voice",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 90).forEach { sec ->
                            val selected = selectedDurationSec == sec
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectDuration(sec) },
                                label = {
                                    Text(
                                        text = "${sec}s (${if (sec == 30) "7" else if (sec == 60) "13" else "19"} scenes)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("duration_${sec}s"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForgeCoral.copy(alpha = 0.22f),
                                    selectedLabelColor = ForgeCoral
                                )
                            )
                        }
                    }

                    // ElevenLabs Voice Presets + Editable Voice ID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BuiltInPrompts.VOICE_PRESETS.forEach { preset ->
                            val active = voiceInput == preset.id
                            FilterChip(
                                selected = active,
                                onClick = {
                                    voiceInput = preset.id
                                    viewModel.selectVoiceId(preset.id)
                                },
                                label = {
                                    Text(
                                        text = "${preset.name} • ${preset.tag}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = voiceInput,
                        onValueChange = {
                            voiceInput = it
                            viewModel.selectVoiceId(it)
                        },
                        label = { Text("ElevenLabs Voice ID") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = "Voice ID")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_id_input"),
                        colors = studioTextFieldColors()
                    )
                }
            }

            // 4B. QUICK STUDIO PRO SPRITE & BGM CONTROLS
            CharacterSpriteManagerCard(
                customSpritePaths = settings.customSpritePaths,
                previewSprites = previewCustomSprites,
                stickerOutlineEnabled = settings.spriteStickerOutline,
                onImportSprite = { role, uri -> viewModel.importCharacterSprite(role, uri) },
                onRemoveSprite = { role -> viewModel.removeCharacterSprite(role) },
                onToggleStickerOutline = { viewModel.setSpriteStickerOutline(it) }
            )

            CustomBgmCard(
                customBgmName = settings.customBgmName,
                onImportBgm = { uri -> viewModel.importCustomBgm(uri) },
                onClearBgm = { viewModel.clearCustomBgm() }
            )

            // 5. GENERATE VIDEO CTA + LIVE PIPELINE STEPPER
            if (!progress.isRunning) {
                Button(
                    onClick = { triggerGenerate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_video_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForgeCoral)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieCreation,
                        contentDescription = "Generate Video"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (productionMode) {
                            ProductionMode.CLIP_AUTO_EDITOR -> "Finish Clips + Auto-HUD (1080x1920)"
                            ProductionMode.CROSS_SECTION_EXPLAINER -> "Generate 2D Cutaway Video (1080x1920)"
                            ProductionMode.FULL_AI_STORY -> "Generate Animated Video (1080x1920)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.cancelGeneration() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("cancel_render_button"),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Active Render", color = MaterialTheme.colorScheme.error)
                }
            }

            // Live Step-by-Step Progress Card
            if (progress.step != PipelineStep.IDLE) {
                PipelineProgressCard(
                    progress = progress,
                    onOpenVideos = onOpenVideosTab
                )
            }

            // 6. INTERACTIVE LIVE 9:16 CANVAS PREVIEW
            LiveCanvasPreviewCard(
                viewModel = viewModel,
                productionMode = productionMode,
                selectedStyle = selectedStyle,
                customSprites = previewCustomSprites,
                previewPlaying = previewPlaying,
                previewTimeMs = previewTimeMs,
                generatedScenes = progress.generatedScenesPreview,
                demoRole = demoRole,
                demoAction = demoAction,
                demoFx = demoFx,
                demoPill = demoPill,
                demoHudWidget = demoHudWidget,
                demoShotFraming = demoShotFraming
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudioTopHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E172E), Color(0xFF15192B), Color(0xFF281718))
                )
            )
            .border(1.dp, StudioOutline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ToonForge Tagalog",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "1080x1920 @ 30fps On-Device Puppet & EGL14 Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB5BDD6)
                )
            }
            Surface(
                color = ForgeCoral.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ForgeCoral.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = ForgeAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "9:16",
                        style = MaterialTheme.typography.labelMedium,
                        color = ForgeAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineProgressCard(
    progress: com.example.model.GenerationProgress,
    onOpenVideos: () -> Unit
) {
    val steps = listOf(
        PipelineStep.SCRIPT,
        PipelineStep.AUDIO,
        PipelineStep.ART,
        PipelineStep.PUPPET_RENDER,
        PipelineStep.MUXING
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(
            1.dp,
            when (progress.step) {
                PipelineStep.COMPLETED -> ForgeMint
                PipelineStep.ERROR -> MaterialTheme.colorScheme.error
                else -> ForgeCoral
            }
        ),
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
                Text(
                    text = when (progress.step) {
                        PipelineStep.COMPLETED -> "Export Complete!"
                        PipelineStep.ERROR -> "Pipeline Error"
                        else -> "Rendering in Background Service..."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = when (progress.step) {
                        PipelineStep.COMPLETED -> ForgeMint
                        PipelineStep.ERROR -> MaterialTheme.colorScheme.error
                        else -> ForgeAmber
                    }
                )
                if (progress.providerInfo.isNotBlank()) {
                    Text(
                        text = progress.providerInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeCyan
                    )
                }
            }

            // 5-Step Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                steps.forEach { st ->
                    val done = progress.step == PipelineStep.COMPLETED ||
                        (progress.step.index > st.index && progress.step.index > 0)
                    val active = progress.step == st
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = when {
                            done -> ForgeMint.copy(alpha = 0.2f)
                            active -> ForgeCoral.copy(alpha = 0.25f)
                            else -> StudioSurfaceHighlight
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = if (active) BorderStroke(1.dp, ForgeCoral) else null
                    ) {
                        Text(
                            text = st.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                done -> ForgeMint
                                active -> Color.White
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (progress.step == PipelineStep.COMPLETED) ForgeMint else ForgeCoral,
                trackColor = StudioSurfaceHighlight
            )

            Text(
                text = progress.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (progress.step == PipelineStep.COMPLETED) {
                Button(
                    onClick = onOpenVideos,
                    colors = ButtonDefaults.buttonColors(containerColor = ForgeMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Open Gallery",
                        tint = Color.Black
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Watch in Videos Gallery", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LiveCanvasPreviewCard(
    viewModel: MainViewModel,
    productionMode: ProductionMode,
    selectedStyle: AnimationStyle,
    customSprites: Map<ActorRole, android.graphics.Bitmap>,
    previewPlaying: Boolean,
    previewTimeMs: Double,
    generatedScenes: List<com.example.model.TimelineScene>,
    demoRole: ActorRole,
    demoAction: ActorAction,
    demoFx: WorldFx,
    demoPill: StatusPill,
    demoHudWidget: HudWidget,
    demoShotFraming: ShotFraming
) {
    val activeScenes = remember(
        generatedScenes, selectedStyle, demoRole, demoAction, demoFx, demoPill, demoHudWidget, demoShotFraming
    ) {
        if (generatedScenes.isNotEmpty()) generatedScenes else viewModel.buildDemoPreviewScenes()
    }
    val totalDurMs = activeScenes.lastOrNull()?.endMs?.coerceAtLeast(1000.0) ?: 4200.0

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
                Column {
                    Text(
                        text = if (generatedScenes.isNotEmpty()) "Generated Story Preview" else "Interactive Puppet Stage Preview",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Live 9:16 Canvas • ${selectedStyle.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = selectedStyle.accentColor
                    )
                }
                IconButton(
                    onClick = { viewModel.togglePreviewPlay() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ForgeCoral)
                        .testTag("preview_play_pause")
                ) {
                    Icon(
                        imageVector = if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (previewPlaying) "Pause preview" else "Play preview",
                        tint = Color.White
                    )
                }
            }

            // 9:16 Live Preview Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, selectedStyle.accentColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        SceneCompositor.drawFrame(
                            canvas = canvas.nativeCanvas,
                            width = size.width,
                            height = size.height,
                            scenes = activeScenes,
                            style = selectedStyle,
                            currentMs = previewTimeMs,
                            fallbackBackdropCache = viewModel.previewBackdropCache,
                            productionMode = productionMode,
                            customSprites = customSprites
                        )
                    }
                }
            }

            // Timeline Scrubber
            Slider(
                value = (previewTimeMs / totalDurMs).toFloat().coerceIn(0f, 1f),
                onValueChange = { frac -> viewModel.seekPreview(frac * totalDurMs) },
                colors = SliderDefaults.colors(
                    thumbColor = ForgeCoral,
                    activeTrackColor = ForgeCoral
                )
            )

            // Interactive Demo Rig Controls when viewing sample stage
            if (generatedScenes.isEmpty()) {
                Text(
                    text = "Test Puppet Rig, Action & World FX Live:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActorRole.entries.forEach { role ->
                        FilterChip(
                            selected = role == demoRole,
                            onClick = { viewModel.setDemoRole(role) },
                            label = { Text(role.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActorAction.entries.forEach { act ->
                        FilterChip(
                            selected = act == demoAction,
                            onClick = { viewModel.setDemoAction(act) },
                            label = { Text(act.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WorldFx.entries.forEach { fx ->
                        FilterChip(
                            selected = fx == demoFx,
                            onClick = { viewModel.setDemoWorldFx(fx) },
                            label = { Text(fx.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    text = "Test Kinetic HUD Pill & Telemetry Widget:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill.entries.forEach { pill ->
                        FilterChip(
                            selected = pill == demoPill,
                            onClick = { viewModel.setDemoStatusPill(pill) },
                            label = { Text("Pill: ${pill.name}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HudWidget.entries.forEach { widget ->
                        FilterChip(
                            selected = widget == demoHudWidget,
                            onClick = { viewModel.setDemoHudWidget(widget) },
                            label = { Text("HUD: ${widget.name}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ShotFraming.WIDE_FULL_BODY,
                        ShotFraming.MEDIUM_WAIST_UP,
                        ShotFraming.CLOSE_UP_FACE,
                        ShotFraming.RULE_OF_THIRDS_LEFT,
                        ShotFraming.RULE_OF_THIRDS_RIGHT
                    ).forEach { framing ->
                        FilterChip(
                            selected = framing == demoShotFraming,
                            onClick = { viewModel.setDemoShotFraming(framing) },
                            label = { Text("Shot: ${framing.name}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun studioTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ForgeCoral,
    unfocusedBorderColor = StudioOutline,
    focusedContainerColor = StudioSurfaceElevated,
    unfocusedContainerColor = StudioSurfaceElevated
)
