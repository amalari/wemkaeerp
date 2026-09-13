# 🎓 Modul Pembelajaran: Auto-Bypass Alur Pabrik & Modal Konfirmasi Dampak pada Entitlement Modul Tenant

> **Level Target**: Junior to Mid-Level Fullstack Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Modular Multi-Tenancy, Safe Pipeline Mutation, Compose Multiplatform UI, Neo-Brutalist Claymorphism  
> **Prasyarat**: Kotlin dasar, konsep DDD (Domain Entity, UseCase, Repository), Ktor routing, dan dasar Jetpack Compose / Compose Multiplatform  
> **Referensi Task**: Opsi 2 — Safe Auto-Bypass & Impact Confirmation Modal on Module Revocation

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan sebuah pabrik konveksi (*tenant*) berlangganan SaaS WeMade ERP. Pabrik ini memiliki alur operasional produksi:
$$\text{Pola \& Potong} \longrightarrow \text{Jahit (Sewing)} \longrightarrow \text{QC} \longrightarrow \text{Packing \& Surat Jalan}$$

Suatu hari, pabrik tersebut berhenti membayar add-on modul **Packing & Surat Jalan** (atau Superadmin platform ingin mencabut akses modul tersebut karena downgrade paket langganan). 
Jika Superadmin membuka dialog **Kelola Akses Modul Tenant** dan mematikan centang modul Packing:
- **Pendekatan Naif 1 (Strict Reject)**: Sistem melempar error: *"Entitlement ini membuat alur tenant menjadi tidak valid. Nonaktifkan modul terkait pada alur tenant lebih dulu."*
  - **Dampak UX**: Superadmin frustrasi! Kenapa ada toggle pemutus modul jika ditekan malah melempar error? Superadmin dipaksa keluar dari modal, membuka diagram alur pabrik, mencari modul, memutus manual, lalu kembali lagi ke dialog entitlement.
- **Pendekatan Naif 2 (Hard Delete / Silent Cut)**: Sistem langsung menghapus node *Packing* dari diagram alur tanpa konfirmasi.
  - **Dampak Fatal**: Jika lantai pabrik sedang memproses 500 jaket yang berada di tahap packing, status batch pesanan bisa rusak (*orphaned WIP*), dan semua kustomisasi nama atau parameter perhitungan yang disetel tenant pada node tersebut musnah permanen.

### Solusi Elegan Kita (Opsi 2 dengan Modal Konfirmasi & Non-Destructive Bypass)
1. **Di UI**: Dialog mendeteksi apakah modul yang dicabut sedang aktif di kanvas diagram alur tenant. Jika iya, saat tombol **Simpan** ditekan, sistem menampilkan **Modal Konfirmasi Dampak (*Impact Confirmation View*)**.
2. **Di Domain Backend**: Sistem tidak menghapus node secara destruktif, melainkan menandainya sebagai `isBypassed = true`. Alur tenant tetap utuh, koneksi graph tetap konsisten, kuota lisensi langsung berkurang, dan jika di kemudian hari tenant berlangganan kembali, tahapan tersebut dapat langsung diaktifkan kembali tanpa konfigurasi ulang dari nol!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta mengerjakan fitur semacam ini dari layar kosong, **jangan pernah mulai dari tombol UI atau query database**. Ikuti urutan DDD:

```
[Langkah 1: Domain Core]
  └── SetTenantEntitlementUseCase.kt (Tambahkan parameter autoBypassPipelineModules & logika bypass)
  └── TenantEntitlementUseCaseTest.kt (Tulis unit test pembuktian di level domain murni)
        ↓
[Langkah 2: Backend Server]
  └── AdminRoutes.kt (Tangkap query parameter ?autoBypass=true dan catat ke Audit Trail)
        ↓
[Langkah 3: Client Data Layer]
  └── AdminApiClient.kt (Tambahkan parameter autoBypass pada fungsi setEntitlement)
        ↓
[Langkah 4: Presentation / UI Layer]
  └── TenantModuleEntitlementDialog.kt (Deteksi modul aktif di alur, tag peringatan, & Modal Konfirmasi)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain UseCase — Non-Destructive Auto-Bypass (`core`)

Lokasi: [`core/src/commonMain/kotlin/.../pipeline/usecases/SetTenantEntitlementUseCase.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/usecases/SetTenantEntitlementUseCase.kt)

