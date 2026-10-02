package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconCalculator
import com.eventverse.app.presentation.designsystem.IconCalendarGrid
import com.eventverse.app.presentation.designsystem.IconCheckCircle
import com.eventverse.app.presentation.designsystem.IconClipboard
import com.eventverse.app.presentation.designsystem.IconDatabase
import com.eventverse.app.presentation.designsystem.IconHandshake
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconReceipt
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.designsystem.IconUser
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.resources.Res
import com.eventverse.app.shared.resources.clay_cutting
import com.eventverse.app.shared.resources.clay_delivery
import com.eventverse.app.shared.resources.clay_invoice
import com.eventverse.app.shared.resources.clay_qc
import com.eventverse.app.shared.resources.clay_sewing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Style dan palet warna per seksi alur pabrik. */
internal data class ModuleSectionStyle(
    val title: String,
    val color: Color,
    val background: Color
)

/**
 * Resolver dinamis gaya seksi (Jalur B, Kontrak Variabilitas):
 * 1. Mengutamakan metadata seksi dari server/DomainPack ([draft.sectionsMetadata]).
 * 2. Fallback deterministik berbasis hash untuk seksi baru/kustom yang di-generate AI,
 *    sehingga tidak pernah ada hardcode nama departemen ("SALES", "GOVERNANCE", dsb).
 */
internal fun resolveSectionStyle(sectionCode: String, draft: DiscoveryDraftUi): ModuleSectionStyle {
    val meta = draft.sectionsMetadata.firstOrNull { it.code.equals(sectionCode, ignoreCase = true) }
    if (meta != null && meta.colorHex != 0L) {
        val color = Color(meta.colorHex)
        val bg = if (meta.tintHex != 0L) Color(meta.tintHex) else color.copy(alpha = 0.12f)
        return ModuleSectionStyle(
            title = meta.displayName.ifBlank { sectionCode.replace('_', ' ') },
            color = color,
            background = bg
        )
    }

    // Deterministic palette fallback untuk modul/seksi dinamis hasil generate AI
    val palette = listOf(
        WeMadeColors.Primary to WeMadeColors.PrimaryContainer,
        WeMadeColors.Accent to WeMadeColors.AccentLight,
        WeMadeColors.Teal to WeMadeColors.TealBg,
        WeMadeColors.Purple to WeMadeColors.PurpleBg,
        WeMadeColors.Success to WeMadeColors.SuccessBg,
        WeMadeColors.Info to WeMadeColors.InfoBg,
        WeMadeColors.AccentDark to WeMadeColors.WarningBg
    )
    val index = kotlin.math.abs(sectionCode.hashCode()) % palette.size
    val (col, bg) = palette[index]
    val formattedTitle = sectionCode.replace('_', ' ').split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

    return ModuleSectionStyle(
        title = formattedTitle.ifBlank { "Modul Alur" },
        color = col,
        background = bg
    )
}

/**
 * Resolver Aset 3D Clay Miniatur:
 * Berdasarkan visual archetype / [module.iconKey] semantik platform (bukan teks deskripsi bahasa industri).
 * AI builder menyematkan iconKey (misal "craft", "cutting", "inspection", "shipping", "finance") saat membuat modul.
 */
internal fun resolveClayAsset(module: DiscoveryModuleUi): DrawableResource? {
    val icon = (module.iconKey ?: "").lowercase()
    val slot = (module.slot ?: "").lowercase()
    val key = "${module.id} $icon $slot".lowercase()

    return when {
        // Archetype: Craft / Assembly / Sewing / Machine
        key.contains("craft") || key.contains("sewing") || key.contains("needle") || key.contains("machine") || key.contains("assembly") || key.contains("spk") -> Res.drawable.clay_sewing

        // Archetype: Pattern / Cutting / Material / Preparation
        key.contains("cutting") || key.contains("scissors") || key.contains("pattern") || key.contains("material") || key.contains("sampling") || key.contains("pola") -> Res.drawable.clay_cutting

        // Archetype: Inspection / Quality / Audit
        key.contains("inspection") || key.contains("qc") || key.contains("audit") || key.contains("magnifier") || key.contains("check") || key.contains("inspeksi") -> Res.drawable.clay_qc

        // Archetype: Logistics / Shipping / Delivery / Packaging
        key.contains("shipping") || key.contains("delivery") || key.contains("truck") || key.contains("package") || key.contains("packing") || key.contains("kirim") -> Res.drawable.clay_delivery

        // Archetype: Costing / Billing / Finance / Invoicing
        key.contains("finance") || key.contains("billing") || key.contains("invoice") || key.contains("costing") || key.contains("receipt") || key.contains("tagihan") || key.contains("hpp") -> Res.drawable.clay_invoice

        else -> null
    }
}

