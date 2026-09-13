package com.eventverse.app.domain.rbac

/**
 * Menghitung wewenang efektif seorang [TestingPersona] atas sebuah [BusinessModule].
 *
 * Aturannya satu kalimat: **hak tertinggi menang** (*Highest Privilege Union*). Seseorang bisa
 * memperoleh akses dari dua arah — dari jabatannya ([CustomRole.modulePermissions]) dan dari
 * divisinya ([DepartmentModuleAssignment]) — dan yang berlaku adalah yang paling longgar.
 *
 * Kenapa union dan bukan irisan: dua sumbu itu mewakili dua keputusan admin yang berbeda, dan
 * keduanya bersifat *memberi*. "Divisi Gudang boleh input inventaris" tidak dimaksudkan sebagai
 * pembatas bagi Kepala Gudang yang jabatannya sudah `MANAGE`; kalau diiriskan, menambahkan
 * assignment divisi justru akan **mencabut** hak yang sudah diberikan lewat jabatan — perilaku
 * yang mengejutkan dan hampir selalu bukan yang dimaui.
 *
 * Layanan domain murni: tanpa I/O, tanpa framework, aman dipanggil dari UI mana pun.
 */
/** Dari mana sebuah wewenang berasal. */
enum class AccessSource(val label: String) {
    /** Dari matriks jabatan — inilah yang biasanya ingin diuji. */
    ROLE("Jabatan"),

    /** Dari penugasan modul ke divisi. */
    DEPARTMENT("Divisi"),

    /** Owner pabrik melewati matriks sepenuhnya. */
    OWNER_BYPASS("Owner (bypass)"),

    /** Superadmin platform melewati matriks wewenang dan batasan paket tenant. */
    SUPERADMIN_BYPASS("Superadmin (bypass)"),

    /**
     * Modul tidak disambungkan ke tenant ini sama sekali.
     *
     * Dibedakan dari [NONE] karena keduanya mengirim orang ke pintu yang berbeda: "tidak berwenang"
     * diperbaiki admin pabrik lewat matriks, "tidak termasuk paket" hanya bisa diperbaiki
     * superadmin platform. Menyamakan keduanya membuat admin mencari-cari di layar yang tidak akan
     * pernah menyelesaikan masalahnya.
     */
    NOT_ENTITLED("Tidak termasuk paket"),

    /** Tidak ada satu pun yang memberi. */
    NONE("Tidak ada")
}

/**
 * Hasil evaluasi beserta **asalnya**.
 *
 * Asal ini bukan hiasan. Wewenang datang dari dua arah yang disatukan, sehingga menu yang muncul
 * tidak membuktikan jabatannya sudah benar — bisa jadi divisinya yang memberi. Tanpa menyebut
 * asalnya, penguji yang mengatur jabatan lalu melihat menunya muncul akan menyimpulkan hal yang
 * salah, dan kesimpulan itu tidak akan pernah terbantah oleh layar.
 */
data class AccessDecision(
    val config: ModuleAccessConfig,
    val source: AccessSource,
    /** Wewenang dari jabatan saja, mengabaikan divisi. Inilah yang diuji saat menguji jabatan. */
    val fromRole: ModuleAccessConfig,
    /** Wewenang dari divisi saja, mengabaikan jabatan. */
    val fromDepartment: ModuleAccessConfig
) {
    /** True bila menu terbuka **hanya** karena divisinya, bukan karena jabatannya. */
    val grantedByDepartmentOnly: Boolean
        get() = source == AccessSource.DEPARTMENT && !fromRole.isAccessible

    /** True bila modulnya memang belum disambungkan ke tenant, bukan soal wewenang orangnya. */
    val blockedByEntitlement: Boolean get() = source == AccessSource.NOT_ENTITLED
}

object AccessDecisionEngine {

    /**
     * Wewenang efektif untuk satu modul.
     *
     * @param persona identitas yang sedang disimulasikan
     * @param role jabatan persona; null berarti hak hanya datang dari divisi
     * @param assignments seluruh assignment divisi untuk modul ini (semua divisi, disaring di sini)
     */
    fun evaluate(
        persona: TestingPersona,
        module: BusinessModule,
        role: CustomRole?,
        assignments: List<DepartmentModuleAssignment>,
        grantedModules: Set<BusinessModule>? = null
    ): ModuleAccessConfig = explain(persona, module, role, assignments, grantedModules).config

