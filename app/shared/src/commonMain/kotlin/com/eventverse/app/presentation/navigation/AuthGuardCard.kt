package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.section

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconLock
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Visual barrier presented by the Auth Guard whenever an unauthenticated
 * user tries to access protected factory ERP modules.
 */
@Composable
internal fun AuthGuardCard(
    targetModuleName: String,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // Dicicil dari daftar utang §8 sekalian menyentuh file ini: Card/Button Material mentah
        // dan dua literal amber diganti katalog clay + turunan token.
        ClayCard(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(ClaySpacing.Xxl),
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Warning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconLock(modifier = Modifier.size(32.dp), color = WeMadeColors.Warning)
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Xl))

                Text(
                    text = "Akses Terbatas: Autentikasi Diperlukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                Text(
                    text = "Modul \"$targetModuleName\" dilindungi oleh sistem keamanan multi-tenant pabrik. Silakan masuk menggunakan akun perusahaan Anda untuk melanjutkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Xxl))

                ClayButton(
                    // Tanpa panah "→": Fredoka/Nunito yang dibundel tidak punya glyph U+2192,
                    // jadi ia ter-render sebagai kotak tofu begitu tombolnya memakai font clay.
                    text = "Masuk ke Akun Sekarang",
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                    style = ClayButtonStyle.Primary,
                    contentPadding = PaddingValues(horizontal = ClaySpacing.Xl, vertical = ClaySpacing.Lg)
                )
            }
        }
    }
}

