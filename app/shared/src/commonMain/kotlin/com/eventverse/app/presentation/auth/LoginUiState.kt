package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.UserSession

enum class LoginTab(val label: String, val isAvailable: Boolean = true) {
    GOOGLE("Akun Google"),

    /**
     * Hidden for now: the OTP screen is a UI mock with no server-side flow behind it, so it
     * could only hand out a session that every authenticated endpoint rejects. Flip this
     * back to `true` together with a real OTP endpoint.
     */
    WHATSAPP("WhatsApp OTP", isAvailable = false);

    companion object {
        /** Tabs a user may actually pick. */
        val available: List<LoginTab> get() = entries.filter { it.isAvailable }
    }
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
    /**
     * Carries the Google ID token only. Email and name are no longer accepted from the
     * client: the server derives them from the verified token, so a caller cannot assert
     * who it is.
     */
    data class SubmitGoogleLogin(val idToken: String) : LoginUiEvent
    data object SubmitDemoLogin : LoginUiEvent
    data object SubmitDemoSuperAdminLogin : LoginUiEvent
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
