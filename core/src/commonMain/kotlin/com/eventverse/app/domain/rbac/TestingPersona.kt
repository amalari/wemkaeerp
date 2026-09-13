package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Identitas pengguna simulasi untuk pengujian wewenang (RBAC).
 *
 * Persona menjawab satu pertanyaan: *"kalau saya masuk sebagai orang ini, saya lihat apa?"*
 * Ia sengaja **bukan** [com.eventverse.app.domain.auth.User] — `User.role` adalah enum tetap
 * milik platform (`TENANT_ADMIN`, `STAFF`, …), sementara yang menentukan isi layar adalah role
 * yang dirakit tenant sendiri ([CustomRole]) plus divisi tempat orang itu bekerja. Dua hal itu
 * yang dibawa persona.
 *
 * Persona tidak menyimpan hak akses apa pun. Hak dihitung ulang oleh [AccessDecisionEngine]
 * setiap kali matriks RBAC berubah, supaya tidak ada salinan basi yang bisa berbeda dari
 * konfigurasi sebenarnya.
 */
data class TestingPersona(
    val userId: String,
    val name: String,
    val tenantId: TenantId,
    val tenantSlug: String,
    val departmentId: String?,
    val departmentName: String,
    val roleId: RoleId?,
    val roleTitle: String,
    val isOwnerOrSuperAdmin: Boolean = false,
    val isPlatformSuperAdmin: Boolean = false,
    val sourceEmployeeId: OrgNodeId? = null
) {
    init {
        require(name.isNotBlank()) { "Nama persona tidak boleh kosong" }
        // Memilih jabatan berarti meminta dilihat **persis** sebagai jabatan itu. Bypass owner atau superadmin
        // yang tetap menyala di atasnya akan membuka seluruh modul dan membuat persona tampak benar
        // untuk konfigurasi apa pun — yaitu membuat pengujiannya tidak berarti apa-apa.
        require(!((isOwnerOrSuperAdmin || isPlatformSuperAdmin) && roleId != null)) {
            "Persona berjabatan tidak boleh memakai bypass; hapus roleId atau matikan bypass"
        }
    }

    /**
     * Label ringkas untuk capsule di top bar: `Budi Santoso · Kepala Penjualan`.
     *
     * Pemisahnya titik tengah, bukan kurung. Nama jabatan di pabrik lazim sudah memuat kurung
     * sendiri ("Kepala Penjualan (Head of Sales)"), sehingga membungkusnya lagi menghasilkan
     * kurung bersarang yang tak pernah tertutup rapi saat teksnya terpotong.
     */
    val displayLabel: String
        get() = if (roleTitle.isBlank()) name else "$name · $roleTitle"

    val avatarInitial: String
        get() = name.trim().split(" ").filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
            .ifBlank { "WM" }

    /**
     * Email sintetis yang dipakai server untuk *find-or-create*.
     *
     * Harus deterministik: `users.email` UNIQUE dan `uq_tenant_username` UNIQUE, jadi login
     * persona yang sama dua kali wajib menemukan baris yang sama, bukan gagal di constraint.
     * Domain `.testing.local` dipilih karena tidak dapat dirutekan — tidak ada risiko email
     * pengujian benar-benar terkirim ke seseorang.
     */
    val syntheticEmail: String
        get() = "persona-$tenantSlug-${slugify(name)}@testing.local"

    val syntheticUsername: String
        get() = "persona_${slugify(name).replace('-', '_')}".take(50)

    companion object {
        /**
         * Kloning persona dari karyawan riil di Org Chart.
         *
         * [OrgNode] **tidak** menyimpan tautan ke [CustomRole] — struktur organisasi dan matriks
         * wewenang adalah dua sumbu yang berbeda — jadi role harus disuplai pemanggil. Bila null,
         * persona hanya mewarisi hak dari divisinya.
         */
        fun fromEmployee(
            employee: OrgNode,
            tenantId: TenantId,
            tenantSlug: String,
            role: CustomRole? = null
        ): TestingPersona = TestingPersona(
            userId = "usr-persona-${employee.id.value}",
            name = employee.name,
            tenantId = tenantId,
            tenantSlug = tenantSlug,
            departmentId = employee.department?.id?.value,
            departmentName = employee.department?.displayName ?: "Tanpa Divisi",
            roleId = role?.id,
            roleTitle = role?.name ?: employee.roleTitle,
            // Bypass hanya untuk karyawan yang memang tidak punya jabatan terkonfigurasi — direksi
            // di puncak bagan. Begitu ada jabatan, jabatan itulah yang berlaku, apa pun namanya.
            isOwnerOrSuperAdmin = role == null &&
                employee.department == null &&
                employee.roleTitle.contains("Owner", ignoreCase = true),
            sourceEmployeeId = employee.id
        )

        /** Persona racikan bebas: nama diketik penguji, divisi dan jabatan dipilih dari daftar. */
        fun custom(
            name: String,
            tenantId: TenantId,
            tenantSlug: String,
            department: Department?,
            role: CustomRole?
        ): TestingPersona {
            val trimmed = name.trim()
            require(trimmed.isNotBlank()) { "Nama persona tidak boleh kosong" }
            return TestingPersona(
                userId = "usr-persona-${slugify(trimmed)}",
                name = trimmed,
                tenantId = tenantId,
                tenantSlug = tenantSlug,
                departmentId = department?.id?.value,
                departmentName = department?.displayName ?: "Tanpa Divisi",
                roleId = role?.id,
                roleTitle = role?.name ?: "Tanpa Jabatan",
                isOwnerOrSuperAdmin = false
            )
        }

        /**
         * Merakit persona dari daftar karyawan dan jabatan **apa pun** yang diberikan.
         *
         * Ini jalur yang harus dipakai begitu data dari API tersedia, dan ia sengaja tidak
         * mengenal satu pun kode divisi. Divisi atau jabatan yang ditambahkan admin besok akan
         * langsung terangkut, tanpa menyentuh fungsi ini.
         *
         * Pencocokan jabatan dilakukan lewat [CustomRole.departmentId] — data, bukan tebakan
         * berdasarkan nama. Versi sebelumnya memetakan `code == "sales"` ke `role-sales-head`,
         * yang berarti divisi "Sablon" atau "Bordir" yang baru dibuat tidak pernah mendapat
         * jabatan apa pun dan personanya tampak tak berwenang sama sekali.
         */
        fun fromDirectory(
            employees: List<OrgNode>,
            roles: List<CustomRole>,
            tenantId: TenantId,
            tenantSlug: String
        ): List<TestingPersona> = employees.map { employee ->
            fromEmployee(
                employee = employee,
                tenantId = tenantId,
                tenantSlug = tenantSlug,
                role = matchRole(employee, roles)
            )
        }

        /**
         * Jabatan yang paling masuk akal bagi seorang karyawan, dipilih dari jabatan sedivisi.
         *
         * Org Chart dan matriks RBAC adalah dua sumbu terpisah — tidak ada kolom yang menautkan
         * karyawan ke jabatan — jadi ini memang dugaan, dan disengaja: hasilnya titik awal yang
         * bisa ditimpa lewat persona racikan.
         *
         * Kepala divisi mendapat jabatan berwewenang terluas di divisinya, staf mendapat yang
         * tersempit. Tanpa pembedaan itu seluruh persona satu divisi terlihat identik, dan
         * pengujian kehilangan gunanya.
         */
        fun matchRole(employee: OrgNode, roles: List<CustomRole>): CustomRole? {
            val departmentId = employee.department?.id?.value
                ?: return roles.firstOrNull { it.departmentId == null && it.isSystemDefault }

            val inDepartment = roles.filter { it.departmentId == departmentId }
            if (inDepartment.isEmpty()) return null

            val isHead = listOf("kepala", "head", "manager", "direktur", "supervisor")
                .any { employee.roleTitle.contains(it, ignoreCase = true) }

            return if (isHead) {
                inDepartment.maxByOrNull { role -> role.modulePermissions.values.sumOf { it.level.weight } }
            } else {
                inDepartment.minByOrNull { role -> role.modulePermissions.values.sumOf { it.level.weight } }
            }
        }

        /**
         * Cadangan saat data dari API belum tiba, supaya switcher tidak pernah tampil kosong.
         *
         * Memakai [fromDirectory] dengan data contoh, jadi aturan pencocokannya persis sama
         * dengan jalur sungguhan — tidak ada logika kedua yang bisa menyimpang.
         */
        fun factoryPresets(tenantId: TenantId, tenantSlug: String): List<TestingPersona> =
            fromDirectory(
                employees = OrgNode.createSampleEmployees(tenantId),
                roles = CustomRole.createFactoryPresets(tenantId),
                tenantId = tenantId,
                tenantSlug = tenantSlug
            )

        private fun slugify(raw: String): String =
            raw.lowercase()
                .replace("[^a-z0-9]+".toRegex(), "-")
                .trim('-')
                .ifBlank { "anon" }
    }
}
