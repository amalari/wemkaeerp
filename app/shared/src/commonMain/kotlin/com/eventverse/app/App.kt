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
import com.eventverse.app.presentation.auth.AuthViewModel
import com.eventverse.app.presentation.auth.LoginScreen
import com.eventverse.app.presentation.auth.LoginUiEffect
import com.eventverse.app.presentation.auth.LoginUiEvent
import com.eventverse.app.presentation.orgchart.OrgChartScreen
import com.eventverse.app.presentation.rbac.DynamicRbacScreen
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.theme.WeMadeTheme

enum class AppNavScreen {
    DYNAMIC_RBAC,
    ORG_CHART,
    LOGIN
}

@Composable
fun App() {
    val authViewModel = remember { AuthViewModel() }
    val authState by authViewModel.uiState.collectAsState()
    val session = authState.authenticatedSession
    val isAuthenticated = session != null

    var currentScreen by remember {
        mutableStateOf(if (isAuthenticated) AppNavScreen.ORG_CHART else AppNavScreen.LOGIN)
    }

    // Auto-navigate to dashboard when login succeeds
    LaunchedEffect(authViewModel) {
        authViewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) {
                currentScreen = AppNavScreen.ORG_CHART
            }
        }
    }

    // If logged out while viewing protected screen, redirect back to LOGIN
    var wasAuthenticated by remember { mutableStateOf(isAuthenticated) }
    LaunchedEffect(isAuthenticated) {
        if (wasAuthenticated && !isAuthenticated) {
            currentScreen = AppNavScreen.LOGIN
        }
        wasAuthenticated = isAuthenticated
    }

    WeMadeTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Switcher Bar
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
                    // Left Brand & Workspace Tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            text = "Multi-Tenant Garment Platform",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )

                        if (session != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WeMadeColors.PrimaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🏢 ${session.tenantSlug}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }
                    }

                    // Right Navigation Controls & User Profile Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Protected Navigation Chips
                        FilterChip(
                            selected = currentScreen == AppNavScreen.ORG_CHART,
                            onClick = { currentScreen = AppNavScreen.ORG_CHART },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (!isAuthenticated) {
                                        LockIcon(modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = "Bagan Organisasi",
                                        fontSize = 12.sp,
                                        fontWeight = if (currentScreen == AppNavScreen.ORG_CHART) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )

                        FilterChip(
                            selected = currentScreen == AppNavScreen.DYNAMIC_RBAC,
                            onClick = { currentScreen = AppNavScreen.DYNAMIC_RBAC },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (!isAuthenticated) {
                                        LockIcon(modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = "Hak Akses (RBAC)",
                                        fontSize = 12.sp,
                                        fontWeight = if (currentScreen == AppNavScreen.DYNAMIC_RBAC) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )

                        if (!isAuthenticated) {
                            FilterChip(
                                selected = currentScreen == AppNavScreen.LOGIN,
                                onClick = { currentScreen = AppNavScreen.LOGIN },
                                label = {
                                    Text(
                                        text = "Login Akun",
                                        fontSize = 12.sp,
                                        fontWeight = if (currentScreen == AppNavScreen.LOGIN) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        } else {
                            // Divider
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(WeMadeColors.Border)
                            )

                            // User Profile Capsule
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
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

                            // Distinct Logout Button
                            OutlinedButton(
                                onClick = {
                                    authViewModel.onEvent(LoginUiEvent.Logout)
                                    currentScreen = AppNavScreen.LOGIN
                                },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = WeMadeColors.Error,
                                    containerColor = WeMadeColors.ErrorBg
                                ),
                                border = BorderStroke(1.dp, WeMadeColors.Error.copy(alpha = 0.35f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                LogoutIcon(modifier = Modifier.size(13.dp), color = WeMadeColors.Error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Logout",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
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
                        if (isAuthenticated && session != null) {
                            OrgChartScreen(tenantSlug = session.tenantSlug ?: "wemade-demo")
                        } else {
                            AuthGuardCard(
                                targetModuleName = "Bagan Struktur Organisasi & Karyawan",
                                onLoginClick = { currentScreen = AppNavScreen.LOGIN }
                            )
                        }
                    }
                    AppNavScreen.DYNAMIC_RBAC -> {
                        if (isAuthenticated) {
                            DynamicRbacScreen(
                                onBackToLogin = { currentScreen = AppNavScreen.LOGIN }
                            )
                        } else {
                            AuthGuardCard(
                                targetModuleName = "Manajemen Hak Akses & Matriks RBAC",
                                onLoginClick = { currentScreen = AppNavScreen.LOGIN }
                            )
                        }
                    }
                    AppNavScreen.LOGIN -> {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToDashboard = { currentScreen = AppNavScreen.ORG_CHART }
                        )
                    }
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