package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.domain.rbac.ModuleKind

/**
 * Tebakan wawancara **tanpa LLM** dari narasi + kamus `DomainPack.roleHints` (B2). Fungsi murni: narasi dan pack yang
 * sama ⇒ hasil identik byte-per-byte (tak ada acak, waktu, atau urutan peta).
 *
 * Cara berpikir: kamus mengenali **peran** di cerita; peran menunjuk **modul**; nama modul adalah unit kerjanya
 * (divisi); sambungan mengikuti **urutan peran disebut** di cerita ("pendaftaran, lalu poli"). Pack tanpa kamus ⇒
 * [propose] mengembalikan sesi kosong ⇒ pewawancara bertanya terbuka — tidak pernah meminjam kamus pack lain.
 */
object DeterministicInterviewGuesser : InterviewGuesser {

    private const val CONFIDENCE_ROLE = 70
    private const val CONFIDENCE_HANDOFF = 60

    override suspend fun guess(step: InterviewStep, pack: DomainPack, draft: DiscoveryDraft, narrative: String): Result<List<Guess>> =
        Result.success(guessNow(step, pack, draft, narrative))

    fun guessNow(step: InterviewStep, pack: DomainPack, draft: DiscoveryDraft, narrative: String): List<Guess> {
        val p = propose(pack, narrative)
        val known = draft.interview
        val moduleName = { id: com.eventverse.app.domain.pack.ModuleId -> pack.module(id)?.displayName ?: id.value }
        val roleLabel = p.roles.associate { it.roleKey to it.label }
        return when (step) {
            InterviewStep.G1_DIVISI -> p.divisions.filter { d -> known?.divisions?.none { it.code == d.code } != false }
                .map { Guess(it.code.value, it.name, CONFIDENCE_ROLE) }
            InterviewStep.G2_PERAN -> p.roles.filter { r -> known?.roles?.none { it.roleKey == r.roleKey } != false }
                .map { Guess(it.roleKey.value, it.label, CONFIDENCE_ROLE) }
            InterviewStep.G3_MODUL -> p.links.filter { l -> known?.links?.none { it.roleKey == l.roleKey && it.moduleId == l.moduleId } != false }
                .map { Guess("${it.roleKey.value}:${it.moduleId.value}", "${roleLabel[it.roleKey]} → ${moduleName(it.moduleId)}", CONFIDENCE_ROLE, it.origin) }
            InterviewStep.G4_SAMBUNGAN -> p.handoffs.filter { h -> known?.handoffs?.none { it.from == h.from && it.to == h.to } != false }
                .map { Guess("${it.from.value}>${it.to.value}", "${moduleName(it.from)} → ${moduleName(it.to)}", CONFIDENCE_HANDOFF) }
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> emptyList()
        }
    }

    /** Sesi usulan penuh (semua `GUESSED`, langkah G1) dari [narrative]; kosong bila pack tanpa kamus / tak ada yang cocok. */
    fun propose(pack: DomainPack, narrative: String): InterviewSession {
        val hints = matchedHints(pack, narrative)
        val divisions = linkedMapOf<DivisionCode, DivisionDraft>()
        val roles = linkedMapOf<RoleKey, RoleDraft>()
        val links = mutableListOf<RoleModuleLink>()
        val headTaken = mutableSetOf<DivisionCode>()
        hints.forEach { h ->
            val module = pack.module(h.moduleId) ?: return@forEach
            val division = DivisionCode(slug(module.displayName, "d"))
            divisions.getOrPut(division) { DivisionDraft(division, module.displayName, ItemSource.GUESS) }
            val key = RoleKey(slug(h.label, "r"))
            if (key in roles) return@forEach
            roles[key] = RoleDraft(key, h.label, division, ItemSource.GUESS, isHead = headTaken.add(division))
            links += RoleModuleLink(key, module.id, originOf(module), emptyList(), Confirmation.GUESSED, CONFIDENCE_ROLE)
        }
        return InterviewSession(InterviewStep.G1_DIVISI, divisions.values.toList(), roles.values.toList(), links, handoffsOf(pack, links))
    }

    /** Hint yang muncul di narasi, urut posisi pertama muncul; hint yang terkandung frasa lebih panjang dibuang. */
    private fun matchedHints(pack: DomainPack, narrative: String): List<RoleHint> {
        val text = narrative.lowercase()
        val found = pack.roleHints.mapNotNull { h ->
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(h.word)}(?![\\p{L}\\p{N}])").find(text)?.let { Triple(h, it.range.first, it.range.last) }
        }
        return found.filter { (_, s, e) -> found.none { (o, os, oe) -> os <= s && oe >= e && (os != s || oe != e) } }
            .sortedBy { it.second }.map { it.first }
    }

    private fun originOf(m: ModuleDefinition): ModuleOrigin {
        val shipped = DomainPackRegistry.shipped.firstNotNullOfOrNull { it.module(m.id) } ?: return ModuleOrigin.NEW
        return if (shipped.kind == ModuleKind.OPERATIONAL) ModuleOrigin.REUSE_PACK else ModuleOrigin.REUSE_PLATFORM
    }

    private fun handoffsOf(pack: DomainPack, links: List<RoleModuleLink>): List<ModuleHandoff> {
        val flow = links.map { it.moduleId }.distinct().mapNotNull { id -> pack.module(id)?.takeIf { it.slot != null } }
        return flow.zipWithNext().mapNotNull { (a, b) ->
            val port = pack.slot(requireNotNull(a.slot))?.defaultOutput ?: return@mapNotNull null
            if (port in pack.wiredPortTypes) ModuleHandoff(a.id, b.id, port) else null
        }
    }

    /** Slug kunci: huruf kecil, bukan-alfanumerik → `_`; awalan [prefix] bila tak diawali huruf; maks 64. */
    internal fun slug(text: String, prefix: String): String {
        val s = text.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("")
            .replace(Regex("_+"), "_").trim('_')
        val withLetter = if (s.isEmpty() || s.first() !in 'a'..'z') "${prefix}_$s".trimEnd('_') else s
        return withLetter.take(64)
    }
}
