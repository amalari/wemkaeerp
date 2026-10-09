package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.RbacApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Satu sumber kebenaran untuk "persona yang sedang diuji melihat apa".
 *
 * Dipegang bersama oleh tiga tempat yang sebelumnya tidak saling tahu: layar RBAC yang mengubah
 * matriks, switcher persona di top bar, dan daftar menu navigasi. Tanpa satu tempat seperti ini,
 * mengubah wewenang di layar RBAC tidak akan pernah mengubah menu — persis gejala yang membuat
 * fitur ini dibuat.
 *
 * [effectivePermissions] adalah state turunan, bukan salinan: ia dihitung ulang dari persona +
 * jabatan + penugasan divisi setiap kali salah satunya berubah, jadi tidak ada peluang ia basi.
 */
class RbacAccessPolicyRepository(
    apiClientProvider: () -> RbacApiClient? = { RbacApiClient() },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    /**
     * Dibuat malas dan boleh gagal menjadi null.
     *
     * Repository ini dipegang sebagai instance bersama yang ikut terbangun bersama state holder
     * auth. Membangun `HttpClient` saat konstruksi berarti setiap lingkungan tanpa engine HTTP —
     * test unit, misalnya — gagal sebelum satu baris logika pun dijalankan. Wewenang tetap dapat
     * dihitung tanpa jaringan; yang hilang hanyalah data jarak jauh.
     */
    private val apiClient: RbacApiClient? by lazy {
        runCatching { apiClientProvider() }.getOrNull()
    }
    private val _activePersona = MutableStateFlow<TestingPersona?>(null)
    val activePersona: StateFlow<TestingPersona?> = _activePersona.asStateFlow()

    private val _roles = MutableStateFlow<List<CustomRole>>(emptyList())
    val roles: StateFlow<List<CustomRole>> = _roles.asStateFlow()

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments.asStateFlow()

    private val _employees = MutableStateFlow<List<OrgNode>>(emptyList())
    val employees: StateFlow<List<OrgNode>> = _employees.asStateFlow()

    private val _departmentAssignments =
        MutableStateFlow<Map<BusinessModule, List<DepartmentModuleAssignment>>>(emptyMap())
    val departmentAssignments: StateFlow<Map<BusinessModule, List<DepartmentModuleAssignment>>> =
        _departmentAssignments.asStateFlow()

    /**
     * Mode audit: tampilkan menu yang terkunci alih-alih menyembunyikannya.
     *
     * Default-nya mati, karena itulah perilaku produksi. Dinyalakan penguji ketika yang ingin
     * dilihat justru batasnya — menu tersembunyi dan menu yang memang tidak ada terlihat sama.
     */
    /**
     * Modul yang benar-benar disambungkan ke tenant ini.
     *
     * `null` berarti **belum diketahui**, bukan "tidak ada satu pun". Perbedaan itu penting: pada
     * keadaan null, `AccessDecisionEngine` kembali ke perilaku lama dan mengabaikan entitlement,
     * sehingga permintaan yang gagal atau belum selesai tidak pernah tampil sebagai langganan yang
     * dicabut — kegagalan jaringan tidak boleh terlihat seperti keputusan billing.
     */
    private val _grantedModules = MutableStateFlow<Set<BusinessModule>?>(null)

    /**
     * Keputusan dari server untuk persona aktif (`GET /me/access`, B5). Persona aktif **selalu** pemilik token —
     * switcher persona benar-benar login ulang — jadi keputusan server berlaku untuknya. `null` = belum/tidak
     * didapat; saat itu menu jatuh ke perhitungan lokal (daftar jabatan & penugasan, yang hanya terbaca admin).
     */
    private val _serverDecisions = MutableStateFlow<Map<BusinessModule, AccessDecision>?>(null)
    private var lastTenantSlug: String? = null
    val grantedModules: StateFlow<Set<BusinessModule>?> = _grantedModules.asStateFlow()

    private val _isAuditViewEnabled = MutableStateFlow(false)
    val isAuditViewEnabled: StateFlow<Boolean> = _isAuditViewEnabled.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Keputusan lengkap per modul — termasuk **asal** wewenangnya.
     *
     * Inilah sumbernya; [effectivePermissions] tinggal mengambil bagian konfigurasinya. Satu
     * perhitungan, dua bentuk, sehingga mustahil keduanya menyimpang.
     */
    val accessDecisions: StateFlow<Map<BusinessModule, AccessDecision>> =
        combine(
            _activePersona,
            _roles,
            _departmentAssignments,
            _grantedModules,
            _serverDecisions
        ) { persona, roles, assignments, granted, server ->
            when {
                persona == null -> emptyMap()
                server != null -> server
                else -> AccessDecisionEngine.explainAll(persona, roles, assignments, granted, modules = ActiveTenantPack.current.moduleIds)
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val effectivePermissions: StateFlow<Map<BusinessModule, ModuleAccessConfig>> =
        accessDecisions
            .map { decisions -> decisions.mapValues { it.value.config } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Memuat konfigurasi wewenang satu tenant.
     *
     * Setiap request gagal ditangani sendiri-sendiri: kalau penugasan divisi gagal dimuat, jabatan
     * yang sudah didapat tetap dipakai. Kegagalan sebagian lebih baik daripada layar kosong, karena
     * hak dari kedua sumbu disatukan dan bukan saling bergantung.
     */
    fun load(tenantId: TenantId, tenantSlug: String) {
        val client = runCatching { apiClient }.getOrNull() ?: return
        _isLoading.value = true
        lastTenantSlug = tenantSlug

        scope.launch {
            // Sumber menu pengguna yang login. Daftar jabatan/penugasan di bawah kini hanya terbuka untuk admin
            // RBAC/Org Chart; bagi pengguna lain permintaannya ditolak dan fallback lokal tidak menentukan menu.
            // B7: pack tenant dulu — tanpa itu kunci modul pack data dibuang parser dan menunya kosong.
            client.getTenantPack(tenantSlug).onSuccess { ActiveTenantPack.activate(it) }
            val server = client.getMyAccess(tenantSlug).getOrNull()?.also { _serverDecisions.value = it }
            // Daftar wewenang semua orang hanya untuk admin RBAC. Tanpa keputusan server (offline/server lama) tetap
            // dicoba seperti dulu; dengan keputusan server yang menolak, tidak diminta sama sekali (tanpa 403 sia-sia).
            // Kegagalan TIDAK lagi diganti data contoh garment (TRD-PLAT-010 K5): daftar tetap kosong.
            val readsRbac = server?.get(GarmentModules.DYNAMIC_RBAC)?.config?.isAccessible ?: true
            if (readsRbac) {
                client.getRoles(tenantSlug).onSuccess { remote ->
                    if (remote.isNotEmpty()) _roles.value = remote
                }
            }

            client.getDepartments(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _departments.value = remote
            }

            client.getEmployees(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _employees.value = remote
            }

            if (readsRbac) {
                client.getModuleAssignments(tenantSlug).onSuccess { remote ->
                    if (remote.isNotEmpty()) _departmentAssignments.value = remote
                }
            }

            // Sengaja tanpa onFailure: entitlement yang gagal dimuat harus tetap null (= belum
            // diketahui), bukan himpunan kosong. Himpunan kosong berarti "tenant ini tidak punya
            // modul apa pun" dan akan mengosongkan seluruh menu hanya karena server sedang tidak
            // terjangkau.
            client.getEntitlement(tenantSlug).onSuccess { granted ->
                _grantedModules.value = granted
            }

            _isLoading.value = false
        }
    }

    fun setPersona(persona: TestingPersona?) {
        // Keputusan server milik persona sebelumnya; menunggu `load` untuk persona baru.
        if (persona?.userId != _activePersona.value?.userId) _serverDecisions.value = null
        _activePersona.value = persona
    }

    fun setAuditView(enabled: Boolean) {
        _isAuditViewEnabled.value = enabled
    }

    /** Dipanggil layar RBAC setelah jabatan tersimpan, agar menu ikut berubah tanpa memuat ulang. */
    fun syncRoles(updated: List<CustomRole>) {
        if (updated.isNotEmpty()) _roles.value = updated
        refreshServerDecisions()
    }

    /** Idem untuk penugasan divisi. */
    fun syncAssignments(updated: Map<BusinessModule, List<DepartmentModuleAssignment>>) {
        _departmentAssignments.value = updated
        refreshServerDecisions()
    }

    /** Wewenang di server berubah (jabatan/penugasan disunting) → ambil ulang supaya menu ikut berubah. */
    private fun refreshServerDecisions() {
        val slug = lastTenantSlug ?: return
        val client = runCatching { apiClient }.getOrNull() ?: return
        scope.launch { client.getMyAccess(slug).onSuccess { _serverDecisions.value = it } }
    }

    /** Wewenang efektif satu modul saat ini — untuk gerbang di layar kerja. */
    fun accessFor(module: BusinessModule): ModuleAccessConfig =
        effectivePermissions.value[module] ?: ModuleAccessConfig()

    /**
     * Apakah modul ini disambungkan ke tenant aktif. Selama entitlement belum diketahui, jawabannya
     * `true` — lihat alasan di [grantedModules].
     */
    fun isModuleEntitled(module: BusinessModule): Boolean =
        _grantedModules.value?.contains(module) ?: true

    /** Dipanggil setelah superadmin mengubah entitlement, agar menu ikut berubah tanpa reload. */
    fun syncGrantedModules(granted: Set<BusinessModule>) {
        _grantedModules.value = granted
    }

    companion object {
        /**
         * Instance bersama aplikasi.
         *
         * Manual DI seperti sisa proyek ini. Dijadikan satu instance karena state-nya memang
         * tunggal: hanya ada satu persona aktif per sesi, dan seluruh layar harus melihat yang sama.
         */
        val shared: RbacAccessPolicyRepository by lazy { RbacAccessPolicyRepository() }
    }
}
