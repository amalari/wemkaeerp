package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.builder.BuilderConsoleMenu
import com.eventverse.app.domain.builder.BuilderMenuKey
import com.eventverse.app.domain.builder.BuilderRouteMap
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayNavItem
import com.eventverse.app.presentation.designsystem.ClayNavSection
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.i18n.missingTranslationLabel
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Section Builder dari path `/builder/<key>` — query dipotong lebih dulu; kosong = **chat**
 * (TRD-PLAT-014 FR-1-1: `/builder` membuka Chat). Bisa berupa sub-segmen (`live/production`).
 */
fun builderSection(path: String): String =
    path.substringBefore('?').removePrefix("/builder").trim('/').ifEmpty { BuilderConsoleMenu.CHAT.value }

/**
 * Nilai satu parameter query **route internal** Builder, mis. `template` dari
 * `/builder/chat?template=bordir`. Dibaca dari [path] yang dipegang navigasi internal — bukan
 * `window.location.search` — supaya berperilaku sama di kelima target KMP (Android/iOS/Desktop
 * tidak punya URL browser). Kosong = `null`, bukan string kosong.
 */
fun builderQueryParam(path: String, name: String): String? =
    path.substringAfter('?', "")
        .split('&')
        .mapNotNull { segment -> segment.split('=', limit = 2).takeIf { it.size == 2 && it[0] == name } }
        .firstOrNull()
        ?.get(1)
        ?.takeIf { it.isNotBlank() }

/**
 * Judul header Builder: "Builder · Chat". Key lama di-resolve dulu ([BuilderRouteMap]) supaya
 * judul ikut lokasi barunya; key yang bukan menu v2 (mis. `newpack`) tampil apa adanya — perilaku
 * lama untuk layar aksi.
 */
@Composable
fun builderSectionTitle(path: String, language: LanguageCode): String {
    val key = (BuilderRouteMap.resolve(builderSection(path)) ?: builderSection(path)).substringBefore('/')
    val label = runCatching { BuilderConsoleMenu.label(BuilderMenuKey(key)).resolve(language).text }.getOrNull()
    return "Builder · " + (label ?: key)
}

/**
 * Menu Builder v2 untuk drawer header — pengganti sidebar tetap, sehingga lebar penuh milik pane.
 * Badge (FR-1-4) dimuat di sini sekali ([rememberBuilderMenuBadges]); penanda "belum diterjemahkan"
 * mengikuti mekanisme i18n menu modul. Memilih item memanggil [onSelect] dengan key menu; menutup
 * drawer adalah urusan pemanggil.
 */
@Composable
fun builderDrawerSections(
    path: String,
    language: LanguageCode,
    onSelect: (String) -> Unit
): List<ClayNavSection> {
    val selected = builderSection(path)
    val badges = rememberBuilderMenuBadges()
    val missingNote = missingTranslationLabel(language)
    return listOf(
        ClayNavSection(
            title = "Navigasi Builder",
            items = builderMenu(language, badges).map { item ->
                val entry = BuilderConsoleMenu.entries.firstOrNull { it.key.value == item.key }
                val label = entry?.labelText?.resolve(language)
                ClayNavItem(
                    key = item.key,
                    label = item.label,
                    labelNote = missingNote.takeIf { label?.isFallback == true },
                    selected = item.key == selected.substringBefore('/'),
                    onClick = { onSelect(item.key) },
                    icon = { tint -> item.icon?.invoke(Modifier.size(18.dp), tint) },
                    enabled = item.enabled,
                    badge = item.badge,
                    badgeTint = item.badgeTint
                )
            }
        )
    )
}

/** Kaki drawer Builder: pindah ke aplikasi ERP tenant / kembali ke konsol platform (bila berhak). */
@Composable
fun ColumnScope.BuilderDrawerFooter(
    onOpenApp: (suspend () -> Any?)?,
    onBackToConsole: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        onOpenApp?.let { open ->
            ClayButton(
                text = "Buka Aplikasi ERP",
                onClick = { scope.launch { open() } },
                style = ClayButtonStyle.Secondary,
                leading = { IconGlobe(Modifier.size(14.dp), color = WeMadeColors.OnSurface) }
            )
        }
        onBackToConsole?.let { back ->
            ClayButton(
                text = "Kembali ke Admin",
                onClick = back,
                style = ClayButtonStyle.Secondary,
                leading = { IconArrowBack(Modifier.size(14.dp), color = WeMadeColors.OnSurface) }
            )
        }
    }
}
