package com.eventverse.app.presentation.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.crm.CrmWorkspaceScreen
import com.eventverse.app.presentation.masterdata.MasterDataWorkspaceScreen
import com.eventverse.app.presentation.sampling.SamplingWorkspaceScreen
import com.eventverse.app.presentation.techpack.TechPackWorkspaceScreen
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar kerja untuk sembilan modul bisnis konveksi.
 *
 * `CRM_SALES` adalah modul pertama yang lepas dari placeholder generik — lihat
 * [CrmWorkspaceScreen]. Delapan modul sisanya masih memakai [ModuleWorkspacePlaceholder]:
 * banner yang berbeda, tombol yang hidup atau mati, dan cakupan data yang dinyatakan
 * terang-terangan, supaya perbedaan yang terlihat pasti berasal dari wewenang, bukan dari
 * layar yang kebetulan berbeda. Saat modul kesembilan pindah ke layar sungguhannya,
 * `ModuleWorkspacePlaceholder` dan `sampleRowsFor` di bawah bisa dihapus seluruhnya.
 */
@Composable
fun ModuleWorkspaceScreen(
    module: BusinessModule,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier
) {
    val access = decision.config

    if (!access.isAccessible) {
        Column(
            modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
        ) {
            AccessDeniedCard(
                moduleName = module.displayName,
                personaName = persona?.name ?: "Tanpa persona",
                roleTitle = persona?.roleTitle ?: "Tanpa jabatan",
                departmentName = persona?.departmentName ?: "Tanpa divisi"
            )
        }
        return
    }

    val resolvedSlug = persona?.tenantSlug?.takeIf { it.isNotBlank() } ?: "wemade-demo"

    if (module == BusinessModule.CRM_SALES) {
        CrmWorkspaceScreen(
            tenantSlug = resolvedSlug,
            access = access,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.SAMPLING_ORDER) {
        SamplingWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.MASTER_DATA) {
        MasterDataWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.TECH_PACK_BOM) {
        TechPackWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.INVOICING) {
        com.eventverse.app.presentation.invoicing.InvoiceWorkspaceScreen(
            tenantSlug = resolvedSlug,
            access = access,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.COSTING_HPP) {
        com.eventverse.app.presentation.costing.CostingWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.OPERATOR_EXEC) {
        com.eventverse.app.presentation.finishing.FinishingOperatorWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.QUALITY_CONTROL) {
        com.eventverse.app.presentation.qc.QcInspectorWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (module == BusinessModule.PRODUCTION_MRP) {
        com.eventverse.app.presentation.production.ProductionWorkspaceScreen(
            tenantSlug = resolvedSlug,
            decision = decision,
            persona = persona,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    ModuleWorkspacePlaceholder(module = module, decision = decision, persona = persona, modifier = modifier)
}

@Composable
private fun ModuleWorkspacePlaceholder(
    module: BusinessModule,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier
) {
    val access = decision.config

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        ModuleHeader(module = module, access = access, persona = persona)
        AccessProvenanceCard(decision = decision, persona = persona)
        AccessBanner(access = access)
        ActionToolbar(level = access.level)
        SampleRecords(module = module, access = access)
    }
}

@Composable
private fun ModuleHeader(
    module: BusinessModule,
    access: ModuleAccessConfig,
    persona: TestingPersona?
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = module.displayName,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClayBadge(text = access.level.displayName, tint = access.level.tint(), dot = true)
        }

        Text(
            text = persona?.let { "${it.name} — ${it.roleTitle}, divisi ${it.departmentName}" }
                ?: "Belum ada persona aktif.",
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/**
 * Kartu yang menjawab pertanyaan sebenarnya saat menguji sebuah jabatan:
 * **"menu ini terbuka karena jabatannya, atau karena divisinya?"**
 *
 * Tanpa ini, penguji yang mengatur wewenang jabatan lalu melihat menunya muncul akan menyimpulkan
 * konfigurasi jabatannya benar — padahal bisa jadi jabatannya `NONE` dan divisinya yang memberi.
 * Kesimpulan salah itu tidak akan pernah terbantah oleh layar mana pun.
 */
@Composable
private fun AccessProvenanceCard(decision: AccessDecision, persona: TestingPersona?) {
    val roleName = persona?.roleTitle ?: "Tanpa jabatan"
    val deptName = persona?.departmentName ?: "Tanpa divisi"

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (decision.grantedByDepartmentOnly) {
            WeMadeColors.WarningBg
        } else {
            WeMadeColors.Surface
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Asal Wewenang",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ClayBadge(
                text = decision.source.label,
                tint = when (decision.source) {
                    AccessSource.ROLE -> WeMadeColors.Primary
                    AccessSource.DEPARTMENT -> WeMadeColors.Warning
                    AccessSource.OWNER_BYPASS,
                    AccessSource.SUPERADMIN_BYPASS -> WeMadeColors.Success
                    // Amber, bukan merah: modulnya belum disambungkan ke pabrik ini, dan itu
                    // keadaan langganan — bukan penolakan wewenang.
                    AccessSource.NOT_ENTITLED -> WeMadeColors.Warning
                    AccessSource.NONE -> WeMadeColors.Error
                }
            )
        }

        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ProvenanceRow(
                label = "Dari jabatan “$roleName”",
                level = decision.fromRole.level,
                isDecisive = decision.source == AccessSource.ROLE
            )
            ProvenanceRow(
                label = "Dari divisi “$deptName”",
                level = decision.fromDepartment.level,
                isDecisive = decision.source == AccessSource.DEPARTMENT
            )
        }

        if (decision.grantedByDepartmentOnly) {
            Text(
                text = "Menu ini terbuka karena penugasan divisi, bukan karena jabatannya. " +
                    "Jabatan “$roleName” sendiri tidak diberi akses ke modul ini.",
                modifier = Modifier.padding(top = ClaySpacing.Lg),
                fontSize = 11.sp,
                color = WeMadeColors.OnSurface
            )
        }
    }
}

@Composable
private fun ProvenanceRow(label: String, level: AccessLevel, isDecisive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isDecisive) "$label  ← berlaku" else label,
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 12.sp,
            fontWeight = if (isDecisive) FontWeight.Bold else FontWeight.Normal,
            color = if (isDecisive) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
        ClayTag(text = level.displayName, tint = level.tint())
    }
}

