package com.example.model

import androidx.compose.ui.graphics.Color

enum class AnimationStyle(
    val displayName: String,
    val badge: String,
    val subtitle: String,
    val accentHex: Long,
    val backdropPromptSuffix: String,
    val usesNetworkArt: Boolean,
    val captionStyleLabel: String
) {
    PAPER_DIORAMA(
        displayName = "2.5D Paper-Cut Diorama",
        badge = "DIORAMA",
        subtitle = "Tilt-shift miniature stages, 10px cutouts, whip-pans",
        accentHex = 0xFFFF5E36,
        backdropPromptSuffix = "stylized 3D miniature paper-cut diorama set, layered craft paper depth, soft studio lighting, tilt-shift bokeh, no text, no people, empty stage, centered composition",
        usesNetworkArt = true,
        captionStyleLabel = "Bold white + thick black stroke"
    ),
    STICKMAN_WHITEBOARD(
        displayName = "Stickman Whiteboard Explainer",
        badge = "100% OFFLINE",
        subtitle = "Textured paper grain, hand-drawn wobble, chalk PathMeasure",
        accentHex = 0xFF38BDF8,
        backdropPromptSuffix = "",
        usesNetworkArt = false,
        captionStyleLabel = "Bold black & red marker emphasis"
    ),
    FLAT_VECTOR(
        displayName = "Modern Flat Vector",
        badge = "CLEAN VECTOR",
        subtitle = "Borderless gradients, infographic icons, smooth zoom-ins",
        accentHex = 0xFF2DD4BF,
        backdropPromptSuffix = "clean modern flat vector illustration backdrop, smooth geometric gradients, minimalist architectural depth, no text, no people, empty stage, centered composition",
        usesNetworkArt = true,
        captionStyleLabel = "Yellow text in dark rounded pill"
    ),
    DARK_SUSPENSE(
        displayName = "Dark Motion Comic",
        badge = "HORROR / DRAMA",
        subtitle = "Moody cel-shading, rim light, vignette + film grain",
        accentHex = 0xFFEF4444,
        backdropPromptSuffix = "high contrast dark motion comic backdrop, moody dramatic chiaroscuro lighting, deep shadows, atmospheric fog, no text, no people, empty stage, centered composition",
        usesNetworkArt = true,
        captionStyleLabel = "Red-accented suspense captions"
    ),
    CHIBI_STORYTIME(
        displayName = "Chibi Anime Storytime",
        badge = "KAWAII ANIME",
        subtitle = "Big-eyed squash & stretch, anime emotes, bubbly captions",
        accentHex = 0xFFF472B6,
        backdropPromptSuffix = "colorful vibrant anime background art, soft pastel sky highlights, cozy detailed environment, Makoto Shinkai inspired lighting, no text, no people, empty stage, centered composition",
        usesNetworkArt = true,
        captionStyleLabel = "Pastel-outlined bubbly captions"
    ),
    TECH_EXPLAINER(
        displayName = "Viral HUD & Motion Explainer",
        badge = "HUD EXPLAINER",
        subtitle = "Clean vector diagrams, dynamic zoom/pan, animated HUD overlays",
        accentHex = 0xFF00E5FF,
        backdropPromptSuffix = "clean dark tech infographic vector stage, subtle geometric grid, glowing cyan and emerald depth accents, no text, no people, empty stage, centered composition",
        usesNetworkArt = true,
        captionStyleLabel = "Kinetic spring pop + keyword glow"
    ),
    CROSS_SECTION(
        displayName = "2D Cutaway & Engineering Diagram",
        badge = "CUTAWAY 2D",
        subtitle = "Kurzgesagt soil/house layers, pipes, fans & PathMeasure energy flow",
        accentHex = 0xFF22D3EE,
        backdropPromptSuffix = "flat 2D architectural cross-section cutaway diagram illustration, Kurzgesagt style clean geometric layers, soil strata, house interior pipes and airflow vents, no text, no people",
        usesNetworkArt = false,
        captionStyleLabel = "Cyan-glow engineering pill"
    ),
    VIRAL_2D_EXPLAINER(
        displayName = "Viral 2D Science & Story Explainer",
        badge = "2D VIRAL",
        subtitle = "6px bold outlines, cartoon reactions, visual metaphors & yellow captions",
        accentHex = 0xFFFFD600,
        backdropPromptSuffix = "vibrant clean 2D animated science story explainer backdrop, crisp geometric shapes, bright studio lighting, no text, no people, empty stage, centered composition",
        usesNetworkArt = false,
        captionStyleLabel = "Thick black-outlined yellow/white captions"
    );

    val accentColor: Color
        get() = Color(accentHex)
}
