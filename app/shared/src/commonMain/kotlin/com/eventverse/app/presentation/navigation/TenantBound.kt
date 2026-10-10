package com.eventverse.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Slug tenant yang boleh dipakai layar terikat-tenant: bukan null dan bukan kosong, selain itu null.
 * Tidak ada tenant bawaan yang ditebak (tenant-variability Kontrak 4: tolak, bukan fallback senyap).
 */
internal fun explicitTenantSlug(raw: String?): String? = raw?.trim()?.ifEmpty { null }

/** Layar terikat-tenant: tanpa slug eksplisit tampil keadaan "pilih tenant", bukan data tenant tebakan. */
@Composable
internal fun TenantBound(slug: String?, content: @Composable (String) -> Unit) {
    val explicit = explicitTenantSlug(slug)
    if (explicit == null) TenantNotSelectedView() else content(explicit)
}

@Composable
internal fun TenantNotSelectedView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl), contentAlignment = Alignment.Center) {
        Text(
            text = "Pilih tenant terlebih dahulu untuk membuka layar ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}