```kotlin
suspend operator fun invoke(
    tenantId: TenantId,
    tier: SubscriptionTier,
    grants: TenantEntitlementGrants,
    autoBypassPipelineModules: Boolean = false
): Result<TenantModuleEntitlement> = runCatching {
    val resolved = TenantModuleEntitlement.resolve(tier, grants)

    pipelineRepository.findByTenantId(tenantId)?.let { pipeline ->
        if (!pipeline.isEmpty) {
            var currentPipeline = pipeline
            var pipelineModified = false

            if (autoBypassPipelineModules) {
                // 1. Identifikasi node aktif di alur yang tidak lagi diizinkan oleh paket baru
                val unpermittedActiveNodes = pipeline.activeNodes.filterNot { resolved.permits(it) }
                if (unpermittedActiveNodes.isNotEmpty()) {
                    for (node in unpermittedActiveNodes) {
                        currentPipeline = currentPipeline.setNodeBypassed(node.nodeId, isBypassed = true)
                    }
                    pipelineModified = true
                }
            }

            // 2. Simetri saat modul disambungkan kembali (re-grant): aktifkan kembali node yang di-bypass
            val bypassedNodesToRestore = currentPipeline.bypassedNodes.filter { resolved.permits(it) }
            for (node in bypassedNodesToRestore) {
                val candidate = currentPipeline.setNodeBypassed(node.nodeId, isBypassed = false)
                if (resolved.validate(candidate).isEmpty()) {
                    currentPipeline = candidate
                    pipelineModified = true
                }
            }

            if (pipelineModified) {
                pipelineRepository.save(currentPipeline).getOrThrow()
            }

            // 3. Validasi ulang — pastikan tidak ada pelanggaran kuota atau izin
            val violations = resolved.validate(currentPipeline)
            require(violations.isEmpty()) {
                "Entitlement ini membuat alur tenant yang sedang berjalan menjadi tidak valid: " +
                    violations.joinToString(" ") +
                    " Nonaktifkan modul terkait pada alur tenant lebih dulu."
            }
        }
    }

    entitlementRepository.save(tenantId, grants).getOrThrow()
    resolved
}
```

**Mengapa ditulis begini?**
- `autoBypassPipelineModules = false` (default): Menjaga *backward-compatibility*. Pemanggil lama yang tidak sengaja mengirim request tidak akan mengubah alur tenant tanpa izin eksplisit.
- `currentPipeline.setNodeBypassed(node.nodeId, isBypassed = true)`: Kita memanfaatkan immutability domain entity (`CustomTenantPipeline`). Graph tidak dihapus; node hanya di-flag *bypassed*.
- `resolved.validate(currentPipeline)`: Dalam aturan domain kita, `node.isBypassed -> true` berarti node tersebut tidak lagi memakan lisensi ataupun kuota modul aktif.

---

### Blok B: Server Endpoint & Audit Trail (`server`)

