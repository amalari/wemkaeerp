package com.eventverse.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.infrastructure.navigation.PlatformNavigation
import com.eventverse.app.presentation.auth.AuthViewModel
import com.eventverse.app.presentation.auth.LoginScreen
import com.eventverse.app.presentation.auth.LoginUiEffect
import com.eventverse.app.presentation.auth.LoginUiEvent
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayNavDrawer
import com.eventverse.app.presentation.designsystem.ClayNavItem
import com.eventverse.app.presentation.designsystem.ClayNavSection
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconLock
import com.eventverse.app.presentation.designsystem.IconMenu
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.module.ModuleIcon
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.PersonaSwitcherDropdown
import com.eventverse.app.presentation.navigation.buildNavMenu
import com.eventverse.app.presentation.navigation.ProfileDropdown
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.workspace.ModuleWorkspaceScreen
import com.eventverse.app.presentation.workspace.tint
import com.eventverse.app.presentation.orgchart.OrgChartScreen
import com.eventverse.app.presentation.pipeline.FactoryFlowScreen
import com.eventverse.app.presentation.rbac.DynamicRbacScreen
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.theme.WeMadeTheme

@Composable
fun App() {
    val authViewModel = remember { AuthViewModel() }
    val authState by authViewModel.uiState.collectAsState()
    val session = authState.authenticatedSession
    val isAuthenticated = session != null

    val policyRepository = remember { RbacAccessPolicyRepository.shared }
    val activePersona by policyRepository.activePersona.collectAsState()
    val effectivePermissions by policyRepository.effectivePermissions.collectAsState()
    val accessDecisions by policyRepository.accessDecisions.collectAsState()
    val auditView by policyRepository.isAuditViewEnabled.collectAsState()
    val policyRoles by policyRepository.roles.collectAsState()
    val policyDepartments by policyRepository.departments.collectAsState()
    val policyEmployees by policyRepository.employees.collectAsState()

    // Determine initial screen from browser URL or session status
    val initialPath = remember { PlatformNavigation.getCurrentPath() }
    val initialScreen = remember {
        val matched = AppNavScreen.fromPath(initialPath)
        when {
            matched != null -> matched
            isAuthenticated -> AppNavScreen.ORG_CHART
            else -> AppNavScreen.LOGIN
        }
    }

    var currentScreen by remember { mutableStateOf(initialScreen) }
    var pendingRedirectScreen by remember { mutableStateOf<AppNavScreen?>(null) }

    // Centralized navigation action with browser history push
    val navigateTo: (AppNavScreen) -> Unit = remember {
        { target ->
            if (currentScreen != target) {
                currentScreen = target
                PlatformNavigation.pushPath(target.route)
            }
        }
    }

    // Synchronize initial URL and listen to browser Back/Forward (popstate/hashchange)
    LaunchedEffect(Unit) {
        val current = PlatformNavigation.getCurrentPath()
        if (AppNavScreen.fromPath(current) == null) {
            PlatformNavigation.replacePath(currentScreen.route)
        }

        PlatformNavigation.listenToPathChanges { newPath ->
            val matched = AppNavScreen.fromPath(newPath)
            if (matched != null && matched != currentScreen) {
                currentScreen = matched
            }
        }
    }

    // Auto-navigate when login succeeds: redirect to pending screen or default to ORG_CHART
    LaunchedEffect(authViewModel) {
        authViewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) {
                val destination = pendingRedirectScreen ?: AppNavScreen.ORG_CHART
                pendingRedirectScreen = null
                navigateTo(destination)
            }
        }
    }

    // If logged out while viewing protected screen, redirect back to LOGIN
    var wasAuthenticated by remember { mutableStateOf(isAuthenticated) }
    LaunchedEffect(isAuthenticated) {
        if (wasAuthenticated && !isAuthenticated && currentScreen.isProtected) {
            navigateTo(AppNavScreen.LOGIN)
        }
        wasAuthenticated = isAuthenticated
    }

    var drawerOpen by remember { mutableStateOf(false) }

    // Memilih item menutup drawer-nya, seperti panel produk Google Cloud Console.
    val openScreen: (AppNavScreen) -> Unit = { target ->
        navigateTo(target)
        drawerOpen = false
    }

    // Penyusunan menu — modul mana yang muncul, di seksi kategori mana, dengan badge wewenang apa —
    // adalah keputusan wewenang, jadi ia hidup sebagai fungsi murni yang bisa diuji tanpa merender
    // apa pun (lihat NavMenu.kt). Yang tersisa di sini hanyalah penerjemahannya ke bahasa clay.
    val isImpersonating = activePersona?.isOwnerOrSuperAdmin == false
    val menuSections = remember(effectivePermissions, auditView, isImpersonating) {
        buildNavMenu(
            permissions = effectivePermissions,
            auditView = auditView,
            isImpersonating = isImpersonating
        )
    }

    val navSections = menuSections.map { section ->
        ClayNavSection(
            title = section.title,
            items = section.entries.map { entry ->
                val module = entry.screen.businessModule
                ClayNavItem(
                    key = entry.screen.route,
                    label = entry.screen.title,
                    selected = currentScreen == entry.screen,
                    onClick = { openScreen(entry.screen) },
                    icon = { tint ->
                        when {
                            entry.locked || !isAuthenticated ->
                                IconLock(modifier = Modifier.fillMaxSize(), color = tint)
                            module != null ->
                                ModuleIcon(
                                    iconKey = module.iconKey,
                                    modifier = Modifier.fillMaxSize(),
                                    color = tint
                                )
                            else -> AdminScreenIcon(screen = entry.screen, tint = tint)
                        }
                    },
                    badge = entry.badge,
                    badgeTint = entry.accessLevel?.tint() ?: WeMadeColors.OnSurfaceDisabled,
                    enabled = !entry.locked
                )
            }
        )
    }

    WeMadeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar: Menu Trigger, Brand, Tenant Switcher & User Profile
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = WeMadeColors.Surface,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hamburger Menu Trigger & Brand
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClayIconButton(
                                onClick = { drawerOpen = true },
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
                            Text(
                                text = currentScreen.title,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        if (isAuthenticated && session != null) {
                            PersonaSwitcherDropdown(
                                activePersona = activePersona,
                                employees = policyEmployees,
                                departments = policyDepartments,
                                roles = policyRoles,
                                tenantId = session.user.tenantId ?: TenantId("ten-demo-001"),
                                tenantSlug = session.tenantSlug ?: "wemade-demo",
                                isAuditViewEnabled = auditView,
                                onAuditViewChange = { policyRepository.setAuditView(it) },
                                onApplyPersona = { authViewModel.switchPersona(it) },
                                onResetSuperadmin = {
                                    authViewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(WeMadeColors.Border)
                            )

                            // GCP-Style Company Switcher Dropdown, for platform superadmins
                            // only: the server authorises acting on another tenant purely by
                            // role, so offering it to a tenant-bound account would just
                            // produce 403s on every request after the switch.
                            if (session.user.role == Role.PLATFORM_SUPERADMIN) {
                                com.eventverse.app.presentation.navigation.CompanySwitcherDropdown(
                                    currentSlug = session.tenantSlug ?: "wemade-demo",
                                    onSelectCompany = { company ->
                                        authViewModel.switchTenant(company)
                                    }
                                )

                                // Divider
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp)
                                        .height(24.dp)
                                        .width(1.dp)
                                        .background(WeMadeColors.Border)
                                )
                            }

                            // User Profile Dropdown (info akun & aksi logout)
                            ProfileDropdown(
                                session = session,
                                onLogout = {
                                    authViewModel.onEvent(LoginUiEvent.Logout)
                                    navigateTo(AppNavScreen.LOGIN)
                                }
                            )
                        }
                        }
                    }
                }

                // Screen Content Area with Auth Guard
                Crossfade(targetState = currentScreen, modifier = Modifier.weight(1f)) { screen ->
                    when (screen) {
                        AppNavScreen.ORG_CHART -> {
                            if (session != null) {
                                OrgChartScreen(tenantSlug = session.tenantSlug ?: "wemade-demo")
                            } else {
                                AuthGuardCard(
                                    targetModuleName = "Bagan Struktur Organisasi & Karyawan",
                                    onLoginClick = {
                                        pendingRedirectScreen = AppNavScreen.ORG_CHART
                                        navigateTo(AppNavScreen.LOGIN)
                                    }
                                )
                            }
                        }
                        AppNavScreen.DYNAMIC_RBAC -> {
                            if (isAuthenticated) {
                                DynamicRbacScreen(
                                    onBackToLogin = { navigateTo(AppNavScreen.LOGIN) }
                                )
                            } else {
                                AuthGuardCard(
                                    targetModuleName = "Manajemen Hak Akses & Matriks RBAC",
                                    onLoginClick = {
                                        pendingRedirectScreen = AppNavScreen.DYNAMIC_RBAC
                                        navigateTo(AppNavScreen.LOGIN)
                                    }
                                )
                            }
                        }
                        AppNavScreen.FACTORY_FLOW -> {
                            if (isAuthenticated) {
                                FactoryFlowScreen(tenantSlug = session?.tenantSlug ?: "wemade-demo")
                            } else {
                                AuthGuardCard(
                                    targetModuleName = "Alur Operasional & Monitoring Pabrik (Live Pipeline)",
                                    onLoginClick = {
                                        pendingRedirectScreen = AppNavScreen.FACTORY_FLOW
                                        navigateTo(AppNavScreen.LOGIN)
                                    }
                                )
                            }
                        }
                        // Sembilan modul operasional berbagi satu layar kerja. Gerbangnya ganda:
                        // sesi dulu (AuthGuardCard), baru wewenang (AccessDeniedCard di dalam
                        // ModuleWorkspaceScreen) — belum login dan tidak berwenang adalah dua
                        // keadaan berbeda dan pantas memberi pesan yang berbeda.
                        AppNavScreen.CRM_SALES,
                        AppNavScreen.SAMPLING_ORDER,
                        AppNavScreen.INVENTORY,
                        AppNavScreen.TECH_PACK_BOM,
                        AppNavScreen.COSTING_HPP,
                        AppNavScreen.PRODUCTION_MRP,
                        AppNavScreen.OPERATOR_EXEC,
                        AppNavScreen.QUALITY_CONTROL,
                        AppNavScreen.FULFILLMENT -> {
                            val module = screen.businessModule
                            if (isAuthenticated && module != null) {
                                ModuleWorkspaceScreen(
                                    module = module,
                                    decision = accessDecisions[module] ?: AccessDecision(
                                        config = ModuleAccessConfig(),
                                        source = AccessSource.NONE,
                                        fromRole = ModuleAccessConfig(),
                                        fromDepartment = ModuleAccessConfig()
                                    ),
                                    persona = activePersona
                                )
                            } else {
                                AuthGuardCard(
                                    targetModuleName = screen.title,
                                    onLoginClick = {
                                        pendingRedirectScreen = screen
                                        navigateTo(AppNavScreen.LOGIN)
                                    }
                                )
                            }
                        }
                        AppNavScreen.LOGIN -> {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToDashboard = {
                                    val destination = pendingRedirectScreen ?: AppNavScreen.ORG_CHART
                                    pendingRedirectScreen = null
                                    navigateTo(destination)
                                }
                            )
                        }
                    }
                }
            }

            // Google Cloud Console–style product panel: floats over the content with a scrim,
            // closes on ✕, on scrim click, or once a destination is chosen.
            ClayNavDrawer(
                open = drawerOpen,
                onDismiss = { drawerOpen = false },
                title = "WeMade ERP",
                subtitle = "Multi-Tenant Garment Platform",
                sections = navSections,
                footer = if (!isAuthenticated) {
                    {
                        ClayButton(
                            text = "Login Akun",
                            onClick = { openScreen(AppNavScreen.LOGIN) },
                            modifier = Modifier.fillMaxWidth(),
                            style = ClayButtonStyle.Primary
                        )
                    }
                } else null
            )
        }
    }
}

