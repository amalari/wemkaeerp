package com.eventverse.app.domain.auth

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import kotlin.jvm.JvmInline

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
        require(value.length in 3..64) { "UserId must be between 3 and 64 characters" }
    }
}

@JvmInline
value class Username(val value: String) {
    init {
        require(value.isNotBlank()) { "Username cannot be blank" }
        require(value.length in 3..30) { "Username must be between 3 and 30 characters" }
        require(USERNAME_REGEX.matches(value)) { 
            "Username can only contain alphanumeric characters, underscores, and dots: $value" 
        }
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._]+$")
    }
}

@JvmInline
value class EmailAddress(val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "EmailAddress cannot be blank" }
        require(EMAIL_REGEX.matches(trimmed)) { "Invalid email address format: $value" }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
    }
}

enum class Permission {
    // Platform Superadmin
    MANAGE_PLATFORM,
    IMPERSONATE_TENANT,

    // Tenant Administration
    MANAGE_TENANT,
    MANAGE_USERS,
    VIEW_BILLING,

    // WeMake Builder (PLAN-builder-console): konsol project ala Vercel, 1 akun = 1 project.
    // Permission sistem (lolos Uji Variabilitas): milik TENANT_ADMIN & superadmin secara default;
    // bisa diberikan ke user tenant lain sebagai kolaborator builder.
    MANAGE_BUILDER,

    // CRM & Sales
    VIEW_LEADS,
    MANAGE_LEADS,
    MANAGE_SAMPLING_ORDERS,

    // Inventory & PLM
    VIEW_INVENTORY,
    MANAGE_INVENTORY,
    VIEW_PLM,
    MANAGE_PLM,
    CALCULATE_COSTING,
    /**
     * Mengunci lembar HPP menjadi "Komitmen Komersial". Dipisah dari CALCULATE_COSTING
     * karena menyetujui HPP adalah keputusan bisnis, bukan eksekusi teknis.
     */
    APPROVE_COSTING,
    /**
     * Melihat bucket MARGIN dan harga jual akhir. Dipisah karena margin laba pabrik
     * bersifat "rahasia" per deskripsi GarmentModules.COSTING_HPP — staf yang bisa
     * menghitung HPP tidak otomatis boleh melihat berapa persen laba pabriknya.
     */
    VIEW_COSTING_MARGIN,

    // Production & MRP
    APPROVE_SPK,
    MANAGE_PRODUCTION_SCHEDULE,
    INPUT_SHOPFLOOR_OUTPUT,

    // Master Data & Bahan Baku
    VIEW_MASTER_DATA,
    MANAGE_MASTER_DATA,

    // QC & Fulfillment
    PERFORM_QC,
    MANAGE_FULFILLMENT,

    // Invoicing & Finance
    VIEW_INVOICE,
    MANAGE_INVOICE,
    MANAGE_INVOICE_TEMPLATE,
    RECORD_PAYMENT;
}

enum class Role(val defaultPermissions: Set<Permission>) {
    PLATFORM_SUPERADMIN(
        Permission.entries.toSet()
    ),
    TENANT_ADMIN(
        setOf(
            Permission.MANAGE_TENANT,
            Permission.MANAGE_USERS,
            Permission.VIEW_BILLING,
            Permission.MANAGE_BUILDER,
            Permission.VIEW_LEADS,
            Permission.MANAGE_LEADS,
            Permission.MANAGE_SAMPLING_ORDERS,
            Permission.VIEW_MASTER_DATA,
            Permission.MANAGE_MASTER_DATA,
            Permission.VIEW_INVENTORY,
            Permission.MANAGE_INVENTORY,
            Permission.VIEW_PLM,
            Permission.MANAGE_PLM,
            Permission.CALCULATE_COSTING,
            Permission.APPROVE_COSTING,
            Permission.VIEW_COSTING_MARGIN,
            Permission.APPROVE_SPK,
            Permission.MANAGE_PRODUCTION_SCHEDULE,
            Permission.INPUT_SHOPFLOOR_OUTPUT,
            Permission.PERFORM_QC,
            Permission.MANAGE_FULFILLMENT,
            Permission.VIEW_INVOICE,
            Permission.MANAGE_INVOICE,
            Permission.MANAGE_INVOICE_TEMPLATE,
            Permission.RECORD_PAYMENT
        )
    ),
    SALES(
        setOf(
            Permission.VIEW_LEADS,
            Permission.MANAGE_LEADS,
            Permission.MANAGE_SAMPLING_ORDERS,
            Permission.VIEW_INVENTORY,
            Permission.VIEW_PLM,
            Permission.CALCULATE_COSTING,
            // Sales bisa lihat margin untuk keperluan penawaran ke klien,
            // tapi tidak bisa approve — itu hak owner/supervisor.
            Permission.VIEW_COSTING_MARGIN,
            Permission.VIEW_INVOICE,
            Permission.MANAGE_INVOICE
        )
    ),
    PPIC_SUPERVISOR(
        setOf(
            Permission.VIEW_INVENTORY,
            Permission.MANAGE_INVENTORY,
            Permission.VIEW_PLM,
            Permission.MANAGE_PLM,
            Permission.APPROVE_SPK,
            Permission.MANAGE_PRODUCTION_SCHEDULE,
            Permission.INPUT_SHOPFLOOR_OUTPUT,
            Permission.PERFORM_QC,
            Permission.MANAGE_FULFILLMENT
        )
    ),
    OPERATOR(
        setOf(
            Permission.INPUT_SHOPFLOOR_OUTPUT
        )
    ),
    QC_INSPECTOR(
        setOf(
            Permission.PERFORM_QC,
            Permission.VIEW_PLM
        )
    ),
    WAREHOUSE(
        setOf(
            Permission.VIEW_INVENTORY,
            Permission.MANAGE_INVENTORY,
            Permission.MANAGE_FULFILLMENT
        )
    );
}
