package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Status visual untuk setiap langkah di dalam [ClayProcessStepper].
 */
enum class ClayStepStatus {
    COMPLETED,
    ACTIVE,
    PENDING
}

/**
 * Data murni untuk satu node langkah di [ClayProcessStepper].
 * Bebas dari keterikatan domain model apapun (Kontrak 6 Design System WeMade).
 */
data class ClayStepData(
    val title: String,
    val subtitle: String? = null,
    val status: ClayStepStatus = ClayStepStatus.PENDING,
    val badgeText: String? = null,
    val stepNumber: Int? = null
)

/**
 * Komponen Process Stepper horizontal bergaya Claymorphism:
 * Node melingkar tebal dengan outline tegas 2-3dp, garis penghubung, status aktif menyala,
 * centang hijau untuk langkah selesai, serta badge informasi status dinamis.
 *
 * Dirancang khusus untuk alur sekuensial pabrik garmen yang berstatus Read-Only.
 */
@Composable
fun ClayProcessStepper(
    steps: List<ClayStepData>,
    modifier: Modifier = Modifier,
    activeColor: Color = WeMadeColors.Primary,
    completedColor: Color = WeMadeColors.Success,
    nodeSize: Dp = 28.dp,
    fullWidth: Boolean = true
) {
    if (steps.isEmpty()) return

    val scrollState = rememberScrollState()

    val rowModifier = if (fullWidth) {
        modifier
            .fillMaxWidth()
            .padding(vertical = ClaySpacing.Sm)
    } else {
        modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = ClaySpacing.Sm)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { index, step ->
            val isFirst = index == 0
            val isLast = index == steps.size - 1

            val colModifier = if (fullWidth) {
                Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            } else {
                Modifier
                    .widthIn(min = 96.dp, max = 120.dp)
                    .padding(horizontal = 2.dp)
            }

            // Satu segmen langkah: [Garis Kiri] -> [Bulatan Node] -> [Garis Kanan] + Teks di bawahnya
            Column(
                modifier = colModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Baris Node + Garis Penghubung
                Box(
                    modifier = Modifier.fillMaxWidth().height(nodeSize + 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Garis kiri (kecuali anak pertama) — hijau hanya jika node sebelumnya COMPLETED
                    if (!isFirst) {
                        val prevStep = steps.getOrNull(index - 1)
                        val leftLineColor = if (prevStep?.status == ClayStepStatus.COMPLETED) {
                            completedColor
                        } else {
                            WeMadeColors.Border
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.5f)
                                .height(3.dp)
                                .background(leftLineColor)
                        )
                    }

                    // Garis kanan (kecuali anak terakhir) — hijau hanya jika node saat ini COMPLETED
                    if (!isLast) {
                        val rightLineColor = if (step.status == ClayStepStatus.COMPLETED) {
                            completedColor
                        } else {
                            WeMadeColors.Border
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(0.5f)
                                .height(3.dp)
                                .background(rightLineColor)
                        )
                    }

                    // Node Bulat
                    when (step.status) {
                        ClayStepStatus.COMPLETED -> {
                            Box(
                                modifier = Modifier
                                    .size(nodeSize)
                                    .clip(CircleShape)
                                    .background(completedColor)
                                    .border(2.dp, WeMadeColors.Outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                IconCheck(
                                    modifier = Modifier.size(nodeSize * 0.55f),
                                    color = WeMadeColors.Surface
                                )
                            }
                        }

                        ClayStepStatus.ACTIVE -> {
                            Box(
                                modifier = Modifier
                                    .size(nodeSize + 4.dp)
                                    .clip(CircleShape)
                                    .background(activeColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(nodeSize)
                                        .clip(CircleShape)
                                        .background(activeColor)
                                        .border(2.dp, WeMadeColors.Outline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${step.stepNumber ?: (index + 1)}",
                                        color = WeMadeColors.Surface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        ClayStepStatus.PENDING -> {
                            Box(
                                modifier = Modifier
                                    .size(nodeSize)
                                    .clip(CircleShape)
                                    .background(WeMadeColors.SurfaceMuted)
                                    .border(1.5.dp, WeMadeColors.Border, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${step.stepNumber ?: (index + 1)}",
                                    color = WeMadeColors.OnSurfaceMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Judul Langkah
                Text(
                    text = step.title,
                    fontSize = 11.sp,
                    fontWeight = if (step.status == ClayStepStatus.ACTIVE) FontWeight.Bold else FontWeight.Medium,
                    color = when (step.status) {
                        ClayStepStatus.ACTIVE -> activeColor
                        ClayStepStatus.COMPLETED -> WeMadeColors.OnSurface
                        ClayStepStatus.PENDING -> WeMadeColors.OnSurfaceMuted
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle / Keterangan Waktu / Resi
                if (!step.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = step.subtitle,
                        fontSize = 9.sp,
                        color = if (step.status == ClayStepStatus.ACTIVE) activeColor else WeMadeColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badge Dinamis (misal: "Draft", "Lolos QC 1", "Finishing", "ACC")
                if (!step.badgeText.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    ClayTag(
                        text = step.badgeText,
                        tint = when (step.status) {
                            ClayStepStatus.COMPLETED -> completedColor
                            ClayStepStatus.ACTIVE -> activeColor
                            ClayStepStatus.PENDING -> WeMadeColors.Border
                        }
                    )
                }
            }
        }
    }
}
