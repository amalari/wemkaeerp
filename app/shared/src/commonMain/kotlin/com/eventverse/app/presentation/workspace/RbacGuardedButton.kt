package com.eventverse.app.presentation.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayGuardedButton
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pembungkus berbasis domain di atas [ClayGuardedButton].
 *
 * Tinggal di package fitur, bukan di `designsystem/`: komponen bersama tidak boleh tahu apa itu
 * [AccessLevel] (Kontrak 6). Di sinilah tingkat wewenang diterjemahkan menjadi "boleh diklik" dan
 * satu kalimat alasan.
 */
@Composable
fun RbacGuardedButton(
    text: String,
    onClick: () -> Unit,
    currentLevel: AccessLevel,
    requiredLevel: AccessLevel,
    modifier: Modifier = Modifier,
    style: ClayButtonStyle = ClayButtonStyle.Primary
) {
    val allowed = currentLevel.isAtLeast(requiredLevel)

    ClayGuardedButton(
        text = text,
        onClick = onClick,
        enabled = allowed,
        modifier = modifier,
        style = style,
        lockedHint = if (allowed) {
            null
        } else {
            "Butuh wewenang ${requiredLevel.displayName}; Anda ${currentLevel.displayName}."
        }
    )
}

/** Warna yang mewakili sebuah tingkat wewenang di seluruh layar kerja. */
fun AccessLevel.tint(): Color = when (this) {
    AccessLevel.NONE -> WeMadeColors.Error
    AccessLevel.VIEW -> WeMadeColors.Warning
    AccessLevel.OPERATE -> WeMadeColors.Info
    AccessLevel.MANAGE -> WeMadeColors.Success
}

/** Label pendek untuk badge di menu navigasi. */
fun AccessLevel.badgeLabel(): String = when (this) {
    AccessLevel.NONE -> "Terkunci"
    AccessLevel.VIEW -> "Lihat"
    AccessLevel.OPERATE -> "Input"
    AccessLevel.MANAGE -> "Penuh"
}
