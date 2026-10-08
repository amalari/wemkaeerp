package com.eventverse.app.domain.audit

/**
 * Platform-level actions worth recording for accountability.
 *
 * These are actions a superadmin takes *on a tenant's data* — the kind of action a factory
 * owner cannot see happening from inside their own workspace, so there has to be a durable
 * trail of who did what and when.
 */
enum class AuditAction(val code: String) {
    TENANT_ENTITLEMENT_UPDATED("tenant_entitlement_updated"),
    TENANT_TIER_UPDATED("tenant_tier_updated"),

    /** Vertikal (Domain Pack) sebuah tenant ditetapkan (B7): mengubah seluruh kosakata modul tenant itu. */
    TENANT_DOMAIN_PACK_ASSIGNED("tenant_domain_pack_assigned"),

    /**
     * Handoff memakai ulang pack identik milik tenant lain dan melepasnya menjadi bersama (TRD-PLAT-005):
     * kepemilikan pack berubah, jadi harus ada jejak siapa yang memicu dan pack apa.
     */
    TENANT_DOMAIN_PACK_SHARED("tenant_domain_pack_shared"),

    /** Trial tenant diperpanjang superadmin (V88) — keputusan uang: memperlambang pendapatan. */
    TENANT_TRIAL_EXTENDED("tenant_trial_extended"),

    /**
     * A build was closed with its actual hours and cost.
     *
     * Recorded because that cost figure is what a subscription price is derived from, and a price
     * runs for the length of a contract. If the number is ever disputed, there has to be a record
     * of who entered it and when.
     */
    MODULE_BUILD_RECORDED("module_build_recorded"),

    /** A monthly price was issued for a module. Same reason: money, and it outlives the session. */
    MODULE_PRICE_QUOTED("module_price_quoted"),

    /** Builder (M2): deploy mengunci versi pack tenant — aksi berisiko, wajib tercatat (TRD NFR). */
    BUILDER_DEPLOYMENT_ACTIVATED("builder_deployment_activated"),

    /** Builder (M2): rollback menurunkan versi — terlebih wajib tercatat, termasuk aksi `force`. */
    BUILDER_DEPLOYMENT_ROLLED_BACK("builder_deployment_rolled_back"),

    /**
     * Builder (M2, FR-M2-5): invoice langganan diterbitkan. Angka di dokumen ini mengikat secara
     * komersial, dan tenant memutuskan bayar berdasarkan dokumen yang sama — jadi "siapa yang
     * menerbitkan, kapan, dengan total berapa" harus bisa direkonstruksi tanpa menebak.
     */
    BUILDER_INVOICE_ISSUED("builder_invoice_issued"),

    /** Builder (M2, FR-M2-5): pembayaran dikonfirmasi manual oleh superadmin. */
    BUILDER_INVOICE_PAID("builder_invoice_paid"),

    /**
     * Superadmin masuk ke workspace tenant (Builder atau aplikasi hasil) lewat tiket act-as
     * (discovery-M3b). Dicatat di tenant **tujuan**, supaya owner bisa melihat kapan operator platform
     * membuka datanya — syarat yang disepakati untuk mengizinkan act-as sama sekali.
     */
    PLATFORM_ACT_AS_STARTED("platform_act_as_started");

    companion object {
        fun fromCode(code: String?): AuditAction? = entries.firstOrNull { it.code == code }
    }
}
