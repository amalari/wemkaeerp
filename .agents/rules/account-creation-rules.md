# WeMade ERP — Aturan Pembuatan Akun Karyawan (Account Provisioning Rules)

## 1. Prinsip Minimalisme Akun (Zero Clutter)
Data profil akun karyawan baru harus dibuat **sesederhana mungkin** tanpa formulir birokrasi yang panjang:
1. **Email Login**: Email akun Google / email perusahaan yang digunakan sebagai kredensial autentikasi.
2. **Nama Lengkap**: Nama resmi karyawan untuk identitas di surat jalan, SPK, atau pesanan sampling.
3. **No. WhatsApp / HP**: Kontak darurat/operasional lantai pabrik.
4. **Divisi Penempatan**: Salah satu dari 5 divisi resmi pabrik (`SALES`, `PRODUCTION_PPIC`, `WAREHOUSE`, `QUALITY_CONTROL`, `FINANCE_EXECUTIVE`).
5. **Level & Judul Jabatan**: Tingkat wewenang (`Direksi`, `Kepala Divisi`, `Staf Pelaksana`) dan nama jabatan operasional (misal: "Operator Jahit", "Sales Seragam").
6. **Atasan Langsung (*Reports To*)**: Penentu alur pelaporan dan approval otomatis.

---

## 2. Matriks Hak Wewenang Pembuatan Akun (Provisioning Matrix)

| Aktor Pembuat Akun | Siapa yang Boleh Dibuat? | Batasan Sistem (*System Guardrail*) |
|---|---|---|
| **Owner / Direktur Utama** | **Seluruh Akun (Full Access)** | Dapat membuat akun untuk level apa saja (Direksi, Kepala Divisi, Staf) di seluruh divisi pabrik. |
| **Tenant Admin** | **Seluruh Akun (Sama dengan Owner)** | Memiliki hak penuh yang sama dengan Owner untuk manajemen akun seluruh divisi. |
| **Kepala Divisi (Head of Department)** | **Hanya Staf Bawahannya di Divisinya Sendiri** | 1. **Divisi terkunci**: Hanya boleh membuat akun pada divisinya sendiri.<br>2. **Level terkunci**: Hanya boleh membuat akun level `STAFF_OPERATOR`.<br>3. **Atasan terkunci**: Kolom *Atasan Langsung* otomatis terkunci ke akun Kepala Divisi tersebut.<br>4. Dilarang membuat akun Kepala Divisi lain atau Direksi. |
| **Staf Pelaksana / Operator** | **Dilarang (No Access)** | Tidak memiliki akses ke menu pembuatan akun atau form penambahan staf. |

---

## 3. Aturan Validasi Teknis di Domain & API

1. **Email Uniqueness per Tenant**:
   - Email tidak boleh duplikat di dalam satu tenant pabrik.
2. **Auto-Enforcement untuk Kepala Divisi**:
   - Jika `creator.level == HEAD_OF_DEPARTMENT`:
     - `target.department` **wajib sama** dengan `creator.department`.
     - `target.level` **wajib** `STAFF_OPERATOR`.
     - `target.reportsToId` **wajib** bernilai `creator.id`.
3. **Pencegahan Eskalasi Hak Istimewa (*Privilege Escalation*)**:
   - Kepala Divisi tidak dapat memberikan hak akses `MANAGE` atau peran `TENANT_ADMIN` kepada staf baru yang dibuatnya.
