package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.tenant.HostSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = remember { AuthViewModel() },
    onNavigateToDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) {
                onNavigateToDashboard?.invoke()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.BackgroundWarm),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Xxl, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header / Logo Section
            HeaderSection()

            Spacer(modifier = Modifier.height(ClaySpacing.Xxl))

            // 2. Main Login Clay Card
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Panel,
                containerColor = WeMadeColors.Surface,
                outlineColor = WeMadeColors.Outline,
                offset = ClayOffset.Rest,
                borderWidth = ClayBorder.Thick,
                contentPadding = PaddingValues(ClaySpacing.Xxl)
            ) {
                // Status Banners (Error / Success)
                AlertBanners(
                    errorMessage = state.errorMessage,
                    successMessage = state.successMessage,
                    onDismiss = { viewModel.onEvent(LoginUiEvent.DismissMessage) }
                )

                // If authenticated, show active session card
                if (state.authenticatedSession != null) {
                    AuthenticatedSessionCard(
                        session = state.authenticatedSession!!,
                        onLogout = { viewModel.onEvent(LoginUiEvent.Logout) },
                        onNavigateToDashboard = onNavigateToDashboard
                    )
                } else {
                    // Kolom slug hanya di host lokal; di app./<slug>. tenant ditentukan akun/host (discovery-M3).
                    when (val surface = state.hostSurface) {
                        HostSurface.Local -> TenantSlugInput(state.tenantSlug) { viewModel.onEvent(LoginUiEvent.UpdateTenantSlug(it)) }
                        is HostSurface.Tenant -> ClayTag(text = "Workspace: ${surface.slug.value}", tint = WeMadeColors.Primary)
                        HostSurface.Platform -> Unit
                    }

                    Spacer(modifier = Modifier.height(ClaySpacing.Xl))

                    // Tab Segment Switcher — hidden entirely when only one login method is available
                    if (LoginTab.available.size > 1) {
                        LoginTabSelector(
                            selectedTab = state.selectedTab,
                            onTabSelected = { viewModel.onEvent(LoginUiEvent.SelectTab(it)) }
                        )

                        Spacer(modifier = Modifier.height(ClaySpacing.Xl))
                    }

                    // Tab Content
                    when (state.selectedTab) {
                        LoginTab.GOOGLE -> {
                            GoogleLoginContent(
                                isLoading = state.isLoading,
                                onGoogleClick = {
                                    val trigger = GoogleAuthBridge.onSignInTrigger
                                    if (trigger != null) {
                                        trigger()
                                    } else {
                                        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin(""))
                                    }
                                },
                                onDemoLoginClick = {
                                    viewModel.onEvent(LoginUiEvent.SubmitDemoLogin)
                                },
                                onDemoSuperAdminLoginClick = {
                                    viewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
                                }
                            )
                        }

                                                LoginTab.WHATSAPP -> {
                            WhatsAppLoginContent(
                                phoneNumber = state.phoneNumber,
                                otpCode = state.otpCode,
                                isOtpSent = state.isOtpSent,
                                isLoading = state.isLoading,
                                onPhoneChange = { viewModel.onEvent(LoginUiEvent.UpdatePhoneNumber(it)) },
                                onOtpChange = { viewModel.onEvent(LoginUiEvent.UpdateOtpCode(it)) },
                                onSendOtp = { viewModel.onEvent(LoginUiEvent.SendWhatsAppOtp) },
                                onVerifyOtp = { viewModel.onEvent(LoginUiEvent.VerifyWhatsAppOtp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xxl))

            // 3. Security Footer Notice
            FooterSecurityNotice()
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ERP Badge Icon inside tactile Clay Tile
        Box(
            modifier = Modifier
                .size(56.dp)
                .claySurface(
                    shape = ClayShapes.Tile,
                    background = WeMadeColors.PrimaryContainer,
                    outline = WeMadeColors.Outline,
                    shadowColor = WeMadeColors.Outline,
                    offset = ClayOffset.Small,
                    borderWidth = ClayBorder.Thick,
                    innerShade = true
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(32.dp)) {
                // Fabric spool / loom geometric icon
                drawRoundRect(
                    color = WeMadeColors.Primary,
                    size = Size(size.width * 0.7f, size.height * 0.7f),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                drawLine(
                    color = WeMadeColors.Accent,
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 4f
                )
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

        Text(
            text = "WeMade ERP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Xs))

        Text(
            text = "Sistem Manajemen Konveksi & Garmen Terpadu",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** Hanya di host lokal/dev ([HostSurface.Local]); di produksi tenant ditentukan host atau akun. */
@Composable
private fun TenantSlugInput(tenantSlug: String, onSlugChange: (String) -> Unit) {
    ClayTextField(
        value = tenantSlug,
        onValueChange = onSlugChange,
        modifier = Modifier.fillMaxWidth(),
        label = "Kode Pabrik (dev)",
        placeholder = "contoh: wemade-demo",
        focusColor = WeMadeColors.Primary
    )
}

@Composable
private fun LoginTabSelector(
    selectedTab: LoginTab,
    onTabSelected: (LoginTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        LoginTab.available.forEach { tab ->
            val isSelected = tab == selectedTab
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier.claySurface(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.Surface,
                                outline = WeMadeColors.Outline,
                                shadowColor = WeMadeColors.Outline,
                                offset = ClayOffset.Pressed,
                                pressed = isPressed,
                                borderWidth = ClayBorder.Medium,
                                innerShade = true
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onTabSelected(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun GoogleLoginContent(
    isLoading: Boolean,
    onGoogleClick: () -> Unit,
    onDemoLoginClick: () -> Unit,
    onDemoSuperAdminLoginClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Masuk secara instan menggunakan akun Google yang terdaftar di perusahaan Anda.",
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Xl))

        // Official Google Sign-In Button with Clay styling
        ClayButton(
            text = if (isLoading) "Memverifikasi..." else "Lanjutkan dengan Google",
            onClick = onGoogleClick,
            enabled = !isLoading,
            style = ClayButtonStyle.Secondary,
            fontSize = 13.sp,
            offset = ClayOffset.Small,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Xl, vertical = 11.dp),
            modifier = Modifier.fillMaxWidth(),
            leading = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = WeMadeColors.Primary
                    )
                } else {
                    GoogleLogoVector(modifier = Modifier.size(18.dp))
                }
            }
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

        // Quick Demo Login Button (Owner Pabrik / Tenant Admin)
        ClayButton(
            text = "Demo Mode: Masuk Cepat (Owner Pabrik)",
            onClick = onDemoLoginClick,
            enabled = !isLoading,
            style = ClayButtonStyle.Primary,
            fontSize = 12.sp,
            offset = ClayOffset.Small,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = 9.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Quick Demo Login Button (Superadmin Apps / Platform Admin)
        ClayButton(
            text = "Demo Mode: Masuk Cepat (Superadmin Apps)",
            onClick = onDemoSuperAdminLoginClick,
            enabled = !isLoading,
            style = ClayButtonStyle.Accent,
            fontSize = 12.sp,
            offset = ClayOffset.Small,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = 9.dp),
            modifier = Modifier.fillMaxWidth(),
            leading = {
                IconZap(modifier = Modifier.size(14.dp), color = Color.White)
            }
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Xl))

        Text(
            text = "Direkomendasikan untuk: Owner, Admin Apps, Sales, dan PPIC",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WhatsAppLoginContent(
    phoneNumber: String,
    otpCode: String,
    isOtpSent: Boolean,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onVerifyOtp: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ClayTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Nomor WhatsApp (No. HP)",
            placeholder = "81234567890",
            enabled = !isOtpSent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = {
                Text(
                    text = "+62",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

        if (!isOtpSent) {
            ClayButton(
                text = if (isLoading) "Mengirim OTP..." else "Kirim Kode OTP via WhatsApp",
                onClick = onSendOtp,
                enabled = !isLoading && phoneNumber.isNotBlank(),
                style = ClayButtonStyle.Accent,
                fontSize = 13.sp,
                offset = ClayOffset.Small,
                contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        } else {
            // OTP verification input
            ClayTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6) onOtpChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = "Masukkan 6-Digit Kode OTP",
                placeholder = "Misal: 749102",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = {
                    IconLock(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
                }
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            ClayButton(
                text = if (isLoading) "Memverifikasi..." else "Verifikasi & Masuk",
                onClick = onVerifyOtp,
                enabled = !isLoading && otpCode.length == 6,
                style = ClayButtonStyle.Primary,
                fontSize = 13.sp,
                offset = ClayOffset.Small,
                contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

        Text(
            text = "Praktis untuk Operator Mesin, Operator Jahit & Staff Gudang.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AuthenticatedSessionCard(
    session: com.eventverse.app.domain.auth.UserSession,
    onLogout: () -> Unit,
    onNavigateToDashboard: (() -> Unit)? = null
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.SurfaceMuted,
        outlineColor = WeMadeColors.Outline,
        offset = ClayOffset.Small,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clayFlat(
                        shape = CircleShape,
                        background = WeMadeColors.SuccessBg,
                        outline = WeMadeColors.Success,
                        borderWidth = ClayBorder.Medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconCheck(modifier = Modifier.size(24.dp), color = WeMadeColors.Success)
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            Text(
                text = "Sesi Aktif Terautentikasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )

            Text(
                text = "${session.user.username.value} (${session.user.email.value})",
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            ClayBadge(
                text = "Peran: ${session.user.role.name}",
                tint = WeMadeColors.Primary,
                dot = true
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            if (onNavigateToDashboard != null) {
                ClayButton(
                    text = "Lanjutkan ke Bagan Organisasi ➔",
                    onClick = onNavigateToDashboard,
                    style = ClayButtonStyle.Primary,
                    fontSize = 13.sp,
                    offset = ClayOffset.Small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Md))
            }

            ClayButton(
                text = "Keluar (Logout)",
                onClick = onLogout,
                style = ClayButtonStyle.Danger,
                fontSize = 13.sp,
                offset = ClayOffset.Small,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AlertBanners(
    errorMessage: String?,
    successMessage: String?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = errorMessage != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ClaySpacing.Lg)
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Medium
                    )
                    .clickable { onDismiss() }
                    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconWarning(modifier = Modifier.size(16.dp), color = WeMadeColors.Error)
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = successMessage != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (successMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ClaySpacing.Lg)
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.SuccessBg,
                        outline = WeMadeColors.Success,
                        borderWidth = ClayBorder.Medium
                    )
                    .clickable { onDismiss() }
                    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconCheck(modifier = Modifier.size(16.dp), color = WeMadeColors.Success)
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Success,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FooterSecurityNotice() {
    Row(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.OutlineSoft.copy(alpha = 0.5f),
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        IconShield(modifier = Modifier.size(14.dp), color = WeMadeColors.Primary)
        Text(
            text = "Isolasi Data Multi-Tenant & PostgreSQL Row-Level Security Aktif",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Geometric Vector canvas rendering of the official Google 4-color "G" icon
 */
@Composable
private fun GoogleLogoVector(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = w * 0.42f
        val stroke = w * 0.18f

        // Blue horizontal bar
        drawLine(
            color = WeMadeColors.GoogleBlue,
            start = Offset(cx, cy),
            end = Offset(w * 0.95f, cy),
            strokeWidth = stroke
        )

        // Arc quadrants
        drawArc(
            color = WeMadeColors.GoogleBlue,
            startAngle = 0f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = WeMadeColors.GoogleGreen,
            startAngle = 45f,
            sweepAngle = 135f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = WeMadeColors.GoogleYellow,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = WeMadeColors.GoogleRed,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
    }
}
