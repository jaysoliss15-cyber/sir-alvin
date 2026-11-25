package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.art.AiHordeImageClient
import com.example.data.SettingsDataStore
import com.example.data.VideoGalleryRepository
import com.example.llm.LlmDirectorClient
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.GenerationProgress
import com.example.model.PipelineStep
import com.example.model.ProductionMode
import com.example.model.TimelineScene
import com.example.model.WordTiming
import com.example.render.CharacterSpriteProcessor
import com.example.render.DeterministicVideoExporter
import com.example.render.ExternalClipFrameSource
import com.example.tts.AudioPcmStitcher
import com.example.tts.ElevenLabsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoGenerationService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_GENERATION"
        const val ACTION_CANCEL = "com.example.action.CANCEL_GENERATION"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_STYLE = "extra_style"
        const val EXTRA_DURATION_SEC = "extra_duration_sec"
        const val EXTRA_VOICE_ID = "extra_voice_id"
        const val EXTRA_PRODUCTION_MODE = "extra_production_mode"
        const val EXTRA_IMPORTED_CLIP_URIS = "extra_imported_clip_uris"

        private const val CHANNEL_ID = "toonforge_render_channel"
        private const val NOTIFICATION_ID = 4201
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                activeJob?.cancel()
                PipelineProgressBus.update {
                    it.copy(
                        isRunning = false,
                        step = PipelineStep.IDLE,
                        statusMessage = "Render cancelled by user"
                    )
                }
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC).orEmpty().trim()
                val styleName = intent.getStringExtra(EXTRA_STYLE) ?: AnimationStyle.PAPER_DIORAMA.name
                val style = AnimationStyle.entries.firstOrNull { it.name == styleName }
                    ?: AnimationStyle.PAPER_DIORAMA
                val durationSec = intent.getIntExtra(EXTRA_DURATION_SEC, 30)
                val voiceId = intent.getStringExtra(EXTRA_VOICE_ID).orEmpty()
                val productionMode = ProductionMode.fromRaw(intent.getStringExtra(EXTRA_PRODUCTION_MODE))
                val importedClipUris = intent.getStringArrayListExtra(EXTRA_IMPORTED_CLIP_URIS)
                    ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                    .orEmpty()
                val effectiveStyle = if (productionMode == ProductionMode.CROSS_SECTION_EXPLAINER) {
                    AnimationStyle.CROSS_SECTION
                } else {
                    style
                }

                if (topic.isEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                ensureNotificationChannel()
                val initialNotif = buildNotification("Starting ToonForge Tagalog pipeline...", 5)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotif)
                }

                activeJob?.cancel()
                activeJob = serviceScope.launch {
                    runPipeline(
                        topic = topic,
                        style = effectiveStyle,
                        durationSec = durationSec,
                        voiceIdOverride = voiceId,
                        productionMode = productionMode,
                        importedClipUris = importedClipUris
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runPipeline(
        topic: String,
        style: AnimationStyle,
        durationSec: Int,
        voiceIdOverride: String,
        productionMode: ProductionMode,
        importedClipUris: List<Uri>
    ) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ToonForge::VideoRenderWakeLock"
        )
        wakeLock.acquire(15 * 60 * 1000L) // 15-minute safety ceiling

        val tempWorkDir = File(cacheDir, "toonforge_work_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        val renderBackdrops = mutableListOf<Bitmap>()
        var customSprites: Map<ActorRole, Bitmap> = emptyMap()
        var externalClipFrameSource: ExternalClipFrameSource? = null

        try {
            val settingsStore = SettingsDataStore(applicationContext)
            val settings = settingsStore.getSnapshot()
            val effectiveVoiceId = voiceIdOverride.ifBlank { settings.selectedVoiceId }
            val galleryRepo = VideoGalleryRepository(applicationContext)

            fun publish(
                step: PipelineStep,
                stepProgress: Float,
                overall: Float,
                message: String,
                sceneIdx: Int = 0,
                sceneCount: Int = 0,
                providerInfo: String? = null
            ) {
                PipelineProgressBus.update { cur ->
                    cur.copy(
                        step = step,
                        stepProgress = stepProgress.coerceIn(0f, 1f),
                        overallProgress = overall.coerceIn(0f, 1f),
                        statusMessage = message,
                        currentSceneIndex = sceneIdx,
                        sceneCount = if (sceneCount > 0) sceneCount else cur.sceneCount,
                        providerInfo = providerInfo ?: cur.providerInfo,
                        errorMessage = null,
                        isRunning = true
                    )
                }
                updateNotification(message, (overall * 100).toInt().coerceIn(0, 100))
            }

            // STEP 1: SCRIPT GENERATION
            publish(PipelineStep.SCRIPT, 0.1f, 0.05f, "Directing Tagalog story script...")
            val llmClient = LlmDirectorClient()
            val scriptResult = llmClient.generateStoryScript(
                topic = topic,
                durationSeconds = durationSec,
                style = style,
                settings = settings,
                onStatus = { status ->
                    publish(PipelineStep.SCRIPT, 0.5f, 0.12f, status)
                }
            )
            val scenes = scriptResult.scenes
            publish(
                PipelineStep.SCRIPT,
                1.0f,
                0.20f,
                "Script ready (${scenes.size} scenes) via ${scriptResult.providerUsed}",
                sceneCount = scenes.size,
                providerInfo = scriptResult.providerUsed
            )

            // STEP 2: ELEVENLABS TTS + SAMPLE-ACCURATE PCM STITCHING
            val ttsClient = ElevenLabsClient(applicationContext)
            val pcmPerScene = mutableListOf<ByteArray>()
            val timingsPerScene = mutableListOf<List<WordTiming>>()

            scenes.forEachIndexed { idx, scene ->
                currentCoroutineContext().ensureActive()
                val frac = (idx + 1).toFloat() / scenes.size.toFloat()
                publish(
                    PipelineStep.AUDIO,
                    frac * 0.85f,
                    0.20f + frac * 0.22f,
                    "Synthesizing Tagalog voiceover (${idx + 1}/${scenes.size})...",
                    sceneIdx = idx + 1,
                    sceneCount = scenes.size
                )

                val synth = ttsClient.synthesizeScene(
                    rawTagalogNarration = scene.tagalogNarration,
                    voiceId = effectiveVoiceId,
                    keyPool = settingsStore.getSnapshot().elevenLabsKeys,
                    allowOfflineFallback = settings.allowOfflineTtsFallback,
                    tempCacheDir = tempWorkDir,
                    customDictionary = settings.pronunciationDictionary,
                    previousText = scenes.getOrNull(idx - 1)?.tagalogNarration,
                    nextText = scenes.getOrNull(idx + 1)?.tagalogNarration,
                    onKeyStatusUpdate = { updatedKey ->
                        settingsStore.updateElevenLabsKeyEntry(updatedKey)
                    }
                )
                val pcm44100Mono = AudioPcmStitcher.decodeAudioBytesToPcm44100Mono(
                    audioBytes = synth.audioBytes,
                    isWav = synth.isWav,
                    tempCacheDir = tempWorkDir
                )
                pcmPerScene.add(pcm44100Mono)
                timingsPerScene.add(synth.localWordTimings)
            }

            val customBgmPcm = if (settings.customBgmPath.isNotBlank()) {
                runCatching {
                    val bgmFile = File(settings.customBgmPath)
                    if (bgmFile.exists() && bgmFile.canRead()) {
                        AudioPcmStitcher.decodeAudioBytesToPcm44100Mono(
                            audioBytes = bgmFile.readBytes(),
                            isWav = bgmFile.name.endsWith(".wav", ignoreCase = true),
                            tempCacheDir = tempWorkDir
                        )
                    } else null
                }.getOrNull()
            } else null

            val stitchedAudio = AudioPcmStitcher.stitchScenes(
                decodedPcmPerScene = pcmPerScene,
                localTimingsPerScene = timingsPerScene,
                narrationPerScene = scenes.map { it.tagalogNarration },
                scenes = scenes,
                style = style,
                customBgmPcm44100Mono = customBgmPcm
            )
            pcmPerScene.clear() // release per-scene chunks immediately; keep unified buffer

            // STEP 3: ART / CLIPS / BACKDROP PIPELINE
            val timelineScenes = mutableListOf<TimelineScene>()
            val previewScenes = mutableListOf<TimelineScene>()

            if (productionMode == ProductionMode.CLIP_AUTO_EDITOR && importedClipUris.isNotEmpty()) {
                publish(
                    PipelineStep.ART,
                    0.2f,
                    0.46f,
                    "Preparing ${importedClipUris.size} imported video/image clip(s) for Auto-HUD...",
                    sceneIdx = 0,
                    sceneCount = scenes.size
                )
                externalClipFrameSource = ExternalClipFrameSource(
                    context = applicationContext,
                    clipUris = importedClipUris,
                    targetWidth = DeterministicVideoExporter.VIDEO_WIDTH,
                    targetHeight = DeterministicVideoExporter.VIDEO_HEIGHT
                )
                scenes.forEachIndexed { idx, scene ->
                    currentCoroutineContext().ensureActive()
                    val sampleThumb = externalClipFrameSource?.getFrameBitmap(idx, 100.0)?.let {
                        Bitmap.createScaledBitmap(it, 360, 640, true)
                    }
                    val startMs = stitchedAudio.sceneStartMs[idx]
                    val endMs = stitchedAudio.sceneEndMs[idx]
                    val wordTimings = stitchedAudio.sceneWordTimings[idx]
                    timelineScenes.add(
                        TimelineScene(
                            scene = scene,
                            startMs = startMs,
                            endMs = endMs,
                            wordTimings = wordTimings,
                            backdropBitmap = null
                        )
                    )
                    previewScenes.add(
                        TimelineScene(
                            scene = scene,
                            startMs = startMs,
                            endMs = endMs,
                            wordTimings = wordTimings,
                            backdropBitmap = sampleThumb
                        )
                    )
                    publish(
                        PipelineStep.ART,
                        (idx + 1).toFloat() / scenes.size.toFloat(),
                        0.46f + ((idx + 1).toFloat() / scenes.size.toFloat()) * 0.18f,
                        "Indexed clip ${idx + 1}/${scenes.size}...",
                        sceneIdx = idx + 1,
                        sceneCount = scenes.size
                    )
                }
            } else {
                val imageClient = AiHordeImageClient()
                val artBatchStartMsg = if (style.usesNetworkArt) {
                    "Fetching ${scenes.size} ${style.badge} diorama backdrops in parallel..."
                } else {
                    "Generating ${scenes.size} offline ${style.badge} stages..."
                }
                publish(
                    PipelineStep.ART,
                    0.05f,
                    0.42f,
                    artBatchStartMsg,
                    sceneIdx = 0,
                    sceneCount = scenes.size
                )

                val backdropResults = imageClient.generateBackdropsBatch(
                    scenes = scenes,
                    style = style,
                    aiHordeApiKey = settings.aiHordeKey,
                    topicSeed = topic.hashCode()
                ) { doneCount, totalCount ->
                    val frac = doneCount.toFloat() / totalCount.coerceAtLeast(1).toFloat()
                    val label = if (style.usesNetworkArt) {
                        "Downloaded/rendered backdrop ($doneCount/$totalCount)..."
                    } else {
                        "Prepared offline stage ($doneCount/$totalCount)..."
                    }
                    publish(
                        PipelineStep.ART,
                        frac,
                        0.42f + frac * 0.22f,
                        label,
                        sceneIdx = doneCount,
                        sceneCount = totalCount
                    )
                }

                scenes.forEachIndexed { idx, scene ->
                    currentCoroutineContext().ensureActive()
                    val backdropRes = backdropResults[idx]
                    renderBackdrops.add(backdropRes.bitmap)

                    val previewThumb = Bitmap.createScaledBitmap(backdropRes.bitmap, 360, 640, true)
                    val startMs = stitchedAudio.sceneStartMs[idx]
                    val endMs = stitchedAudio.sceneEndMs[idx]
                    val wordTimings = stitchedAudio.sceneWordTimings[idx]

                    timelineScenes.add(
                        TimelineScene(
                            scene = scene,
                            startMs = startMs,
                            endMs = endMs,
                            wordTimings = wordTimings,
                            backdropBitmap = backdropRes.bitmap
                        )
                    )
                    previewScenes.add(
                        TimelineScene(
                            scene = scene,
                            startMs = startMs,
                            endMs = endMs,
                            wordTimings = wordTimings,
                            backdropBitmap = previewThumb
                        )
                    )
                }
            }

            PipelineProgressBus.update { cur ->
                cur.copy(generatedScenesPreview = previewScenes)
            }

            // Load optional custom PNG character cutouts with sticker outlines
            customSprites = CharacterSpriteProcessor.loadCustomSpritesMap(
                paths = settings.customSpritePaths,
                stickerOutline = settings.spriteStickerOutline
            )

            // STEP 4 & 5: PUPPET RENDER + EGL14/MEDIACODEC/MEDIAMUXER
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val slug = topic.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(24)
                .ifBlank { "story" }
            val fileName = "ToonForge_${slug}_$stamp.mp4"

            val exporter = DeterministicVideoExporter(galleryRepo)
            val exportRes = exporter.exportToMediaStore(
                scenes = timelineScenes,
                unifiedPcm44100Mono = stitchedAudio.unifiedPcm44100Mono,
                totalDurationMs = stitchedAudio.totalDurationMs,
                style = style,
                displayName = fileName,
                tempCacheDir = tempWorkDir,
                onRenderProgress = { pct, msg ->
                    publish(
                        PipelineStep.PUPPET_RENDER,
                        pct,
                        0.64f + pct * 0.28f,
                        msg
                    )
                },
                onMuxProgress = { msg ->
                    publish(
                        PipelineStep.MUXING,
                        0.85f,
                        0.95f,
                        msg
                    )
                },
                productionMode = productionMode,
                customSprites = customSprites,
                externalClipFrameSource = externalClipFrameSource
            )

            PipelineProgressBus.update { cur ->
                cur.copy(
                    step = PipelineStep.COMPLETED,
                    stepProgress = 1f,
                    overallProgress = 1f,
                    statusMessage = "Saved ${exportRes.displayName} to Movies/ToonForgeTagalog!",
                    outputVideoUri = exportRes.mediaStoreUri,
                    outputDisplayName = exportRes.displayName,
                    errorMessage = null,
                    isRunning = false,
                    generatedScenesPreview = previewScenes
                )
            }
            updateNotification("Video ready: ${exportRes.displayName}", 100)
        } catch (ce: CancellationException) {
            PipelineProgressBus.update { cur ->
                cur.copy(
                    step = PipelineStep.IDLE,
                    statusMessage = "Render cancelled",
                    isRunning = false
                )
            }
            throw ce
        } catch (e: Exception) {
            val msg = e.message ?: "Unexpected rendering error"
            PipelineProgressBus.update { cur ->
                cur.copy(
                    step = PipelineStep.ERROR,
                    statusMessage = "Failed: $msg",
                    errorMessage = msg,
                    isRunning = false
                )
            }
            updateNotification("Render failed: $msg", 0)
        } finally {
            // Strict temporary file & bitmap memory hygiene
            renderBackdrops.forEach { bmp ->
                runCatching { if (!bmp.isRecycled) bmp.recycle() }
            }
            renderBackdrops.clear()
            customSprites.values.forEach { bmp ->
                runCatching { if (!bmp.isRecycled) bmp.recycle() }
            }
            customSprites = emptyMap()
            runCatching { externalClipFrameSource?.close() }
            runCatching { tempWorkDir.deleteRecursively() }
            runCatching {
                if (wakeLock.isHeld) wakeLock.release()
            }
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(content: String, progressPct: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoGenerationService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ToonForge Tagalog Studio")
            .setContentText(content)
            .setProgress(100, progressPct, progressPct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            .build()
    }

    private fun updateNotification(content: String, progressPct: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content, progressPct))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
