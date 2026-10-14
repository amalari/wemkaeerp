# Rencana: Pack Theme Customization — Visual Style per Tenant

> Status: Draft · 2026-10-14 · **Pemilik: Achmad Jamaludin**
> Asal: Diskusi dengan Claude tentang glass design dan customization per tenant
> Rujukan: [`TRD-PLAT-013-pack-theme-customization.md`](../trd/features/TRD-PLAT-013-pack-theme-customization.md)

## Context

Platform WeMade ERP saat ini menggunakan **Claymorphism + Neo-Brutalism** sebagai bahasa visual default. Namun:

1. **Kebutuhan bisnis**: Beberapa tenant/pack ingin menggunakan gaya visual berbeda — glass design (frosted glass), minimal, atau brutalist
2. **Use case chat + designer**: Menu chat untuk connect dengan designer membutuhkan gaya visual yang berbeda dari UI operasional factory flow
3. **Multi-tenant customization**: Setiap pack (FOB, CMT, Custom) harus bisa memiliki tema visual berbeda tanpa mengubah kode platform

## 0. Audit Kode Aktual

| # | Asumsi / Kebutuhan | Hasil Audit | Keputusan |
|---|---|---|---|
| 0.1 | `DomainPack` sudah ada | ✅ Benar — di `core/src/commonMain/kotlin/com/eventverse/app/domain/pack/DomainPack.kt` | Pakai yang ada, tambah field `theme` |
| 0.2 | `WeMadeColors` dan `ClayTokens` sudah ada | ✅ Benar — di `presentation/theme/` dan `presentation/designsystem/ClayTokens.kt` | Pakai untuk fallback dan base tokens |
| 0.3 | `ChatMessageBubble` sudah ada | ✅ Benar — di `presentation/builder/chat/ChatMessageBubble.kt` | Tambah variant GLASS dan BUBBLE |
| 0.4 | Glass effect butuh backdrop blur | ⚠️ Perlu dicek — Compose `RenderEffect` untuk blur | Pakai `Modifier.graphicsLayer` + `RenderEffect.createBlurEffect` |
| 0.5 | Theme resolution per tenant | ❌ Belum ada — pack theme di-resolve saat load | Buat `PackThemeResolver` service |

## 1. Keputusan Kunci: Theme di Pack, Bukan di Tenant

Theme adalah properti **pack**, bukan tenant:

| Pendekatan | Kelebihan | Kekurangan |
|---|---|---|
| **Theme di Pack (dipilih)** | Satu konfigurasi untuk semua tenant pakai pack itu; tidak perlusimpan di DB per tenant | Tenant tidak bisa ubah theme tanpa ganti pack |
| Theme di Tenant (DB) | Full customization per tenant | Perlu migrasi DB, complex override logic |

**Alasan pilih theme di pack**:
- Pack = "produk" dengan identitas visual sendiri
- Gaya visual adalah bagian dari brand pack, bukan preferensi user
- Tidak perlu复杂 override logic

## 2. Visual Styles yang Didukung

| Style | Karakteristik | Use Case |
|---|---|---|
| **CLAY** (default) | Outline 3dp, hard shadow 6dp, rounded 12-24dp | UI operasional factory |
| **GLASS** | Backdrop blur, transparent bg, subtle border | Chat + Designer, modern apps |
| **MINIMAL** | Flat, clean, subtle border | Dashboard, analytics |
| **BRUTAL** | Bold border 3-4dp, high contrast | Gaming, creative tools |

## 3. Component yang Perlu Diubah

### Priority 1 — Chat Components (untuk fitur chat + designer)

| Component | Actions |
|---|---|
| `ChatMessageBubble` | Tambah params `theme: PackTheme`, buat 3 variant (CARD, BUBBLE, GLASS) |
| `ChatInputBar` | Tambah glass variant |
| `ChatContainer` | Theme-aware container |

### Priority 2 — Core UI Components

| Component | Actions |
|---|---|
| `ClayCard` | Tambah glass variant atau modifier |
| `ClayButton` | Tambah glass variant |
| `ClayBadge` | Tambah glass variant |

### Priority 3 — Extended Components

| Component | Actions |
|---|---|
| `ClayDialog` | Glass variant |
| `ClayNavDrawer` | Glass variant |
| Module cards | Theme-aware |

## 4. Technical Approach

### 4.1 Pack Theme Definition

```kotlin
// di domain/pack/PackTheme.kt (file baru)

data class PackTheme(
    val style: VisualStyle = VisualStyle.CLAY,
    val accentColorHex: Long? = null,
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.CARD,
    val cardStyle: CardStyle? = null
)

enum class VisualStyle { CLAY, GLASS, MINIMAL, BRUTAL }
enum class ChatBubbleStyle { CARD, BUBBLE, GLASS }
enum class CardStyle { DEFAULT, ELEVATED, FLAT }
```

### 4.2 DomainPack Integration

```kotlin
// di domain/pack/DomainPack.kt — tambah field

data class DomainPack(
    // ... existing fields ...
    val theme: PackTheme? = null,  // nullable for backward compat
)
```

### 4.3 Theme Resolver

```kotlin
// di domain/pack/PackThemeResolver.kt (file baru)

class PackThemeResolver(private val packRegistry: DomainPackRegistry) {
    fun resolveForTenant(tenantId: TenantId): PackTheme {
        val pack = packRegistry.findEffective(tenantId)
        return pack.theme ?: PackTheme() // fallback to CLAY
    }
}
```