/**
 * Resolver Ikon Semantik Vektor (Skiko Canvas Native):
 * Memetakan [module.iconKey] semantik platform ke ikon vektor tajam dan konsisten.
 */
@Composable
internal fun resolveModuleIcon(module: DiscoveryModuleUi): @Composable (Modifier, Color) -> Unit {
    val icon = (module.iconKey ?: "").lowercase()
    val key = "${module.id} $icon ${module.slot.orEmpty()}".lowercase()

    return when {
        // Identity, People & Org
        key.contains("user") || key.contains("people") || key.contains("org") || key.contains("employee") -> { m, c -> IconUser(m, c) }

        // Security, Access, Shield
        key.contains("shield") || key.contains("security") || key.contains("rbac") || key.contains("role") -> { m, c -> IconShield(m, c) }

        // Database, Master Data
        key.contains("database") || key.contains("master") || key.contains("data") -> { m, c -> IconDatabase(m, c) }

        // Partnership, Vendor, CRM Handshake
        key.contains("handshake") || key.contains("partner") || key.contains("vendor") -> { m, c -> IconHandshake(m, c) }

        // Measurement, Pattern, Design
        key.contains("ruler") || key.contains("measurement") || key.contains("pattern") -> { m, c -> IconRuler(m, c) }

        // Warehouse, Inventory, Package
        key.contains("package") || key.contains("box") || key.contains("inventory") || key.contains("stock") -> { m, c -> IconPackage(m, c) }

        // Logistics, Truck, Transport
        key.contains("truck") || key.contains("delivery") || key.contains("shipping") -> { m, c -> IconTruck(m, c) }

        // Planning, Tech Pack, Checklist
        key.contains("clipboard") || key.contains("task") || key.contains("checklist") || key.contains("spec") -> { m, c -> IconClipboard(m, c) }

        // Calculation, Estimator, Costing
        key.contains("calculator") || key.contains("math") || key.contains("costing") -> { m, c -> IconCalculator(m, c) }

        // Scheduling, Calendar, Batch
        key.contains("calendar") || key.contains("schedule") || key.contains("batch") -> { m, c -> IconCalendarGrid(m, c) }

        // QC, Verification, Check
        key.contains("check") || key.contains("qc") || key.contains("verify") -> { m, c -> IconCheckCircle(m, c) }

        // Billing, Invoicing, Receipt
        key.contains("receipt") || key.contains("invoice") || key.contains("billing") -> { m, c -> IconReceipt(m, c) }

        // Pipeline, Flow, Activity
        key.contains("activity") || key.contains("flow") || key.contains("pipeline") -> { m, c -> IconActivity(m, c) }

        // Fallback generic layer
        else -> { m, c -> IconLayers(m, c) }
    }
}

/**
 * `ModuleMapPane` (plan §4, Fase C/D): kanvas visual peta modul alur pabrik.
 * Mendukung tata letak dinamis per baris (3 kolom modul responsif) dengan miniatur 3D clay besar (64dp)
 * dan filter departemen cepat.
 */
