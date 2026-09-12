package com.eventverse.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.auth.UserSession
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pill profil pengguna dengan avatar di top bar.
 *
 * Menggantikan tombol logout terpisah di top bar dan sidebar;
 * saat diklik, membuka dropdown menu dengan informasi akun dan tombol Keluar (Logout).
 */
@Composable
fun ProfileDropdown(
    session: UserSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Pill profil di top bar
        ClayActionSurface(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
        ) {
            val initial = session.user.username.value.take(2).uppercase().ifBlank { "WM" }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WeMadeColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = session.user.username.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.user.role.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconChevronDown(
                modifier = Modifier.size(10.dp),
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Dropdown menu profil
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(260.dp)
                .background(WeMadeColors.Surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header profil pengguna
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    val initial = session.user.username.value.take(2).uppercase().ifBlank { "WM" }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = session.user.username.value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.user.email.value,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ClayTag(
                    text = session.user.role.name,
                    tint = WeMadeColors.Primary
                )

                HorizontalDivider(
                    thickness = ClayBorder.Medium,
                    color = WeMadeColors.Border
                )

                // Tombol Logout di dalam dropdown profil
                ClayButton(
                    text = "Keluar (Logout)",
                    onClick = {
                        expanded = false
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = ClayButtonStyle.Danger,
                    fontSize = 12.sp
                )
            }
        }
    }
}
