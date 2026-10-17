# 🎓 Modul Pembelajaran: Tutorial System dengan DB Storage (TRD-HELP-001)

> **Level Target**: Mid to Senior Developer  
> **Topik Utama**: Tutorial/RAG System, Database Storage, Multiplatform Kotlin, Ktor Routes  
> **Prasyarat**: Memahami DDD, Exposed ORM, Ktor Routing, Repository Pattern  
> **Referensi Task**: Tutorial Coverage (TUT), DB Storage for Help System (HELP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Sistem help/chat assistant di WeMade ERP membutuhkan tutorial yang bisa:
1. Dicari oleh AI helper untuk menjawab pertanyaan user
2. Disimpan di database agar bisa diedit tanpa deploy kode
3. Tersedia untuk semua modul operasional

### Analogi Sederhana
Tutorial system seperti **perpustakaan dengan kartu indeks**:
- **GarmentTutorials** = daftar buku yang dicetak (shipped with code)
- **Database** = rak perpustakaan tempat buku-buku itu disimpan
- **DbTutorialSource** = pustakawan yang tahu mana buku di rak (DB) mana di gudang (shipped)

### Hasil Akhir yang Diharapkan
- User bisa bertanya ke chat assistant dan mendapat tutorial relevan
- Tutorial tersimpan di DB (V106)
- Admin bisa menambah/edit tutorial tanpa deploy

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

### Langkah 1: Domain Layer (`core/`)
**Mengapa duluan?** Karena ini fondasi type-safe.

1. Buat `ModuleTutorial`, `TutorialScope`, `TutorialStep`, `TutorialAnchorId`, `TutorialId` di `core/src/commonMain/kotlin/com/eventverse/app/domain/tutorial/`
2. Buat interface `TutorialSource` di `TutorialCatalog.kt` — ini kontrak agar implementasi bisa diganti (DB vs shipped)
3. Buat `TutorialRepository` interface di `core/`

```kotlin
// Contoh: TutorialRepository interface
interface TutorialRepository {
    suspend fun findByPack(packCode: DomainPackCode): List<ModuleTutorial>
    suspend fun findAll(): List<ModuleTutorial>
    suspend fun save(tutorial: ModuleTutorial): Unit
    suspend fun saveAll(tutorials: List<ModuleTutorial>)
    suspend fun existsByPack(packCode: DomainPackCode): Boolean
}
```

### Langkah 2: Database Schema (`server/resources/db/migration/`)
1. Buat file `V106__tutorials.sql`
2. Definisikan tabel dengan kolom: id, pack_code, scope_type, scope_code, title, summary, required_level, keywords, steps, sample_questions

```sql
CREATE TABLE tutorials (
    id VARCHAR(64) PRIMARY KEY,
    pack_code VARCHAR(40) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_code VARCHAR(40),
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    required_level VARCHAR(20) NOT NULL,
    keywords VARCHAR(4000),
    steps VARCHAR(16000),
    sample_questions VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Langkah 3: Infrastructure (`server/infrastructure/`)
1. Buat `TutorialsTable.kt` — definisi tabel Exposed
2. Buat `PostgresTutorialRepository.kt` — implementasi `TutorialRepository`
3. Buat `DbTutorialSource.kt` — implementasi `TutorialSource` dengan fallback ke shipped

```kotlin
// DbTutorialSource: membaca dari DB, fallback ke shipped jika kosong
class DbTutorialSource(
    private val repository: PostgresTutorialRepository
) : TutorialSource {
    override fun tutorialsFor(pack: DomainPackCode): List<ModuleTutorial> {
        return runBlocking {
            val dbTutorials = repository.findByPack(pack)
            if (dbTutorials.isNotEmpty()) dbTutorials
            else ShippedTutorialSource.tutorialsFor(pack)
        }
    }
}
```

### Langkah 4: Routes (`server/routes/`)
1. Update `HelpRoutes.kt` untuk menerima `TutorialSource` sebagai parameter
2. Default-nya gunakan `DbTutorialSource`

```kotlin
fun Route.helpRoutes(
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    tutorialSource: TutorialSource = DbTutorialSource(PostgresTutorialRepository()),
    // ...
)
```

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: TutorialSource Interface
```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/tutorial/TutorialCatalog.kt
interface TutorialSource {
    fun tutorialsFor(pack: DomainPackCode): List<ModuleTutorial>
    fun anchorsFor(pack: DomainPackCode): Set<TutorialAnchorId>
}
```

**Mengapa begini?**
- Interface ini memungkinkan implementasi berbeda (DB, file, API external)
- `TutorialCatalog` menggunakan ini untuk composability

### Blok B: TutorialsTable (Exposed)
```kotlin
// server/src/main/kotlin/com/eventverse/app/infrastructure/tables/TutorialsTable.kt
object TutorialsTable : Table("tutorials") {
    val id = varchar("id", 64)
    val packCode = varchar("pack_code", 40)
    // ... kolom lain
    override val primaryKey = PrimaryKey(id)
}
```

**Mengapa begini?**
- Pakai Exposed karena konsisten dengan repo lain
- Simpan sebagai varchar untuk fields yang array (comma-separated)

### Blok C: PostgresTutorialRepository
```kotlin
// server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresTutorialRepository.kt
class PostgresTutorialRepository : TutorialRepository {
    override suspend fun findByPack(packCode: DomainPackCode) = DatabaseFactory.dbQuery {
        TutorialsTable.selectAll()
            .where { TutorialsTable.packCode eq packCode.value }
            .map { toTutorial(it) }
    }
}
```

**Mengapa begini?**
- Gunakan `DatabaseFactory.dbQuery` untuk transaction management
- Mapping manual dari ResultRow ke domain model

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi | Alternatif | Mengapa Pilih Ini | Risiko |
|---|---|---|---|
| **Exposed ORM** | JOOQ, raw JDBC | Konsisten dengan repo lain di codebase | Query kompleks butuh care |
| **VARCHAR untuk array** | JSONB, ARRAY column | Lebih简单 untuk query sederhana | Parsing manual |
| **runBlocking di TutorialSource** | Make interface suspend | TutorialSource harus sync (dipakai di many places) | Blocking thread pool |
| **Fallback ke shipped** | Error jika DB kosong | Migrasi halus, tidak butuh data awal | Terlalu reliance ke shipped |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Suspension di TutorialSource
**Masalah**: `TutorialSource` adalah interface sync, tapi repository方法是 suspend
**Solusi**: Gunakan `runBlocking` di `DbTutorialSource` — ini acceptable karena hanya dipanggil sekali per startup

### Jebakan 2: Missing imports SQL
**Masalah**: `and`, `eq` dari Exposed butuh import yang tepat
**Solusi**:
```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
```

### Jebakan 3: Column Reference di Exposed update/insert
**Masalah**: `it[columnName] = value` tidak worksempre
**Solusi**: Pakai full reference `it[TutorialsTable.columnName] = value`

---

## 🧪 6. Bagaimana Membuktikan Kodingan Bekerja?

### Test Manual
```bash
# Start server
./gradlew :server:run

# Test API
curl -X POST http://localhost:3001/api/tenant/help/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "bagaimana cara membuat lead baru?"}'
```

### Test Coverage
- `TutorialCoverageTest` — memverifikasi semua modul punya tutorial
- Test harus fail jika modul baru tidak punya tutorial

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah tutorial untuk modul baru (follow rules di module-integration-rules.md §6)
- [ ] **Tantangan 2**: Implementasikan admin UI untuk edit tutorial di DB
- [ ] **Tantangan 3**: Tambahkan support untuk multi-language tutorial
