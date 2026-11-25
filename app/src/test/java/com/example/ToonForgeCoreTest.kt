package com.example

import com.example.art.MagicByteValidator
import com.example.data.BuiltInPrompts
import com.example.llm.JsonSanitizer
import com.example.llm.MalformedSceneJsonException
import com.example.model.AnimationStyle
import com.example.model.WordTiming
import com.example.tts.AudioPcmStitcher
import com.example.tts.TagalogPhoneticNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToonForgeCoreTest {

    @Test
    fun `jsonSanitizer strips think tags and markdown fences`() {
        val raw = """
            <think>
            Let me carefully plan the Tagalog scenes first...
            </think>
            ```json
            [
              {
                "scene_id": 1,
                "tagalog_narration": "Alam mo ba kung bakit laging ubos ang sahod mo?"
              }
            ]
            ```
        """.trimIndent()

        val extracted = JsonSanitizer.extractJsonArrayText(raw)
        assertTrue(extracted.startsWith("["))
        assertTrue(extracted.endsWith("]"))
        assertFalse(extracted.contains("<think>"))
        assertFalse(extracted.contains("```"))
    }

    @Test(expected = MalformedSceneJsonException::class)
    fun `jsonSanitizer rejects response without array brackets`() {
        JsonSanitizer.extractJsonArrayText("<think>only thoughts</think> no json here")
    }

    @Test
    fun `tagalogPhoneticNormalizer expands peso currency percentages and acronyms`() {
        val input = "Nakatipid ng ₱1,500 at 50% ang OFW gamit ang GCash budget."
        val out = TagalogPhoneticNormalizer.normalize(input)
        assertTrue(out.contains("isang libo limang daan piso"))
        assertTrue(out.contains("limampu porsyento"))
        assertTrue(out.contains("oh ef double-u"))
        assertTrue(out.contains("ji-kash"))
        assertTrue(out.contains("badyet"))
    }

    @Test
    fun `magicByteValidator validates PNG JPEG WebP and rejects HTML`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        val webp = "RIFF1234WEBP".toByteArray(Charsets.US_ASCII)
        val html = "<html><body>401 Unauthorized</body></html>".toByteArray(Charsets.US_ASCII)

        assertEquals(MagicByteValidator.ImageFormat.PNG, MagicByteValidator.detectFormat(png))
        assertEquals(MagicByteValidator.ImageFormat.JPEG, MagicByteValidator.detectFormat(jpeg))
        assertEquals(MagicByteValidator.ImageFormat.WEBP, MagicByteValidator.detectFormat(webp))
        assertFalse(MagicByteValidator.isValidImageBytes(html))
    }

    @Test
    fun `twoLayerPromptComposition locks json schema after custom creative prompt`() {
        val customPrompt = "Custom Pinoy comedy tone with punchy jokes."
        val composed = BuiltInPrompts.composeSystemPrompt(customPrompt, 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(composed.startsWith(customPrompt))
        assertTrue(composed.contains("MANDATORY TECHNICAL JSON FORMAT (DO NOT OVERRIDE)"))
        assertTrue(composed.contains("\"tagalog_narration\""))
        assertTrue(composed.contains("\"world_fx\""))
    }

    @Test
    fun `audioPcmStitcher computes sample accurate offsets`() {
        // 44100 samples * 2 bytes = 88200 bytes = exactly 1000.0 ms
        val oneSecPcm = ByteArray(88200)
        val halfSecPcm = ByteArray(44100)
        val stitched = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(oneSecPcm, halfSecPcm),
            localTimingsPerScene = listOf(
                listOf(WordTiming("Una", 100.0, 400.0)),
                listOf(WordTiming("Pangalawa", 50.0, 350.0))
            ),
            narrationPerScene = listOf("Una", "Pangalawa")
        )
        assertEquals(1500.0, stitched.totalDurationMs, 0.01)
        assertEquals(0.0, stitched.sceneStartMs[0], 0.01)
        assertEquals(1000.0, stitched.sceneStartMs[1], 0.01)
        assertEquals(1050.0, stitched.sceneWordTimings[1][0].startMs, 0.01)
        assertEquals(1350.0, stitched.sceneWordTimings[1][0].endMs, 0.01)
    }
}
