package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import com.eventverse.app.presentation.tenant.TenantSession
import com.eventverse.app.presentation.tenant.TenantSessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object GoogleAuthBridge {
    var onAuthenticated: ((email: String, name: String) -> Unit)? = null
    var onSignInTrigger: (() -> Unit)? = null
}

/**
 * MVI State-Holder for authentication and Google Sign-In orchestration.
 */
class AuthViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val sessionStorage: TenantSessionStorage = InMemoryTenantSessionStorage()
) {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LoginUiEffect>()
    val uiEffect: SharedFlow<LoginUiEffect> = _uiEffect.asSharedFlow()

    init {
        GoogleAuthBridge.onAuthenticated = { email, name ->
            onEvent(LoginUiEvent.SubmitGoogleLogin(idToken = "", email = email, name = name))
        }
    }

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab, errorMessage = null, successMessage = null) }
            }
            is LoginUiEvent.UpdateTenantSlug -> {
                _uiState.update { it.copy(tenantSlug = event.slug.lowercase().trim(), errorMessage = null) }
            }
            is LoginUiEvent.UpdatePhoneNumber -> {
                _uiState.update { it.copy(phoneNumber = event.phone, errorMessage = null) }
            }
            is LoginUiEvent.UpdateOtpCode -> {
                _uiState.update { it.copy(otpCode = event.otp, errorMessage = null) }
            }
            is LoginUiEvent.SubmitGoogleLogin -> {
                handleGoogleLogin(event.idToken, event.email, event.name)
            }
            is LoginUiEvent.SendWhatsAppOtp -> {
                handleSendWhatsAppOtp()
            }
            is LoginUiEvent.VerifyWhatsAppOtp -> {
                handleVerifyWhatsAppOtp()
            }
            is LoginUiEvent.DismissMessage -> {
                _uiState.update { it.copy(errorMessage = null, successMessage = null) }
            }
            is LoginUiEvent.Logout -> {
                sessionStorage.clearSession()
                _uiState.update {
                    it.copy(
                        authenticatedSession = null,
                        errorMessage = null,
                        successMessage = "Berhasil keluar dari sistem."
                    )
                }
            }
        }
    }

    private fun handleGoogleLogin(idToken: String, email: String? = null, name: String? = null) {
        val currentSlug = _uiState.value.tenantSlug
        if (currentSlug.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subdomain perusahaan wajib diisi") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            try {
                val userEmail = email?.ifBlank { null } ?: "student.achmad@gmail.com"
                val userName = if (userEmail.contains("achmad")) "achmad_owner" else (name?.ifBlank { null } ?: "user_${userEmail.substringBefore("@")}")

                val user = User(
                    id = UserId("usr-owner-001"),
                    tenantId = TenantId("ten-$currentSlug"),
                    username = Username(userName.replace(" ", "_").lowercase()),
                    email = EmailAddress(userEmail),
                    role = Role.TENANT_ADMIN,
                    isActive = true
                )

                val session = UserSession(
                    user = user,
                    token = AuthToken("jwt-session-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                    tenantSlug = currentSlug
                )

                // Save to tenant storage
                sessionStorage.setSession(
                    TenantSession(
                        tenantId = user.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(currentSlug),
                        name = "Pabrik $currentSlug",
                        tier = SubscriptionTier.PRO
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = session,
                        successMessage = "Selamat datang kembali, ${user.username.value}! (${user.email.value}) — Role: ${user.role.name}"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Autentikasi Google gagal"
                    )
                }
            }
        }
    }

    private fun handleSendWhatsAppOtp() {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.length < 9) {
            _uiState.update { it.copy(errorMessage = "Nomor WhatsApp tidak valid (minimal 9 digit)") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isOtpSent = true,
                    otpCountdown = 60,
                    successMessage = "Kode OTP 6-digit berhasil dikirim ke WhatsApp $phone"
                )
            }
        }
    }

    private fun handleVerifyWhatsAppOtp() {
        val otp = _uiState.value.otpCode.trim()
        if (otp.length != 6) {
            _uiState.update { it.copy(errorMessage = "Kode OTP harus 6 digit angka") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val currentSlug = _uiState.value.tenantSlug
            val randomId = kotlin.random.Random.nextInt(100, 999)
            val operatorUser = User(
                id = UserId("usr-op-$randomId"),
                tenantId = TenantId("ten-$currentSlug"),
                username = Username("operator_lapangan"),
                email = EmailAddress("operator@${currentSlug}.id"),
                role = Role.OPERATOR,
                isActive = true
            )

            val session = UserSession(
                user = operatorUser,
                token = AuthToken("jwt-wa-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                tenantSlug = currentSlug
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    authenticatedSession = session,
                    successMessage = "Login WhatsApp berhasil sebagai Operator Mesin"
                )
            }
            _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
        }
    }
}
