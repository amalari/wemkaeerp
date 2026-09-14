package com.eventverse.app.presentation.crm

import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.presentation.theme.WeMadeColors

/** Maps [LeadStage] to a token colour in WeMade's Neo-Brutalist Clay palette. */
fun LeadStage.tint(): Color = when (this) {
    LeadStage.NEW_LEAD -> WeMadeColors.Info
    LeadStage.QUALIFIED -> WeMadeColors.Success
    LeadStage.UNQUALIFIED -> WeMadeColors.Error
}

fun LeadStage.iconLabel(): String = when (this) {
    LeadStage.NEW_LEAD -> "Inquiry"
    LeadStage.QUALIFIED -> "Qualified"
    LeadStage.UNQUALIFIED -> "Unqualified"
}
