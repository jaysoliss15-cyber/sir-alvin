package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BuiltInPrompts
import com.example.data.SettingsDataStore
import com.example.data.VideoGalleryRepository
import com.example.model.ActorAction
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.CameraMotion
import com.example.model.GenerationProgress
import com.example.model.HudWidget
import com.example.model.ImportedClipItem
import com.example.model.PipelineStep
import com.example.model.ProductionMode
import com.example.model.SavedVideoItem
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.StatusPill
import com.example.model.StudioSettings
import com.example.model.TimelineScene
import com.example.model.WordTiming
import com.example.model.WorldFx
import com.example.render.CharacterSpriteProcessor
import com.example.service.PipelineProgressBus
import com.example.service.VideoGenerationService
import com.example.tts.ElevenLabsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsStore = SettingsDataStore(appContext)
    private val galleryRepo = VideoGalleryRepository(appContext)
    private val elevenLabsClient = ElevenLabsClient(appContext)

    val settings: StateFlow<StudioSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StudioSettings(promptTemplates = BuiltInPrompts.DEFAULT_TEMPLATES)
    )

    val progress: StateFlow<GenerationProgress> = PipelineProgressBus.progress

    private val _topic = MutableStateFlow(BuiltInPrompts.VIRAL_TAGALOG_IDEAS.first())
    val topic: StateFlow<String> = _topic.asStateFlow()

    private val _productionMode = MutableStateFlow(ProductionMode.FULL_AI_STORY)
    val productionMode: StateFlow<ProductionMode> = _productionMode.asStateFlow()

    private val _selectedStyle = MutableStateFlow(AnimationStyle.PAPER_DIORAMA)
    val selectedStyle: StateFlow<AnimationStyle> = _selectedStyle.asStateFlow()

    private val _importedClips = MutableStateFlow<List<ImportedClipItem>>(emptyList())
    val importedClips: StateFlow<List<ImportedClipItem>> = _importedClips.asStateFlow()

    private val _previewCustomSprites = MutableStateFlow<Map<ActorRole, Bitmap>>(emptyMap())
    val previewCustomSprites: StateFlow<Map<ActorRole, Bitmap>> = _previewCustomSprites.asStateFlow()
    private val allocatedSpriteBitmaps = mutableListOf<Bitmap>()

    private val _selectedDurationSec = MutableStateFlow(30)
    val selectedDurationSec: StateFlow<Int> = _selectedDurationSec.asStateFlow()

    private val _savedVideos = MutableStateFlow<List<SavedVideoItem>>(emptyList())
    val savedVideos: StateFlow<List<SavedVideoItem>> = _savedVideos.asStateFlow()

    private val _isLoadingVideos = MutableStateFlow(false)
    val isLoadingVideos: StateFlow<Boolean> = _isLoadingVideos.asStateFlow()

    private val _isCheckingCredits = MutableStateFlow(false)
    val isCheckingCredits: StateFlow<Boolean> = _isCheckingCredits.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // Interactive Live Preview state
    private val _previewPlaying = MutableStateFlow(true)
    val previewPlaying: StateFlow<Boolean> = _previewPlaying.asStateFlow()

    private val _previewTimeMs = MutableStateFlow(0.0)
    val previewTimeMs: StateFlow<Double> = _previewTimeMs.asStateFlow()

    private val _demoActorRole = MutableStateFlow(ActorRole.CHICKEN_MENTOR)
    val demoActorRole: StateFlow<ActorRole> = _demoActorRole.asStateFlow()

    private val _demoActorAction = MutableStateFlow(ActorAction.POINT_STICK)
    val demoActorAction: StateFlow<ActorAction> = _demoActorAction.asStateFlow()

    private val _demoWorldFx = MutableStateFlow(WorldFx.COIN_RAIN)
    val demoWorldFx: StateFlow<WorldFx> = _demoWorldFx.asStateFlow()

    private val _demoStatusPill = MutableStateFlow(StatusPill.NONE)
    val demoStatusPill: StateFlow<StatusPill> = _demoStatusPill.asStateFlow()

    private val _demoHudWidget = MutableStateFlow(HudWidget.NONE)
    val demoHudWidget: StateFlow<HudWidget> = _demoHudWidget.asStateFlow()

    private val _demoShotFraming = MutableStateFlow(ShotFraming.WIDE_FULL_BODY)
    val demoShotFraming: StateFlow<ShotFraming> = _demoShotFraming.asStateFlow()

    val previewBackdropCache = mutableMapOf<Int, Bitmap>()

    init {
        refreshVideos()
        viewModelScope.launch {
            settings.collect { st ->
                val loaded = withContext(Dispatchers.IO) {
                    CharacterSpriteProcessor.loadCustomSpritesMap(
                        paths = st.customSpritePaths,
                        stickerOutline = st.spriteStickerOutline
                    )
                }
                allocatedSpriteBitmaps.addAll(loaded.values)
                _previewCustomSprites.value = loaded
            }
        }
        viewModelScope.launch {
            PipelineProgressBus.progress.collect { state ->
                if (state.step == PipelineStep.COMPLETED) {
                    refreshVideos()
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(33L)
                if (_previewPlaying.value) {
                    val scenes = progress.value.generatedScenesPreview
                    val maxDur = if (scenes.isNotEmpty()) {
                        scenes.last().endMs.coerceAtLeast(1000.0)
                    } else {
                        4200.0
                    }
                    val next = _previewTimeMs.value + 33.0
                    _previewTimeMs.value = if (next >= maxDur) 0.0 else next
                }
            }
        }
    }

    fun updateTopic(value: String) {
        _topic.value = value
    }

    fun pickRandomIdea() {
        val current = _topic.value
        val candidates = BuiltInPrompts.VIRAL_TAGALOG_IDEAS.filterNot { it == current }
        _topic.value = candidates.randomOrNull() ?: BuiltInPrompts.VIRAL_TAGALOG_IDEAS.first()
    }

    fun selectProductionMode(mode: ProductionMode) {
        _productionMode.value = mode
        if (mode == ProductionMode.CROSS_SECTION_EXPLAINER) {
            _selectedStyle.value = AnimationStyle.CROSS_SECTION
        } else if (mode == ProductionMode.FULL_AI_STORY && _selectedStyle.value == AnimationStyle.CROSS_SECTION) {
            _selectedStyle.value = AnimationStyle.PAPER_DIORAMA
        }
        previewBackdropCache.clear()
    }

    fun selectStyle(style: AnimationStyle) {
        _selectedStyle.value = style
        if (style == AnimationStyle.CROSS_SECTION) {
            _productionMode.value = ProductionMode.CROSS_SECTION_EXPLAINER
        } else if (_productionMode.value == ProductionMode.CROSS_SECTION_EXPLAINER) {
            _productionMode.value = ProductionMode.FULL_AI_STORY
        }
        previewBackdropCache.clear()
    }

    fun selectDuration(seconds: Int) {
        _selectedDurationSec.value = seconds
    }

    fun togglePreviewPlay() {
        _previewPlaying.value = !_previewPlaying.value
    }

    fun seekPreview(ms: Double) {
        _previewTimeMs.value = ms.coerceAtLeast(0.0)
    }

    fun setDemoRole(role: ActorRole) {
        _demoActorRole.value = role
    }

    fun setDemoAction(action: ActorAction) {
        _demoActorAction.value = action
    }

    fun setDemoWorldFx(fx: WorldFx) {
        _demoWorldFx.value = fx
    }

    fun setDemoStatusPill(pill: StatusPill) {
        _demoStatusPill.value = pill
    }

    fun setDemoHudWidget(widget: HudWidget) {
        _demoHudWidget.value = widget
    }

    fun setDemoShotFraming(framing: ShotFraming) {
        _demoShotFraming.value = framing
    }

    fun buildDemoPreviewScenes(): List<TimelineScene> {
        val role = _demoActorRole.value
        val action = _demoActorAction.value
        val fx = _demoWorldFx.value
        val style = _selectedStyle.value
        val pill = if (style == AnimationStyle.TECH_EXPLAINER && _demoStatusPill.value == StatusPill.NONE) {
            StatusPill.GOAL
        } else {
            _demoStatusPill.value
        }
        val widget = if (style == AnimationStyle.TECH_EXPLAINER && _demoHudWidget.value == HudWidget.NONE) {
            HudWidget.PROGRESS_GAUGE
        } else {
            _demoHudWidget.value
        }
        val callout = if (style == AnimationStyle.TECH_EXPLAINER || pill != StatusPill.NONE || widget != HudWidget.NONE) {
            "90% LAKAS"
        } else ""
        val narration = "Alam mo ba kung bakit ubos ang 90% ng sahod mo?"
        val words = narration.split(" ")
        var cursor = 120.0
        val timings = words.map { w ->
            val wt = WordTiming(w, cursor, cursor + 340.0)
            cursor += 350.0
            wt
        }
        return listOf(
            TimelineScene(
                scene = SceneScript(
                    sceneId = 1,
                    tagalogNarration = narration,
                    backgroundPrompt = "stylized Pinoy village street with glowing warm lanterns",
                    cameraMotion = CameraMotion.ZOOM_IN,
                    actorRole = role,
                    actorAction = action,
                    worldFx = fx,
                    statusPill = pill,
                    calloutBanner = callout,
                    hudWidget = widget,
                    hudLabel = "IPON POWER",
                    hudFromValue = 18,
                    hudToValue = 88,
                    checklistItems = listOf(
                        "Iwasan ang impulse spend",
                        "Magtabi agad ng 20%",
                        "I-invest sa emergency fund"
                    ),
                    shotFraming = _demoShotFraming.value
                ),
                startMs = 0.0,
                endMs = 4200.0,
                wordTimings = timings,
                backdropBitmap = null
            )
        )
    }

    fun selectVoiceId(voiceId: String) {
        viewModelScope.launch {
            settingsStore.setSelectedVoiceId(voiceId)
        }
    }

    fun selectPromptTemplate(id: String) {
        viewModelScope.launch {
            settingsStore.setActivePrompt(id)
        }
    }

    fun savePromptTemplate(id: String, name: String, content: String) {
        viewModelScope.launch {
            settingsStore.updateTemplateContent(id, name, content)
            showMessage("Master prompt saved")
        }
    }

    fun resetPromptTemplate(id: String) {
        viewModelScope.launch {
            settingsStore.resetTemplate(id)
            showMessage("Built-in template restored")
        }
    }

    fun deletePromptTemplate(id: String) {
        viewModelScope.launch {
            settingsStore.deleteTemplate(id)
            showMessage("Custom template deleted")
        }
    }

    fun importTxtPrompt(uri: Uri) {
        viewModelScope.launch {
            try {
                val (fileName, text) = withContext(Dispatchers.IO) {
                    val resolver = appContext.contentResolver
                    var name = "Imported_Prompt.txt"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) name = c.getString(idx) ?: name
                        }
                    }
                    val content = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }.orEmpty()
                    name to content
                }
                if (text.isBlank()) {
                    showMessage("Selected .txt file is empty")
                    return@launch
                }
                val saved = settingsStore.importTxtTemplate(fileName, text)
                showMessage("Imported & activated \"${saved.name}\"")
            } catch (e: Exception) {
                showMessage("Failed to import .txt: ${e.message}")
            }
        }
    }

    fun addImportedClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        appContext.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val name = queryDisplayName(uri, fallback = "Clip_${uri.lastPathSegment ?: "media"}")
                    val mime = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
                    val isVideo = mime.startsWith("video/") ||
                        name.endsWith(".mp4", true) ||
                        name.endsWith(".mov", true) ||
                        name.endsWith(".mkv", true) ||
                        name.endsWith(".webm", true)
                    ImportedClipItem(
                        uriString = uri.toString(),
                        displayName = name,
                        isVideo = isVideo
                    )
                }
            }
            val current = _importedClips.value.toMutableList()
            var addedCount = 0
            newItems.forEach { item ->
                if (current.none { it.uriString == item.uriString }) {
                    current.add(item)
                    addedCount++
                }
            }
            _importedClips.value = current
            if (addedCount > 0) {
                showMessage("Added $addedCount clip(s) for Auto-HUD finishing")
            }
        }
    }

    fun removeImportedClip(uriString: String) {
        _importedClips.value = _importedClips.value.filterNot { it.uriString == uriString }
    }

    fun clearImportedClips() {
        _importedClips.value = emptyList()
    }

    fun importCharacterSprite(role: ActorRole, uri: Uri) {
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    CharacterSpriteProcessor.importSpriteFromUri(appContext, uri, role)
                }
                settingsStore.setCustomSpritePath(role.name, path)
                showMessage("Imported transparent sprite for ${role.name}")
            } catch (e: Exception) {
                showMessage("Failed to import PNG sprite: ${e.message}")
            }
        }
    }

    fun removeCharacterSprite(role: ActorRole) {
        viewModelScope.launch {
            val existing = settings.value.customSpritePaths[role.name]
            settingsStore.setCustomSpritePath(role.name, null)
            withContext(Dispatchers.IO) {
                CharacterSpriteProcessor.deleteSpriteFile(existing)
            }
            showMessage("Restored default vector puppet for ${role.name}")
        }
    }

    fun setSpriteStickerOutline(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setSpriteStickerOutline(enabled)
        }
    }

    fun importCustomBgm(uri: Uri) {
        viewModelScope.launch {
            try {
                val oldPath = settings.value.customBgmPath
                val (path, displayName) = withContext(Dispatchers.IO) {
                    val name = queryDisplayName(uri, fallback = "custom_bgm.mp3")
                    val ext = name.substringAfterLast('.', "mp3").lowercase().let {
                        if (it in setOf("mp3", "wav", "m4a", "aac", "ogg")) it else "mp3"
                    }
                    val dir = File(appContext.filesDir, "custom_bgm").apply { mkdirs() }
                    val outFile = File(dir, "bgm_${System.currentTimeMillis()}.$ext")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Unable to read audio file")
                    if (oldPath.isNotBlank()) {
                        runCatching { File(oldPath).delete() }
                    }
                    outFile.absolutePath to name
                }
                settingsStore.setCustomBgm(path, displayName)
                showMessage("Imported custom BGM: $displayName")
            } catch (e: Exception) {
                showMessage("Failed to import BGM: ${e.message}")
            }
        }
    }

    fun clearCustomBgm() {
        viewModelScope.launch {
            val oldPath = settings.value.customBgmPath
            settingsStore.setCustomBgm("", "")
            if (oldPath.isNotBlank()) {
                withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } }
            }
            showMessage("Restored built-in ambient lo-fi synth BGM")
        }
    }

    fun upsertPronunciation(original: String, spoken: String) {
        if (original.isBlank() || spoken.isBlank()) {
            showMessage("Enter both the word and its phonetic Tagalog spelling")
            return
        }
        viewModelScope.launch {
            settingsStore.upsertPronunciationEntry(original, spoken)
            showMessage("Saved pronunciation: \"$original\" -> \"$spoken\"")
        }
    }

    fun deletePronunciation(original: String) {
        viewModelScope.launch {
            settingsStore.deletePronunciationEntry(original)
        }
    }

    fun resetPronunciationDictionary() {
        viewModelScope.launch {
            settingsStore.resetPronunciationDictionary()
            showMessage("Restored default Tagalog pronunciation dictionary")
        }
    }

    private fun queryDisplayName(uri: Uri, fallback: String): String {
        var name = fallback
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx) ?: name
                }
            }
        }
        return name
    }

    fun startGeneration() {
        val currentTopic = _topic.value.trim()
        if (currentTopic.isEmpty()) {
            showMessage("Enter a Tagalog story topic first")
            return
        }
        val mode = _productionMode.value
        val clips = _importedClips.value
        if (mode == ProductionMode.CLIP_AUTO_EDITOR && clips.isEmpty()) {
            showMessage("Select at least 1 .mp4 or image clip for Clip + Auto-HUD mode")
            return
        }
        if (progress.value.isRunning) return

        val intent = Intent(appContext, VideoGenerationService::class.java).apply {
            action = VideoGenerationService.ACTION_START
            putExtra(VideoGenerationService.EXTRA_TOPIC, currentTopic)
            putExtra(VideoGenerationService.EXTRA_STYLE, _selectedStyle.value.name)
            putExtra(VideoGenerationService.EXTRA_DURATION_SEC, _selectedDurationSec.value)
            putExtra(VideoGenerationService.EXTRA_VOICE_ID, settings.value.selectedVoiceId)
            putExtra(VideoGenerationService.EXTRA_PRODUCTION_MODE, mode.name)
            putStringArrayListExtra(
                VideoGenerationService.EXTRA_IMPORTED_CLIP_URIS,
                ArrayList(clips.map { it.uriString })
            )
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun cancelGeneration() {
        val intent = Intent(appContext, VideoGenerationService::class.java).apply {
            action = VideoGenerationService.ACTION_CANCEL
        }
        appContext.startService(intent)
    }

    fun refreshVideos() {
        viewModelScope.launch {
            _isLoadingVideos.value = true
            try {
                _savedVideos.value = galleryRepo.querySavedVideos()
            } finally {
                _isLoadingVideos.value = false
            }
        }
    }

    fun deleteVideo(uri: Uri) {
        viewModelScope.launch {
            val ok = galleryRepo.deleteVideo(uri)
            if (ok) {
                showMessage("Video deleted")
                refreshVideos()
            } else {
                showMessage("Could not delete video")
            }
        }
    }

    fun addElevenLabsKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            settingsStore.addElevenLabsKey(key)
            val updated = elevenLabsClient.checkSubscriptionQuota(key.trim())
            settingsStore.updateElevenLabsKeyEntry(updated)
            showMessage("ElevenLabs key added")
        }
    }

    fun removeElevenLabsKey(key: String) {
        viewModelScope.launch {
            settingsStore.removeElevenLabsKey(key)
            showMessage("ElevenLabs key removed")
        }
    }

    fun checkAllElevenLabsCredits() {
        viewModelScope.launch {
            _isCheckingCredits.value = true
            try {
                val keys = settingsStore.getSnapshot().elevenLabsKeys
                if (keys.isEmpty()) {
                    showMessage("Add at least one ElevenLabs key first")
                    return@launch
                }
                for (entry in keys) {
                    val checked = elevenLabsClient.checkSubscriptionQuota(entry.key)
                    settingsStore.updateElevenLabsKeyEntry(checked)
                }
                showMessage("Quota refreshed for ${keys.size} key(s)")
            } finally {
                _isCheckingCredits.value = false
            }
        }
    }

    fun saveLlmSettings(
        groqApiKey: String,
        openRouterApiKey: String,
        customBaseUrl: String,
        customModel: String,
        customApiKey: String,
        aiHordeKey: String,
        allowOfflineTtsFallback: Boolean
    ) {
        viewModelScope.launch {
            settingsStore.saveLlmSettings(
                groqApiKey = groqApiKey,
                openRouterApiKey = openRouterApiKey,
                customBaseUrl = customBaseUrl,
                customModel = customModel,
                customApiKey = customApiKey,
                aiHordeKey = aiHordeKey,
                allowOfflineTtsFallback = allowOfflineTtsFallback
            )
            showMessage("API & Studio settings saved")
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    private fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    override fun onCleared() {
        previewBackdropCache.values.forEach { bmp ->
            runCatching { if (!bmp.isRecycled) bmp.recycle() }
        }
        previewBackdropCache.clear()
        allocatedSpriteBitmaps.forEach { bmp ->
            runCatching { if (!bmp.isRecycled) bmp.recycle() }
        }
        allocatedSpriteBitmaps.clear()
        super.onCleared()
    }
}
