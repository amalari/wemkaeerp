package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Master data pelanggan (Contact) — daftar semua kontak tenant beserta jejak lead
 * asalnya. Kontak di sini tidak bisa diedit langsung: ia dikelola lewat kualifikasi
 * lead (find-or-create) supaya tidak lahir duplikat di luar jalur kualifikasi.
 */
@Composable
fun ContactsPane(
    tenantSlug: String,
    modifier: Modifier = Modifier
) {
    val dataSource = remember { DealApiClient() }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(tenantSlug, refreshKey) {
        isLoading = true
        error = null
        dataSource.getContacts(tenantSlug)
            .onSuccess {
                contacts = it
                isLoading = false
            }
            .onFailure {
                error = it.message
                isLoading = false
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kontak (${contacts.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClayButton(
                text = "Muat Ulang",
                onClick = { refreshKey++ },
                style = ClayButtonStyle.Ghost,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        when {
            isLoading -> Text(
                text = "Memuat kontak...",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            error != null -> Text(
                text = error ?: "Terjadi kesalahan.",
                fontSize = 12.sp,
                color = WeMadeColors.Error
            )
            contacts.isEmpty() -> Text(
                text = "Belum ada kontak. Kontak lahir otomatis saat sebuah lead di-qualify.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(contacts, key = { it.id.value }) { contact ->
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = contact.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!contact.brandName.isBlank) {
                                ClayTag(text = contact.brandName.value, tint = WeMadeColors.Info)
                            }
                        }
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        Text(
                            text = listOfNotNull(
                                contact.phone?.localDisplay,
                                contact.email.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  ").ifBlank { "-" },
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
