package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconStar
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

internal data class GarmentTemplate(
    val id: String,
    val name: String,
    val description: String,
    val divisions: List<String>,
    val icon: @Composable (Modifier, Color) -> Unit
)

private val garmentTemplates = listOf(
    GarmentTemplate(
        id = "fob",
        name = "Konveksi FOB",
        description = "Cutting → Sewing → Finishing → QC → Fulfillment\n(Tanpa procurement)",
        divisions = listOf("Cutting", "Sewing", "Finishing", "QC"),
        icon = { m, c -> IconLayers(m, c) }
    ),
    GarmentTemplate(
        id = "cmt",
        name = "Konveksi CMT",
        description = "Cutting → Sewing → Finishing → QC\n(Bahan dari buyer)",
        divisions = listOf("Cutting", "Sewing", "Finishing", "QC"),
        icon = { m, c -> IconLayers(m, c) }
    ),
    GarmentTemplate(
        id = "sablon",
        name = "Sablon",
        description = "Cutting → Sablon → Finishing → QC\n(Screen Printing)",
        divisions = listOf("Cutting", "Sablon", "Finishing", "QC"),
        icon = { m, c -> IconLayers(m, c) }
    ),
    GarmentTemplate(
        id = "bordir",
        name = "Bordir",
        description = "Cutting → Bordir → Finishing → QC\n(Embroidery)",
        divisions = listOf("Cutting", "Bordir", "Finishing", "QC"),
        icon = { m, c -> IconLayers(m, c) }
    ),
    GarmentTemplate(
        id = "rajut",
        name = "Rajut",
        description = "Knitting → Cutting → Finishing → QC\n(Circular Knitting)",
        divisions = listOf("Knitting", "Cutting", "Finishing", "QC"),
        icon = { m, c -> IconLayers(m, c) }
    ),
    GarmentTemplate(
        id = "custom",
        name = "Custom",
        description = "Tanya divisi → generate sesuai kebutuhan",
        divisions = emptyList(),
        icon = { m, c -> IconStar(m, c) }
    )
)

@Composable
fun BuilderNewPackScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val typography = rememberClayTypography()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onNavigate("overview") }
                        .padding(ClaySpacing.Xs)
                ) {
                    IconArrowBack(Modifier.size(20.dp), color = WeMadeColors.OnSurfaceMuted)
                }
                Column {
                    Text(
                        text = "Buat Pack Baru",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Pilih metode yang sesuai kebutuhan Anda",
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        Text(
            text = "Pilih Metode",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            MethodCard(
                modifier = Modifier.weight(1f),
                title = "Template-based",
                subtitle = "Disarankan",
                description = "Pilih template garment dan jawab beberapa pertanyaan untuk mengkonfigurasi modul secara otomatis.",
                icon = { m, c -> IconLayers(m, c) },
                accentColor = WeMadeColors.Primary,
                onClick = { onNavigate("chat?template=wizard") }
            )
            MethodCard(
                modifier = Modifier.weight(1f),
                title = "From Scratch",
                subtitle = "Custom 100%",
                description = "Buat pack dari awal tanpa template. Semua keputusan ada di tangan Anda.",
                icon = { m, c -> IconStar(m, c.copy(alpha = 0.8f)) },
                accentColor = WeMadeColors.Secondary,
                onClick = { onNavigate("chat") }
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

        Text(
            text = "Pilih Template Garment",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Text(
            text = "Pilih template yang paling sesuai dengan jenis pabrik Anda. Setelah dipilih, AI akan bertanya tentang divisi dan konfigurasi spesifik.",
            style = typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            items(garmentTemplates) { template ->
                TemplateCard(
                    template = template,
                    onClick = {
                        onNavigate("chat?template=${template.id}")
                    }
                )
            }
        }
    }
}

@Composable
private fun MethodCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    description: String,
    icon: @Composable (Modifier, Color) -> Unit,
    accentColor: Color,
    onClick: () -> Unit
) {
    val typography = rememberClayTypography()

    ClayCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = ClayShapes.Panel,
        outlineColor = accentColor.copy(alpha = 0.3f),
        borderWidth = ClayBorder.Medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.1f), ClayShapes.Chip),
                    contentAlignment = Alignment.Center
                ) {
                    icon(Modifier.size(20.dp), accentColor)
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = title,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        if (subtitle.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.1f), ClayShapes.Pill)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = subtitle,
                                    style = typography.bodySmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = description,
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: GarmentTemplate,
    onClick: () -> Unit
) {
    val typography = rememberClayTypography()

    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ClayShapes.Tile,
        outlineColor = WeMadeColors.Outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(WeMadeColors.Primary.copy(alpha = 0.1f), ClayShapes.Chip),
                    contentAlignment = Alignment.Center
                ) {
                    template.icon(Modifier.size(18.dp), WeMadeColors.Primary)
                }
                Text(
                    text = template.name,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }

            if (template.description.isNotEmpty()) {
                Text(
                    text = template.description,
                    style = typography.bodySmall,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Start
                )
            }

            if (template.divisions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    template.divisions.take(3).forEach { division ->
                        Box(
                            modifier = Modifier
                                .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = division,
                                style = typography.bodySmall,
                                fontSize = 8.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                    if (template.divisions.size > 3) {
                        Box(
                            modifier = Modifier
                                .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${template.divisions.size - 3}",
                                style = typography.bodySmall,
                                fontSize = 8.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
