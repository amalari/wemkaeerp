package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconSearch
import com.eventverse.app.presentation.designsystem.IconUser
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Selector Penanggung Jawab (PIC) ala Monday.com:
 * - Trigger: avatar inisial + nama PIC aktif, atau siluet "Tugaskan PIC" bila kosong.
 * - Popup: kolom pencarian yang otomatis fokus, opsi "Tanpa PIC", daftar karyawan
 *   yang terfilter real-time, dan ceklis pada PIC yang sedang ditugaskan.
 * - Pencarian dilakukan client-side: daftar karyawan tenant sudah dimuat penuh
 *   dari `GET /api/tenant/employees`, jadi tidak perlu endpoint pencarian baru.
 */
@Composable
internal fun PicAssigneeSelector(
    owner: OrgNode?,
    employees: List<OrgNode>,
    canWrite: Boolean,
    onAssign: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // Reset query & fokuskan kolom pencarian setiap kali popup dibuka (perilaku Monday.com).
    LaunchedEffect(expanded) {
        if (expanded) {
            query = ""
            runCatching { searchFocus.requestFocus() }
        }
    }

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "Penanggung Jawab (PIC)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(2.dp))

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = if (canWrite) Modifier.clickable { expanded = true } else Modifier
            ) {
                if (owner != null) {
                    Text(
                        text = owner.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clayFlat(
                                shape = CircleShape,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.OnSurfaceMuted,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconUser(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
                    }
                    Text(
                        text = "Tugaskan PIC",
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (canWrite) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ClayTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Cari nama karyawan",
                        leadingIcon = { IconSearch(modifier = Modifier.size(16.dp)) },
                        focusRequester = searchFocus,
                        modifier = Modifier
                            .width(248.dp)
                            .padding(
                                start = ClaySpacing.Md,
                                end = ClaySpacing.Md,
                                top = ClaySpacing.Sm,
                                bottom = ClaySpacing.Xs
                            )
                    )

                    val filtered = employees.filter { it.name.contains(query.trim(), ignoreCase = true) }

                    DropdownMenuItem(
                        leadingIcon = { IconBan(Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted) },
                        text = { Text("Tanpa PIC", fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onAssign(null)
                        }
                    )
                    filtered.forEach { emp ->
                        DropdownMenuItem(
                            leadingIcon = { PicAvatarCircle(initials = getAuthorInitials(emp.name), size = 20.dp) },
                            text = { Text(emp.name, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            trailingIcon = if (owner?.id == emp.id) {
                                { IconCheck(Modifier.size(14.dp), color = WeMadeColors.Success) }
                            } else null,
                            onClick = {
                                expanded = false
                                onAssign(emp.id.value)
                            }
                        )
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = "Tidak ada karyawan yang cocok",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                        )
                    }
                }
            }
        }
    }
}

/** Lingkaran avatar inisial karyawan dengan outline clay. */
@Composable
private fun PicAvatarCircle(initials: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clayFlat(
                shape = CircleShape,
                background = WeMadeColors.Primary,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Hairline
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Surface
        )
    }
}