    /**
     * Sama seperti [evaluate], tetapi ikut menyebut **dari mana** wewenangnya datang.
     *
     * Dipakai layar pengujian: untuk membuktikan sebuah *jabatan* sudah benar dikonfigurasi, tidak
     * cukup melihat menunya muncul — harus terlihat bahwa yang memunculkannya memang jabatan itu.
     */
    fun explain(
        persona: TestingPersona,
        module: BusinessModule,
        role: CustomRole?,
        assignments: List<DepartmentModuleAssignment>,
        grantedModules: Set<BusinessModule>? = null
    ): AccessDecision {
        val roleAccess = (role?.getAccess(module) ?: ModuleAccessConfig(AccessLevel.NONE))
            .sanitizeFor(module)

        // Superadmin platform melewati batasan paket (entitlement) maupun matriks wewenang.
        // Superadmin adalah pengelola SaaS yang bertugas mengonfigurasi seluruh tenant, termasuk
        // menyambung/memutus modul via RBAC dan billing. Menunya tidak boleh ikut hilang ketika
        // modul diputus dari tenant.
        if (persona.isPlatformSuperAdmin) {
            return AccessDecision(
                config = ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                    .sanitizeFor(module),
                source = AccessSource.SUPERADMIN_BYPASS,
                fromRole = roleAccess,
                fromDepartment = ModuleAccessConfig(AccessLevel.NONE)
            )
        }

        // Entitlement tenant diperiksa **sebelum** apa pun, termasuk sebelum bypass Owner pabrik.
        //
        // Urutannya menentukan artinya: modul yang tidak disambungkan ke sebuah pabrik bukan modul
        // yang "Owner-nya berwenang tapi stafnya tidak" — ia tidak ada untuk pabrik itu. Kalau
        // bypass Owner diletakkan lebih dulu, memutus modul lewat billing tidak akan berpengaruh
        // apa pun bagi orang yang paling sering memakai sistem.
        //
        // `null` berarti "entitlement belum diketahui" — misalnya panggilan lama, atau layar yang
        // dimuat sebelum permintaan entitlement selesai. Di keadaan itu perilakunya sengaja
        // dikembalikan seperti semula, supaya kegagalan jaringan tidak tampil sebagai pencabutan
        // langganan.
        if (grantedModules != null && module !in grantedModules) {
            val denied = ModuleAccessConfig(AccessLevel.NONE)
            return AccessDecision(
                config = denied,
                source = AccessSource.NOT_ENTITLED,
                fromRole = roleAccess,
                fromDepartment = denied
            )
        }
        val departmentMatch = resolveDepartmentAccess(persona, assignments)
        val departmentAccess = departmentMatch
            ?.let { ModuleAccessConfig(it.accessLevel, it.scope) }
            ?.sanitizeFor(module)
            ?: ModuleAccessConfig(AccessLevel.NONE)

        // Owner dan superadmin melewati matriks sepenuhnya. Bukan pintasan kenyamanan: tanpa ini,
        // admin bisa mengunci dirinya sendiri keluar dari layar RBAC dan kehilangan satu-satunya
        // tempat untuk membukanya kembali.
        if (persona.isOwnerOrSuperAdmin) {
            return AccessDecision(
                config = ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                    .sanitizeFor(module),
                source = AccessSource.OWNER_BYPASS,
                fromRole = roleAccess,
                fromDepartment = departmentAccess
            )
        }

        val departmentWins = departmentAccess.level.weight > roleAccess.level.weight
        val winner = if (departmentWins) departmentAccess else roleAccess

        // sanitizeFor() bukan hiasan: modul GLOBAL_ONLY (inventaris, HPP, jadwal mesin) tidak punya
        // konsep "data milik siapa". Scope sempit yang lolos ke sana membuat layar tampak kosong
        // dan dilaporkan sebagai kerusakan sistem.
        return AccessDecision(
            config = winner.sanitizeFor(module),
            source = when {
                !winner.isAccessible -> AccessSource.NONE
                departmentWins -> AccessSource.DEPARTMENT
                else -> AccessSource.ROLE
            },
            fromRole = roleAccess,
            fromDepartment = departmentAccess
        )
    }

    /** Versi batch dari [explain] — dipakai layar pengujian untuk menandai menu per asalnya. */
    fun explainAll(
        persona: TestingPersona,
        roles: List<CustomRole>,
        assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
        grantedModules: Set<BusinessModule>? = null
    ): Map<BusinessModule, AccessDecision> {
        val role = persona.roleId?.let { id -> roles.firstOrNull { it.id == id } }
        return BusinessModule.entries.associateWith { module ->
            explain(persona, module, role, assignments[module].orEmpty(), grantedModules)
        }
    }

    /** Versi batch — inilah yang dipakai untuk menyusun menu navigasi sekali jalan. */
    fun evaluateAll(
        persona: TestingPersona,
        roles: List<CustomRole>,
        assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
        grantedModules: Set<BusinessModule>? = null
    ): Map<BusinessModule, ModuleAccessConfig> =
        explainAll(persona, roles, assignments, grantedModules).mapValues { it.value.config }

    /**
     * Assignment divisi yang benar-benar berlaku untuk persona ini.
     *
     * Sebuah divisi boleh punya beberapa assignment untuk modul yang sama: satu untuk seluruh
     * jabatan, dan yang lain menyasar jabatan tertentu. Yang diambil adalah yang **tertinggi**
     * di antara yang cocok, konsisten dengan aturan union di atas.
     */
    private fun resolveDepartmentAccess(
        persona: TestingPersona,
        assignments: List<DepartmentModuleAssignment>
    ): DepartmentModuleAssignment? {
        val departmentId = persona.departmentId ?: return null
        return assignments
            .filter { it.departmentId == departmentId }
            .filter { it.appliesToAllRoles || persona.roleId?.value in it.specificRoleIds }
            .maxByOrNull { it.accessLevel.weight }
    }
}