/**
 * Banner yang menyatakan mode kerja saat ini.
 *
 * Cakupan data ikut disebut karena itulah yang paling sering disalahartikan sebagai kerusakan:
 * layar yang hanya memuat lima baris belum tentu kehilangan data, bisa jadi memang dibatasi ke
 * data milik pengguna sendiri.
 */
@Composable
private fun AccessBanner(access: ModuleAccessConfig) {
    val (title, detail) = when (access.level) {
        AccessLevel.VIEW -> "Mode Baca Saja (Wewenang Terbatas)" to
            "Data dapat dibaca; seluruh aksi ubah dinonaktifkan."
        AccessLevel.OPERATE -> "Mode Input & Kerja" to
            "Boleh menambah dan mengubah dokumen harian. Approval dan hapus tetap tertutup."
        AccessLevel.MANAGE -> "Akses Penuh / Supervisi" to
            "Termasuk approval SPK dan penghapusan data."
        AccessLevel.NONE -> "Akses Ditutup" to ""
    }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = when (access.level) {
            AccessLevel.VIEW -> WeMadeColors.WarningBg
            AccessLevel.OPERATE -> WeMadeColors.PrimaryContainer
            AccessLevel.MANAGE -> WeMadeColors.SuccessBg
            AccessLevel.NONE -> WeMadeColors.ErrorBg
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = access.level.tint(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ClayTag(text = access.scope.shortLabel, tint = WeMadeColors.OnSurfaceMuted)
        }

        if (detail.isNotBlank()) {
            Text(
                text = detail,
                modifier = Modifier.padding(top = ClaySpacing.Sm),
                fontSize = 12.sp,
                color = WeMadeColors.OnSurface
            )
        }

        Text(
            text = "Cakupan data: ${access.scope.description}",
            modifier = Modifier.padding(top = ClaySpacing.Xs),
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun ActionToolbar(level: AccessLevel) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Aksi",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            RbacGuardedButton(
                text = "Tambah Pesanan",
                onClick = {},
                currentLevel = level,
                requiredLevel = AccessLevel.OPERATE
            )
            RbacGuardedButton(
                text = "Input Progres",
                onClick = {},
                currentLevel = level,
                requiredLevel = AccessLevel.OPERATE,
                style = ClayButtonStyle.Secondary
            )
            RbacGuardedButton(
                text = "Setujui SPK",
                onClick = {},
                currentLevel = level,
                requiredLevel = AccessLevel.MANAGE,
                style = ClayButtonStyle.Accent
            )
            RbacGuardedButton(
                text = "Hapus Data",
                onClick = {},
                currentLevel = level,
                requiredLevel = AccessLevel.MANAGE,
                style = ClayButtonStyle.Danger
            )
        }
    }
}