Lokasi: [`server/src/main/kotlin/.../routes/AdminRoutes.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/routes/AdminRoutes.kt)

```kotlin
put("/entitlement") {
    val tenant = call.requireTargetTenant(tenantRepository) ?: return@put
    ...
    val autoBypass = call.request.queryParameters["autoBypass"]?.toBooleanStrictOrNull() ?: false

    setEntitlementUseCase(tenant.id, tenant.tier, grants, autoBypassPipelineModules = autoBypass)
        .onSuccess {
            val bypassNote = if (autoBypass) " (auto-bypass alur aktif)" else ""
            call.recordAudit(
                auditLogRepository = auditLogRepository,
                actor = actor,
                tenant = tenant,
                action = AuditAction.TENANT_ENTITLEMENT_UPDATED,
                summary = "Mengubah entitlement modul tenant '${tenant.slug.value}'$bypassNote: " +
                    "modul bawaan=${grants.grantedModules?.size ?: "semua"}, " +
                    "modul kustom=${grants.grantedCustomModuleIds}"
            )
            respondAdminView(call, tenant, getEntitlementUseCase, getModuleCatalogUseCase)
        }
        .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengubah entitlement") }
}
```

**Mengapa ditulis begini?**
- **Audit Logging**: Mencabut modul dari pabrik tanpa sepengetahuan operator lokal adalah tindakan sensitif. Dengan menyertakan keterangan `(auto-bypass alur aktif)` pada catatan audit, siapa pun yang mengaudit sistem mengetahui bahwa alur pabrik diubah atas aksi Superadmin.

---

### Blok C: Presentation UI — Deteksi & Modal Konfirmasi (`app/shared`)

Lokasi: [`app/shared/src/commonMain/kotlin/.../tenant/TenantModuleEntitlementDialog.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/tenant/TenantModuleEntitlementDialog.kt)

```kotlin
val activePipelineModules = remember(view) {
    view?.catalog?.modules
        ?.filter { it.isActive && it.standardModule != null }
        ?.mapNotNull { it.standardModule }
        ?.toSet() ?: emptySet()
}

val modulesToAutoBypass = remember(draft, view, activePipelineModules) {
    val currentDraft = draft ?: return@remember emptyList<BusinessModule>()
    val originalGranted = view?.grantedModules ?: return@remember emptyList<BusinessModule>()
    (originalGranted - currentDraft).filter { it in activePipelineModules }
}
```

Saat tombol **Simpan** diklik:
```kotlin
ClayButton(
    text = if (isBusy) "Menyimpan…" else "Simpan Entitlement",
    onClick = {
        if (modulesToAutoBypass.isNotEmpty()) {
            showImpactConfirmation = true
        } else {
            executeSave(autoBypass = false)
        }
    }
)
```

**Mengapa ditulis begini?**
- **Zero Interruption jika Aman**: Jika Superadmin hanya memutus modul yang memang *tidak* aktif di alur (misal modul tata kelola atau modul yang belum diinstal tenant), tombol langsung menyimpan seketika tanpa modal konfirmasi.
- **Intersepsi Cerdas**: Modal konfirmasi hanya muncul saat ada modul aktif di diagram alur yang akan terpengaruh.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Desain | Alternatif | Mengapa Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| **Non-Destructive Bypass (`isBypassed = true`)** | Hapus Node (`removeNode()`) | Mempertahankan wiring edge, ID node, dan kustomisasi nama saat modul diaktifkan lagi di masa depan. | Node terhapus permanen; jika tenant berlangganan lagi, operator harus menggambar ulang alur dari nol. |
| **Modal Konfirmasi Berdampak** | Silent Auto-Bypass | Superadmin sadar penuh bahwa aksinya akan menonaktifkan tahapan di lantai pabrik tenant. | Superadmin tidak sadar bahwa pesanan yang sedang berjalan di pabrik kehilangan stasiun kerja. |
| **Query Param `?autoBypass=true`** | Ganti JSON Body Schema | Sangat ramah *backward compatibility* dan tidak merusak kontrak serialisasi DTO yang sudah ada. | Mengubah skema body DTO memaksa semua integrasi klien dan testing lama diubah. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### Jebakan 1: `Modifier.fillMaxSize()` di dalam Unconstrained Column
- **Gejala Bug**: Kotak item modul di modal konfirmasi tiba-tiba membengkak vertikal setinggi 600px dan tombol simpan terlempar keluar layar!
- **Penyebab**: Menulis `Box(modifier = Modifier.width(18.dp))` yang di dalamnya berisi `ModuleIcon(modifier = Modifier.fillMaxSize())`. Karena `Box` tidak menentukan tinggi (`height`), ikon meminta tinggi maksimal dari parent-nya. Di dalam `Column` tanpa batas tinggi, tinggi maksimal adalah tak terhingga!
- **Solusi Benar**: Gunakan ukuran eksplisit 2 dimensi untuk ikon:
  ```kotlin
  // ✅ BENAR
  Box(modifier = Modifier.size(20.dp)) {
      ModuleIcon(iconKey = ..., modifier = Modifier.fillMaxSize())
  }
  ```