### 4.4 Glass Surface Modifier

```kotlin
// di presentation/designsystem/GlassModifier.kt (file baru)

fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundAlpha: Float = 0.2f,
    blurRadius: Dp = 25.dp
): Modifier = this
    .graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(blurRadius.toPx(), blurRadius.toPx(), BlurEdgeTreatment.Compact)
            .asComposeRenderEffect()
    }
    .background(Color.White.copy(alpha = backgroundAlpha), shape)
    .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
```

### 4.5 Chat Bubble Variants

```kotlin
@Composable
fun ChatMessageBubble(
    // ... existing params ...
    theme: PackTheme = PackTheme()
) {
    when (theme.chatBubbleStyle) {
        ChatBubbleStyle.CARD -> ClayChatBubble(...)
        ChatBubbleStyle.BUBBLE -> BubbleChatBubble(...)
        ChatBubbleStyle.GLASS -> GlassChatBubble(...)
    }
}
```

## 5. Pack Examples

### Pack garment FOB dengan Glass

```kotlin
val garmentFOBPack = DomainPack(
    code = DomainPackCode("garment_fob"),
    displayName = "Garment FOB",
    theme = PackTheme(
        style = VisualStyle.GLASS,
        accentColorHex = 0xFF6366F1,  // indigo
        chatBubbleStyle = ChatBubbleStyle.GLASS
    ),
    // ... rest of pack config
)
```

### Pack garment CMT dengan Clay (default)

```kotlin
val garmentCmtPack = DomainPack(
    code = DomainPackCode("garment_cmt"),
    displayName = "Garment CMT",
    theme = PackTheme(
        style = VisualStyle.CLAY,
        chatBubbleStyle = ChatBubbleStyle.CARD
    ),
    // ... rest of pack config
)
```

### Pack Custom tanpa theme (fallback)

```kotlin
val customPack = DomainPack(
    code = DomainPackCode("custom"),
    displayName = "Custom",
    // theme = null — akan fallback ke CLAY
)
```

## 6. Implementation Plan

### Track 0 — Contract Phase (Serial / Prerequisites)

**Tujuan**: Bekukan kontrak sebelum tracks lain berjalan paralel.

| # | Task | Output | Status |
|---|---|---|---|
| T0.1 | Buat `PackTheme.kt` | Data class + 3 enum | ✅ Selesai |
| T0.2 | Update `DomainPack.kt` | Tambah field `theme: PackTheme?` | ✅ Selesai |
| T0.3 | Update `GarmentDomainPack.kt` | Pack variant dengan theme | ✅ Selesai |
| T0.4 | Verifikasi kompilasi | Build berhasil | ✅ Selesai |

### Track A — Core & Domain (Fully Coding)

| # | Task | Output | Status |
|---|---|---|---|
| A.1 | Buat `PackThemeResolver` | Service resolve theme | ⏳ |
| A.2 | Validasi pack dengan theme | Hex validation, enum validation | ⏳ |
| A.3 | Unit test resolver | PackThemeResolverTest | ⏳ |
| A.4 | Parity test | PackThemeParityTest | ⏳ |

### Track B — Server & Persistence

**TIDAK ADA** — theme config di pack JSON.

### Track C — Client & UI Implementation

| # | Task | Output | Status |
|---|---|---|---|
| C.1 | Buat `GlassModifier` | Modifier.glassSurface() | ⏳ |
| C.2 | Setup `ThemeCompositionLocal` | LocalPackTheme | ⏳ |
| C.3 | Refactor `ChatMessageBubble` | Theme-aware component | ⏳ |
| C.4 | Buat `GlassChatBubble` variant | Glass bubble composable | ⏳ |
| C.5 | Buat `BubbleChatBubble` variant | Simple bubble composable | ⏳ |

### Track D — UI Visual Check & Verification

| # | Task | Output | Status |
|---|---|---|---|
| D.1 | Glass effect check | Screenshot | ⏳ |
| D.2 | Clay fallback check | Screenshot | ⏳ |
| D.3 | Performance check | Frame timing < 16ms | ⏳ |
| D.4 | Multi-tenant check | wemade-demo + bordir-uji | ⏳ |

## 7. Non-Functional Considerations

### Performance
- Glass blur expensive — test di berbagai device
- Fallback ke solid bila blur > 16ms

### Accessibility
- Glass effect harus tetap readable
- Contrast ratio untuk text di atas glass

### Browser Support
- Backdrop filter support di modern browsers
- Polyfill consideration untuk Safari (terlambat)

## 8. Open Questions

1. **Q**: Apakah accentColorHex perlu divalidasi?
   - **A**: Ya, perlu format hex yang benar (6-8 digit)

2. **Q**: Apakah theme bisa diubah setelah pack di-deploy?
   - **A**: Tidak — theme adalah bagian dari pack version, perlu deploy ulang

3. **Q**: Bagaimana dengan existing tenants yang upgrade?
   - **A**: Tidak ada perubahan — pack lama punya `theme = null` → fallback ke CLAY

## 9. Referensi

- TRD-PLAT-013: `../trd/features/TRD-PLAT-013-pack-theme-customization.md`
- Existing Theme: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/theme/WeMadeTheme.kt`
- Existing Tokens: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/ClayTokens.kt`
- Existing Chat: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/ChatMessageBubble.kt`
