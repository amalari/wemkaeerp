package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayNavItem
import com.eventverse.app.presentation.designsystem.ClayNavSection
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/** Section Builder dari path `/builder/<key>`; kosong = `overview`. */
fun builderSection(path: String): String = path.removePrefix("/builder").trim('/').ifEmpty { "overview" }

/** Judul header Builder: "Builder · Chat AI". */
fun builderSectionTitle(path: String): String {
    val key = builderSection(path)
    return "Builder · " + (builderMenu().firstOrNull { it.key == key }?.label ?: key)
}

/**
 * Menu Builder untuk drawer header — pengganti sidebar tetap, sehingga lebar penuh milik pane. Memilih
 * item memanggil [onSelect] dengan key menu; menutup drawer adalah urusan pemanggil.
 */
fun builderDrawerSections(path: String, onSelect: (String) -> Unit): List<ClayNavSection> {
    val selected = builderSection(path)
    return listOf(
        ClayNavSection(
            title = "Navigasi Builder",
            items = builderMenu().map { item ->
                ClayNavItem(
                    key = item.key,
                    label = item.label,
                    selected = item.key == selected,
                    onClick = { onSelect(item.key) },
                    icon = { tint -> item.icon?.invoke(Modifier.size(18.dp), tint) },
                    enabled = item.enabled
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
