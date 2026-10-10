package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.HostSurface
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.api.AuthApiError
import com.eventverse.app.presentation.common.FriendlyErrors
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

        /** Pendaratan di subdomain tenant: aplikasi hasil generate (bukan Builder, yang tinggal di `app.`). */
        const val APP_LANDING_PATH = "/login"

        const val MSG_SLUG_REQUIRED = "Pilih/isi kode pabrik terlebih dahulu."

        const val MSG_SESSION_EXPIRED = "Sesi telah kedaluwarsa. Silakan masuk kembali."

        const val MSG_SESSION_STALE = "Sesi lama tidak lagi berlaku. Silakan masuk ulang."

        /** Pesan verifikasi sesi tersimpan yang gagal; dibedakan lewat kode alasan bertipe, bukan teks pesan. */
        fun sessionRejectedMessage(cause: Throwable): String =
            if ((cause as? AuthApiError.Rejected)?.reason == AuthRejectionReason.TOKEN_WITHOUT_TENANT) MSG_SESSION_STALE
            else MSG_SESSION_EXPIRED

        const val MSG_SESSION_NO_TENANT = "Sesi tersimpan tidak menyebut pabrik. Silakan masuk kembali."
    }

    /** Dari `/config`; dipakai menyusun origin tenant saat handoff. */
    private var platformBaseDomain: String? = null

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
        if (restoredSession != null && !isRestorable(restoredSession)) {
            // Sesi tersimpan tanpa tenant (bukan superadmin platform): tidak menebak "wemade-demo", kembali ke login.
            PlatformLocalStorage.removeItem(STORAGE_KEY)
            sessionStorage.clearSession()
            _uiState.update { it.copy(errorMessage = MSG_SESSION_NO_TENANT) }
        } else if (restoredSession != null) {
            _uiState.update {
                it.copy(
                    authenticatedSession = restoredSession,
                    tenantSlug = restoredSession.tenantSlug ?: it.tenantSlug
                )
            }
            storeTenantSession(restoredSession, restoredSession.tenantSlug, "Pabrik ${restoredSession.tenantSlug}")

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
                }.onFailure { cause ->
                    // Token expired or invalid: clear session
                    PlatformLocalStorage.removeItem(STORAGE_KEY)
                    sessionStorage.clearSession()
                    _uiState.update {
                        it.copy(
                            authenticatedSession = null,
                            errorMessage = sessionRejectedMessage(cause)
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

    /**
     * Login demo. Gagal APA PUN (403 bukan tenant demo, 404 demo dimatikan, jaringan mati) = pesan di
     * UI tanpa sesi. Dulu galat apa pun membuat sesi offline berisi identitas tenant lain; itu
     * menutupi penolakan gerbang server dan menghasilkan token yang ditolak semua endpoint.
     */
    private fun handleDemoLogin(targetRole: Role = Role.TENANT_ADMIN) {
        val currentSlug = _uiState.value.tenantSlug.trim()
        if (currentSlug.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = MSG_SLUG_REQUIRED) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val result = authApiClient.loginDemo(currentSlug, role = targetRole.name)
            if (_uiState.value.hostSurface is HostSurface.Platform && targetRole != Role.PLATFORM_SUPERADMIN) {
                result.onSuccess { applyVerifiedSession(it, fallbackSlug = currentSlug, restorePersona = true) }
                    .onFailure { e -> failLogin(e) }
                return@launch
            }
            result.onSuccess { session ->
                // 1. Simpan session token & profil ke PlatformLocalStorage (browser localStorage)
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))

                // 2. Simpan ke tenant session storage
                storeTenantSession(
                    session,
                    session.tenantSlug ?: currentSlug,
                    if (targetRole == Role.PLATFORM_SUPERADMIN) "WeMade Platform Admin" else "Pabrik ${session.tenantSlug ?: currentSlug}"
                )

                // 3. Login demo tetap butuh persona, kalau tidak menu modulnya kosong.
                restorePersonaFrom(session)

                // 4. Update state & navigasi
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = session,
                        successMessage = "Selamat datang, ${session.user.username.value}! (${session.user.email.value}) - Terkoneksi ke DB & JWT Aktif"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
            }.onFailure { e -> failLogin(e) }
        }
    }

    /** Sesi tersimpan boleh dipulihkan bila menyebut tenant; superadmin platform memang tanpa tenant. */
    private fun isRestorable(session: UserSession): Boolean =
        !session.tenantSlug.isNullOrBlank() || session.user.role == Role.PLATFORM_SUPERADMIN

    /**
     * Menyimpan [TenantSession] hanya bila sesi punya tenantId DAN slug eksplisit. Selain itu storage
     * dikosongkan: dulu tenantId/slug yang hilang diganti `ten-default`/`wemade-demo` secara senyap.
     */
    private fun storeTenantSession(session: UserSession, slug: String?, name: String) {
        val tenantId = session.user.tenantId
        val explicit = slug?.trim()?.ifEmpty { null }
        if (tenantId == null || explicit == null) {
            sessionStorage.clearSession()
            return
        }
        sessionStorage.setSession(TenantSession(tenantId, TenantSlug(explicit), name, SubscriptionTier.PRO))
    }

    /** Satu pintu galat login demo/persona: pesan jelas, tanpa sesi, tanpa tulis storage. */
    private fun failLogin(cause: Throwable) {
        _uiState.update { it.copy(isLoading = false, errorMessage = loginFailureMessage(cause)) }
    }

    private fun loginFailureMessage(cause: Throwable): String = when (cause) {
        is AuthApiError.Unreachable -> FriendlyErrors.UNREACHABLE
        else -> FriendlyErrors.friendly(cause, "Login gagal.")
    }

    /**
     * Masuk — atau berpindah — ke sebuah persona pengujian.
     *
     * Dipakai dua jalur sekaligus: tombol persona di layar login, dan switcher di top bar untuk
     * berganti persona tanpa keluar lebih dulu. Keduanya melewati server yang sama, jadi tidak ada
     * jalur "cepat" yang menghasilkan wewenang berbeda dari jalur normal.
     *
     * Tidak ada sesi offline. Keputusan: token offline tidak pernah diterima server (semua endpoint
     * menolaknya) dan wewenangnya hanya dihitung lokal, jadi ia menyesatkan pengujian; kegagalan
     * jaringan juga tampil sebagai pesan jelas ([FriendlyErrors.UNREACHABLE]), sama seperti 403/404.
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
                            successMessage = "Persona aktif: ${persona.displayLabel} - sesi live dari server."
                        )
                    }
                    _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
                }
                .onFailure { e -> failLogin(e) }
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
        val slug = session.tenantSlug?.trim()?.ifEmpty { null } ?: return // tanpa tenant: tidak ada persona

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
                        applyVerifiedSession(session, fallbackSlug = currentSlug, restorePersona = true)
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
        val host = PlatformHost.currentHost() ?: run { _uiState.update { it.copy(hostResolved = true) }; return }
        scope.launch {
            val baseDomain = authApiClient.fetchPlatformBaseDomain().getOrNull()
            platformBaseDomain = baseDomain
            val surface = HostSurface.parse(host, baseDomain)
            _uiState.update {
                it.copy(hostSurface = surface, hostResolved = true, tenantSlug = (surface as? HostSurface.Tenant)?.slug?.value ?: it.tenantSlug)
            }
            val ticket = PlatformHost.queryParameter(AuthApiClient.HANDOFF_QUERY_PARAM)
            if (surface is HostSurface.Tenant && ticket != null) {
                // Tiket sekali pakai: buang query-nya dari history, path tujuan (/builder) tetap.
                PlatformNavigation.replacePath(PlatformNavigation.getCurrentPath())
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
     * Builder hidup di `app.`; subdomain `<slug>.<base>` hanya untuk aplikasi hasil generate. Membuka
     * aplikasi berarti pindah origin, dan localStorage tidak melintasi origin — jadi sesi dibawa lewat
     * tiket sekali pakai. Superadmin (sesi act-as) wajib menyebut tenant tujuan; pemilik memakai tenant akunnya.
     */
    suspend fun openTenantApp(): Result<Unit> {
        val session = _uiState.value.authenticatedSession ?: return Result.failure(IllegalStateException("Sesi tidak ada"))
        val slug = session.tenantSlug?.ifBlank { null } ?: return Result.failure(IllegalStateException("Tenant belum dipilih"))
        val actAs = slug.takeIf { session.user.role == Role.PLATFORM_SUPERADMIN }
        return authApiClient.issueHandoff(session.token.value, actAs = actAs).map { h ->
            val origin = tenantOriginFromHere(h.tenantSlug, platformBaseDomain) ?: h.origin
            PlatformHost.openUrl("$origin$APP_LANDING_PATH?${AuthApiClient.HANDOFF_QUERY_PARAM}=${h.ticket}")
        }
    }

    /** Untuk tombol yang tidak punya coroutine scope sendiri (footer sidebar Builder); gagal → pesan di [LoginUiState.errorMessage]. */
    fun openTenantAppInBackground() {
        scope.launch { openTenantApp().onFailure { cause -> _uiState.update { it.copy(errorMessage = cause.message) } } }
    }

    /** Konsol `app./admin`: superadmin masuk Builder tenant [slug] di origin yang sama (act-as ber-audit, discovery-M3b). */
    suspend fun actAsBuilder(slug: String): Result<Unit> {
        val token = _uiState.value.authenticatedSession?.token?.value ?: return Result.failure(IllegalStateException("Sesi tidak ada"))
        return authApiClient.actAsSession(token, slug).map { session ->
            applyVerifiedSession(session, fallbackSlug = slug, restorePersona = true, announce = false)
        }
    }

    /** Konsol `app./admin`: superadmin masuk **aplikasi** tenant [slug] di subdomainnya (tiket handoff act-as, ber-audit). */
    suspend fun actAsTenant(slug: String, landingPath: String): Result<Unit> {
        val token = _uiState.value.authenticatedSession?.token?.value ?: return Result.failure(IllegalStateException("Sesi tidak ada"))
        return authApiClient.issueHandoff(token, actAs = slug).map { h ->
            val origin = tenantOriginFromHere(h.tenantSlug, platformBaseDomain) ?: h.origin
            PlatformHost.openUrl("$origin$landingPath?${AuthApiClient.HANDOFF_QUERY_PARAM}=${h.ticket}")
        }
    }

    /**
     * Menyimpan sesi yang sudah diterbitkan server, lalu masuk ke dashboard. [restorePersona] untuk
     * jalur handoff: tanpa persona, wewenang kosong dan pendaratan tidak pernah terjadi. Jalur Google
     * sengaja belum memakainya (perilaku lama dipertahankan).
     */
    private suspend fun applyVerifiedSession(
        session: UserSession,
        fallbackSlug: String,
        restorePersona: Boolean = false,
        announce: Boolean = true
    ) {
        val user = session.user
        val slug = session.tenantSlug?.ifBlank { null } ?: fallbackSlug
        PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))
        storeTenantSession(session, slug, "Pabrik $slug")
        if (restorePersona) restorePersonaFrom(session)
        _uiState.update {
            it.copy(
                isLoading = false,
                authenticatedSession = session,
                tenantSlug = slug,
                successMessage = "Selamat datang, ${user.username.value} (${user.email.value}) - Role: ${user.role.name}"
            )
        }
        if (announce) _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
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

    /**
     * Superadmin berpindah tenant dari top bar: act-as sungguhan (token dari server, ber-audit), bukan sesi rakitan klien.
     * Gagal → pesan di [LoginUiState.errorMessage]; sesi sebelumnya tidak diubah.
     */
    fun switchTenant(slug: String) {
        scope.launch { actAsBuilder(slug).onFailure { cause -> _uiState.update { it.copy(errorMessage = cause.message) } } }
    }
}