/**
 * Visual barrier presented by the Auth Guard whenever an unauthenticated
 * user tries to access protected factory ERP modules.
 */
@Composable
private fun AuthGuardCard(
    targetModuleName: String,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // Dicicil dari daftar utang §8 sekalian menyentuh file ini: Card/Button Material mentah
        // dan dua literal amber diganti katalog clay + turunan token.
        ClayCard(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(ClaySpacing.Xxl),
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Warning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconLock(modifier = Modifier.size(32.dp), color = WeMadeColors.Warning)
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Xl))

                Text(
                    text = "Akses Terbatas: Autentikasi Diperlukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                Text(
                    text = "Modul \"$targetModuleName\" dilindungi oleh sistem keamanan multi-tenant pabrik. Silakan masuk menggunakan akun perusahaan Anda untuk melanjutkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Xxl))

                ClayButton(
                    // Tanpa panah "→": Fredoka/Nunito yang dibundel tidak punya glyph U+2192,
                    // jadi ia ter-render sebagai kotak tofu begitu tombolnya memakai font clay.
                    text = "Masuk ke Akun Sekarang",
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                    style = ClayButtonStyle.Primary,
                    contentPadding = PaddingValues(horizontal = ClaySpacing.Xl, vertical = ClaySpacing.Lg)
                )
            }
        }
    }
}

/**
 * Ikon untuk layar tata kelola, yang tidak punya `BusinessModule` sehingga tidak bisa memakai
 * `ModuleIcon`. Jumlahnya tetap tiga; kalau nanti bertambah, pertimbangkan memberi `AppNavScreen`
 * sebuah `iconKey` sendiri seperti yang sudah dipunyai `BusinessModule`.
 */
@Composable
private fun AdminScreenIcon(screen: AppNavScreen, tint: Color) {
    when (screen) {
        AppNavScreen.DYNAMIC_RBAC -> IconShield(modifier = Modifier.fillMaxSize(), color = tint)
        AppNavScreen.FACTORY_FLOW -> IconZap(modifier = Modifier.fillMaxSize(), color = tint)
        else -> IconLayers(modifier = Modifier.fillMaxSize(), color = tint)
    }
}