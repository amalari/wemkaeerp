package com.eventverse.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserSession
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.IconHelp
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconMenu
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Bilah atas aplikasi (Top Navigation Bar) WeMade ERP.
 *
 * Responsif terhadap lebar layar:
 * - Pada layar mobile ([isCompact] = true): Hanya menampilkan tombol drawer, judul layar aktif
 *   secara ringkas, dan dropdown profil pengguna agar tidak terjadi horizontal overflow.
 * - Pada layar desktop ([isCompact] = false): Menampilkan branding lengkap, Persona Switcher,
 *   Company Switcher (platform superadmin), modul entitlement trigger, dan profil pengguna.
 */
@Composable
fun AppTopBar(
    currentScreen: AppNavScreen,
    /** Judul pengganti — nama modul untuk rute generik `/m/{code}` (B6f). */
    title: String? = null,
    onOpenDrawer: () -> Unit,
    isAuthenticated: Boolean,
    session: UserSession?,
    activePersona: TestingPersona?,
    policyEmployees: List<OrgNode>,
    policyDepartments: List<Department>,
    policyRoles: List<CustomRole>,
    auditView: Boolean,
    isCompact: Boolean,
    onAuditViewChange: (Boolean) -> Unit,
    onApplyPersona: (TestingPersona) -> Unit,
    onResetSuperadmin: () -> Unit,
    onSelectCompany: (CompanyTenantProfile) -> Unit,
    onOpenTenantEntitlements: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** Membuka daftar panduan (TRD-HELP-001 FR-4); `null` = tombol disembunyikan. */
    onOpenHelp: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WeMadeColors.Surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCompact) 12.dp else 20.dp,
                    vertical = 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sisi Kiri: Tombol Hamburger Drawer & Judul Layar
            Row(
                modifier = if (isCompact) Modifier.weight(1f, fill = false) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClayIconButton(
                    onClick = onOpenDrawer,
                    shape = ClayShapes.Tile,
                    size = 34.dp,
                    containerColor = WeMadeColors.SurfaceMuted
                ) {
                    IconMenu(
                        modifier = Modifier.size(15.dp),
                        color = WeMadeColors.OnSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (!isCompact) {
                    Text(
                        text = "WeMade ERP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = WeMadeColors.Primary
                    )
                    Text(
                        text = "•",
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Text(
                    text = title ?: currentScreen.title,
                    fontSize = if (isCompact) 14.sp else 12.sp,
                    fontWeight = if (isCompact) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sisi Kanan: Kontrol Pengguna & Switcher
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAuthenticated && session != null) {
                    // Di layar desktop: Switcher persona lengkap
                    if (!isCompact) {
                        PersonaSwitcherDropdown(
                            activePersona = activePersona,
                            employees = policyEmployees,
                            departments = policyDepartments,
                            roles = policyRoles,
                            tenantId = session.user.tenantId ?: TenantId("ten-demo-001"),
                            tenantSlug = session.tenantSlug ?: "wemade-demo",
                            isAuditViewEnabled = auditView,
                            onAuditViewChange = onAuditViewChange,
                            onApplyPersona = onApplyPersona,
                            onResetSuperadmin = onResetSuperadmin
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .height(24.dp)
                                .width(1.dp)
                                .background(WeMadeColors.Border)
                        )

                        if (session.user.role == Role.PLATFORM_SUPERADMIN) {
                            CompanySwitcherDropdown(
                                currentSlug = session.tenantSlug ?: "wemade-demo",
                                onSelectCompany = onSelectCompany
                            )

                            ClayIconButton(
                                onClick = onOpenTenantEntitlements,
                                shape = ClayShapes.Tile
                            ) {
                                IconLayers(
                                    modifier = Modifier.size(16.dp),
                                    color = WeMadeColors.OnSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(WeMadeColors.Border)
                            )
                        }
                    }

                    onOpenHelp?.let { open ->
                        ClayIconButton(onClick = open, shape = ClayShapes.Tile, modifier = Modifier.padding(end = 10.dp)) {
                            IconHelp(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurface)
                        }
                    }

                    // Avatar & Profil Pengguna (selalu ada di desktop maupun mobile)
                    ProfileDropdown(
                        session = session,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}
