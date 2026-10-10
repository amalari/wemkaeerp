package com.eventverse.app.presentation.orgchart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayGuardedButton
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.workspace.badgeLabel
import com.eventverse.app.presentation.workspace.tint

/*
 * Header halaman Org Chart: judul, chip statistik, lencana wewenang, dan toolbar aksi.
 *
 * Disusun dengan flow (membungkus), bukan Row SpaceBetween: Row mengukur judul lebih dulu dengan lebar
 * tak terbatas sehingga toolbar kebagian sisa beberapa dp, tombolnya pecah per kata, dan header
 * membengkak ratusan dp (design-system-rules Kontrak 13).
 */
@Composable
internal fun OrgChartHeader(
    totalEmployees: Int?,
    totalDepartments: Int?,
    isResetMenuOpen: Boolean,
    accessLevel: AccessLevel,
    isDepartmentLocked: Boolean = false,
    lockedDepartmentName: String? = null,
    onToggleResetMenu: () -> Unit,
    onAddNewEmployee: () -> Unit,
    onAddNewDepartment: () -> Unit,
    onRestorePresets: () -> Unit,
    isLoadFailed: Boolean = false
) {
    val canWrite = accessLevel.isAtLeast(AccessLevel.OPERATE)

    ClayFlowRow(
        modifier = Modifier.fillMaxWidth(),
        spacing = ClaySpacing.Lg,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            ClayFlowRow(spacing = ClaySpacing.Md) {
                Text(
                    text = "Bagan Struktur Organisasi & Karyawan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayTag(
                    text = "T-Shape Dynamic Org",
                    tint = WeMadeColors.Primary,
                    fontSize = 11.sp
                )
                if (isDepartmentLocked && lockedDepartmentName != null) {
                    ClayTag(
                        text = "Divisi: $lockedDepartmentName",
                        tint = WeMadeColors.Warning,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
            Text(
                text = if (isDepartmentLocked && lockedDepartmentName != null) {
                    "Menampilkan bagan struktur khusus divisi $lockedDepartmentName sesuai batasan wewenang data Anda."
                } else {
                    "Kelola struktur pelaporan, atur divisi fleksibel sesuai kebutuhan pabrik."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        ClayFlowRow(spacing = ClaySpacing.Md) {
            if (totalEmployees != null) HeaderBadge(label = "Total Karyawan", value = "$totalEmployees Orang")
            if (totalDepartments != null) HeaderBadge(label = "Divisi Aktif", value = "$totalDepartments Divisi")
            ClayBadge(text = accessLevel.badgeLabel(), tint = accessLevel.tint(), dot = true)

            // Opsi Struktur, Divisi Baru, dan Tambah Karyawan hanya tampil untuk pengguna dengan wewenang tulis
            if (canWrite) {
                Box {
                    ClayGuardedButton(
                        text = "Opsi Struktur",
                        onClick = onToggleResetMenu,
                        enabled = accessLevel.isAtLeast(AccessLevel.MANAGE),
                        lockedHint = "Butuh wewenang ${AccessLevel.MANAGE.displayName}.",
                        style = ClayButtonStyle.Secondary,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        maxLines = 1
                    )

                    // Aksi menu menutup menunya dulu: dialog konfirmasi tidak boleh meninggalkan menu terbuka di belakangnya.
                    DropdownMenu(
                        expanded = isResetMenuOpen,
                        onDismissRequest = onToggleResetMenu
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Pulihkan Contoh yang Hilang", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.PrimaryDark)
                                    Text("Server menambah contoh yang belum ada", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                                }
                            },
                            onClick = { onToggleResetMenu(); onRestorePresets() }
                        )
                    }
                }

                ClayGuardedButton(
                    text = "+ Divisi Baru",
                    onClick = onAddNewDepartment,
                    enabled = accessLevel.isAtLeast(AccessLevel.OPERATE),
                    lockedHint = "Butuh wewenang ${AccessLevel.OPERATE.displayName}.",
                    style = ClayButtonStyle.Secondary,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1
                )

                if (!isLoadFailed) ClayGuardedButton(
                    text = "+ Tambah Karyawan",
                    onClick = onAddNewEmployee,
                    enabled = accessLevel.isAtLeast(AccessLevel.OPERATE),
                    lockedHint = "Butuh wewenang ${AccessLevel.OPERATE.displayName}.",
                    style = ClayButtonStyle.Primary,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeaderBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.PrimaryDark)
            Text(text = label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}
