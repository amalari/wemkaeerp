package com.eventverse.app.domain.tenant

/**
 * Permukaan mana yang sedang dibuka, diturunkan dari host browser dan base domain platform
 * (PLAN-builder-console §2):
 *
 * - [Platform] — `wemakeerp.com` / `app.wemakeerp.com`: login, daftar, **Builder**, admin platform. Pemilik
 *   tenant dan superadmin sama-sama membuka Builder di sini; sesinya tinggal di origin ini.
 * - [Tenant] — `<slug>.wemakeerp.com`: **aplikasi hasil generate** milik satu tenant; tenant diambil dari
 *   host, jadi login tidak lagi menanyakan "Kode Pabrik". Dibuka dari Builder lewat tiket handoff.
 * - [Local] — host di luar base domain (localhost, IP, target non-web) **atau** base domain belum
 *   dikonfigurasi. Perilakunya sama persis dengan sebelum pemisahan login: kolom slug tetap ada.
 *
 * Status sistem, bukan konsep tenant (lolos Uji Variabilitas): tiga permukaan ini milik platform.
 * Satu parser untuk klien dan server, supaya keduanya tidak pernah berbeda pendapat soal host.
 */
sealed interface HostSurface {
    data object Local : HostSurface
    data object Platform : HostSurface
    data class Tenant(val slug: TenantSlug) : HostSurface

    companion object {
        /** Subdomain yang menunjuk permukaan platform, bukan tenant. */
        private const val PLATFORM_LABEL = "app"

        /**
         * [host] boleh berisi port (`bordir.wemakeerp.com:443`). Label subdomain yang bukan slug sah
         * (terlarang seperti `www`/`admin`, atau berformat salah) jatuh ke [Platform] — tidak pernah
         * ditebak menjadi tenant lain. Subdomain bertingkat (`a.b.wemakeerp.com`) dianggap [Local]
         * karena tidak ada tenant yang dialamatkan seperti itu.
         */
        fun parse(host: String?, baseDomain: String?): HostSurface {
            val base = baseDomain?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotBlank() }
                ?: return Local
            val cleanHost = host?.substringBefore(":")?.trim()?.trimEnd('.')?.lowercase()
                ?.takeIf { it.isNotBlank() } ?: return Local

            if (cleanHost == base || cleanHost == "$PLATFORM_LABEL.$base") return Platform
            if (!cleanHost.endsWith(".$base")) return Local

            val label = cleanHost.removeSuffix(".$base")
            if (label.contains('.')) return Local
            return runCatching { Tenant(TenantSlug(label)) }.getOrElse { Platform }
        }

        /** URL dasar aplikasi tenant, mis. `https://bordir.wemakeerp.com`. */
        fun tenantOrigin(slug: TenantSlug, baseDomain: String): String =
            "https://${slug.value}.${baseDomain.trim().trimEnd('.').lowercase()}"
    }
}
