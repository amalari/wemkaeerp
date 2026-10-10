# Teaching: Penjaga Glyph Latin-1 untuk Teks UI

## Masalah
Font Nunito yang dibundel hanya punya glyph U+0000-U+00FF (Latin-1). Panah, bullet, elipsis,
em/en dash, centang/silang, dan emoji di literal string UI tampil sebagai KOTAK. Bug ini tidak
tertangkap kompilator maupun tes biasa; hanya terlihat dengan mata.

## Step 0 - Pahami apa yang aman
- Aman: ASCII dan Latin-1, termasuk `·` (U+00B7), `°`, `±`, huruf beraksen.
- Tidak aman: `—` `–` `…` `•` `→` `←` `➔` `✕` `⚠` `≥` `≠` `“ ”` dan emoji.

## Step 1 - Aturan penggantian
| Dulu | Sekarang |
|---|---|
| `—` / `–` | ` - ` / `-` |
| `…` | `...` |
| `•` | `·` |
| `→` `➔` / `←` | `->` / `<-` (atau ikon vektor `ClayIcons` bila fungsional) |
| `≥` `≠` | `>=` `!=` |
| emoji, `✕`, `⚠`, `▼` dekoratif | dihapus (teks sudah cukup), atau ikon vektor |

Hanya teks tampilan yang diubah; kunci tersimpan dan kontrak API tidak disentuh. Pengenal input
pengguna (mis. regex `ganti nama ... -> / →` di `DeterministicSpecOpProposer`) sengaja dibiarkan
karena itu masukan, bukan tampilan.

## Step 2 - Penjaga
`UiTextLatin1GuardTest` (jvmTest `app/shared`) memindai `presentation/**/*.kt` dengan pemindai
leksikal kecil: komentar/KDoc dibuang, kode di dalam `${...}` tidak dihitung, hanya teks literal
string/raw string/char yang diperiksa. Pelanggaran dilaporkan sebagai `file:baris U+XXXX`.
Daftar pengecualian (`exemptFiles`) eksplisit, wajib beralasan, dan kosong saat ini.

Pemindainya sendiri diuji (`scan_*`) dengan string sampel supaya tes ini tidak selalu hijau.

## Jebakan
- Jangan grep mentah karakter non-Latin-1: komentar KDoc memakainya dengan sah dan itu bukan bug.
- Menghapus emoji pada teks tunggal (`Text("⚠")`) membuat string kosong; ganti dengan `!`.
- Bila tes membandingkan teks tampilan, perbarui tesnya bersama perubahan teks.
