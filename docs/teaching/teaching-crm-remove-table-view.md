# Teaching — Menghapus Mode Tabel di CRM Leads (Kanban-Only)

> Kasus: halaman `/crm-sales/leads` dulu punya toggle `Kanban | Tabel`. Kebutuhan produk
> berubah: mode Tabel dihapus total, Kanban jadi satu-satunya tampilan.

## 1. Start dari Mana? (Order of Operations)

1. **Petakan dulu sebelum menghapus**: `grep -rn "CrmViewMode\\|SetViewMode\\|viewMode"`
   → ketemu enum `CrmViewMode { KANBAN, LIST }`, state `viewMode`, event `SetViewMode`,
   dan 4 komponen yang hanya hidup di mode LIST.
2. **Bedakan pemakai vs pemakai-bersama**: `CrmKanbanBoard`/`CrmMobileKanbanView` dipakai
   kedua mode (mereka hanya menampilkan toggle), sedangkan
   `LeadsMasterDetailLayout` / `LeadsMobileFeedLayout` / `LeadListPane` / `CrmViewToggle`
   **hanya** dipakai mode LIST → aman dihapus file-nya.
3. **Hapus dari hulu ke hilir**: state (`CrmUiState`) → event (`SetViewMode` + handler VM) →
   parameter komponen → branch pemanggil (`CrmWorkspaceScreen`) → file yang tersisa.

## 2. File yang Dihapus & Diubah

**Dihapus (4 file):**
- `components/CrmViewToggle.kt` — tombol `Kanban | Tabel`
- `components/LeadsMasterDetailLayout.kt` — tampilan Tabel desktop
- `components/LeadsMobileFeedLayout.kt` — tampilan Tabel mobile
- `components/LeadListPane.kt` — pane daftar yang hanya dipakai master-detail

**Diubah:**
- `CrmUiState.kt` — enum `CrmViewMode`, field `viewMode`, event `SetViewMode` dihapus.
- `CrmViewModel.kt` — handler `SetViewMode` dihapus.
- `CrmKanbanBoard.kt` / `CrmMobileKanbanView.kt` — param `viewMode` & `onViewModeChange`
  dihapus dari signature (break signature memang, tapi pemanggilnya cuma satu).
- `CrmWorkspaceScreen.kt` — branch `else if (viewMode == KANBAN)` menjadi `else` biasa;
  blok LIST (± 65 baris) dihapus.

## 3. The Why

- **Hapus dari hulu ke hilir** membuat compiler yang bekerja untukmu: setelah enum dihapus,
  semua referensi tersisa jadi error merah yang tidak mungkin terlewat — lebih aman daripada
  mengandalkan grep.
- **File dihapus, bukan dibiarkan mati**: dead code yang tidak direferensikan tetap membebani
  pembacaan dan review ("apa ini masih dipakai?"). Aturan single-responsibility berlaku juga
  untuk file yang sudah tak punya pemanggil.
- Tidak ada perubahan BE — mode tampilan murni keputusan presentasi.

## 4. Jebakan Pemula

1. **Menghapus satu dari dua branch `when`/`if-else` pada sealed event lalu lupa menghapus
   event-nya** — `when` exhaustiveness akan menangkapnya saat kompilasi.
2. **False positive grep**: `viewMode` adalah substring dari `viewModel` — selalu periksa
   konteks hasil pencarian sebelum menyimpulkan masih ada referensi.
3. **Edit ganda pada `CrmViewModel`**: mengganti handler `SetViewMode` dengan versi
   `SetMobileStage` yang sudah ada akan membuat branch `when` duplikat (compile error).
   Yang benar: hapus barisnya saja.

## 5. Verifikasi

- [ ] `grep -rn "CrmViewMode|SetViewMode|CrmViewToggle" app/shared/src` → nol hasil nyata.
- [ ] Kompilasi JVM + WasmJS + JS + `jvmTest` hijau (Android tersendiri masih gagal karena
      kerusakan pre-existing `MockupCropDialog.kt`, tidak berhubungan).
- [ ] Buka `/crm-sales/leads`: hanya Kanban, tombol toggle hilang, kartu + inspector bekerja.
