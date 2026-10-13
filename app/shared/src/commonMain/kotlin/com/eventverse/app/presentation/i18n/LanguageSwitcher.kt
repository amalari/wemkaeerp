package com.eventverse.app.presentation.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing

/**
 * Pengganti bahasa aplikasi: dua pil ID | EN. Berlaku langsung (state reaktif di [AppLanguage]) dan tersimpan
 * lewat [LanguagePreference]. Label sengaja kode bahasa, bukan terjemahan — tampil benar apa pun bahasa aktifnya.
 */
@Composable
fun LanguageSwitcher(modifier: Modifier = Modifier) {
    val active = rememberActiveLanguage()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(LanguageCode.ID, LanguageCode.EN).forEach { language ->
            ClayChoiceChip(
                text = language.code.uppercase(),
                selected = language == active,
                onClick = { AppLanguage.getInstance().setLanguage(language) }
            )
        }
    }
}
