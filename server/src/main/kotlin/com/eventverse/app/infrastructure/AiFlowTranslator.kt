package com.eventverse.app.infrastructure

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentSlots
import com.eventverse.app.domain.pipeline.code
import com.eventverse.app.domain.pipeline.defaultExpectedInputType
import com.eventverse.app.domain.pipeline.defaultProducedOutputType
import com.eventverse.app.domain.pipeline.displayName
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.FlowTranslationDraft
import com.eventverse.app.domain.prospect.FlowTranslator
import com.eventverse.app.domain.prospect.RawCapabilityRequirement

/**
 * LLM-based flow translator that generates Blueprint directly from narrative.
 *
 * Replaces [KeywordFlowTranslator] with comprehension-based translation:
 * - Understands natural language descriptions of factory capabilities
 * - Generates Blueprint with active modules based on detected needs
 * - Identifies open slots that tenant needs to fill
 *
 * Output is untrusted and must be validated before use.
 */
class AiFlowTranslator(
    private val llmClient: LlmClient = DefaultLlmClient(),
    override val translatorRef: String = TRANSLATOR_REF
) : FlowTranslator {

    companion object {
        const val TRANSLATOR_REF = "ai/llm-v1"
    }

    override suspend fun translate(narrative: String): Result<FlowTranslationDraft> = runCatching {
        val prompt = buildPrompt(narrative)
        val response = llmClient.complete(prompt)
        parseBlueprintResponse(response)
    }

    private fun buildPrompt(narrative: String): String {
        val moduleCatalog = GarmentSlots.all.joinToString("\n") { slot ->
            "- ${slot.code}: ${slot.displayName} (input: ${slot.defaultExpectedInputType}, output: ${slot.defaultProducedOutputType})"
        }

        val presets = listOf(
            "fob_full_package: FOB Full Package - pabrik menyediakan kain & aksesoris",
            "cmt_makloon: CMT/Makloon - buyer menyediakan kain, pabrik hanya jahit",
            "d2c_brand: D2C Brand - menjual langsung ke konsumen akhir"
        )

        return """
Anda adalah ahli konveksi garmen. translation tugas: ubah deskripsi kebutuhan pabrik menjadi Blueprint pipeline produksi.

Deskripsi kebutuhan:
$narrative

Katalog modul yang tersedia:
$moduleCatalog

Template yang tersedia:
${presets.joinToString("\n")}

Respon dalam format JSON:
{
  "detectedPreset": "fob_full_package" | "cmt_makloon" | "d2c_brand" | null,
  "modules": [
    {"code": "modul_code", "active": true/false, "reason": "alasanaktifkan/nonaktif"}
  ],
  "openSlots": ["slot_code yang perlu diisi tenant"],
  "confidence": 0.0-1.0
}

Pilih preset yang paling cocok atau null jika tidak cocok dengan template manapun.
Modul diaktifkan jika memang dibutuhkan dalam alur.
"""
    }

    private fun parseBlueprintResponse(response: String): FlowTranslationDraft {
        val json = response.trim().let {
            if (it.startsWith("```json")) it.removeSurrounding("```json", "```")
            else if (it.startsWith("```")) it.removeSurrounding("```", "```")
            else it
        }

        return try {
            val parsed = parseSimpleJson(json)
            val detectedPreset = parsed["detectedPreset"]?.let { code ->
                when (code) {
                    "fob_full_package" -> GarmentBlueprints.FOB_FULL_PACKAGE.code.value
                    "cmt_makloon" -> GarmentBlueprints.CMT_MAKLOON.code.value
                    "brand_d2c", "d2c_brand" -> GarmentBlueprints.BRAND_D2C.code.value
                    else -> null
                }
            }

            val modules = parsed["modules"]?.let { mods ->
                (mods as? List<*>)?.mapNotNull { m ->
                    val map = m as? Map<*, *>
                    val code = map?.get("code")?.toString()
                    val active = map?.get("active")?.toString()?.toBooleanStrictOrNull() ?: false
                    val reason = map?.get("reason")?.toString() ?: ""
                    if (code != null) {
                        RawCapabilityRequirement(
                            archetypeCode = code,
                            title = code,
                            description = reason
                        )
                    } else null
                }
            } ?: emptyList()

            val openSlots = parsed["openSlots"]?.let { slots ->
                (slots as? List<*>)?.mapNotNull { it?.toString() }
            } ?: emptyList()

            FlowTranslationDraft(
                translatorRef = translatorRef,
                detectedPresetCode = detectedPreset,
                requirements = modules,
                openQuestions = openSlots
            )
        } catch (e: Exception) {
            FlowTranslationDraft(
                translatorRef = translatorRef,
                detectedPresetCode = null,
                requirements = emptyList(),
                openQuestions = listOf("Gagal parsing respons LLM: ${e.message}")
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSimpleJson(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val content = json.trim().removeSurrounding("{", "}")
        val pairs = splitJsonPairs(content)
        
        for ((key, value) in pairs) {
            result[key.trim()] = parseJsonValue(value.trim())
        }
        return result
    }

    private fun splitJsonPairs(content: String): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        var depth = 0
        var currentKey = ""
        var currentValue = ""
        var inKey = true
        var inString = false
        
        for (char in content) {
            when {
                char == '"' && (currentValue.isEmpty() || currentValue.last() != '\\') -> inString = !inString
                !inString -> {
                    when (char) {
                        '{', '[' -> depth++
                        '}', ']' -> depth--
                        ':' -> if (depth == 0) inKey = false
                        ',' -> if (depth == 0) {
                            pairs.add(currentKey to currentValue)
                            currentKey = ""
                            currentValue = ""
                            inKey = true
                        }
                    }
                }
            }
            if (inKey) currentKey += char else currentValue += char
        }
        if (currentKey.isNotEmpty()) pairs.add(currentKey to currentValue)
        return pairs
    }

    private fun parseJsonValue(value: String): Any? {
        val v = value.trim()
        return when {
            v == "null" -> null
            v == "true" -> true
            v == "false" -> false
            v.startsWith("[") -> v.removeSurrounding("[", "]")
                .split(",").map { parseJsonValue(it.trim()) }
            v.startsWith("\"") -> v.removeSurrounding("\"", "\"")
            v.toDoubleOrNull() != null -> v.toDouble()
            else -> v
        }
    }

    private fun String.removeSurrounding(prefix: String, suffix: String): String {
        return if (startsWith(prefix) && endsWith(suffix)) {
            substring(prefix.length, length - suffix.length)
        } else this
    }
}

interface LlmClient {
    suspend fun complete(prompt: String): String
}

class DefaultLlmClient : LlmClient {
    override suspend fun complete(prompt: String): String {
        // TODO: Integrate with actual LLM (OpenAI, Anthropic, etc.)
        // For now, returns a placeholder that will be validated
        throw NotImplementedError("LLM client not configured. Set up API key to enable.")
    }
}
