package com.eventverse.app

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

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
import com.eventverse.app.presentation.auth.PlatformLoginScreen
import com.eventverse.app.presentation.platform.PlatformAdminConsole
import com.eventverse.app.domain.tenant.HostSurface
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
import com.eventverse.app.presentation.designsystem.IconEdit
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconLock
import com.eventverse.app.presentation.designsystem.IconMenu
import com.eventverse.app.presentation.discovery.DiscoveryWizardScreen
import com.eventverse.app.presentation.discovery.studio.DemandLedgerScreen
import com.eventverse.app.presentation.discovery.studio.PrototypeStudioScreen
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.module.ModuleIcon
import com.eventverse.app.presentation.builder.BuilderShell
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.tutorial.LocalTutorialAnchors
import com.eventverse.app.presentation.tutorial.TutorialLayer
import com.eventverse.app.presentation.tutorial.rememberTutorialUiState
import com.eventverse.app.presentation.tutorial.tutorialScreenFor
import com.eventverse.app.presentation.navigation.AppTopBar
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.navigation.PersonaSwitcherDropdown
import com.eventverse.app.presentation.navigation.AuthGuardCard
import com.eventverse.app.presentation.navigation.GenericModuleRoute
import com.eventverse.app.presentation.navigation.moduleFromGenericPath
import com.eventverse.app.presentation.navigation.studioDrawerSection
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
    var modulePath by remember { mutableStateOf(initialPath) } // B6f: path `/m/{code}` untuk AppNavScreen.MODULE

    // WeMake Builder (PLAN-builder-console M0): `/builder` adalah konsol project dengan shell
    // sendiri — satu cabang delegasi dari App, bukan layar AppNavScreen.
    var shellPath by remember { mutableStateOf(initialPath) } // `/builder` & `/admin` (M3b) punya shell sendiri
    val builderRoute = shellPath.startsWith("/builder")
    val adminRoute = shellPath.startsWith("/admin")
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
        if (AppNavScreen.fromPath(current) == null && !current.startsWith("/builder") && !current.startsWith("/admin")) {
            PlatformNavigation.replacePath(currentScreen.route)
        }

        PlatformNavigation.listenToPathChanges { newPath ->
            val matched = AppNavScreen.fromPath(newPath)
            shellPath = newPath
            if (matched == AppNavScreen.MODULE) modulePath = newPath
            if (matched != null && matched != currentScreen) {
                currentScreen = matched
            }
        }
    }

    // Superadmin di `app.` → konsol platform (M3b); handoff ke /builder → tetap di Builder; selain itu modul pertama.
    fun landAfterLogin(role: Role) = when {
        role == Role.PLATFORM_SUPERADMIN && authViewModel.uiState.value.hostSurface is HostSurface.Platform -> { shellPath = "/admin"; PlatformNavigation.pushPath("/admin") }
        shellPath.startsWith("/builder") -> Unit
        else -> awaitingLandingScreen = true
    }

    fun finishLogin(role: Role) {
        val destination = pendingRedirectScreen
        pendingRedirectScreen = null
        if (destination != null) navigateTo(destination) else landAfterLogin(role)
    }

    // Auto-navigate when login succeeds: redirect to the screen the user was trying to reach,
    // otherwise wait for permissions and land on the first screen they may actually open.
    LaunchedEffect(authViewModel) {
        authViewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) finishLogin(effect.session.user.role)
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
    val tutorials = rememberTutorialUiState() // TRD-HELP-001: coach mark + daftar panduan

    /** Dialog penyambungan modul per tenant; hanya dapat dibuka platform superadmin. */
    var showTenantEntitlementDialog by remember { mutableStateOf(false) }

    // Memilih item menutup drawer-nya, seperti panel produk Google Cloud Console.
    val openScreen: (AppNavScreen) -> Unit = { target ->
        shellPath = target.route
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
                val module = entry.module
                val isGeneric = entry.screen == AppNavScreen.MODULE
                ClayNavItem(
                    key = entry.route,
                    label = entry.title,
                    selected = currentScreen == entry.screen && (!isGeneric || modulePath == entry.route),
                    onClick = {
                        if (isGeneric) { modulePath = entry.route; currentScreen = entry.screen; PlatformNavigation.pushPath(entry.route); drawerOpen = false }
                        else openScreen(entry.screen)
                    },
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

    // Studio Discovery (R16/Fase D) dan Studio Pola Prototipe (Fase C) bukan modul, jadi tidak lewat
    // buildNavMenu; barisnya ditambahkan eksplisit dan hanya saat keputusan wewenang sudah termuat.
    // Lambda, bukan `::openScreen`: referensi fungsi lokal belum didukung backend KMP ini.
    val drawerSections = if (isAuthenticated && accessDecisions.isNotEmpty()) {
        navSections + studioDrawerSection(
            currentScreen = currentScreen,
            // Buku demand superadmin-only di server; baris drawer ikut supaya pengguna lain
            // tidak menabrak layar yang pasti 403.
            showDemandLedger = session.user.role == Role.PLATFORM_SUPERADMIN,
            isBuilder = builderRoute,
            onOpenBuilder = { shellPath = "/builder"; PlatformNavigation.pushPath("/builder"); drawerOpen = false }
        ) { openScreen(it) }
    } else navSections

    CompositionLocalProvider(
        LocalAppNavigator provides navigateTo,
        LocalTutorialAnchors provides tutorials.anchors
    ) {
        WeMadeTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < ClayBreakpoints.MasterDetail
            val isBuilderLogin = currentScreen == AppNavScreen.LOGIN && (authState.hostSurface is HostSurface.Platform || builderRoute)

            if (adminRoute && session?.user?.role == Role.PLATFORM_SUPERADMIN) {
                PlatformAdminConsole(onActAs = authViewModel::actAsTenant, modifier = Modifier.fillMaxSize())
            } else Column(modifier = Modifier.fillMaxSize()) {
                if (!isBuilderLogin) {
                    AppTopBar(
                        currentScreen = currentScreen,
                        title = if (builderRoute) "Builder Console" else if (currentScreen == AppNavScreen.MODULE) moduleFromGenericPath(modulePath)?.displayName else null,
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
                        onResetSuperadmin = { authViewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin) },
                        onSelectCompany = { authViewModel.switchTenant(it) },
                        onOpenTenantEntitlements = { showTenantEntitlementDialog = true },
                        onOpenHelp = if (isAuthenticated) ({ tutorials.isListOpen = true }) else null,
                        onLogout = { authViewModel.onEvent(LoginUiEvent.Logout); navigateTo(AppNavScreen.LOGIN) }
                    )
                }

                if (builderRoute && isAuthenticated) {
                    BuilderShell(modifier = Modifier.weight(1f))
                } else Crossfade(targetState = currentScreen, modifier = Modifier.weight(1f)) { screen ->
                    when (screen) {
                        // Ketiga layar tata kelola kini melewati gerbang yang sama dengan sembilan
                        // modul operasional: sesi, lalu entitlement tenant, lalu wewenang jabatan.
                        // Sebelumnya hanya sesi yang diperiksa, sehingga siapa pun yang bisa login
                        // melihat ketiganya dengan hak penuh.
                        AppNavScreen.ORG_CHART -> {
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                decision = accessDecisions[GarmentModules.ORG_CHART],
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
                                decision = accessDecisions[GarmentModules.DYNAMIC_RBAC],
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
                        // R16/Fase D: funnel discovery. Gerbangnya ganda tapi beda bentuk dari modul:
                        // sesi dulu (AuthGuardCard), lalu kehadiran keputusan wewenang — draf prospek
                        // bukan aset tenant, jadi tidak dijabatkan ke satu AccessDecision modul.
                        //
                        // Fase C menumpang gerbang yang sama: Studio pola prototype juga platform,
                        // bukan modul. Yang membedakan keduanya hanya layarnya; wewenang menulis pola
                        // diputus server, dan `canWrite` di sini sekadar menyembunyikan tombol.
                        AppNavScreen.DISCOVERY, AppNavScreen.DISCOVERY_STUDIO, AppNavScreen.DISCOVERY_DEMANDS -> {
                            if (isAuthenticated && accessDecisions.isNotEmpty()) {
                                val isSuperadmin = session.user.role == Role.PLATFORM_SUPERADMIN
                                when (screen) {
                                    AppNavScreen.DISCOVERY_STUDIO ->
                                        PrototypeStudioScreen(canWrite = isSuperadmin)
                                    AppNavScreen.DISCOVERY_DEMANDS ->
                                        DemandLedgerScreen(isSuperadmin = isSuperadmin)
                                    else -> DiscoveryWizardScreen()
                                }
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
                        AppNavScreen.FACTORY_FLOW -> {
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                decision = accessDecisions[GarmentModules.FACTORY_FLOW],
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
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                // Fitur menumpang modul induk: wewenang & entitlement modul itu yang berlaku
                                // (module-integration-rules §5.4), bukan sekadar status login.
                                decision = screen.businessModule?.let { accessDecisions[it] },
                                persona = activePersona,
                                tenantName = session?.tenantSlug ?: "pabrik ini",
                                authGuard = {
                                    AuthGuardCard(targetModuleName = screen.title, onLoginClick = {
                                        pendingRedirectScreen = screen
                                        navigateTo(AppNavScreen.LOGIN)
                                    })
                                }
                            ) { com.eventverse.app.presentation.traceability.TraceabilityWorkspaceScreen(tenantSlug = session?.tenantSlug ?: "wemade-demo") }
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
                            GovernanceModuleGate(
                                screen = screen,
                                isAuthenticated = isAuthenticated,
                                // Fitur menumpang modul induk: wewenang & entitlement modul itu yang berlaku
                                // (module-integration-rules §5.4), bukan sekadar status login.
                                decision = screen.businessModule?.let { accessDecisions[it] },
                                persona = activePersona,
                                tenantName = session?.tenantSlug ?: "pabrik ini",
                                authGuard = {
                                    AuthGuardCard(targetModuleName = screen.title, onLoginClick = {
                                        pendingRedirectScreen = screen
                                        navigateTo(AppNavScreen.LOGIN)
                                    })
                                }
                            ) { com.eventverse.app.presentation.transfer.SuratJalanWorkspaceScreen() }
                        }
                        AppNavScreen.MODULE -> if (isAuthenticated) {
                            GenericModuleRoute(path = modulePath, accessDecisions = accessDecisions, persona = activePersona)
                        } else AuthGuardCard(targetModuleName = screen.title, onLoginClick = { navigateTo(AppNavScreen.LOGIN) })
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
                            val onLoggedIn: () -> Unit = { session?.let { finishLogin(it.user.role) } }
                            // discovery-M3: `app.` punya pintu platform sendiri, netral industri.
                            if (authState.hostSurface is HostSurface.Platform) PlatformLoginScreen(authViewModel, onLoggedIn)
                            else LoginScreen(viewModel = authViewModel, onNavigateToDashboard = onLoggedIn)
                        }
                    }
                }
            }

            // Google Cloud Console–style product panel: floats over the content with a scrim,
            // closes on ✕, on scrim click, or once a destination is chosen.
            if (!isBuilderLogin) {
                ClayNavDrawer(
                    open = drawerOpen,
                    onDismiss = { drawerOpen = false },
                    title = "WeMade ERP",
                    subtitle = "Multi-Tenant Garment Platform",
                    sections = drawerSections,
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

            if (isAuthenticated && !builderRoute && !adminRoute) TutorialLayer(
                state = tutorials,
                currentModule = if (currentScreen == AppNavScreen.MODULE) moduleFromGenericPath(modulePath) else currentScreen.businessModule,
                decisions = accessDecisions
            ) { id -> tutorialScreenFor(id)?.let(navigateTo) ?: run { modulePath = "${AppNavScreen.MODULE.route}/${id.value}"; currentScreen = AppNavScreen.MODULE; PlatformNavigation.pushPath(modulePath) } }

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
