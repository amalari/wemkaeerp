package com.eventverse.app.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(WeMadeColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header / Logo Section
            HeaderSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Main Login Glassmorphic Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
                border = BorderStroke(1.dp, WeMadeColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                        // Subdomain / Tenant Slug Input
                        TenantSlugInput(
                            tenantSlug = state.tenantSlug,
                            onSlugChange = { viewModel.onEvent(LoginUiEvent.UpdateTenantSlug(it)) }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Tab Segment Switcher
                        LoginTabSelector(
                            selectedTab = state.selectedTab,
                            onTabSelected = { viewModel.onEvent(LoginUiEvent.SelectTab(it)) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Security Footer Notice
            FooterSecurityNotice()
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ERP Badge Icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WeMadeColors.PrimaryContainer),
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "WeMade ERP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Text(
            text = "Sistem Manajemen Konveksi & Garmen Terpadu",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TenantSlugInput(
    tenantSlug: String,
    onSlugChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Subdomain / Kode Pabrik",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = tenantSlug,
            onValueChange = onSlugChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            placeholder = { Text("contoh: wemade-demo") },
            trailingIcon = {
                Text(
                    text = ".wemade.id",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.BorderFocus,
                unfocusedBorderColor = WeMadeColors.Border
            )
        )
    }
}

@Composable
private fun LoginTabSelector(
    selectedTab: LoginTab,
    onTabSelected: (LoginTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F5F9))
            .padding(4.dp)
    ) {
        LoginTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) WeMadeColors.Surface else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
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
    onDemoLoginClick: () -> Unit
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

        Spacer(modifier = Modifier.height(20.dp))

        // Official Google Sign-In Button
        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WeMadeColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = WeMadeColors.Surface,
                contentColor = WeMadeColors.OnSurface
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = WeMadeColors.Primary
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleLogoVector(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Lanjutkan dengan Google",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Demo Login Button for instant access
        FilledTonalButton(
            onClick = onDemoLoginClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = WeMadeColors.PrimaryContainer.copy(alpha = 0.6f),
                contentColor = WeMadeColors.Primary
            )
        ) {
            Text(
                text = "Demo Mode: Masuk Cepat (Owner Pabrik)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Direkomendasikan untuk: Owner, Admin, Sales, dan PPIC",
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
        Text(
            text = "Nomor WhatsApp (No. HP)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isOtpSent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(10.dp),
            placeholder = { Text("81234567890") },
            prefix = {
                Text(
                    text = "+62 ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.BorderFocus,
                unfocusedBorderColor = WeMadeColors.Border
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (!isOtpSent) {
            Button(
                onClick = onSendOtp,
                enabled = !isLoading && phoneNumber.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Accent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Kirim Kode OTP via WhatsApp",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // OTP verification input
            Text(
                text = "Masukkan 6-Digit Kode OTP",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6) onOtpChange(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("Misal: 749102") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeMadeColors.BorderFocus,
                    unfocusedBorderColor = WeMadeColors.Border
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onVerifyOtp,
                enabled = !isLoading && otpCode.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verifikasi & Masuk", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(WeMadeColors.SuccessBg),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val strokeWidth = 2.5f * density
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.76f)
                    lineTo(size.width * 0.82f, size.height * 0.28f)
                }
                drawPath(path, color = WeMadeColors.Success, style = Stroke(width = strokeWidth))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Sesi Aktif Terautentikasi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "${session.user.username.value} (${session.user.email.value})",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        AssistChip(
            onClick = {},
            label = { Text("Peran: ${session.user.role.name}") },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = WeMadeColors.Primary,
                containerColor = WeMadeColors.PrimaryContainer
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (onNavigateToDashboard != null) {
            Button(
                onClick = onNavigateToDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary)
            ) {
                Text(
                    text = "Lanjutkan ke Bagan Organisasi ➔",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WeMadeColors.Error),
            border = BorderStroke(1.dp, WeMadeColors.Error.copy(alpha = 0.5f))
        ) {
            Text("Keluar (Logout)", fontWeight = FontWeight.SemiBold)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WeMadeColors.ErrorBg)
                    .clickable { onDismiss() }
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Error,
                    fontWeight = FontWeight.Medium
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WeMadeColors.SuccessBg)
                    .clickable { onDismiss() }
                    .padding(12.dp)
            ) {
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Success,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FooterSecurityNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
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
            color = Color(0xFF4285F4),
            start = Offset(cx, cy),
            end = Offset(w * 0.95f, cy),
            strokeWidth = stroke
        )

        // Arc quadrants
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 0f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color(0xFF34A853), // Green
            startAngle = 45f,
            sweepAngle = 135f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color(0xFFFBBC05), // Yellow
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color(0xFFEA4335), // Red
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
    }
}
