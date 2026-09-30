package com.eventverse.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.eventverse.app.presentation.designsystem.ClayMaterialShapes
import com.eventverse.app.presentation.designsystem.rememberClayTypography

/**
 * Token warna WeMade ERP.
 *
 * Bahasa visualnya claymorphism + neo-brutalism (outline tebal, hard shadow tanpa blur, sudut
 * membulat besar), tapi **paletnya tetap palet WeMade** — biru kepercayaan dan oranye keselamatan
 * garmen. Palet pastel dari referensi desainnya sengaja tidak diambil: layar Factory Flow memakai
 * hijau/amber/merah sebagai sinyal produksi, dan warna pastel akan meredam sinyal itu.
 */
object WeMadeColors {
    val Primary = Color(0xFF2563EB)         // Trust Blue
    val PrimaryDark = Color(0xFF1D4ED8)
    val PrimaryContainer = Color(0xFFEFF6FF)
    val Secondary = Color(0xFF3B82F6)
    val Accent = Color(0xFFEA580C)          // Garment Safety Orange
    val AccentDark = Color(0xFFC2410C)      // Orange-700, untuk teks di atas AccentLight
    val AccentLight = Color(0xFFFFF7ED)
    val Background = Color(0xFFF8FAFC)      // Slate-50 Background
    val Surface = Color(0xFFFFFFFF)         // Pure Card Surface
    val OnSurface = Color(0xFF1E293B)       // Slate-800 Body Text
    val OnSurfaceMuted = Color(0xFF64748B)  // Slate-500 Secondary Text
    val Border = Color(0xFFE2E8F0)          // Slate-200 Border
    val BorderFocus = Color(0xFF2563EB)     // Accessible Focus Ring
    val Success = Color(0xFF16A34A)         // Emerald-600
    val SuccessBg = Color(0xFFF0FDF4)
    val Warning = Color(0xFFD97706)         // Amber-600 Bottleneck Warning
    val WarningBg = Color(0xFFFFFBEB)
    val Error = Color(0xFFDC2626)           // Red-600
    val ErrorBg = Color(0xFFFEF2F2)
    val Purple = Color(0xFF7C3AED)          // Violet-600
    val PurpleBg = Color(0xFFF5F3FF)
    val Teal = Color(0xFF0D9488)            // Teal-600
    val TealBg = Color(0xFFF0FDFA)

    /** Permukaan netral satu tingkat di bawah [Surface] — chip, segmented control, well. */
    val SurfaceMuted = Color(0xFFF1F5F9)    // Slate-100

    /**
     * Teks dan ikon yang sengaja tidak dapat ditindaklanjuti — menu terkunci, tombol di luar
     * wewenang. Lebih redup dari [OnSurfaceMuted], yang masih berarti "terbaca, sekadar sekunder".
     */
    val OnSurfaceDisabled = Color(0xFF94A3B8) // Slate-400

    /** Aliran data otomatis / mesin-ke-mesin (berbeda dari [Primary] yang berarti aksi user). */
    val Info = Color(0xFF0284C7)            // Sky-600
    val InfoDark = Color(0xFF0369A1)        // Sky-700

    /**
     * Latar banner/kartu informasional. Wajib dipakai sebagai isi kartu alih-alih
     * `Info.copy(alpha = …)` — lihat catatan opaque di KDoc `ClayCard`.
     */
    val InfoBg = Color(0xFFF0F9FF)          // Sky-50

    /** Cacat produk & jalur rework mundur. Sengaja dibedakan dari [Error] (kegagalan sistem). */
    val Defect = Color(0xFFE11D48)          // Rose-600

    // ── Token clay ───────────────────────────────────────────────────────────────────────────
    /**
     * Warna outline **dan** hard shadow sekaligus. Dipakainya satu warna untuk keduanya bukan
     * kebetulan: itulah yang membuat kartu dan bayangannya terbaca sebagai satu benda padat,
     * bukan kartu yang kebetulan punya bayangan.
     */
    val Outline = Color(0xFF1E293B)

    /** Outline untuk elemen sekunder yang tidak boleh mendominasi hierarki. */
    val OutlineSoft = Color(0xFF475569)

    /** Outline di atas latar gelap (presentation mode), di mana [Outline] tak terlihat. */
    val OutlineInverse = Color(0xFF64748B)

