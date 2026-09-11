package com.eventverse.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.eventverse.app.presentation.designsystem.ClayActionSurface
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayNavDrawer
import com.eventverse.app.presentation.designsystem.ClayNavItem
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconMenu
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.presentation.navigation.AppNavScreen
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

    val navItems = remember(currentScreen, isAuthenticated) {
        listOf(
            ClayNavItem(
                key = AppNavScreen.ORG_CHART.route,
                label = AppNavScreen.ORG_CHART.title,
                selected = currentScreen == AppNavScreen.ORG_CHART,
                onClick = { openScreen(AppNavScreen.ORG_CHART) },
                icon = { tint ->
                    if (!isAuthenticated) LockIcon(modifier = Modifier.fillMaxSize(), color = tint)
                    else IconLayers(modifier = Modifier.fillMaxSize(), color = tint)
                }
            ),
            ClayNavItem(
                key = AppNavScreen.DYNAMIC_RBAC.route,
                label = AppNavScreen.DYNAMIC_RBAC.title,
                selected = currentScreen == AppNavScreen.DYNAMIC_RBAC,
                onClick = { openScreen(AppNavScreen.DYNAMIC_RBAC) },
                icon = { tint ->
                    if (!isAuthenticated) LockIcon(modifier = Modifier.fillMaxSize(), color = tint)
                    else IconShield(modifier = Modifier.fillMaxSize(), color = tint)
                }
            ),
            ClayNavItem(
                key = AppNavScreen.FACTORY_FLOW.route,
                label = AppNavScreen.FACTORY_FLOW.title,
                selected = currentScreen == AppNavScreen.FACTORY_FLOW,
                onClick = { openScreen(AppNavScreen.FACTORY_FLOW) },
                icon = { tint ->
                    if (!isAuthenticated) LockIcon(modifier = Modifier.fillMaxSize(), color = tint)
                    else IconZap(modifier = Modifier.fillMaxSize(), color = tint)
                }
            )
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

                            // User Profile Capsule
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                val initial = session.user.username.value.take(2).uppercase()
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
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = session.user.role.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WeMadeColors.Primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Distinct Logout Button
                            ClayActionSurface(
                                onClick = {
                                    authViewModel.onEvent(LoginUiEvent.Logout)
                                    navigateTo(AppNavScreen.LOGIN)
                                },
                                containerColor = WeMadeColors.ErrorBg,
                                outlineColor = WeMadeColors.Error,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                LogoutIcon(modifier = Modifier.size(13.dp), color = WeMadeColors.Error)
                                Text(
                                    text = "Logout",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.Error
                                )
                            }
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
                sectionLabel = "MODUL PABRIK",
                items = navItems
            ) {
                if (isAuthenticated) {
                    ClayButton(
                        text = "Logout",
                        onClick = {
                            authViewModel.onEvent(LoginUiEvent.Logout)
                            openScreen(AppNavScreen.LOGIN)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = ClayButtonStyle.Danger
                    )
                } else {
                    ClayButton(
                        text = "Login Akun",
                        onClick = { openScreen(AppNavScreen.LOGIN) },
                        modifier = Modifier.fillMaxWidth(),
                        style = ClayButtonStyle.Primary
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
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
            border = BorderStroke(1.dp, WeMadeColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)), // Amber-100
                    contentAlignment = Alignment.Center
                ) {
                    LockIcon(modifier = Modifier.size(32.dp), color = Color(0xFFD97706)) // Amber-600
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Akses Terbatas: Autentikasi Diperlukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Modul \"$targetModuleName\" dilindungi oleh sistem keamanan multi-tenant pabrik. Silakan masuk menggunakan akun perusahaan Anda untuk melanjutkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary)
                ) {
                    Text(
                        text = "Masuk ke Akun Sekarang →",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Crisp vector render of a Lock / Shield Icon
 */
@Composable
private fun LockIcon(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Shackle (arc loop)
        val shackle = Path().apply {
            moveTo(w * 0.32f, h * 0.44f)
            lineTo(w * 0.32f, h * 0.26f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.32f, h * 0.10f, w * 0.68f, h * 0.42f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.68f, h * 0.44f)
        }
        drawPath(shackle, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Lock body rounded rect
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.20f, h * 0.44f),
            size = Size(w * 0.60f, h * 0.46f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f)
        )
    }
}

/**
 * Crisp vector render of a Logout / Exit Door Icon
 */
@Composable
private fun LogoutIcon(modifier: Modifier = Modifier, color: Color = WeMadeColors.Error) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Door frame: top, left, bottom
        val door = Path().apply {
            moveTo(w * 0.55f, h * 0.15f)
            lineTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.2f, h * 0.85f)
            lineTo(w * 0.55f, h * 0.85f)
        }
        drawPath(door, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Exit arrow line
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.5f),
            end = Offset(w * 0.88f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Arrow head
        val arrow = Path().apply {
            moveTo(w * 0.72f, h * 0.34f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.72f, h * 0.66f)
        }
        drawPath(arrow, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}