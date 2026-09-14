package com.eventverse.app.presentation.tenant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleCategory
import com.eventverse.app.infrastructure.api.AdminApiClient
import com.eventverse.app.infrastructure.api.TenantAdminView
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.module.ModuleIcon
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Dialog superadmin untuk menyambung dan memutus modul dari satu tenant.
 *
 * Ini permukaan manusiawi dari `PUT /api/admin/tenants/{slug}/entitlement`, yang selama ini hanya
 * dapat dipanggil lewat HTTP langsung. Setiap penyimpanan tercatat di audit log oleh server —
 * mencabut modul dari sebuah pabrik adalah tindakan yang tak terlihat dari dalam pabrik itu, jadi
 * satu-satunya cara ia tetap dapat dipertanggungjawabkan adalah bila setiap pemakaiannya berjejak.
 *
 * Mendukung auto-bypass dengan modal konfirmasi dampak jika modul yang dicabut sedang aktif
 * pada diagram alur pabrik tenant.
 */
@Composable
fun TenantModuleEntitlementDialog(
    tenantSlug: String,
    onDismiss: () -> Unit,
    onSaved: (Set<BusinessModule>) -> Unit,
    apiClient: AdminApiClient = remember { AdminApiClient() }
) {
    val scope = rememberCoroutineScope()

    var view by remember(tenantSlug) { mutableStateOf<TenantAdminView?>(null) }
    var draft by remember(tenantSlug) { mutableStateOf<Set<BusinessModule>?>(null) }
    var isBusy by remember(tenantSlug) { mutableStateOf(true) }
    var error by remember(tenantSlug) { mutableStateOf<String?>(null) }
    var showImpactConfirmation by remember(tenantSlug) { mutableStateOf(false) }

    LaunchedEffect(tenantSlug) {
        isBusy = true
        apiClient.getTenantAdminView(tenantSlug)
            .onSuccess {
                view = it
                draft = it.grantedModules
                error = null
            }
            .onFailure { error = it.message }
        isBusy = false
    }

    val activePipelineModules = remember(view) {
        view?.catalog?.modules
            ?.filter { it.isActive && it.standardModule != null }
            ?.mapNotNull { it.standardModule }
            ?.toSet() ?: emptySet()
    }

    val modulesToAutoBypass = remember(draft, view, activePipelineModules) {
        val currentDraft = draft ?: return@remember emptyList<BusinessModule>()
        val originalGranted = view?.grantedModules ?: return@remember emptyList<BusinessModule>()
        (originalGranted - currentDraft).filter { it in activePipelineModules }
    }

    fun executeSave(autoBypass: Boolean) {
        val snapshot = view ?: return
        val target = draft ?: return
        isBusy = true
        error = null
        scope.launch {
            apiClient.setEntitlement(
                tenantSlug = tenantSlug,
                grants = snapshot.copy(grantedModules = target).toGrants(),
                autoBypass = autoBypass
            )
                .onSuccess {
                    view = it
                    draft = it.grantedModules
                    showImpactConfirmation = false
                    onSaved(it.grantedModules)
                    onDismiss()
                }
                .onFailure {
                    error = it.message
                    showImpactConfirmation = false
                }
            isBusy = false
        }
    }

    // Scrim. Klik di luar kartu menutup dialog; klik pada kartunya sendiri tidak boleh menembus,
    // karena itulah interactionSource kosong pada Box bagian dalam.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeMadeColors.Scrim.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) {
            ClayCard(
                modifier = Modifier.widthIn(max = 620.dp).padding(ClaySpacing.Xl),
                shape = ClayShapes.Panel
            ) {
                if (showImpactConfirmation) {
                    ImpactConfirmationView(
                        tenantName = view?.name?.takeIf { it.isNotBlank() } ?: tenantSlug,
                        modules = modulesToAutoBypass,
                        isBusy = isBusy,
                        error = error,
                        onCancel = { showImpactConfirmation = false },
                        onConfirm = { executeSave(autoBypass = true) }
                    )
                } else {
                    DialogHeader(view = view, tenantSlug = tenantSlug)

                    Spacer(modifier = Modifier.padding(top = ClaySpacing.Lg))

                    when {
                        isBusy && view == null -> Text(
                            text = "Memuat data tenant…",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )

                        view == null -> Text(
                            text = error ?: "Data tenant tidak dapat dimuat.",
                            fontSize = 13.sp,
                            color = WeMadeColors.Error
                        )

                        else -> {
                            val current = draft.orEmpty()
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                            ) {
                                ModuleGroup(
                                    title = "Modul Sistem & Tata Kelola",
                                    subtitle = "Tidak memakan kuota modul produksi.",
                                    modules = BusinessModule.governance,
                                    granted = current,
                                    activePipelineModules = activePipelineModules,
                                    onToggle = { module, enabled ->
                                        draft = if (enabled) current + module else current - module
                                    }
                                )
                                ModuleGroup(
                                    title = "Modul Operasional Pabrik",
                                    subtitle = "Terhitung terhadap batas paket langganan.",
                                    modules = BusinessModule.operational,
                                    granted = current,
                                    activePipelineModules = activePipelineModules,
                                    onToggle = { module, enabled ->
                                        draft = if (enabled) current + module else current - module
                                    }
                                )
                            }

                            error?.let {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(top = ClaySpacing.Lg),
                                    fontSize = 12.sp,
                                    color = WeMadeColors.Error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.padding(top = ClaySpacing.Xl))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md, Alignment.End)
                    ) {
                        ClayButton(
                            text = "Batal",
                            onClick = onDismiss,
                            style = ClayButtonStyle.Secondary
                        )
                        ClayButton(
                            text = if (isBusy) "Menyimpan…" else "Simpan Entitlement",
                            onClick = {
                                if (modulesToAutoBypass.isNotEmpty()) {
                                    showImpactConfirmation = true
                                } else {
                                    executeSave(autoBypass = false)
                                }
                            },
                            enabled = view != null && !isBusy && draft != view?.grantedModules
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactConfirmationView(
    tenantName: String,
    modules: List<BusinessModule>,
    isBusy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTag(text = "Perhatian Alur", tint = WeMadeColors.Warning)
            Text(
                text = "Konfirmasi Pemutusan Modul",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }

        Text(
            text = "Modul berikut saat ini sedang aktif digunakan dalam diagram Alur Produksi tenant '$tenantName':",
            fontSize = 13.sp,
            color = WeMadeColors.OnSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            modules.forEach { module ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(20.dp)) {
                            ModuleIcon(
                                iconKey = module.iconKey,
                                modifier = Modifier.fillMaxSize(),
                                color = categoryTint(module.category)
                            )
                        }
                        Text(
                            text = module.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    ClayBadge(
                        text = "Aktif di Alur",
                        tint = WeMadeColors.Warning,
                        dot = true
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Warning,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sistem akan secara otomatis menonaktifkan (bypass) tahapan di atas dari diagram alur tenant agar alur pabrik tetap valid dan tidak terjadi error pemutusan.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    lineHeight = 16.sp
                )
            }
        }

        error?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = WeMadeColors.Error
            )
        }

        Spacer(modifier = Modifier.padding(top = ClaySpacing.Md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md, Alignment.End)
        ) {
            ClayButton(
                text = "Kembali ke Pilihan",
                onClick = onCancel,
                style = ClayButtonStyle.Secondary,
                enabled = !isBusy
            )
            ClayButton(
                text = if (isBusy) "Memproses…" else "Ya, Nonaktifkan dari Alur & Simpan",
                onClick = onConfirm,
                enabled = !isBusy
            )
        }
    }
}

@Composable
private fun DialogHeader(view: TenantAdminView?, tenantSlug: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "Kelola Modul Tenant",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = view?.name?.takeIf { it.isNotBlank() } ?: tenantSlug,
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(ClaySpacing.Sm))

        if (view != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayTag(text = view.catalog.tier.name, tint = WeMadeColors.Primary)
                // Menyebut kuota dengan angka: tanpa ini, mematikan sebuah modul tata kelola dan
                // melihat angka kuota tidak bergerak akan tampak seperti salah hitung.
                ClayBadge(
                    text = "${view.activeOperationalModules}/${quotaLabel(view.maxActiveModules)} modul produksi",
                    tint = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

private fun quotaLabel(max: Int): String = if (max == Int.MAX_VALUE) "∞" else max.toString()

@Composable
private fun ModuleGroup(
    title: String,
    subtitle: String,
    modules: List<BusinessModule>,
    granted: Set<BusinessModule>,
    activePipelineModules: Set<BusinessModule>,
    onToggle: (BusinessModule, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Text(text = subtitle, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)

        modules.forEach { module ->
            ModuleToggleRow(
                module = module,
                isGranted = module in granted,
                isPipelineActive = module in activePipelineModules,
                onToggle = { onToggle(module, it) }
            )
        }
    }
}

@Composable
private fun ModuleToggleRow(
    module: BusinessModule,
    isGranted: Boolean,
    isPipelineActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (isGranted) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                outline = if (isGranted) WeMadeColors.Outline else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .clickable { onToggle(!isGranted) }
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(18.dp)) {
                ModuleIcon(
                    iconKey = module.iconKey,
                    modifier = Modifier.fillMaxSize(),
                    color = if (isGranted) categoryTint(module.category) else WeMadeColors.OnSurfaceDisabled
                )
            }
            Text(
                text = module.displayName,
                fontSize = 12.sp,
                fontWeight = if (isGranted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isGranted) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(ClaySpacing.Sm))

        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isGranted && isPipelineActive) {
                ClayTag(
                    text = "Aktif di Alur",
                    tint = WeMadeColors.Warning
                )
            }
            ClayBadge(
                text = if (isGranted) "Tersambung" else "Diputus",
                tint = if (isGranted) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                dot = true
            )
        }
    }
}

private fun categoryTint(category: ModuleCategory) = when (category) {
    ModuleCategory.GOVERNANCE -> WeMadeColors.Purple
    ModuleCategory.FOUNDATION -> WeMadeColors.Teal
    ModuleCategory.SALES -> WeMadeColors.Primary
    ModuleCategory.LOGISTICS -> WeMadeColors.Warning
    ModuleCategory.TECHNICAL -> WeMadeColors.Info
    ModuleCategory.PRODUCTION -> WeMadeColors.Accent
    ModuleCategory.QUALITY -> WeMadeColors.Success
}