    /**
     * Lapisan peredup di balik panel melayang (drawer navigasi, modal). Selalu dipakai dengan
     * `.copy(alpha = …)` di call site — pekat penuh hanya berguna untuk `colorScheme.scrim`.
     */
    val Scrim = Color(0xFF0F172A)

    /**
     * Latar utama. Sedikit hangat, meminjam kehangatan `--bg-cream` (#fff9f5) dari referensi
     * desain tapi jauh lebih diredam agar tetap netral untuk layar kerja seharian.
     */
    val BackgroundWarm = Color(0xFFFDFAF7)

    /** Permukaan kartu di presentation mode. */
    val SurfaceDark = Color(0xFF0F172A)
    val SurfaceDarkElevated = Color(0xFF1E293B)
    val SurfaceDarkSunken = Color(0xFF090E1A)
    val BackgroundDark = Color(0xFF020617)

    // Pasangan gelap dari token terang di atas. Ini adalah bahan baku `darkColorScheme` nanti;
    // sampai itu ada, dipakai lewat ternary `isPresentationMode` yang sudah terdaftar sebagai
    // utang teknis di design-system-rules.md — jangan menambah ternary baru.
    val ErrorBgDark = Color(0xFF3B0D0D)
    val ErrorBgDarkMuted = Color(0xFF2A1515)
    val DefectBgDark = Color(0xFF450A0A)
    val OnSurfaceInverse = Color(0xFFCBD5E1)      // Slate-300
    val OnSurfaceMutedInverse = Color(0xFF94A3B8) // Slate-400
    val BorderInverse = Color(0xFF334155)         // Slate-700
    val PrimaryInverse = Color(0xFF93C5FD)        // Blue-300

    // ── Asset pihak ketiga ──────────────────────────────────────────────────────────────────
    val GoogleBlue = Color(0xFF4285F4)
    val GoogleGreen = Color(0xFF34A853)
    val GoogleYellow = Color(0xFFFBBC05)
    val GoogleRed = Color(0xFFEA4335)
}

/**
 * Seluruh slot `ColorScheme` diisi.
 *
 * Sebelumnya hanya 11 slot yang di-override, sehingga sisanya — `surfaceVariant`, `outline`,
 * `tertiary`, `surfaceContainer*` — masih memakai ungu default Material 3. Ungu itu bocor ke
 * `AlertDialog`, `DropdownMenu`, dan `OutlinedTextField` yang tidak diberi warna eksplisit.
 */
val WeMadeLightColorScheme = lightColorScheme(
    primary = WeMadeColors.Primary,
    onPrimary = Color.White,
    primaryContainer = WeMadeColors.PrimaryContainer,
    onPrimaryContainer = WeMadeColors.PrimaryDark,
    secondary = WeMadeColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = WeMadeColors.PrimaryContainer,
    onSecondaryContainer = WeMadeColors.PrimaryDark,
    tertiary = WeMadeColors.Accent,
    onTertiary = Color.White,
    tertiaryContainer = WeMadeColors.AccentLight,
    onTertiaryContainer = WeMadeColors.Accent,
    background = WeMadeColors.BackgroundWarm,
    onBackground = WeMadeColors.OnSurface,
    surface = WeMadeColors.Surface,
    onSurface = WeMadeColors.OnSurface,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = WeMadeColors.OnSurfaceMuted,
    surfaceTint = WeMadeColors.Primary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFAF7),
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    inverseSurface = WeMadeColors.OnSurface,
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = Color(0xFF93C5FD),
    outline = WeMadeColors.Outline,
    outlineVariant = WeMadeColors.Border,
    error = WeMadeColors.Error,
    onError = Color.White,
    errorContainer = WeMadeColors.ErrorBg,
    onErrorContainer = WeMadeColors.Error,
    scrim = WeMadeColors.Scrim
)

@Composable
fun WeMadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WeMadeLightColorScheme,
        typography = rememberClayTypography(),
        // Satu baris ini membuat seluruh AlertDialog, FilterChip, OutlinedTextField, dan
        // DropdownMenu bawaan M3 ikut radius clay tanpa perlu disentuh satu per satu.
        shapes = ClayMaterialShapes,
        content = content
    )
}
