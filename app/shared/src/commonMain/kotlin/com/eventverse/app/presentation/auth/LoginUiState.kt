package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.UserSession

enum class LoginTab(val label: String) {
    GOOGLE("Akun Google"),
    WHATSAPP("WhatsApp OTP")
}

data class LoginUiState(
    val selectedTab: LoginTab = LoginTab.GOOGLE,
    val tenantSlug: String = "wemade-demo",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val phoneNumber: String = "",
    val otpCode: String = "",
    val isOtpSent: Boolean = false,
    val otpCountdown: Int = 0,
    val authenticatedSession: UserSession? = null
)

sealed interface LoginUiEvent {
    data class SelectTab(val tab: LoginTab) : LoginUiEvent
    data class UpdateTenantSlug(val slug: String) : LoginUiEvent
    data class UpdatePhoneNumber(val phone: String) : LoginUiEvent
    data class UpdateOtpCode(val otp: String) : LoginUiEvent
    data class SubmitGoogleLogin(
        val idToken: String,
        val email: String? = null,
        val name: String? = null
    ) : LoginUiEvent
    data object SubmitDemoLogin : LoginUiEvent
    data object SendWhatsAppOtp : LoginUiEvent
    data object VerifyWhatsAppOtp : LoginUiEvent
    data object DismissMessage : LoginUiEvent
    data object Logout : LoginUiEvent
}

sealed interface LoginUiEffect {
    data class NavigateToDashboard(val session: UserSession) : LoginUiEffect
    data class OpenBrowserUrl(val url: String) : LoginUiEffect
    data class ShowSnackbar(val message: String) : LoginUiEffect
}
