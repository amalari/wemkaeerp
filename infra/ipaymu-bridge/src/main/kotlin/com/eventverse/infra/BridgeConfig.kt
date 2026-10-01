package com.eventverse.infra

/**
 * Konfigurasi bertipe stack iPaymu bridge (TRD-PAY-001 §4.2).
 * Diisi lewat `pulumi config set` — lihat runbook di TRD §5.3.
 */
data class BridgeConfig(
    /** OCID compartment OCI tempat seluruh resource dibuat. Wajib bila enableOci = true. */
    val ociCompartmentId: String = "",
    /** Account ID Cloudflare. */
    val cfAccountId: String,
    /** Zone ID Cloudflare untuk domain (mis. wemakeerp.com). */
    val cfZoneId: String,
    /** Domain dasar hostname webhook (mis. wemakeerp.com). */
    val domain: String,
    /** Nama developer — satu tunnel Cloudflare per developer (FR-PAY-2.1). */
    val developers: List<String>,
    /** Kunci publik SSH developer, diikat ke metadata instance (FR-PAY-1.5). */
    val sshPublicKeys: List<String>,
    /** Shape instance; default A1 Flex 1 OCPU / 6 GB. */
    val shape: String = DEFAULT_SHAPE,
    /**
     * Egress gateway OCI dibuat atau tidak (TRD P3). Default false: saat pengujian
     * sandbox iPaymu yang tidak menegakkan IP whitelist, Ktor keluar langsung dari
     * laptop dan hanya tunnel ingress yang di-deploy. Set true menjelang production
     * / bila sandbox ternyata menegakkan whitelist.
     */
    val enableOci: Boolean = false,
) {
    companion object {
        const val DEFAULT_SHAPE = "VM.Standard.A1.Flex"
        const val REGION = "ap-jakarta-1"
    }

    init {
        require(cfAccountId.isNotBlank() && cfZoneId.isNotBlank()) { "cfAccountId / cfZoneId wajib diisi" }
        require(domain.isNotBlank()) { "domain wajib diisi" }
        require(developers.isNotEmpty()) { "minimal satu developer (tunnel per developer)" }
        if (enableOci) {
            require(ociCompartmentId.isNotBlank()) { "ociCompartmentId wajib diisi bila enableOci = true" }
            require(sshPublicKeys.isNotEmpty()) { "minimal satu kunci SSH publik bila enableOci = true" }
        }
    }
}
