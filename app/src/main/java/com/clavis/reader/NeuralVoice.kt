package com.clavis.reader

import com.k2fsa.sherpa.onnx.*
import java.io.File

/** Offline English narrators: original 1:06 voice (sid 0) and Hugo (male, sid 4). */
class NeuralVoice(root: File) {
    private val tts = OfflineTts(config = OfflineTtsConfig(
        model = OfflineTtsModelConfig(kitten = OfflineTtsKittenModelConfig(
            model = File(root,"model.onnx").path,
            voices = File(root,"voices.bin").path,
            tokens = File(root,"tokens.txt").path,
            dataDir = File(root,"espeak-ng-data").path,
        ), numThreads = 2), maxNumSentences = 1, silenceScale = 1.5f,
    ))
    val sampleRate: Int get() = tts.sampleRate()
    fun generate(text: String): ShortArray = generateAtSpeed(text, 1f)
    fun generateAtSpeed(text: String, rate: Float): ShortArray = generateWithVoice(text,rate,0)
    fun generateWithVoice(text: String, rate: Float, speaker: Int): ShortArray {
        val paragraphs = text.split(Regex("\\n[\\t ]*\\n+")).filter { it.isNotBlank() }
        val parts = paragraphs.mapIndexed { index, paragraph ->
            val audio = tts.generate(NarrationText.spoken(paragraph), sid = if (speaker == 1) 0 else 4, speed = rate.coerceIn(0.5f,2.5f))
            require(audio.samples.isNotEmpty()) { "The neural voice returned no audio" }
            val samples = audio.samples
            require(samples.any { kotlin.math.abs(it) > 0.002f }) { "The neural voice returned silent audio" }
            val paragraphEnd = index < paragraphs.lastIndex || text.endsWith("\n\n")
            val heading = paragraph.trim().matches(Regex("(?i)(chapter|book|part)\\s+[\\w -]+[.!:]?")) && paragraph.length < 80
            val pauseMs = when { heading -> 1200; paragraphEnd -> 900; paragraph.trimEnd().lastOrNull() in listOf('.', '!', '?', '”', '"') -> 450; else -> 0 }
            val minimum = (sampleRate * pauseMs / 1000f / rate.coerceIn(.75f,1.5f)).toInt()
            var tail = 0
            for (n in samples.lastIndex downTo 0) { if (kotlin.math.abs(samples[n]) > .003f) break; tail++ }
            ShortArray(samples.size + (minimum-tail).coerceAtLeast(0)) { n -> if (n < samples.size) (samples[n].coerceIn(-1f,1f)*32767f).toInt().toShort() else 0 }
        }
        val result = ShortArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) { part.copyInto(result,offset); offset += part.size }
        return result
    }

    fun release() = tts.release()
}
