package com.eventverse.app.presentation.orgchart.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.TShapeHierarchyResult
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TShapeChartView(
    result: TShapeHierarchyResult,
    onSelectNode: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ─── 1. TOP LAYER: 1 TINGKAT KE ATAS (SUPERIOR) ───
        Text(
            text = "Atasan Langsung (1 Tingkat ke Atas / Jalur Approval)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        val superior = result.superior
        if (superior != null) {
            OrgNodeCard(
                node = superior,
                badgeLabel = "ATASAN LANGSUNG",
                onClick = { onSelectNode(superior.id.value) }
            )
        } else {
            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Posisi Puncak Pabrik (Tidak Memiliki Atasan)",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Connector Line (Top to Middle)
        ConnectorVerticalLine()

        // ─── 2. KASUS A: FOCUS NODE ADALAH STAF (OPERATOR) ───
        // Staf berada SEJAJAR bersama rekan kerja satu divisi di bawah Atasan Langsung
        if (result.focusNode.level == HierarchyLevel.STAFF_OPERATOR) {
            val deptName = result.focusNode.department?.displayName ?: "Perusahaan"
            Text(
                text = "Rekan Kerja Sejajar — Seluruh Tim Divisi $deptName (${result.peersInDepartment.size + 1} Orang)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted
            )
            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // Seluruh staf (focus node + rekan sejajar) ditampilkan berdampingan (SEJAJAR)
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tampilkan rekan-rekan yang sudah ada
                result.peersInDepartment.forEach { peer ->
                    OrgNodeCard(
                        node = peer,
                        onClick = { onSelectNode(peer.id.value) }
                    )
                }

                // Posisi fokus (misal karyawan yang baru dibuat atau diedit)
                OrgNodeCard(
                    node = result.focusNode,
                    isHighlighted = true,
                    badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT"
                )
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xxl))

            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.BackgroundWarm,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Posisi Staf Pelaksana adalah level operasional (tidak membawahi karyawan lain)",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // ─── 2. KASUS B: FOCUS NODE ADALAH HEAD ATAU DIREKSI ───
            // 2.1 Rekan Sejajar Antar Divisi (Peer Heads)
            if (result.peerHeads.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = ClaySpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rekan Sejajar (Kepala Divisi Lain):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    result.peerHeads.forEach { peer ->
                        val peerDeptColor = peer.department?.let { Color(it.colorHex) } ?: WeMadeColors.Primary
                        ClayTag(
                            text = "${peer.department?.shortName ?: "Direksi"}: ${peer.name}",
                            tint = peerDeptColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2.2 Focus Node Card (Head of Dept / Executive)
            if (result.focusNode.level == HierarchyLevel.EXECUTIVE && result.peersInDepartment.isNotEmpty()) {
                Text(
                    text = "Jajaran Dewan Direksi / Pimpinan Puncak (${result.peersInDepartment.size + 1} Orang)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(bottom = ClaySpacing.Md)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    result.peersInDepartment.forEach { peer ->
                        OrgNodeCard(
                            node = peer,
                            onClick = { onSelectNode(peer.id.value) }
                        )
                    }
                    OrgNodeCard(
                        node = result.focusNode,
                        isHighlighted = true,
                        badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT"
                    )
                }
            } else {
                OrgNodeCard(
                    node = result.focusNode,
                    isHighlighted = true,
                    badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT"
                )
            }

            // 2.3 Bawahan Langsung (Subordinates) di bawah Head
            if (result.subordinates.isNotEmpty()) {
                ConnectorVerticalLine()

                Text(
                    text = "Seluruh Staf di Bawah Pimpinan (${result.subordinates.size} Anggota)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Lg))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    result.subordinates.forEach { sub ->
                        OrgNodeCard(
                            node = sub,
                            onClick = { onSelectNode(sub.id.value) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectorVerticalLine() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = ClaySpacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .width(ClayBorder.Thick)
                .height(24.dp)
                .background(WeMadeColors.Outline)
        )
    }
}
