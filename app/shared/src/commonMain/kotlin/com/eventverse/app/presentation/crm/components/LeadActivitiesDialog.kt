package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatTimestamp(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val month = dt.monthNumber.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "$day/$month ${dt.year} • $hour:$minute"
}

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

/**
 * Modal Dialog untuk mencatat dan melihat riwayat aktivitas sales (feed komentar non-reply)
 * terkait satu lead.
 */
@Composable
fun LeadActivitiesDialog(
    lead: CrmLead,
    activities: List<LeadActivity>,
    isLoading: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (content: String) -> Unit
) {
    var newCommentText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .widthIn(min = 440.dp, max = 580.dp)
                .fillMaxHeight(0.85f),
            borderWidth = ClayBorder.Thick
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(text = "💬", fontSize = 18.sp)
                        Text(
                            text = "Aktivitas Sales",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    Text(
                        text = lead.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.Primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    lead.whatsappNumber?.let { wa ->
                        Text(
                            text = "WA: ${wa.normalizedNumber}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                ClayButton(text = "Tutup", onClick = onDismiss, style = ClayButtonStyle.Ghost)
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Body: Timeline Aktivitas (Komentar Sales)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Memuat riwayat aktivitas…",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else if (activities.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(ClaySpacing.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(text = "📝", fontSize = 32.sp)
                        Text(
                            text = "Belum Ada Catatan Aktivitas",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Tulis apa yang sudah dilakukan tim sales untuk prospek ini (misal: follow-up WhatsApp, negosiasi harga, kirim katalog bahan).",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        items(activities) { activity ->
                            ActivityCommentCard(activity = activity)
                        }
                    }
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Footer Input: Tulis Aktivitas Baru
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = "Tulis update apa yang dilakukan sales…",
                    modifier = Modifier.weight(1f)
                )

                ClayButton(
                    text = if (isSubmitting) "Kirim…" else "Kirim",
                    style = ClayButtonStyle.Primary,
                    enabled = newCommentText.isNotBlank() && !isSubmitting,
                    onClick = {
                        val content = newCommentText.trim()
                        if (content.isNotBlank()) {
                            newCommentText = ""
                            onSubmit(content)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivityCommentCard(activity: LeadActivity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar Bulat Author
        Box(
            modifier = Modifier
                .size(32.dp)
                .clayFlat(
                    shape = CircleShape,
                    background = WeMadeColors.Primary,
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Hairline
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = getInitials(activity.authorName),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Surface
            )
        }

        // Isi Komentar Aktivitas
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.authorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = formatTimestamp(activity.createdAt),
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = activity.content,
                fontSize = 12.sp,
                color = WeMadeColors.OnSurface,
                lineHeight = 16.sp
            )
        }
    }
}
