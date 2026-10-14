package com.eventverse.app.domain.builder

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import kotlin.jvm.JvmInline

/**
 * Kontrak navigasi Builder Console v2 — **dibekukan di Track 0** (TRD-PLAT-014 FR-1, PLAN
 * `PLAN-builder-console-v2-navigation.md` §4 T0). Track T1–T4 membangun di atas ini; mengubah
 * isinya berarti mencairkan kontrak lewat revisi TRD + perbarui test kontrak.
 *
 * Isinya murni data + derivasi murni: tidak ada komponen UI, tidak ada akses jaringan, tidak
 * ada dependensi ke platform render — supaya server (T1) dan klien (T2/T3) sama-sama bisa
 * memakainya tanpa saling menunggu.
 */
@JvmInline
value class BuilderMenuKey(val value: String) {
    init {
        require(value.isNotBlank()) { "BuilderMenuKey kosong" }
    }
}

/** Satu entri menu sidebar Builder v2. Label dua bahasa **sebagai data** (TRD FR-1-3), bukan string di composable. */
data class BuilderConsoleMenuEntry(
    val key: BuilderMenuKey,
    val labelText: LocalizedText,
)

/** Lima menu v2 — per momen pelanggan: bicara (chat), lihat (live), serahkan (handoff), percaya (audit), atur (setting). */
object BuilderConsoleMenu {

    val CHAT = BuilderMenuKey("chat")
    val LIVE = BuilderMenuKey("live")
    val HANDOFF = BuilderMenuKey("handoff")
    val AUDIT = BuilderMenuKey("audit")
    val SETTING = BuilderMenuKey("setting")

    val entries: List<BuilderConsoleMenuEntry> = listOf(
        BuilderConsoleMenuEntry(CHAT, LocalizedText.of("Chat", "Chat")),
        BuilderConsoleMenuEntry(LIVE, LocalizedText.of("Live", "Live")),
        BuilderConsoleMenuEntry(HANDOFF, LocalizedText.of("Handoff", "Handoff")),
        BuilderConsoleMenuEntry(AUDIT, LocalizedText.of("Audit", "Audit")),
        BuilderConsoleMenuEntry(SETTING, LocalizedText.of("Pengaturan", "Settings")),
    )

    fun label(key: BuilderMenuKey): LocalizedText =
        entries.firstOrNull { it.key == key }?.labelText
            ?: error("BuilderMenuKey '${key.value}' tidak ada di menu v2 — kontrak Track 0 dilanggar")
}

/**
 * Peta **route lama → tujuan baru** (PLAN §3, disposisi menu). Seluruh deep-link lama
 * (`/builder/<key>`) tetap sampai tujuan lewat redirect klien — teaching doc dan refleks
 * pengguna tidak boleh patah (TRD FR-1-2).
 *
 * Nilai = segmen route baru setelah `/builder/` (identitas diperbolehkan: entri lama yang
 * memang tetap, dicantumkan agar keputusannya terdokumentasi dan test paritas bisa
 * mengiterasinya). Key tak dikenal → [resolve] mengembalikan `null`; penanganan 404 tidak berubah.
 */
object BuilderRouteMap {

    val LEGACY: Map<String, String> = mapOf(
        "overview" to "live/production",
        "newpack" to "newpack",
        "chat" to "chat",
        "modules" to "live/preview",
        "dataflow" to "live/preview",
        "prototype" to "live/preview",
        "buildqueue" to "handoff",
        "deployments" to "live/production",
        "billing" to "setting/billing",
        "settings" to "setting",
    )

    /** Tujuan baru sebuah key menu lama, atau `null` bila bukan key lama yang dikenal. */
    fun resolve(legacyKey: String): String? = LEGACY[legacyKey]
}

/**
 * Sinyal titik status menu Live (TRD FR-1-4): dibedakan lewat *peran*, bukan warna — warna
 * adalah keputusan UI di lapisan tema. `NOT_DEPLOYED` berarti "tidak ada deployment aktif
 * menjalankan tenant" (belum pernah deploy, atau yang terakhir sudah ROLLED_BACK/SUPERSEDED).
 */
enum class BuilderLiveSignal { NOT_DEPLOYED, LIVE, VALIDATING, BLOCKED_ON_BUILD, FAILED }

/**
 * Aturan badge menu v2 (TRD FR-1-4) — keputusan PLAN §7.1 dibekukan di sini:
 * badge Handoff = jumlah permintaan [BuildRequestStatus.isPending] saja (SUPERSEDED, SHIPPED,
 * REJECTED tidak dihitung); titik Live diturunkan dari status deployment **aktif**.
 */
object BuilderConsoleBadges {

    fun handoffActiveCount(statuses: Collection<BuildRequestStatus>): Int =
        statuses.count { it.isPending }

    fun liveSignal(activeDeploymentStatus: DeploymentStatus?): BuilderLiveSignal = when (activeDeploymentStatus) {
        null -> BuilderLiveSignal.NOT_DEPLOYED
        DeploymentStatus.ACTIVE, DeploymentStatus.IMPORTED -> BuilderLiveSignal.LIVE
        DeploymentStatus.VALIDATING -> BuilderLiveSignal.VALIDATING
        DeploymentStatus.BLOCKED_ON_BUILD -> BuilderLiveSignal.BLOCKED_ON_BUILD
        DeploymentStatus.FAILED -> BuilderLiveSignal.FAILED
        // Bukan status "aktif" — repository `findActive` tidak pernah mengembalikannya; bila
        // tetap masuk lewat jalur lain, tenant memang tidak sedang dijalankan deployment itu.
        DeploymentStatus.SUPERSEDED, DeploymentStatus.ROLLED_BACK -> BuilderLiveSignal.NOT_DEPLOYED
    }
}
