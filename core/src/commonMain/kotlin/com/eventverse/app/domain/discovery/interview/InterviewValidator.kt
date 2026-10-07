package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.proposal.VerticalPurity
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.isReferenced
import com.eventverse.app.domain.pack.resolveModule
import com.eventverse.app.domain.pack.slotPorts
import com.eventverse.app.domain.rbac.ModuleKind

/**
 * Validator tunggal wawancara (plan §6). Dipanggil `DiscoveryDraftValidator`; tidak ada jalur lain yang menegakkan
 * aturan ini. Galat **berpath** (`$.interview.links[2].moduleId`) dan berpesan supaya bisa dipahami LLM yang
 * mengoreksi dirinya — tebakan tak sah tidak pernah diperbaiki diam-diam (Kontrak 4).
 *
 * Aturan memakai **peran** (kind modul, asal) bukan kode industri; kemurnian vertikal hanya berlaku untuk pack
 * non-garment dan masih memakai [VerticalPurity] (B5 menjadikannya data pack).
 */
object InterviewValidator {

    fun validate(session: InterviewSession, pack: DomainPack, at: String = "$.interview"): List<DiscoveryValidationIssue> {
        val out = mutableListOf<DiscoveryValidationIssue>()
        fun add(path: String, message: String) { out += DiscoveryValidationIssue(path, message) }
        // Kemurnian vertikal = data: kosakata cadangan pack LAIN yang dikenal registri (bukan daftar kode).
        val reserved = DomainPackRegistry.all.filter { it.code != pack.code }.flatMap { it.reservedTerms }.distinct()
        fun pure(path: String, text: String) {
            VerticalPurity.leak(text, reserved)?.let { add(path, "Istilah '$it' khas pack lain; pack ${pack.code.value} punya kosakatanya sendiri. Pakai istilah usaha ini") }
        }
        fun tooMany(path: String, size: Int, max: Int, what: String) {
            if (size > max) add(path, "Terlalu banyak $what ($size); maksimum $max. Ringkas ke yang utama")
        }

        tooMany("$at.divisions", session.divisions.size, InterviewLimits.DIVISIONS, "divisi")
        tooMany("$at.roles", session.roles.size, InterviewLimits.ROLES, "peran")
        tooMany("$at.links", session.links.size, InterviewLimits.LINKS, "tautan peran-modul")
        tooMany("$at.handoffs", session.handoffs.size, InterviewLimits.HANDOFFS, "sambungan")
        // Giliran G1–G5 dan fase konsultan F0–F2 dibatasi terpisah (PLAN induk §5).
        val consultantTurns = session.answers.count { it.step.isConsultant }
        tooMany("$at.answers", session.answers.size - consultantTurns, InterviewLimits.TURNS, "giliran terjemahan")
        tooMany("$at.answers", consultantTurns, InterviewLimits.CONSULTANT_TURNS, "giliran konsultan")

        val divisionIndex = mutableMapOf<DivisionCode, Int>()
        session.divisions.forEachIndexed { i, d ->
            divisionIndex[d.code]?.let { add("$at.divisions[$i].code", "Kode '${d.code.value}' sudah dipakai di $at.divisions[$it]; kode divisi harus unik") }
                ?: run { divisionIndex[d.code] = i }
            if (d.name.length > InterviewLimits.TEXT) add("$at.divisions[$i].name", "Nama divisi maksimum ${InterviewLimits.TEXT} karakter")
            pure("$at.divisions[$i].name", d.name)
        }

        val roleIndex = mutableMapOf<RoleKey, Int>()
        val headByDivision = mutableMapOf<DivisionCode, Int>()
        session.roles.forEachIndexed { i, r ->
            roleIndex[r.roleKey]?.let { add("$at.roles[$i].roleKey", "Kunci '${r.roleKey.value}' sudah dipakai di $at.roles[$it]; kunci peran harus unik") }
                ?: run { roleIndex[r.roleKey] = i }
            if (r.divisionCode !in divisionIndex) add("$at.roles[$i].divisionCode", "Divisi '${r.divisionCode.value}' tidak ada di $at.divisions; tambahkan divisinya atau pindahkan peran")
            if (r.label.length > InterviewLimits.TEXT) add("$at.roles[$i].label", "Label peran maksimum ${InterviewLimits.TEXT} karakter")
            pure("$at.roles[$i].label", r.label)
            if (r.isHead) headByDivision[r.divisionCode]?.let {
                add("$at.roles[$i].isHead", "Divisi '${r.divisionCode.value}' sudah punya kepala di $at.roles[$it]; satu divisi satu kepala")
            } ?: run { headByDivision[r.divisionCode] = i }
        }

        val packModules = (pack.modules + pack.moduleReferences.mapNotNull { pack.resolveModule(it.platformModuleId) }).associateBy { it.id }
        val shipped = DomainPackRegistry.shipped.flatMap { it.modules }.associateBy { it.id }
        session.links.forEachIndexed { i, l ->
            val p = "$at.links[$i]"
            if (l.roleKey !in roleIndex) add("$p.roleKey", "Peran '${l.roleKey.value}' tidak ada di $at.roles")
            if (l.moduleId !in packModules) add("$p.moduleId", "Modul '${l.moduleId.value}' tidak ada di pack ${pack.code.value}")
            originIssue(l, shipped[l.moduleId]?.kind, shipped.containsKey(l.moduleId), pack.isReferenced(l.moduleId)).forEach { add("$p.origin", it) }
            if (l.features.size > InterviewLimits.FEATURES_PER_LINK) add("$p.features", "Terlalu banyak fitur (${l.features.size}); maksimum ${InterviewLimits.FEATURES_PER_LINK}")
            if (l.features.distinct().size != l.features.size) add("$p.features", "Ada fitur yang kembar")
            l.features.forEachIndexed { j, f ->
                if (f.isBlank() || f.length > InterviewLimits.TEXT) add("$p.features[$j]", "Fitur wajib terisi dan maksimum ${InterviewLimits.TEXT} karakter")
                pure("$p.features[$j]", f)
            }
        }
        val dupLinks = mutableMapOf<Pair<RoleKey, ModuleId>, Int>()
        session.links.forEachIndexed { i, l ->
            dupLinks[l.roleKey to l.moduleId]?.let { add("$at.links[$i]", "Tautan peran '${l.roleKey.value}' → '${l.moduleId.value}' sudah ada di $at.links[$it]") }
                ?: run { dupLinks[l.roleKey to l.moduleId] = i }
        }

        val handoffSeen = mutableMapOf<Triple<ModuleId, ModuleId, String>, Int>()
        session.handoffs.forEachIndexed { i, h ->
            val p = "$at.handoffs[$i]"
            if (h.from !in packModules) add("$p.from", "Modul '${h.from.value}' tidak ada di pack ${pack.code.value}")
            if (h.to !in packModules) add("$p.to", "Modul '${h.to.value}' tidak ada di pack ${pack.code.value}")
            if (h.from == h.to) add("$p.to", "Modul tidak boleh menyerahkan ke dirinya sendiri")
            if (h.portType !in pack.wiredPortTypes) add("$p.portType", "Port '${h.portType.value}' bukan port tersambung di pack ${pack.code.value}: ${pack.wiredPortTypes.joinToString { it.value }}")
            val fromPorts = pack.slotPorts(h.from)   // kosakata pack; modul rujukan lewat portMapping
            val toPorts = pack.slotPorts(h.to)
            if (h.from in packModules && h.to in packModules) {
                if (fromPorts == null || toPorts == null) add(p, "Hanya modul operasional yang bisa disambung; '${(if (fromPorts == null) h.from else h.to).value}' tidak punya slot kanvas")
                else if (h.portType != fromPorts.second && h.portType != toPorts.first)
                    add("$p.portType", "Port '${h.portType.value}' tidak cocok: '${h.from.value}' mengeluarkan '${fromPorts.second.value}' dan '${h.to.value}' menerima '${toPorts.first.value}'. Pakai salah satunya")
            }
            val key = Triple(h.from, h.to, h.portType.value)
            handoffSeen[key]?.let { add(p, "Sambungan sama sudah ada di $at.handoffs[$it]") } ?: run { handoffSeen[key] = i }
        }

        val turns = mutableSetOf<Int>()
        session.answers.forEachIndexed { i, a ->
            if (!turns.add(a.turn)) add("$at.answers[$i].turn", "Giliran ${a.turn} tercatat dua kali")
            if ((a.text?.length ?: 0) > InterviewLimits.TEXT) add("$at.answers[$i].text", "Jawaban bebas maksimum ${InterviewLimits.TEXT} karakter")
        }
        session.answers.forEachIndexed { i, a ->
            if (a.questionId.length > InterviewLimits.TEXT) add("$at.answers[$i].questionId", "questionId maksimum ${InterviewLimits.TEXT} karakter")
        }
        if (session.step == InterviewStep.DONE) {
            val withRole = session.roles.map { it.divisionCode }.toSet()
            session.divisions.forEachIndexed { i, d ->
                if (d.code !in withRole) add("$at.divisions[$i]", "Wawancara selesai tetapi divisi '${d.code.value}' belum punya peran; tambahkan peran atau hapus divisinya")
            }
            val withLink = session.links.map { it.roleKey }.toSet()
            session.roles.forEachIndexed { i, r ->
                if (r.roleKey !in withLink) add("$at.roles[$i]", "Wawancara selesai tetapi peran '${r.roleKey.value}' belum punya modul; tautkan ke modul atau hapus perannya")
            }
            session.links.forEachIndexed { i, l ->
                if (l.confirmed == Confirmation.GUESSED) add("$at.links[$i].confirmed", "Wawancara selesai tetapi tautan masih GUESSED; tandai CONFIRMED, CHANGED, atau SKIPPED (terima semua)")
            }
            session.handoffs.forEachIndexed { i, h ->
                if (h.confirmed == Confirmation.GUESSED) add("$at.handoffs[$i].confirmed", "Wawancara selesai tetapi sambungan masih GUESSED; tandai CONFIRMED, CHANGED, atau SKIPPED")
            }
        }
        out += InterviewBasisValidator.validate(session, at)
        return out
    }

