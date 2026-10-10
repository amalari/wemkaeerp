package com.eventverse.app.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pintu masuk platform di `app.<base>` (discovery-M3-login-split, PLAN-builder-console §2).
 *
 * Berbeda dari [LoginScreen] (pintu workspace tenant): tidak ada kolom slug — tenant diturunkan dari
 * akun — dan bahasanya netral industri, karena platform bukan milik satu Domain Pack (CLAUDE.md
 * Jalur B poin 4). Setelah login, user tenant dibawa ke `<slug>.<base>/builder` lewat tiket handoff;
 * superadmin tetap di platform.
 */
@Composable
fun PlatformLoginScreen(
    viewModel: AuthViewModel,
    onNavigateToDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            if (effect is LoginUiEffect.NavigateToDashboard) onNavigateToDashboard?.invoke()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(WeMadeColors.BackgroundWarm),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .padding(horizontal = ClaySpacing.Xxl, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlatformBrandHeader()
            Spacer(Modifier.height(ClaySpacing.Xxl))

            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Panel,
                containerColor = WeMadeColors.Surface,
                outlineColor = WeMadeColors.Outline,
                offset = ClayOffset.Rest,
                borderWidth = ClayBorder.Thick,
                contentPadding = PaddingValues(ClaySpacing.Xxl)
            ) {
                AlertBanners(
                    errorMessage = state.errorMessage,
                    successMessage = state.successMessage,
                    onDismiss = { viewModel.onEvent(LoginUiEvent.DismissMessage) }
                )
                Text(
                    text = "Masuk ke akun Anda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(Modifier.height(ClaySpacing.Xs))
                Text(
                    text = "Kami akan membawa Anda langsung ke Builder project Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(ClaySpacing.Xl))

                ClayButton(
                    text = if (state.isLoading) "Memverifikasi..." else "Lanjutkan dengan Google",
                    onClick = {
                        GoogleAuthBridge.onSignInTrigger?.invoke()
                            ?: viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin(""))
                    },
                    enabled = !state.isLoading,
                    style = ClayButtonStyle.Secondary,
                    fontSize = 13.sp,
                    offset = ClayOffset.Small,
                    contentPadding = PaddingValues(horizontal = ClaySpacing.Xl, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WeMadeColors.Primary)
                        } else {
                            GoogleLogoVector(Modifier.size(18.dp))
                        }
                    }
                )

                Spacer(Modifier.height(ClaySpacing.Xl))
                Text(
                    text = "Akses demo",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(ClaySpacing.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                    PlatformDemoButton(
                        text = "Owner ${state.tenantSlug}",
                        enabled = !state.isLoading,
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.onEvent(LoginUiEvent.SubmitDemoLogin) }
                    PlatformDemoButton(
                        text = "Superadmin",
                        enabled = !state.isLoading,
                        style = ClayButtonStyle.Accent,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin) }
                }
            }
        }
    }
}

@Composable
private fun PlatformBrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "WeMake ERP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Text(
            text = "Bangun sistem operasional bisnis Anda dari percakapan.",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlatformDemoButton(
    text: String,
    enabled: Boolean,
    style: ClayButtonStyle,
    modifier: Modifier,
    onClick: () -> Unit
) = ClayButton(
    text = text,
    onClick = onClick,
    enabled = enabled,
    style = style,
    fontSize = 12.sp,
    offset = ClayOffset.Small,
    contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = 9.dp),
    maxLines = 1,
    modifier = modifier
)
