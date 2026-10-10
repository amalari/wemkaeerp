package com.eventverse.app.presentation.workspace

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
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.VocabularyKey

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
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar kerja generik sebuah modul pack: dipakai setiap modul yang belum punya layar khusus
 * ([ModuleScreenRegistry]), termasuk modul vertikal non-konveksi.
 *
 * Chrome-nya **tidak boleh** menyebut kosakata satu industri (A4): istilah ("pabrik", "SPK") dan
 * label tombol aksi dibaca dari [pack], bukan ditulis sebagai literal di sini. Dulu layar ini menulis
 * `"pabrik"`/`"SPK"` langsung, sehingga tenant klinik melihat kata pabrik di layarnya sendiri.
 */
@Composable
fun ModuleWorkspaceScreen(
    module: BusinessModule,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    pack: DomainPack = ActiveTenantPack.current
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

    val resolvedSlug = persona?.tenantSlug?.takeIf { it.isNotBlank() }
        ?: return com.eventverse.app.presentation.navigation.TenantNotSelectedView(modifier)

    // B6e: layar khusus per modul dari registry (data), bukan rantai `if (module == X)`. Modul tanpa entri —
    // termasuk modul pack lain — memakai layar generik di bawah.
    val screen = ModuleScreenRegistry.screens[module]
    if (screen != null) {
        screen(ModuleScreenContext(resolvedSlug, decision, persona, modifier.fillMaxSize()))
        return
    }

    ModuleWorkspacePlaceholder(module = module, decision = decision, persona = persona, pack = pack, modifier = modifier)
}

@Composable
private fun ModuleWorkspacePlaceholder(
    module: BusinessModule,
    decision: AccessDecision,
    persona: TestingPersona?,
    pack: DomainPack,
    modifier: Modifier = Modifier
) {
    val access = decision.config
    val documentWord = pack.term(VocabularyKey.DOCUMENT)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        ModuleHeader(module = module, access = access, persona = persona)
        AccessProvenanceCard(decision = decision, persona = persona)
        AccessBanner(access = access, documentWord = documentWord)
        ActionToolbar(level = access.level, actions = pack.actions)
        SampleRecords(module = module, access = access, documentWord = documentWord)
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
private fun AccessBanner(access: ModuleAccessConfig, documentWord: String) {
    val (title, detail) = when (access.level) {
        AccessLevel.VIEW -> "Mode Baca Saja (Wewenang Terbatas)" to
            "Data dapat dibaca; seluruh aksi ubah dinonaktifkan."
        AccessLevel.OPERATE -> "Mode Input & Kerja" to
            "Boleh menambah dan mengubah dokumen harian. Approval dan hapus tetap tertutup."
        AccessLevel.MANAGE -> "Akses Penuh / Supervisi" to
            "Termasuk persetujuan dan penghapusan ${documentWord.lowercase()}."
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

/**
 * Toolbar aksi generik: **urutannya dari pack** ([DomainPack.actions]), label dari pack, sedangkan
 * gaya & wewenang minimum dari peran aksinya ([ModuleActionCode]) — bahaya tetap merah di semua vertikal.
 */
@Composable
private fun ActionToolbar(level: AccessLevel, actions: List<ModuleAction>) {
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
            actions.forEach { action ->
                RbacGuardedButton(
                    text = action.label,
                    onClick = {},
                    currentLevel = level,
                    requiredLevel = action.code.requiredLevel,
                    style = action.code.toolbarStyle()
                )
            }
        }
    }
}

/** Gaya tombol per peran aksi — sistem, bukan data pack. */
private fun ModuleActionCode.toolbarStyle(): ClayButtonStyle = when (this) {
    ModuleActionCode.ADD -> ClayButtonStyle.Primary
    ModuleActionCode.EDIT -> ClayButtonStyle.Secondary
    ModuleActionCode.APPROVE -> ClayButtonStyle.Accent
    ModuleActionCode.DELETE -> ClayButtonStyle.Danger
}

@Composable
private fun SampleRecords(module: BusinessModule, access: ModuleAccessConfig, documentWord: String) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daftar ${documentWord.replaceFirstChar { it.uppercase() }}",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ClayTag(text = access.level.badgeLabel(), tint = access.level.tint())
        }

        val rows = ModuleSampleRows.rowsFor(module)

        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            if (rows.isEmpty()) {
                // Modul pack tanpa contoh baris (mis. modul vertikal non-konveksi) tidak boleh tampil
                // sebagai kartu kosong: penguji tidak bisa membedakan "belum ada contoh" dari "layout rusak".
                Text(
                    text = "Belum ada contoh $documentWord untuk modul ini.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            rows.forEach { row ->
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