### Jebakan 2: Lupa Me-restart Backend Ktor Saat Continuous Compiler Hanya Mengawasi Frontend
- **Gejala**: Kode Kotlin di `core` dan `server` sudah diubah dan unit test lulus, tetapi di browser server masih mengembalikan pesan error lama!
- **Penyebab**: Script `./dev.sh` menjalankan `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous` untuk frontend, tetapi `:server:run` dijalankan satu kali sebagai proses statis.
- **Solusi**: Jika mengubah layer `core` atau `server`, restart task Ktor backend agar bytecode terbaru yang dimuat.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### A. Unit Test di Domain Layer (`core`)
Jalankan test suite domain:
```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.pipeline.TenantEntitlementUseCaseTest"
```
Test kunci yang memvalidasi auto-bypass:
```kotlin
@Test
fun setEntitlement_withAutoBypass_shouldBypassRunningPipelineNodesAndSucceed() = runTest {
    getPipeline(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE).getOrThrow()
    val initialPipeline = pipelineRepository.findByTenantId(tenantId)!!
    val packingNode = initialPipeline.nodes.first { it.moduleId == BusinessModule.FULFILLMENT.code }
    assertFalse(packingNode.isBypassed)

    // Revoke fulfillment dengan autoBypassPipelineModules = true
    val result = setEntitlement(
        tenantId = tenantId,
        tier = SubscriptionTier.PRO,
        grants = TenantEntitlementGrants(grantedModules = BusinessModule.entries.toSet() - BusinessModule.FULFILLMENT),
        autoBypassPipelineModules = true
    )

    assertTrue(result.isSuccess)
    val updatedPackingNode = pipelineRepository.findByTenantId(tenantId)!!
        .nodes.first { it.moduleId == BusinessModule.FULFILLMENT.code }
    assertTrue(updatedPackingNode.isBypassed, "Packing node wajib otomatis di-bypass")
}
```

### B. End-to-End Browser Verification
1. Buka `http://localhost:3000/rbac` sebagai Superadmin.
2. Klik tombol kelola modul tenant (ikon tumpukan layer).
3. Matikan modul **Packing & Surat Jalan** -> Muncul tag **Aktif di Alur** dan badge **Diputus**.
4. Klik **Simpan Entitlement** -> Muncul **Konfirmasi Pemutusan Modul**.
5. Klik **Ya, Nonaktifkan dari Alur & Simpan**.
6. Modal tertutup, kuota modul produksi berkurang menjadi `8/9 modul produksi`, dan modul tersimpan sebagai **Diputus** tanpa error!

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Di `TenantPipelineProjector`, pelajari bagaimana status `isBypassed` diproyeksikan menjadi badge abu-abu bertuliskan *"Modul dinonaktifkan (bypass) pada konfigurasi alur tenant ini"*.
- [ ] **Tantangan 2**: Buatlah skenario di mana tenant mengaktifkan kembali modul yang sebelumnya di-bypass, dan amati bagaimana `SetTenantModuleActivationUseCase` mengembalikan `isBypassed = false` tanpa merusak relasi upstream dan downstream yang sudah ada.