@Composable
fun ModuleMapPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var groupByDepartment by remember { mutableStateOf(false) }

    val displayedModules = remember(draft, selectedSection) {
        if (selectedSection != null) {
            draft.modules.filter { it.section == selectedSection }
        } else {
            draft.modules
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 1024.dp -> 3
            maxWidth >= 680.dp -> 2
            else -> 1
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            // Filter Bar per Departemen + Toggle Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Chips
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayChoiceChip(
                        text = "Semua (${draft.modules.size})",
                        selected = selectedSection == null,
                        onClick = { selectedSection = null }
                    )

                    draft.sections.forEach { section ->
                        val count = draft.modules.count { it.section == section }
                        val style = resolveSectionStyle(section, draft)
                        ClayChoiceChip(
                            text = "${style.title} ($count)",
                            selected = selectedSection == section,
                            tint = style.color,
                            onClick = { selectedSection = section }
                        )
                    }
                }

                // Switch Tampilan: "Grid 3 Kolom" vs "Per Seksi" (hanya aktif saat Semua)
                if (selectedSection == null) {
                    ClayChoiceChip(
                        text = if (groupByDepartment) "Per Seksi" else "Grid 3 Kolom",
                        selected = groupByDepartment,
                        tint = WeMadeColors.Accent,
                        onClick = { groupByDepartment = !groupByDepartment },
                        fontSize = 10.sp
                    )
                }
            }

            // Render Modul: Berkelompok per Seksi ATAU Grid Dinamis 3 Kolom
            if (groupByDepartment && selectedSection == null) {
                draft.sections.forEach { section ->
                    val inSection = draft.modules.filter { it.section == section }
                    val activeInSection = inSection.count { it.active }
                    val style = resolveSectionStyle(section, draft)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        // Header Seksi
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Box(Modifier.size(10.dp).background(style.color, CircleShape))
                            Text(
                                text = style.title.uppercase(),
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                fontSize = 12.sp
                            )
                            ClayBadge(
                                text = "$activeInSection/${inSection.size} Modul Aktif",
                                tint = if (activeInSection > 0) style.color else WeMadeColors.OnSurfaceDisabled,
                                fontSize = 9.sp
                            )
                        }

                        // Baris Kartu 3 Kolom
                        inSection.chunked(columns).forEach { rowModules ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                            ) {
                                rowModules.forEach { module ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ModuleItemCard(module = module, style = style)
                                    }
                                }
                                repeat(columns - rowModules.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Tampilan Grid Dinamis: 3 Kolom per Baris
                displayedModules.chunked(columns).forEach { rowModules ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        rowModules.forEach { module ->
                            Box(modifier = Modifier.weight(1f)) {
                                ModuleItemCard(module = module, style = resolveSectionStyle(module.section, draft))
                            }
                        }
                        repeat(columns - rowModules.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleItemCard(
    module: DiscoveryModuleUi,
    style: ModuleSectionStyle
) {
    val typography = rememberClayTypography()
    val iconRenderer = resolveModuleIcon(module)
    val clayRes = resolveClayAsset(module)

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = if (module.active) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
        outlineColor = if (module.active) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
        offset = if (module.active) ClayOffset.Small else ClayOffset.Flat
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            // Header Baris: Badge Departemen & Badge Status Aktif
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayBadge(
                    text = style.title.uppercase(),
                    tint = style.color,
                    fontSize = 9.sp
                )

                if (module.active) {
                    ClayBadge(
                        text = "AKTIF",
                        tint = WeMadeColors.Success,
                        fontSize = 8.sp,
                        dot = true
                    )
                } else {
                    Text(
                        text = "Non-aktif",
                        style = typography.bodySmall,
                        fontSize = 9.sp,
                        color = WeMadeColors.OnSurfaceDisabled
                    )
                }
            }

            // Badan Kartu: Tile Ikon 64dp (Besar & Jelas) + Judul & Slot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kotak Tile Ikon: 64dp x 64dp untuk gambar miniatur clay 3D yang jelas
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (module.active) style.background else WeMadeColors.SurfaceMuted,
                            ClayShapes.Tile
                        )
                        .border(
                            ClayBorder.Medium,
                            if (module.active) style.color.copy(alpha = 0.35f) else WeMadeColors.Border,
                            ClayShapes.Tile
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (clayRes != null && module.active) {
                        Image(
                            painter = painterResource(clayRes),
                            contentDescription = module.displayName,
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        iconRenderer(
                            Modifier.size(28.dp),
                            if (module.active) style.color else WeMadeColors.OnSurfaceDisabled
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = module.displayName,
                        style = typography.titleSmall,
                        fontWeight = if (module.active) FontWeight.Bold else FontWeight.Medium,
                        color = if (module.active) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = module.slot ?: module.id,
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Footer: Hint Aliran Data Port IO
            if (module.active && (module.slotInput != null || module.slotOutput != null)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.5f), ClayShapes.Chip)
                        .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs)
                ) {
                    Text(
                        text = "In: ${module.slotInput ?: "—"}  •  Out: ${module.slotOutput ?: "—"}",
                        style = typography.bodySmall,
                        fontSize = 9.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

