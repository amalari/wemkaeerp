package com.eventverse.app.presentation.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.ResolvedText
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.resources.Res
import com.eventverse.app.shared.resources.i18n_pack_text_missing_en
import com.eventverse.app.shared.resources.i18n_pack_text_missing_id
import org.jetbrains.compose.resources.stringResource

/**
 * Pembungkus tampilan [LocalizedText] data pack (TRD-I18N-002). Hidup di `presentation/i18n/`, BUKAN di
 * `designsystem/` — ia tahu tipe domain. Tidak ada fallback senyap: bila terjemahan belum ada, teks `id`
 * tampil bersama penanda [MissingTranslationBadge].
 */

/** Bahasa aktif aplikasi (provider Fase 1), reaktif terhadap [AppLanguage.setLanguage]. */
@Composable
fun rememberActiveLanguage(): LanguageCode {
    val language by AppLanguage.getInstance().language.collectAsState()
    return language
}

/**
 * Resolve murni (bisa diuji tanpa Compose). [transform] dipakai pemanggil untuk olah-tampilan, mis. membuang
 * awalan nomor "1. " pada nama fase; penanda fallback ([ResolvedText.isFallback]) tetap terbawa.
 */
fun LocalizedText.resolveForDisplay(
    language: LanguageCode,
    transform: (String) -> String = { it }
): ResolvedText = resolve(language).let { it.copy(text = transform(it.text)) }

/** Teks penanda per bahasa aktif; pilihan by bahasa aplikasi (bukan locale sistem), jadi deterministik. */
@Composable
fun missingTranslationLabel(language: LanguageCode): String =
    if (language == LanguageCode.EN) stringResource(Res.string.i18n_pack_text_missing_en)
    else stringResource(Res.string.i18n_pack_text_missing_id)

/** Lencana "Belum diterjemahkan". Tint = token peran `Warning`. */
@Composable
fun MissingTranslationBadge(language: LanguageCode, modifier: Modifier = Modifier) {
    ClayBadge(
        text = missingTranslationLabel(language),
        tint = WeMadeColors.Warning,
        modifier = modifier,
        fontSize = 9.sp
    )
}

/**
 * Merender [text] pada bahasa aktif lewat [content]; bila fallback, lencana ditempel di sebelahnya.
 * Konten (teks) boleh mengalah: `weight(1f, fill = false)` + [maxLines]/ellipsis dipasang di sini; lencana tidak menyusut.
 */
@Composable
fun LocalizedLabel(
    text: LocalizedText,
    modifier: Modifier = Modifier,
    transform: (String) -> String = { it },
    /** `false` untuk induk yang lebarnya mengikuti isi (chip): `weight` di lebar tak terbatas mengecilkan teks jadi nol. */
    shrinkable: Boolean = true,
    content: @Composable (String, Modifier) -> Unit = { value, mod ->
        Text(value, modifier = mod, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
) {
    val language = rememberActiveLanguage()
    val resolved = text.resolveForDisplay(language, transform)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        content(resolved.text, if (shrinkable) Modifier.weight(1f, fill = false) else Modifier)
        if (resolved.isFallback) MissingTranslationBadge(language)
    }
}

/** Membuang awalan nomor pada nama fase ("1. Sampling" -> "Sampling"); nomor sudah tampil di lencana urutan. */
val StripPhaseOrdinal: (String) -> String = { it.substringAfter(". ") }
