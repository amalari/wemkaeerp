package com.eventverse.app.presentation.orgchart.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.TShapeHierarchyResult
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ─── 1. TOP LAYER: 1 TINGKAT KE ATAS (SUPERIOR) ───
        Text(
            text = "Atasan Langsung (1 Tingkat ke Atas / Jalur Approval)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(modifier = Modifier.height(8.dp))

        val superior = result.superior
        if (superior != null) {
            OrgNodeCard(
                node = superior,
                badgeLabel = "ATASAN LANGSUNG",
                onClick = { onSelectNode(superior.id.value) }
            )
        } else {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Text(
                    text = "Posisi Puncak Pabrik (Tidak Memiliki Atasan)",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
            Spacer(modifier = Modifier.height(10.dp))

            // Seluruh staf (focus node + rekan sejajar) ditampilkan berdampingan (SEJAJAR)
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tampilkan rekan-rekan yang sudah ada
                result.peersInDepartment.forEach { peer ->
                    OrgNodeCard(
                        node = peer,
                        onClick = { onSelectNode(peer.id.value) },
                        modifier = Modifier.width(220.dp)
                    )
                }

                // Posisi fokus (misal karyawan yang baru dibuat atau diedit)
                OrgNodeCard(
                    node = result.focusNode,
                    isHighlighted = true,
                    badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT",
                    modifier = Modifier.width(240.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Text(
                    text = "Posisi Staf Pelaksana adalah level operasional (tidak membawahi karyawan lain)",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        } else {
            // ─── 2. KASUS B: FOCUS NODE ADALAH HEAD ATAU DIREKSI ───
            // 2.1 Rekan Sejajar Antar Divisi (Peer Heads)
            if (result.peerHeads.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rekan Sejajar (Kepala Divisi Lain):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    result.peerHeads.forEach { peer ->
                        val peerDeptColor = Color(peer.department?.colorHex ?: 0xFF6366F1)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(peerDeptColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${peer.department?.shortName ?: "Direksi"}: ${peer.name}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = peerDeptColor
                            )
                        }
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
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    result.peersInDepartment.forEach { peer ->
                        OrgNodeCard(
                            node = peer,
                            onClick = { onSelectNode(peer.id.value) },
                            modifier = Modifier.width(240.dp)
                        )
                    }
                    OrgNodeCard(
                        node = result.focusNode,
                        isHighlighted = true,
                        badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT",
                        modifier = Modifier.width(240.dp)
                    )
                }
            } else {
                OrgNodeCard(
                    node = result.focusNode,
                    isHighlighted = true,
                    badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT",
                    modifier = Modifier.width(240.dp)
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
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(Color(0xFFCBD5E1))
        )
    }
}
