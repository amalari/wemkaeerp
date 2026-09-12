package com.eventverse.app.presentation.rbac

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
        combine(_activePersona, _roles, _departmentAssignments) { persona, roles, assignments ->
            if (persona == null) {
                emptyMap()
            } else {
                AccessDecisionEngine.explainAll(persona, roles, assignments)
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

        scope.launch {
            client.getRoles(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _roles.value = remote
            }.onFailure {
                if (_roles.value.isEmpty()) _roles.value = CustomRole.createFactoryPresets(tenantId)
            }

            client.getDepartments(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _departments.value = remote
            }.onFailure {
                if (_departments.value.isEmpty()) _departments.value = Department.defaultPresets()
            }

            client.getEmployees(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _employees.value = remote
            }.onFailure {
                if (_employees.value.isEmpty()) _employees.value = OrgNode.createSampleEmployees(tenantId)
            }

            client.getModuleAssignments(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) _departmentAssignments.value = remote
            }

            _isLoading.value = false
        }
    }

    fun setPersona(persona: TestingPersona?) {
        _activePersona.value = persona
    }

    fun setAuditView(enabled: Boolean) {
        _isAuditViewEnabled.value = enabled
    }

    /** Dipanggil layar RBAC setelah jabatan tersimpan, agar menu ikut berubah tanpa memuat ulang. */
    fun syncRoles(updated: List<CustomRole>) {
        if (updated.isNotEmpty()) _roles.value = updated
    }

    /** Idem untuk penugasan divisi. */
    fun syncAssignments(updated: Map<BusinessModule, List<DepartmentModuleAssignment>>) {
        _departmentAssignments.value = updated
    }

    /** Wewenang efektif satu modul saat ini — untuk gerbang di layar kerja. */
    fun accessFor(module: BusinessModule): ModuleAccessConfig =
        effectivePermissions.value[module] ?: ModuleAccessConfig()

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
