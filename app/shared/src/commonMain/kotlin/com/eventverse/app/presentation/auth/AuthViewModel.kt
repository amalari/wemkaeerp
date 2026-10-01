package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.HostSurface
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.navigation.PlatformHost
import com.eventverse.app.infrastructure.navigation.PlatformNavigation
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import com.eventverse.app.presentation.tenant.TenantSession
import com.eventverse.app.presentation.tenant.TenantSessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Bridge to each platform's Google sign-in.
 *
 * [onAuthenticated] carries the Google **ID token**, not an email/name pair: the server
 * verifies that token against Google and derives the identity itself. Passing a profile
 * would mean the client decides who it is.
 */
object GoogleAuthBridge {
    var onAuthenticated: ((idToken: String) -> Unit)? = null
    var onSignInTrigger: (() -> Unit)? = null
}

/**
 * MVI State-Holder for authentication, persistent session restoration,
 * and database-backed demo login orchestration.
 */
class AuthViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val sessionStorage: TenantSessionStorage = InMemoryTenantSessionStorage(),
    private val authApiClient: AuthApiClient = AuthApiClient(),
    private val policyRepository: RbacAccessPolicyRepository = RbacAccessPolicyRepository.shared
) {
    companion object {
        const val STORAGE_KEY = AuthApiClient.SESSION_STORAGE_KEY
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LoginUiEffect>()
    val uiEffect: SharedFlow<LoginUiEffect> = _uiEffect.asSharedFlow()

    init {
        GoogleAuthBridge.onAuthenticated = { idToken ->
            onEvent(LoginUiEvent.SubmitGoogleLogin(idToken = idToken))
        }

        resolveHostSurface()

        // 1. Auto-restore session from PlatformLocalStorage on startup / reload
        val savedJson = PlatformLocalStorage.getItem(STORAGE_KEY)
        val restoredSession = AuthApiClient.deserializeSession(savedJson)
        if (restoredSession != null) {
            _uiState.update {
                it.copy(
                    authenticatedSession = restoredSession,
                    tenantSlug = restoredSession.tenantSlug ?: it.tenantSlug
                )
            }
            sessionStorage.setSession(
                TenantSession(
                    tenantId = restoredSession.user.tenantId ?: TenantId("ten-default"),
                    slug = TenantSlug(restoredSession.tenantSlug ?: "wemade-demo"),
                    name = "Pabrik ${restoredSession.tenantSlug ?: "wemade-demo"}",
                    tier = SubscriptionTier.PRO
                )
            )

            // Pulihkan juga persona-nya. Tanpa ini, reload halaman mengembalikan sesi tetapi
            // mengosongkan wewenang, dan seluruh menu modul lenyap tanpa sebab yang terlihat.
            restorePersonaFrom(restoredSession)

            // 2. Asynchronously verify token validity against backend DB
            scope.launch {
                val verifyResult = authApiClient.verifySession(restoredSession.token.value)
                verifyResult.onSuccess { verifiedSession ->
                    _uiState.update { it.copy(authenticatedSession = verifiedSession) }
                    restorePersonaFrom(verifiedSession)
                    PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(verifiedSession))
                }.onFailure {
                    // Token expired or invalid: clear session
                    PlatformLocalStorage.removeItem(STORAGE_KEY)
                    sessionStorage.clearSession()
                    _uiState.update {
                        it.copy(
                            authenticatedSession = null,
                            errorMessage = "Sesi telah kedaluwarsa. Silakan masuk kembali."
                        )
                    }
                }
            }
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
            is LoginUiEvent.SubmitDemoLogin -> {
                handleDemoLogin(Role.TENANT_ADMIN)
            }
            is LoginUiEvent.SubmitDemoSuperAdminLogin -> {
                handleDemoLogin(Role.PLATFORM_SUPERADMIN)
            }
            is LoginUiEvent.SubmitPersonaLogin -> {
                handlePersonaLogin(event.persona)
            }
            is LoginUiEvent.SubmitGoogleLogin -> {
                handleGoogleLogin(event.idToken)
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
                PlatformLocalStorage.removeItem(STORAGE_KEY)
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

    private fun handleDemoLogin(targetRole: Role = Role.TENANT_ADMIN) {
        val currentSlug = _uiState.value.tenantSlug.ifBlank { "wemade-demo" }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val result = authApiClient.loginDemo(currentSlug, role = targetRole.name)
            result.onSuccess { session ->
                // 1. Simpan session token & profil ke PlatformLocalStorage (browser localStorage)
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))

                // 2. Simpan ke tenant session storage
                sessionStorage.setSession(
                    TenantSession(
                        tenantId = session.user.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(session.tenantSlug ?: currentSlug),
                        name = if (targetRole == Role.PLATFORM_SUPERADMIN) "WeMade Platform Admin" else "Pabrik ${session.tenantSlug ?: currentSlug}",
                        tier = SubscriptionTier.PRO
                    )
                )

                // 3. Login demo tetap butuh persona, kalau tidak menu modulnya kosong.
                restorePersonaFrom(session)

                // 4. Update state & navigasi
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = session,
                        successMessage = "Selamat datang, ${session.user.username.value}! (${session.user.email.value}) — Terkoneksi ke DB & JWT Aktif"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
            }.onFailure { error ->
                // Fallback offline session jika backend offline
                val fallbackUser = if (targetRole == Role.PLATFORM_SUPERADMIN) {
                    User(
                        id = UserId("usr-superadmin-001"),
                        tenantId = TenantId("ten-$currentSlug"),
                        username = Username("superadmin_apps"),
                        email = EmailAddress("superadmin@wemade.id"),
                        role = Role.PLATFORM_SUPERADMIN,
                        isActive = true
                    )
                } else {
                    User(
                        id = UserId("usr-owner-001"),
                        tenantId = TenantId("ten-$currentSlug"),
                        username = Username("achmad_owner"),
                        email = EmailAddress("student.achmad@gmail.com"),
                        role = Role.TENANT_ADMIN,
                        isActive = true
                    )
                }
                val offlineSession = UserSession(
                    user = fallbackUser,
                    token = AuthToken("jwt-offline-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                    tenantSlug = currentSlug
                )
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(offlineSession))
                sessionStorage.setSession(
                    TenantSession(
                        tenantId = fallbackUser.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(currentSlug),
                        name = if (targetRole == Role.PLATFORM_SUPERADMIN) "WeMade Platform Admin" else "Pabrik $currentSlug",
                        tier = SubscriptionTier.PRO
                    )
                )
                restorePersonaFrom(offlineSession)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = offlineSession,
                        successMessage = "Mode Demo Offline: ${fallbackUser.username.value} (${fallbackUser.role.name})"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(offlineSession))
            }
        }
    }

    /**
     * Masuk — atau berpindah — ke sebuah persona pengujian.
     *
     * Dipakai dua jalur sekaligus: tombol persona di layar login, dan switcher di top bar untuk
     * berganti persona tanpa keluar lebih dulu. Keduanya melewati server yang sama, jadi tidak ada
     * jalur "cepat" yang menghasilkan wewenang berbeda dari jalur normal.
     *
     * Kegagalan jaringan jatuh ke sesi offline agar pengujian UI tidak terhenti saat backend belum
     * dijalankan. Token offline itu **tidak pernah** diterima server — ia hanya membuka gerbang di
     * client — dan itulah sebabnya statusnya dinyatakan terang-terangan di pesan sukses, bukan
     * disamarkan sebagai login biasa.
     */
    fun handlePersonaLogin(persona: TestingPersona) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            authApiClient.loginPersona(persona)
                .onSuccess { session ->
                    applyPersonaSession(session, persona, session.tenantSlug ?: persona.tenantSlug)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Persona aktif: ${persona.displayLabel} — sesi live dari server."
                        )
                    }
                    _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
                }
                .onFailure {
                    val offlineSession = UserSession(
                        user = User(
                            id = UserId(persona.userId),
                            tenantId = persona.tenantId,
                            username = Username(persona.syntheticUsername),
                            email = EmailAddress(persona.syntheticEmail),
                            role = Role.OPERATOR,
                            isActive = true,
                            departmentId = persona.departmentId,
                            customRoleId = persona.roleId?.value
                        ),
                        token = AuthToken("offline-persona-${persona.userId}"),
                        tenantSlug = persona.tenantSlug
                    )
                    applyPersonaSession(offlineSession, persona, persona.tenantSlug)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Persona aktif: ${persona.displayLabel} — mode offline, " +
                                "wewenang dihitung lokal dan tidak ditegakkan server."
                        )
                    }
                    _uiEffect.emit(LoginUiEffect.NavigateToDashboard(offlineSession))
                }
        }
    }

    /** Alias yang lebih terbaca di tempat pemanggilan switcher. */
    fun switchPersona(persona: TestingPersona) = handlePersonaLogin(persona)

    /**
     * Menyusun ulang persona dari sesi yang tersimpan.
     *
     * Nama divisi dan jabatan sengaja dibiarkan berupa id dulu: begitu [RbacAccessPolicyRepository]
     * selesai memuat daftar divisi dan jabatan, switcher menampilkan nama yang benar. Yang penting
     * di sini adalah **id**-nya, karena itulah yang dipakai menghitung wewenang.
     */
    private fun restorePersonaFrom(session: UserSession) {
        val user = session.user
        val tenantId = user.tenantId ?: return
        val slug = session.tenantSlug ?: "wemade-demo"

        policyRepository.setPersona(
            TestingPersona(
                userId = user.id.value,
                name = user.username.value,
                tenantId = tenantId,
                tenantSlug = slug,
                departmentId = user.departmentId,
                departmentName = user.departmentId ?: "Tanpa Divisi",
                roleId = user.customRoleId?.let { com.eventverse.app.domain.rbac.RoleId(it) },
                roleTitle = user.role.name,
                // Jabatan menang atas peran platform.
                //
                // Kalau sesi membawa jabatan tenant, persona harus tampil **persis** sebagai
                // jabatan itu — termasuk setelah halaman dimuat ulang. Menyimpulkan "owner" dari
                // peran platform akan membuka seluruh modul dan diam-diam membatalkan pengujian,
                // justru pada jalur yang paling jarang diperiksa: reload.
                //
                // Bypass hanya berlaku untuk sesi tanpa jabatan, yaitu akun admin yang sedang tidak
                // menyamar. Tanpa itu, admin bisa terkunci dari layar RBAC-nya sendiri.
                isOwnerOrSuperAdmin = user.customRoleId == null &&
                    (user.role == Role.TENANT_ADMIN || user.role == Role.PLATFORM_SUPERADMIN),
                isPlatformSuperAdmin = user.customRoleId == null && user.role == Role.PLATFORM_SUPERADMIN
            )
        )
        policyRepository.load(tenantId, slug)
    }

    private fun applyPersonaSession(
        session: UserSession,
        persona: TestingPersona,
        slug: String
    ) {
        PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))
        sessionStorage.setSession(
            TenantSession(
                tenantId = session.user.tenantId ?: persona.tenantId,
                slug = TenantSlug(slug),
                name = "Pabrik $slug",
                tier = SubscriptionTier.PRO
            )
        )
        policyRepository.setPersona(persona)
        policyRepository.load(session.user.tenantId ?: persona.tenantId, slug)
        _uiState.update { it.copy(authenticatedSession = session, tenantSlug = slug) }
    }

    /**
     * Exchanges a Google ID token for a real WeMade session.
     *
     * The identity is decided by the server, which verifies the token directly against
     * Google. Previously this method minted a session and a token locally, which meant the
     * "logged in" user was whatever the client claimed — and the fabricated token was
     * rejected by every authenticated endpoint.
     */
    private fun handleGoogleLogin(idToken: String) {
        val surface = _uiState.value.hostSurface
        val currentSlug = _uiState.value.tenantSlug
        if (surface is HostSurface.Local && currentSlug.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subdomain perusahaan wajib diisi") }
            return
        }
        if (idToken.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Login Google belum tersedia di platform ini. " +
                        "Gunakan Login Demo, atau buka aplikasi versi web."
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            // Platform: tenant dari akun (slug null). Tenant: host yang menentukan. Lokal: kolom slug.
            val requestedSlug = when (surface) {
                HostSurface.Platform -> null
                is HostSurface.Tenant -> surface.slug.value
                HostSurface.Local -> currentSlug
            }
            authApiClient.loginWithGoogle(idToken = idToken, tenantSlug = requestedSlug)
                .onSuccess { session ->
                    if (surface is HostSurface.Platform && session.user.role != Role.PLATFORM_SUPERADMIN) {
                        handOffToTenant(session)
                    } else {
                        applyVerifiedSession(session, fallbackSlug = currentSlug)
                    }
                }
                .onFailure { cause ->
                    // No local fallback session here: a session the API would reject is
                    // worse than a clear failure, because it fails later and elsewhere.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = cause.message ?: "Autentikasi Google gagal"
                        )
                    }
                }
        }
    }

    /**
     * Membaca permukaan host (discovery-M3-login-split). Base domain datang dari server, bukan
     * dikompilasi ke bundle, supaya satu build melayani dev (lokal) dan produksi. Gagal memuatnya =
     * tetap [HostSurface.Local] — perilaku lama, tidak pernah lebih longgar.
     *
     * Di subdomain tenant, URL `?handoff=<tiket>` (redirect dari `app.`) langsung ditukar jadi sesi.
     */
    private fun resolveHostSurface() {
        val host = PlatformHost.currentHost() ?: return
        scope.launch {
            val surface = HostSurface.parse(host, authApiClient.fetchPlatformBaseDomain().getOrNull())
            _uiState.update {
                it.copy(hostSurface = surface, tenantSlug = (surface as? HostSurface.Tenant)?.slug?.value ?: it.tenantSlug)
            }
            val ticket = PlatformHost.queryParameter(AuthApiClient.HANDOFF_QUERY_PARAM)
            if (surface is HostSurface.Tenant && ticket != null) {
                PlatformNavigation.replacePath("/login") // tiket sekali pakai: jangan tinggal di history
                redeemHandoff(ticket, surface.slug.value)
            }
        }
    }

    private suspend fun redeemHandoff(ticket: String, slug: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        authApiClient.redeemHandoff(ticket)
            .onSuccess { applyVerifiedSession(it, fallbackSlug = slug, restorePersona = true) }
            .onFailure { cause -> _uiState.update { it.copy(isLoading = false, errorMessage = cause.message) } }
    }

    /**
     * Login tenant di `app.`: sesi **tidak** disimpan di origin platform — ia dibawa ke
     * `<slug>.<base>` lewat tiket sekali pakai, karena localStorage tidak melintasi origin.
     */
    private suspend fun handOffToTenant(session: UserSession) {
        authApiClient.issueHandoff(session.token.value)
            .onSuccess { handoff ->
                _uiState.update { it.copy(successMessage = "Mengalihkan ke workspace ${session.tenantSlug.orEmpty()}…") }
                PlatformHost.openUrl("${handoff.origin}/login?${AuthApiClient.HANDOFF_QUERY_PARAM}=${handoff.ticket}")
            }
            .onFailure { cause -> _uiState.update { it.copy(isLoading = false, errorMessage = cause.message) } }
    }

    /**
     * Menyimpan sesi yang sudah diterbitkan server, lalu masuk ke dashboard. [restorePersona] untuk
     * jalur handoff: tanpa persona, wewenang kosong dan pendaratan tidak pernah terjadi. Jalur Google
     * sengaja belum memakainya (perilaku lama dipertahankan).
     */
    private suspend fun applyVerifiedSession(session: UserSession, fallbackSlug: String, restorePersona: Boolean = false) {
        val user = session.user
        val slug = session.tenantSlug?.ifBlank { null } ?: fallbackSlug
        PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))
        sessionStorage.setSession(
            TenantSession(
                tenantId = user.tenantId ?: TenantId("ten-default"),
                slug = TenantSlug(slug),
                name = "Pabrik $slug",
                tier = SubscriptionTier.PRO
            )
        )
        if (restorePersona) restorePersonaFrom(session)
        _uiState.update {
            it.copy(
                isLoading = false,
                authenticatedSession = session,
                tenantSlug = slug,
                successMessage = "Selamat datang, ${user.username.value} (${user.email.value}) — Role: ${user.role.name}"
            )
        }
        _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
    }

    /** See [handleVerifyWhatsAppOtp]: no OTP is actually sent, so do not claim one was. */
    private fun handleSendWhatsAppOtp() {
        _uiState.update {
            it.copy(
                isLoading = false,
                isOtpSent = false,
                errorMessage = "Pengiriman OTP WhatsApp belum tersedia. " +
                    "Silakan gunakan Login Google atau Login Demo."
            )
        }
    }

    /**
     * WhatsApp OTP has no server-side flow behind it yet.
     *
     * This used to mint an OPERATOR session with a locally fabricated token, which every
     * authenticated endpoint rejects — the user appeared logged in, then nothing worked.
     * Until a real OTP endpoint exists, refuse explicitly. The tab is hidden
     * ([LoginTab.isAvailable]); this guard keeps the fake session from coming back if it is
     * ever re-enabled.
     */
    private fun handleVerifyWhatsAppOtp() {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = "Login WhatsApp OTP belum tersedia. " +
                    "Silakan gunakan Login Google atau Login Demo."
            )
        }
    }

    fun switchTenant(company: com.eventverse.app.presentation.navigation.CompanyTenantProfile) {
        val currentSession = _uiState.value.authenticatedSession
        val updatedUser = currentSession?.user?.copy(
            tenantId = TenantId(company.id)
        ) ?: User(
            id = UserId("usr-owner-001"),
            tenantId = TenantId(company.id),
            username = Username("superadmin"),
            email = EmailAddress("student.achmad@gmail.com"),
            role = Role.TENANT_ADMIN,
            isActive = true
        )
        val newSession = UserSession(
            user = updatedUser,
            token = currentSession?.token ?: AuthToken("jwt-session-admin"),
            tenantSlug = company.slug
        )

        PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(newSession))
        sessionStorage.setSession(
            TenantSession(
                tenantId = TenantId(company.id),
                slug = TenantSlug(company.slug),
                name = company.name,
                tier = SubscriptionTier.PRO
            )
        )

        _uiState.update {
            it.copy(
                authenticatedSession = newSession,
                tenantSlug = company.slug,
                successMessage = "Beralih ke perusahaan: ${company.name}"
            )
        }
    }
}
