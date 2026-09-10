# 🎓 Modul Pembelajaran: Penyimpanan Persisten Alur Dinamis per Tenant (Composable Workflow DAG Engine)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Multi-Tenant Workflow Engine, Directed Acyclic Graph (DAG) Storage, PostgreSQL JSONB Persistence, Ktor REST API, Flyway Migration & Row-Level Security (RLS)  
> **Prasyarat**: Dasar Kotlin, JetBrains Exposed, konsep HTTP API di Ktor, dan Domain-Driven Design (DDD).  
> **Referensi Task**: Penyimpanan Alur Kerja Kustom per Tenant (WeMade ERP Issue #21)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan kamu membangun fitur drag-and-drop workflow seperti n8n, Zapier, atau Shopify Flow, tetapi di dalam sistem ERP pabrik konveksi.

- **Tenant A** ingin alurnya: `Order Masuk` $\rightarrow$ `Beli Bahan` $\rightarrow$ `Potong` $\rightarrow$ `Jahit` $\rightarrow$ `Packing`.
- **Tenant B** (makloon) ingin alurnya: `Penerimaan Kain Titipan` $\rightarrow$ `Hitung SAM Jasa` $\rightarrow$ `Jahit` $\rightarrow$ `Retur Kain Perca`.

### Masalah Nyata Jika Memakai Tabel Relasional Kaku
Jika kamu mencoba membuat tabel terpisah untuk setiap koneksi garis dan tipe modul:
- Setiap kali tenant menambahkan modul kustom baru atau memindahkan urutan kabel, kamu harus mengubah skema relasi database (rentan *lock contention* dan migrasi kompleks).
- Skema menjadi sangat kaku dan sulit mendukung parameter rumus dinamis (seperti koefisien HPP atau margin unik per tenant).

### Solusi Elegan: Hybrid Relational + JSONB Workflow Storage
Kita menyimpan identitas kepemilikan tenant secara relasional di PostgreSQL (`tenant_id`, `pipeline_name`, `base_preset`), sedangkan topologi graf (*nodes*, *edges*, dan *customFormulaParameters*) disimpan sebagai dokumen terstruktur **JSONB**. Pendekatan ini memberi fleksibilitas 100% tanpa mengorbankan performa kueri maupun keamanan Row-Level Security (RLS).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengoding fitur penyimpanan alur dinamis ini dari nol, ikuti roadmap ini:

```
┌────────────────────────────────────────────────────────┐
│ 1. Core Domain: Entity CustomTenantPipeline & Helper   │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 2. Core Domain: TenantPipelineRepository (Interface)   │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 3. Core Domain: 3 Use Cases (Get, Save, Reset)         │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 4. Unit Test Domain: Fake Repository & Validasi Graph  │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 5. Server DB: Flyway SQL Migration (V8) & Exposed Table│
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 6. Server Infra: InMemory & Postgres Repositories      │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 7. Server API: PipelineRoutes (GET, PUT, POST Reset)   │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ 8. Server Integration Test: PipelineApiTest (200 OK)   │
└────────────────────────────────────────────────────────┘
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Fallback Otomatis Saat Tenant Baru Pertama Kali Buka Alur

```kotlin
class GetTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val existing = pipelineRepository.findByTenantId(tenantId)
        if (existing != null) {
            existing
        } else {
            // Jika tenant belum pernah mengotak-atik alur, sintesis dari preset awal
            val initialPipeline = CustomTenantPipeline.fromPreset(tenantId, fallbackPreset)
            pipelineRepository.save(initialPipeline).getOrThrow()
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- **Zero-Friction Onboarding**: User yang baru mendaftar tidak akan mendapatkan layar kosong atau error 404. Sistem otomatis menyusun graf default berdasarkan model bisnisnya, menyimpannya, lalu menampilkannya di UI kanvas.

---

### Blok B: Validasi Integritas Graf Sebelum Disimpan

```kotlin
class SaveTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> = runCatching {
        require(pipeline.nodes.isNotEmpty()) { "Pipeline must contain at least one operational node" }
        require(pipeline.pipelineName.isNotBlank()) { "Pipeline name cannot be blank" }

        val nodeIds = pipeline.nodes.map { it.nodeId }
        val duplicateIds = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate node IDs detected in pipeline: $duplicateIds" }

        // Pastikan setiap ujung edge merujuk ke node yang benar-benar ada
        val validNodeIdSet = nodeIds.toSet()
        pipeline.edges.forEach { edge ->
            require(validNodeIdSet.contains(edge.fromNodeId)) { "Edge source missing: ${edge.fromNodeId}" }
            require(validNodeIdSet.contains(edge.toNodeId)) { "Edge target missing: ${edge.toNodeId}" }
        }

        pipelineRepository.save(pipeline).getOrThrow()
    }
}
```
**Mengapa validasi ini krusial?**
- Mencegah *dangling edges* (kabel koneksi yang menuju ke node hantu/sudah dihapus) yang bisa menyebabkan crash pada render visualisasi Bezier canvas di frontend.

---

### Blok C: Skema Database Flyway dengan Multi-Tenant RLS

```sql
CREATE TABLE IF NOT EXISTS tenant_pipelines (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    pipeline_name VARCHAR(100) NOT NULL,
    base_preset VARCHAR(50),
    graph_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_pipeline UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_pipelines_tenant ON tenant_pipelines(tenant_id);

-- Aktifkan PostgreSQL Row-Level Security
SELECT apply_tenant_rls('tenant_pipelines');
```
**Mengapa memakai `apply_tenant_rls`?**
- Menjamin keamanan level database engine: Pabrik A tidak akan pernah bisa mengintip atau menimpa alur kerja rahasia milik Pabrik B, bahkan jika ada bug di query aplikasi.

---

### Blok D: Parser JSON Bersarang dengan Pelacak Kedalaman Kurung (*Brace Depth Tracking*)

```kotlin
fun splitJsonObjects(arrayJson: String): List<String> {
    val list = mutableListOf<String>()
    var depth = 0
    var startIndex = -1
    var inQuotes = false
    var escapeNext = false

    for (i in arrayJson.indices) {
        val c = arrayJson[i]
        if (escapeNext) { escapeNext = false; continue }
        if (c == '\\') { escapeNext = true; continue }
        if (c == '"') { inQuotes = !inQuotes; continue }
        if (!inQuotes) {
            if (c == '{') {
                if (depth == 0) startIndex = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && startIndex != -1) {
                    list.add(arrayJson.substring(startIndex, i + 1))
                    startIndex = -1
                }
            }
        }
    }
    return list
}
```
**Mengapa regex sederhana gagal di sini?**
- Setiap node memiliki properti `customFormulaParameters: {"samRate":"750","margin":"0.2"}`. Jika memakai regex rakus biasa, ia akan terpotong pada tanda kurung kurawal pertama di dalam formula. Pelacak kedalaman (*depth counter*) memastikan objek JSON bersarang (*nested objects*) diekstrak secara utuh.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Terpilih | Alternatif Lain | Mengapa Memilih Pendekatan Ini? | Risiko Alternatif Lain |
|---|---|---|---|
| **JSONB Column untuk Graph Topologies** | Tabel EAV (*Entity-Attribute-Value*) untuk setiap parameter modul | Kueri cepat, satu kali baca langsung dapat seluruh struktur graf tanpa *multi-table joins*. | Memerlukan 4-5 tabel relasional terpisah hanya untuk menyimpan satu rantai kabel graf alur. |
| **In-Memory Cache & Pre-seed Starter** | Setiap tes harus menyalakan database Postgres nyata | Unit test dan integrasi berjalan dalam hitungan milidetik (test suite tuntas dalam 2 detik). | CI/CD build lambat dan rentan *timeout* saat koneksi database lokal terkendala. |
| **Idempotent Synthesis pada GET** | Endpoint inisialisasi terpisah (`/init-pipeline`) | Frontend tidak perlu repot memeriksa apakah alur sudah ada atau belum. Cukup panggil `GET /api/tenant/pipeline`. | Kemungkinan alur kosong (*null pointer*) jika frontend lupa memanggil inisialisasi. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menyimpan state runtime (WIP / Cycle Time) ke dalam tabel pipeline**
   - *Kenapa salah*: Struktur alur adalah **definisi konfigurasi (metadata)**, bukan **data transaksi harian**. Jika angka WIP disimpan di sini, setiap ada potongan kain yang lewat kamu harus me-write ulang dokumen JSONB.
   - *Solusi*: Tabel `tenant_pipelines` hanya menyimpan definisi node dan rumus. Metrik real-time diambil secara dinamis dari tabel SPK / transaksi kerja.
2. **Jebakan 2: Lupa menerapkan constraint unik pada `tenant_id`**
   - *Kenapa bahaya*: Satu tenant bisa memiliki duplikat pipeline aktif yang saling menimpa secara acak saat dimuat.
   - *Solusi*: Tambahkan `CONSTRAINT uq_tenant_pipeline UNIQUE (tenant_id)`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Jalankan perintah pengujian gabungan:
```bash
./gradlew compileKotlinMetadata :core:jvmTest :server:test
```

Hasil verifikasi:
1. `GetTenantPipelineUseCaseTest`: Menguji sintesis otomatis alur default dari template preset.
2. `SaveTenantPipelineUseCaseTest`: Menguji validasi pencegahan duplikasi ID node dan edge hantu.
3. `PipelineApiTest`: Menguji panggilan HTTP nyata (`GET /api/tenant/pipeline`, `PUT /api/tenant/pipeline`, dan `POST /api/tenant/pipeline/reset`) dengan respons `200 OK`.
