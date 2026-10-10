package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceTemplateId
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.InvoicingApiClient
import com.eventverse.app.infrastructure.api.InvoicingRemoteDataSource
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Filter jenis faktur untuk galeri template.
 */
enum class TemplateKindFilter(val label: String, val targetKind: InvoiceKind?) {
    ALL("Semua Jenis", null),
    SAMPLE("Sample Invoice (Fokus)", InvoiceKind.SAMPLE),
    DOWN_PAYMENT("DP Produksi", InvoiceKind.DOWN_PAYMENT),
    SETTLEMENT("Pelunasan", InvoiceKind.SETTLEMENT),
    FULL("Faktur Penuh", InvoiceKind.FULL)
}

/**
 * Layar galeri daftar tata letak (template) invoice.
 * Menampilkan template yang dikelompokkan dan difilter berdasarkan jenis tagihan.
 */
@Composable
fun InvoiceTemplateGalleryScreen(
    tenantSlug: String,
    onOpenDesigner: (templateId: String?) -> Unit,
    onBackToWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
    remoteDataSource: InvoicingRemoteDataSource = remember { InvoicingApiClient() }
) {
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<InvoiceTemplate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(TemplateKindFilter.ALL) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }

    val loadTemplates: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            remoteDataSource.getTemplates(tenantSlug, includeArchived = false).onSuccess { list ->
                templates = list
                isLoading = false
            }.onFailure { err ->
                errorMessage = "Gagal memuat daftar template: ${err.message}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(tenantSlug) {
        loadTemplates()
    }

    val filteredTemplates = remember(templates, selectedFilter) {
        val target = selectedFilter.targetKind
        if (target == null) templates
        else templates.filter { it.applicableKinds.contains(target) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Header Navigasi & Aksi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Kembali ke Faktur",
                    onClick = onBackToWorkspace,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp,
                    leading = { IconArrowBack(Modifier.size(13.dp), color = WeMadeColors.OnSurfaceMuted) }
                )

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Katalog Tata Letak & Template Faktur",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = "A4 CANVAS",
                            tint = WeMadeColors.Primary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Kelola format dokumen cetak faktur sesuai peruntukan tagihan (Sample, Termin DP, atau Pelunasan).",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            ClayButton(
                text = "+ Buat Layout Baru",
                onClick = { isCreateDialogOpen = true },
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp,
                leading = { IconPlus(Modifier.size(13.dp), color = WeMadeColors.Surface) }
            )
        }

        // Pesan Sukses / Error
        errorMessage?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = err, color = WeMadeColors.Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ClayIconButton(
                    onClick = { errorMessage = null },
                    size = 28.dp,
                    containerColor = WeMadeColors.Surface
                ) {
                    IconClose(Modifier.size(13.dp), color = WeMadeColors.Error)
                }
            }
        }

        successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SuccessBg,
                        outline = WeMadeColors.Success,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = msg, color = WeMadeColors.Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ClayIconButton(
                    onClick = { successMessage = null },
                    size = 28.dp,
                    containerColor = WeMadeColors.Surface
                ) {
                    IconClose(Modifier.size(13.dp), color = WeMadeColors.Success)
                }
            }
        }

        // Filter Bar Jenis Tagihan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Card,
                    background = WeMadeColors.Surface,
                    outline = WeMadeColors.Border,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Jenis Tagihan:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted
            )

            TemplateKindFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = if (filter.targetKind == null) templates.size
                else templates.count { it.applicableKinds.contains(filter.targetKind) }

                Row(
                    modifier = Modifier
                        .claySurface(
                            shape = ClayShapes.Chip,
                            background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                            outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                            borderWidth = ClayBorder.Hairline,
                            offset = if (isSelected) ClayOffset.Pressed else ClayOffset.Flat
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
                    )
                }
            }
        }

        // Grid Kartu Template
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Memuat daftar tata letak faktur...", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)
            }
        } else if (filteredTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Text(
                        text = "Belum ada template untuk kategori ini",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayButton(
                        text = "+ Buat Template Sekarang",
                        onClick = { isCreateDialogOpen = true },
                        style = ClayButtonStyle.Primary,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                items(filteredTemplates, key = { it.id.value }) { template ->
                    TemplateCard(
                        template = template,
                        onOpen = { onOpenDesigner(template.id.value) },
                        onSetDefault = {
                            scope.launch {
                                remoteDataSource.setDefaultTemplate(tenantSlug, template.id).onSuccess {
                                    successMessage = "Template '${template.name}' dijadikan default."
                                    loadTemplates()
                                }.onFailure { err ->
                                    errorMessage = "Gagal mengubah default: ${err.message}"
                                }
                            }
                        },
                        onArchive = {
                            scope.launch {
                                remoteDataSource.archiveTemplate(tenantSlug, template.id).onSuccess {
                                    successMessage = "Template '${template.name}' berhasil diarsipkan."
                                    loadTemplates()
                                }.onFailure { err ->
                                    errorMessage = "Gagal mengarsipkan template: ${err.message}"
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (isCreateDialogOpen) {
        CreateTemplateDialog(
            tenantSlug = tenantSlug,
            defaultKind = selectedFilter.targetKind ?: InvoiceKind.SAMPLE,
            onClose = { isCreateDialogOpen = false },
            onCreated = { createdId ->
                isCreateDialogOpen = false
                onOpenDesigner(createdId.value)
            },
            remoteDataSource = remoteDataSource
        )
    }
}

@Composable
private fun TemplateCard(
    template: InvoiceTemplate,
    onOpen: () -> Unit,
    onSetDefault: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Header Kartu: Judul & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${template.paperSize.name} · ${template.elements.size} Elemen tata letak",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                if (template.isDefault) {
                    ClayBadge(
                        text = "DEFAULT",
                        tint = WeMadeColors.Success,
                        fontSize = 10.sp
                    )
                }
            }

            // Target Jenis Tagihan (Tepat 1 jenis tagihan per template)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jenis Tagihan:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                val tint = when (template.targetKind) {
                    InvoiceKind.SAMPLE -> WeMadeColors.Primary
                    InvoiceKind.DOWN_PAYMENT -> WeMadeColors.Accent
                    InvoiceKind.SETTLEMENT -> WeMadeColors.Success
                    InvoiceKind.FULL -> WeMadeColors.OnSurfaceMuted
                }
                ClayBadge(
                    text = template.targetKind.displayName,
                    tint = tint,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xs))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Buka Desain Layout",
                    onClick = onOpen,
                    style = ClayButtonStyle.Primary,
                    fontSize = 11.sp,
                    leading = { IconRuler(Modifier.size(12.dp), color = WeMadeColors.Surface) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    if (!template.isDefault) {
                        ClayButton(
                            text = "Set Default",
                            onClick = onSetDefault,
                            style = ClayButtonStyle.Ghost,
                            fontSize = 11.sp
                        )
                    }

                    ClayIconButton(
                        onClick = onArchive,
                        size = 32.dp,
                        containerColor = WeMadeColors.SurfaceMuted
                    ) {
                        IconTrash(Modifier.size(13.dp), color = WeMadeColors.Error)
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog pembuatan template layout invoice baru.
 */
@Composable
private fun CreateTemplateDialog(
    tenantSlug: String,
    defaultKind: InvoiceKind,
    onClose: () -> Unit,
    onCreated: (InvoiceTemplateId) -> Unit,
    remoteDataSource: InvoicingRemoteDataSource
) {
    val scope = rememberCoroutineScope()
    val now = remember { Clock.System.now() }
    var selectedKind by remember { mutableStateOf(defaultKind) }
    var name by remember {
        mutableStateOf("Layout Faktur ${defaultKind.displayName} Baru")
    }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose) {
        ClayCard(
            modifier = Modifier.widthIn(min = 380.dp, max = 460.dp),
            containerColor = WeMadeColors.Surface,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buat Layout Faktur Baru",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayIconButton(
                        onClick = onClose,
                        size = 28.dp,
                        containerColor = WeMadeColors.SurfaceMuted
                    ) {
                        IconClose(Modifier.size(13.dp), color = WeMadeColors.OnSurfaceMuted)
                    }
                }
                error?.let { err ->
                    Text(text = err, color = WeMadeColors.Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                ClayTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nama Template Layout",
                    placeholder = "contoh: Standard Garment Sample Invoice"
                )

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Jenis Tagihan (1 Template = 1 Jenis Tagihan):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    InvoiceKind.entries.forEach { kind ->
                        val isSelected = kind == selectedKind
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .claySurface(
                                    shape = ClayShapes.Chip,
                                    background = if (isSelected) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                                    outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Hairline,
                                    offset = if (isSelected) ClayOffset.Pressed else ClayOffset.Flat
                                )
                                .clickable {
                                    selectedKind = kind
                                    if (name.startsWith("Layout Faktur ")) {
                                        name = "Layout Faktur ${kind.displayName} Baru"
                                    }
                                }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = kind.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
                            )
                            if (isSelected) {
                                IconCheck(Modifier.size(14.dp), color = WeMadeColors.Primary)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = onClose,
                        style = ClayButtonStyle.Ghost,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSaving) "Membuat..." else "Buat & Buka Desainer",
                        enabled = !isSaving && name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isSaving = true
                                error = null
                                val templateId = InvoiceTemplateId("tpl-${now.toEpochMilliseconds()}")
                                val newTemplate = InvoiceTemplateFactory.standardIndonesianInvoice(
                                    tenantId = TenantId(tenantSlug),
                                    now = now
                                ).copy(
                                    id = templateId,
                                    name = name.trim(),
                                    applicableKinds = setOf(selectedKind),
                                    isDefault = false
                                )
                                remoteDataSource.saveTemplate(tenantSlug, newTemplate).onSuccess { saved ->
                                    isSaving = false
                                    onCreated(saved.id)
                                }.onFailure { err ->
                                    isSaving = false
                                    error = err.message ?: "Gagal membuat template"
                                }
                            }
                        },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
