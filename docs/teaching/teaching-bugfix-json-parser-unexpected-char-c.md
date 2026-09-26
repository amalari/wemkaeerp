# 🎓 Modul Pembelajaran: Bugfix Penanganan Error HTTP Client & JsonParser Crash (`Unexpected character 'c'`)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: HTTP Client Architecture, Resilient JSON Deserialization, Failure Handling, KMP (Kotlin Multiplatform)  
> **Prasyarat**: Ktor Client, JSON Serialization basics, Result pattern di Kotlin  
> **Referensi File**: [`ProcessCatalogApiClient.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/ProcessCatalogApiClient.kt) & [`JsonParser.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/shared/json/JsonParser.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat membuka halaman atau modal detail, aplikasi tiba-tiba menampilkan badge merah misterius:
```
Unexpected character 'c' (at offset 0)
```
Bagi pengguna akhir atau operator garmen, pesan ini tidak bermakna sama sekali. Bagi junior developer, pesan ini membingungkan karena seolah-olah data JSON di backend memiliki sintaks rusak.

Padahal kenyataannya:
- Server atau proxy jaringan sedang mengembalikan respon non-200 (misalnya `502 Bad Gateway`, `connect ECONNREFUSED`, atau `401 Unauthorized`) dalam bentuk **Plain Text**.
- Kode HTTP client di sisi frontend langsung menyuapkan teks mentah tersebut ke `JsonParser` **sebelum memeriksa status response code**.
- Karena string error diawali huruf `'c'` (misal dari kata `connect` atau `cannot`), parser JSON langsung meledak di offset 0.

### Analogi Sederhana
Bayangkan Anda menerima paket kurir. Di paket tertulis surat peringatan dari kurir: *"Catatan: Alamat penerima sedang tutup"*. Bukannya membaca surat kurir terlebih dahulu, Anda langsung memasukkan kertas itu ke mesin penghitung uang (yang hanya menerima uang kertas nominal rupiah). Mesin penghitung uang langsung macet dan berbunyi keras: *"Benda asing tak dikenal di sensor 0!"*.

---

## 🧭 2. "Start dari Mana?" — Alur Investigasi & Penulisan

Ketika menemukan error parser seperti `Unexpected character 'x' (at offset N)`:

1. **Step 1 — Identifikasi Sumber Exception**:
   Cari teks `Unexpected character` di codebase. Ditemukan bahwa exception ini berasal dari [`JsonParser.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/shared/json/JsonParser.kt):
   ```kotlin
   else -> if (char == '-' || char in '0'..'9') readNumber() else fail("Unexpected character '$char'")
   ```
2. **Step 2 — Telusuri Call Stack / Titik Pemanggilan**:
   Badge error muncul di panel Alur Proses ([`ProcessFlowAdjusterPanel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/ProcessFlowAdjusterPanel.kt)), yang diisi oleh `state.error` dari [`ProcessFlowViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/ProcessFlowViewModel.kt). ViewModel tersebut memanggil remote client [`ProcessCatalogApiClient.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/ProcessCatalogApiClient.kt).
3. **Step 3 — Bedah Anti-Pattern di HTTP Client**:
   Perhatikan fungsi `decodeBody`:
   ```kotlin
   // ❌ Anti-pattern: Parser dipanggil sebelum memeriksa isSuccess
   private fun decodeBody(text: String, isSuccess: Boolean): JsonValue.Obj {
       val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid JSON response: $text")
       if (!isSuccess) error("HTTP error: ${obj.string("error") ?: text}")
       return obj
   }
   ```
4. **Step 4 — Terapkan Guard Clause & Resilient Parsing**:
   Cek `isSuccess` terlebih dahulu. Bila request gagal, ekstrak pesan error dari JSON (bila respon error berformat JSON) atau gunakan plain text yang ada. Jangan pernah memaksakan strict JSON parser jika payload berpotensi berupa plain text error.

---

## 🔬 3. Bedah Kode Blok per Blok

### Sebelum (Rentan Meledak):
```kotlin
private fun decodeBody(text: String, isSuccess: Boolean): JsonValue.Obj {
    // 💥 Meledak jika text bukan JSON (misal "connect ECONNREFUSED")
    val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid JSON response: $text")
    if (!isSuccess) error("HTTP error: ${obj.string("error") ?: text}")
    return obj
}
```

### Sesudah (Aman & Informatif):
```kotlin
private fun decodeBody(text: String, isSuccess: Boolean): JsonValue.Obj {
    if (!isSuccess) {
        // Coba baca field "error" jika server mengirim JSON error, 
        // tapi jangan biarkan meledak jika respon adalah plain text!
        val errorMsg = runCatching {
            JsonParser.parseObjectOrNull(text)?.string("error")
        }.getOrNull() ?: text.takeIf { it.isNotBlank() } ?: "Terjadi kesalahan pada server"
        error(errorMsg)
    }
    // Jika isSuccess == true, baru kita harapkan payload JSON yang valid
    return JsonParser.parseObjectOrNull(text) ?: error("Format JSON tidak valid: $text")
}
```

### Mental Model di Balik Perubahan:
1. **`JsonParser.parseObjectOrNull` vs `JsonParser.parse`**:
   `parseObjectOrNull` adalah varian *lenient* yang mengembalikan `null` jika string bukan JSON, alih-alih melempar exception `JsonParseException`. Ini mencegah crash saat response body berupa teks bebas.
2. **Prioritas Status HTTP**:
   Status HTTP (`response.status.isSuccess()`) adalah kontrak layer protokol (Layer 7). Selalu validasi kontrak layer protokol sebelum masuk ke deserialisasi layer aplikasi (JSON parsing).

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

| Jebakan | Mengapa Berbahaya | Solusi Benar |
|---|---|---|
| Mem-parse response tanpa cek status code | Saat server mengembalikan 401/403/500 dalam bentuk HTML/Plain text, parser JSON meledak dengan pesan asing. | Selalu evaluasi `status.isSuccess()` terlebih dahulu. |
| Menampilkan pesan teknis raw ke UI (`state.error = exception.message`) | Operator melihat `Unexpected character 'c' at offset 0` alih-alih `Koneksi ke backend terputus`. | Format error menjadi pesan manusiawi di boundary Presenter/ViewModel. |
| Mengabaikan hot-reload dev server | Di environment lokal, continuous build atau restart server sesaat memicu `connect ECONNREFUSED` dari proxy webpack. | HTTP Client harus anggun menangani respon proxy jaringan. |

---

## 🧪 5. Verifikasi Mandiri

1. **Uji Kasus Normal**:
   Buka detail SPK di `http://localhost:3000/sampling-order`. Pastikan alur proses SPK berhasil dimuat dan badge status menampilkan alur yang benar (`Mengikuti Alur Default`).
2. **Uji Kasus Server Down / Disconnect**:
   Hentikan backend Ktor (`localhost:8080`), lalu buka kembali modal SPK. Pastikan UI tidak lagi menampilkan error kriptik `Unexpected character 'c'`, melainkan error koneksi server yang jelas dan dapat ditangani.
3. **Kompilasi Multiplatform**:
   Jalankan `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs` untuk memastikan kepatuhan multi-target.
