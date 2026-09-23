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
import com.eventverse.app.presentation.fulfillment.FulfillmentWorkspaceScreen
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
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.module.ModuleIcon
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.AppTopBar
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.navigation.PersonaSwitcherDropdown
import com.eventverse.app.presentation.navigation.buildNavMenu
import com.eventverse.app.presentation.navigation.firstAccessibleScreen
import com.eventverse.app.presentation.navigation.ProfileDropdown
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.workspace.GovernanceModuleGate
import com.eventverse.app.presentation.workspace.ModuleWorkspaceScreen
import com.eventverse.app.presentation.workspace.tint
import com.eventverse.app.presentation.orgchart.OrgChartScreen
import com.eventverse.app.presentation.pipeline.FactoryFlowScreen
import com.eventverse.app.presentation.rbac.DynamicRbacScreen
import com.eventverse.app.presentation.tenant.TenantModuleEntitlementDialog
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

    /**
     * True selama kita masih menunggu wewenang tiba untuk memutuskan layar pendaratan.
     *
     * Sebelum ketiga layar tata kelola menjadi modul, tujuan setelah login boleh berupa konstanta
     * `ORG_CHART` karena layar itu selalu terbuka untuk semua orang. Kini ia bisa tertutup — bagi
     * operator jahit, atau bagi tenant yang modulnya diputus — sehingga tujuannya harus dihitung.
     * Dan karena wewenang datang dari jaringan setelah login, keputusannya harus ditunda sampai
     * data itu ada; memutuskan lebih awal akan mendaratkan orang di "akses ditolak" lalu melompat
     * lagi sesaat kemudian.
     */
    var awaitingLandingScreen by remember { mutableStateOf(false) }

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

    // Auto-navigate when login succeeds: redirect to the screen the user was trying to reach,
    // otherwise wait for permissions and land on the first screen they may actually open.
    LaunchedEffect(authViewModel) {
        authViewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) {
                val destination = pendingRedirectScreen
                pendingRedirectScreen = null
                if (destination != null) {
                    navigateTo(destination)
                } else {
                    awaitingLandingScreen = true
                }
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

    /** Dialog penyambungan modul per tenant; hanya dapat dibuka platform superadmin. */
    var showTenantEntitlementDialog by remember { mutableStateOf(false) }

    // Memilih item menutup drawer-nya, seperti panel produk Google Cloud Console.
    val openScreen: (AppNavScreen) -> Unit = { target ->
        navigateTo(target)
        drawerOpen = false
    }

    // Penyusunan menu — modul mana yang muncul, di seksi kategori mana, dengan badge wewenang apa —
    // adalah keputusan wewenang, jadi ia hidup sebagai fungsi murni yang bisa diuji tanpa merender
    // apa pun (lihat NavMenu.kt). Yang tersisa di sini hanyalah penerjemahannya ke bahasa clay.
    val menuSections = remember(effectivePermissions, auditView) {
        buildNavMenu(permissions = effectivePermissions, auditView = auditView)
    }

    // Menyelesaikan pendaratan pasca-login begitu menu benar-benar tersusun. Bila ternyata tidak
    // ada satu pun modul terbuka — tenant yang seluruh modulnya diputus, misalnya — kita tetap
    // mendarat di Bagan Organisasi supaya kartu penjelasannya yang muncul, bukan layar kosong.
    LaunchedEffect(awaitingLandingScreen, menuSections) {
        if (awaitingLandingScreen && menuSections.isNotEmpty()) {
            awaitingLandingScreen = false
            navigateTo(firstAccessibleScreen(menuSections) ?: AppNavScreen.ORG_CHART)
        }
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
                            // Setiap baris menu kini berasal dari sebuah modul, jadi cabang ini
                            // hanya tersisa sebagai jaring pengaman bila suatu saat ada layar tanpa
                            // modul yang ikut masuk daftar.
                            else -> IconLayers(modifier = Modifier.fillMaxSize(), color = tint)
                        }
                    },
                    badge = entry.badge,
                    badgeTint = entry.accessLevel?.tint() ?: WeMadeColors.OnSurfaceDisabled,
                    enabled = !entry.locked
                )
            }
        )
    }

    CompositionLocalProvider(
        LocalAppNavigator provides navigateTo
    ) {
        WeMadeTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < ClayBreakpoints.MasterDetail

            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(
                    currentScreen = currentScreen,
                    onOpenDrawer = { drawerOpen = true },
                    isAuthenticated = isAuthenticated,
                    session = session,
                    activePersona = activePersona,
                    policyEmployees = policyEmployees,
                    policyDepartments = policyDepartments,
                    policyRoles = policyRoles,
                    auditView = auditView,
                    isCompact = isCompact,
                    onAuditViewChange = { policyRepository.setAuditView(it) },
                    onApplyPersona = { authViewModel.switchPersona(it) },
                    onResetSuperadmin = {
                        authViewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
                    },
                    onSelectCompany = { company ->
                        authViewModel.switchTenant(company)
                    },
                    onOpenTenantEntitlements = { showTenantEntitlementDialog = true },
                    onLogout = {
                        authViewModel.onEvent(LoginUiEvent.Logout)
                        navigateTo(AppNavScreen.LOGIN)
                    }
                )

                // Screen Content Area with Auth Guard
                Crossfade(targetState = currentScreen, modifier = Modifier.weight(1f)) { screen ->
                    when (screen) {
                        // Ketiga layar tata kelola kini melewati gerbang yang sama dengan sembilan
                        // modul operasional: sesi, lalu entitlement tenant, lalu wewenang jabatan.
                        // Sebelumnya hanya sesi yang diperiksa, sehingga siapa pun yang bisa login
                        // melihat ketiganya dengan hak penuh.
                        AppNavScreen.ORG_CHART -> {
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                decision = accessDecisions[BusinessModule.ORG_CHART],
                                persona = activePersona,
                                tenantName = session?.tenantSlug ?: "pabrik ini",
                                authGuard = {
                                    AuthGuardCard(
                                        targetModuleName = "Bagan Struktur Organisasi & Karyawan",
                                        onLoginClick = {
                                            pendingRedirectScreen = screen
                                            navigateTo(AppNavScreen.LOGIN)
                                        }
                                    )
                                }
                            ) { access ->
                                OrgChartScreen(
                                    tenantSlug = session?.tenantSlug ?: "wemade-demo",
                                    access = access,
                                    viewerDepartmentId = activePersona?.departmentId ?: session?.user?.departmentId,
                                    viewerEmployeeId = activePersona?.sourceEmployeeId?.value ?: session?.user?.id?.value
                                )
                            }
                        }
                        AppNavScreen.DYNAMIC_RBAC -> {
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                decision = accessDecisions[BusinessModule.DYNAMIC_RBAC],
                                persona = activePersona,
                                tenantName = session?.tenantSlug ?: "pabrik ini",
                                authGuard = {
                                    AuthGuardCard(
                                        targetModuleName = "Manajemen Hak Akses & Matriks RBAC",
                                        onLoginClick = {
                                            pendingRedirectScreen = screen
                                            navigateTo(AppNavScreen.LOGIN)
                                        }
                                    )
                                }
                            ) { access ->
                                DynamicRbacScreen(
                                    onBackToLogin = { navigateTo(AppNavScreen.LOGIN) },
                                    access = access
                                )
                            }
                        }
                        AppNavScreen.FACTORY_FLOW -> {
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                decision = accessDecisions[BusinessModule.FACTORY_FLOW],
                                persona = activePersona,
                                tenantName = session?.tenantSlug ?: "pabrik ini",
                                authGuard = {
                                    AuthGuardCard(
                                        targetModuleName = "Alur Operasional & Monitoring Pabrik (Live Pipeline)",
                                        onLoginClick = {
                                            pendingRedirectScreen = screen
                                            navigateTo(AppNavScreen.LOGIN)
                                        }
                                    )
                                }
                            ) { access ->
                                FactoryFlowScreen(
                                    tenantSlug = session?.tenantSlug ?: "wemade-demo",
                                    access = access
                                )
                            }
                        }
                        // Sembilan modul operasional berbagi satu layar kerja. Gerbangnya ganda:
                        // sesi dulu (AuthGuardCard), baru wewenang (AccessDeniedCard di dalam
                        // ModuleWorkspaceScreen) — belum login dan tidak berwenang adalah dua
                        // keadaan berbeda dan pantas memberi pesan yang berbeda.
                        AppNavScreen.TRACEABILITY -> {
                            if (isAuthenticated) {
                                com.eventverse.app.presentation.traceability.TraceabilityWorkspaceScreen(
                                    tenantSlug = session?.tenantSlug ?: "wemade-demo"
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
                        // FULFILLMENT punya layar kerjanya sendiri (kurir antar karung),
                        // tapi gerbangnya tetap ganda seperti modul lain: sesi dulu, baru wewenang.
                        AppNavScreen.FULFILLMENT -> {
                            val module = screen.businessModule
                            if (isAuthenticated && module != null) {
                                FulfillmentWorkspaceScreen(
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
                        AppNavScreen.SURAT_JALAN -> {
                            if (isAuthenticated) {
                                com.eventverse.app.presentation.transfer.SuratJalanWorkspaceScreen()
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
                        AppNavScreen.CRM_SALES,
                        AppNavScreen.SAMPLING_ORDER,
                        AppNavScreen.MASTER_DATA,
                        AppNavScreen.VENDOR_CONTACTS,
                        AppNavScreen.INVENTORY,
                        AppNavScreen.TECH_PACK_BOM,
                        AppNavScreen.COSTING_HPP,
                        AppNavScreen.PRODUCTION_MRP,
                        AppNavScreen.OPERATOR_EXEC,
                        AppNavScreen.QUALITY_CONTROL,
                        AppNavScreen.INVOICING,
                        AppNavScreen.INVOICING_TEMPLATES -> {
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
                                    val destination = pendingRedirectScreen
                                    pendingRedirectScreen = null
                                    if (destination != null) {
                                        navigateTo(destination)
                                    } else {
                                        awaitingLandingScreen = true
                                    }
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

            // Dialog penyambungan modul per tenant.
            //
            // Syarat perannya diulang di sini, bukan hanya di tombol pembukanya: state boolean bisa
            // tertinggal menyala saat pengguna berpindah akun, dan rute `/api/admin/**` di server
            // tetap menolak pemanggil non-superadmin apa pun yang terjadi di layar.
            if (showTenantEntitlementDialog && session?.user?.role == Role.PLATFORM_SUPERADMIN) {
                TenantModuleEntitlementDialog(
                    tenantSlug = session.tenantSlug ?: "wemade-demo",
                    onDismiss = { showTenantEntitlementDialog = false },
                    onSaved = { granted ->
                        // Drawer ikut berubah tanpa memuat ulang halaman: entitlement adalah salah
                        // satu masukan keputusan wewenang, bukan data yang berdiri sendiri.
                        policyRepository.syncGrantedModules(granted)
                    }
                )
            }
        }
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

