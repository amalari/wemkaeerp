package com.eventverse.app.presentation.pipeline.components

import androidx.compose.runtime.Composable
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.shared.resources.Res
import com.eventverse.app.shared.resources.factory_flow_node_activate_en
import com.eventverse.app.shared.resources.factory_flow_node_activate_id
import com.eventverse.app.shared.resources.factory_flow_node_inactive_en
import com.eventverse.app.shared.resources.factory_flow_node_inactive_id
import org.jetbrains.compose.resources.stringResource

/** Label aktivasi manual node (PLAN A2); dipilih lewat bahasa aplikasi, bukan locale sistem. */
@Composable
fun nodeInactiveLabel(language: LanguageCode): String =
    if (language == LanguageCode.EN) stringResource(Res.string.factory_flow_node_inactive_en)
    else stringResource(Res.string.factory_flow_node_inactive_id)

@Composable
fun nodeActivateLabel(language: LanguageCode): String =
    if (language == LanguageCode.EN) stringResource(Res.string.factory_flow_node_activate_en)
    else stringResource(Res.string.factory_flow_node_activate_id)
