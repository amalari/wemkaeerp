package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.clayFlat

/**
 * Pemicu bulat kecil di pojok kartu Kanban untuk memindahkan lead tanpa drag — jalur yang
 * wajib ada karena drag tidak tersedia di keyboard/pembaca layar.
 */
@Composable
fun LeadStageMenuButton(
    currentStage: LeadStage,
    onUpdateStage: (LeadStage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = currentStage.tint()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clayFlat(
                    shape = CircleShape,
                    background = tint.copy(alpha = 0.12f),
                    outline = tint.copy(alpha = 0.45f),
                    borderWidth = ClayBorder.Medium
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            IconChevronDown(Modifier.size(10.dp), color = tint)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LeadStage.entries.filter { it != currentStage }.forEach { target ->
                DropdownMenuItem(
                    leadingIcon = {
                        when (target) {
                            LeadStage.QUALIFIED -> IconCheck(Modifier.size(16.dp), color = target.tint())
                            LeadStage.UNQUALIFIED -> IconBan(Modifier.size(16.dp), color = target.tint())
                            LeadStage.NEW_LEAD -> IconInbox(Modifier.size(16.dp), color = target.tint())
                            LeadStage.FOLLOW_UP -> IconChat(Modifier.size(16.dp), color = target.tint())
                        }
                    },
                    text = {
                        Text(
                            text = when (target) {
                                LeadStage.NEW_LEAD -> "Pindahkan ke Inquiry / New Lead"
                                LeadStage.FOLLOW_UP -> "Sedang Follow Up"
                                LeadStage.QUALIFIED -> "Kualifikasi (Qualified)"
                                LeadStage.UNQUALIFIED -> "Tandai Unqualified"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = target.tint()
                        )
                    },
                    onClick = {
                        expanded = false
                        onUpdateStage(target)
                    }
                )
            }
        }
    }
}
