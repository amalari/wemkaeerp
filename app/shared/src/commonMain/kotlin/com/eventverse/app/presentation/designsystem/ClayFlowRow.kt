package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Baris badge/tag/chip yang membungkus ke baris berikutnya saat lebarnya habis.
 *
 * Diangkat ke design system karena pola yang sama muncul di tiga tempat (estimator costing,
 * kepala lembar QC, baris status QC) dan dua di antaranya sebelumnya memakai `Row` biasa —
 * yang di lebar telepon tidak membungkus melainkan memotong chip terakhir di luar layar.
 *
 * Juga mengurung anotasi opt-in `ExperimentalLayoutApi` di satu berkas, alih-alih mengulangnya
 * di tiap pemakaian.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClayFlowRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = ClaySpacing.Sm,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}