    /** Asal vs kenyataan registri: modul bawaan hanya boleh diklaim dipakai-ulang/dikembangkan, modul baru tak boleh sudah ada. */
    private fun originIssue(l: RoleModuleLink, shippedKind: ModuleKind?, inShipped: Boolean, referenced: Boolean): List<String> =
        if (referenced) referencedOriginIssue(l) else when (l.origin) {
        ModuleOrigin.REUSE_PLATFORM ->
            if (!inShipped || shippedKind == ModuleKind.OPERATIONAL) listOf("REUSE_PLATFORM hanya untuk modul tata kelola/fondasi bawaan platform; '${l.moduleId.value}' bukan. Pakai REUSE_PACK, EXTEND, atau NEW")
            else emptyList()
        ModuleOrigin.REUSE_PACK ->
            if (!inShipped) listOf("REUSE_PACK mensyaratkan modul ada di pack bawaan; '${l.moduleId.value}' tidak ada. Pakai NEW atau EXTEND")
            else emptyList()
        ModuleOrigin.EXTEND -> buildList {
            if (!inShipped) add("EXTEND mensyaratkan modul ada di pack bawaan; '${l.moduleId.value}' tidak ada. Pakai NEW")
            if (l.features.isEmpty()) add("EXTEND wajib menyebut fitur tambahan di features")
        }
        ModuleOrigin.NEW ->
            if (inShipped) listOf("Modul '${l.moduleId.value}' sudah ada di pack bawaan; pakai REUSE_PACK atau EXTEND, bukan NEW")
            else emptyList()
    }

    /** Modul rujukan = modul bersama platform: dipakai apa adanya (`REUSE_PLATFORM`) atau dikembangkan (`EXTEND` + fitur). */
    private fun referencedOriginIssue(l: RoleModuleLink): List<String> = when (l.origin) {
        ModuleOrigin.REUSE_PLATFORM -> emptyList()
        ModuleOrigin.EXTEND -> if (l.features.isEmpty()) listOf("EXTEND wajib menyebut fitur tambahan di features") else emptyList()
        ModuleOrigin.REUSE_PACK, ModuleOrigin.NEW ->
            listOf("'${l.moduleId.value}' modul bersama platform yang dirujuk pack ini; pakai REUSE_PLATFORM (atau EXTEND dengan fitur), bukan ${l.origin}")
    }
}