@Composable
private fun SampleRecords(module: BusinessModule, access: ModuleAccessConfig) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daftar Dokumen",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ClayTag(text = access.level.badgeLabel(), tint = access.level.tint())
        }

        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            sampleRowsFor(module).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.first,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ClayTag(text = row.second, tint = WeMadeColors.Info)
                }
            }
        }
    }
}

/** Baris contoh per modul — cukup untuk membuat layar terasa nyata saat wewenang diuji. */
private fun sampleRowsFor(module: BusinessModule): List<Pair<String, String>> = when (module) {
    BusinessModule.CRM_SALES -> listOf(
        "PT Sinar Jaya — 1.200 pcs kemeja" to "Prospek",
        "CV Amanah — 500 pcs seragam" to "Nego"
    )
    BusinessModule.SAMPLING_ORDER -> listOf(
        "Sample #SP-1043 — Polo Cotton" to "Jahit",
        "Sample #SP-1044 — Kemeja PDH" to "Review"
    )
    BusinessModule.MASTER_DATA -> listOf(
        "Benang Cotton Combed 30s — YRN-0001" to "Aktif",
        "Kain Fleece Katun 280 gsm — FAB-0002" to "Aktif"
    )
    BusinessModule.INVENTORY -> listOf(
        "Cotton Combed 30s — 420 kg" to "Tersedia",
        "Kain titipan buyer — 180 kg" to "Konsinyasi"
    )
    BusinessModule.TECH_PACK_BOM -> listOf(
        "Tech Pack PDH-2024 rev.3" to "Final",
        "BOM Polo Combed" to "Draft"
    )
    BusinessModule.PRODUCTION_MRP -> listOf(
        "SPK-8891 — Line 2, 3 hari" to "Berjalan",
        "SPK-8892 — Line 4" to "Antre"
    )
    BusinessModule.OPERATOR_EXEC -> listOf(
        "Rian — 320 pcs hari ini" to "Tercatat",
        "Agus — 280 pcs hari ini" to "Tercatat"
    )
    BusinessModule.QUALITY_CONTROL -> listOf(
        "Inspeksi AQL 2.5 — lot 8891" to "Lolos",
        "Temuan jahitan loncat — 12 pcs" to "Rework"
    )
    BusinessModule.FULFILLMENT -> listOf(
        "Surat Jalan SJ-2201 — 40 karton" to "Dikirim",
        "Packing list PO-5512" to "Disiapkan"
    )
    BusinessModule.INVOICING -> listOf(
        "INV/2026/03/0001 — PT Sinar Jaya (DP 50%)" to "Issued",
        "INV/2026/03/0002 — CV Amanah (Sampling)" to "Paid"
    )
    // Modul tata kelola punya layar sungguhannya sendiri (Bagan Organisasi, RBAC, Alur Pabrik, Costing) dan
    // tidak pernah dirutekan ke layar kerja generik ini. Cabang ini ada semata agar `when` tetap
    // ekshaustif — dan sengaja kosong, bukan diisi baris contoh yang akan menyesatkan bila suatu
    // saat benar-benar terlihat.
    BusinessModule.COSTING_HPP,
    BusinessModule.ORG_CHART,
    BusinessModule.DYNAMIC_RBAC,
    BusinessModule.FACTORY_FLOW -> emptyList()
}
