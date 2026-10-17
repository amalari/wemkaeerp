package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.builder.BriefExplanation
import com.eventverse.app.domain.builder.ModuleBriefExplanation
import com.eventverse.app.domain.discovery.scenario.AllScenariosExplanation
import com.eventverse.app.domain.discovery.scenario.ScenarioExplanation
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconHelp
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun WhyChosenButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ClayButton(
        text = "Mengapa Dipilih",
        onClick = onClick,
        style = ClayButtonStyle.Secondary,
        modifier = modifier,
        fontSize = 12.sp,
        leading = {
            IconHelp(
                modifier = Modifier.size(16.dp),
                color = Color.White
            )
        }
    )
}

@Composable
fun WhyChosenBriefDialog(
    explanation: ModuleBriefExplanation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mengapa Dipilih",
                    fontWeight = FontWeight.Bold
                )
                if (explanation.isLegacy) {
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayBadge(
                        text = "Legacy",
                        tint = WeMadeColors.Warning
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = explanation.summaryExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurface
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                if (explanation.featureExplanations.isNotEmpty()) {
                    Text(
                        text = "Fitur",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    explanation.featureExplanations.forEach { feature ->
                        FeatureExplanationItem(feature)
                        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    }
                }

                if (explanation.stageExplanations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ClaySpacing.Md))
                    Text(
                        text = "Tahap",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    explanation.stageExplanations.forEach { stage ->
                        StageExplanationItem(stage)
                        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun FeatureExplanationItem(feature: BriefExplanation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ClaySpacing.Xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = feature.itemLabel,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            if (feature.isLegacy) {
                Spacer(modifier = Modifier.width(ClaySpacing.Xs))
                ClayBadge(
                    text = "Legacy",
                    tint = WeMadeColors.Warning.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
        Text(
            text = feature.explanation,
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun StageExplanationItem(stage: BriefExplanation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ClaySpacing.Xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stage.itemLabel,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            if (stage.isLegacy) {
                Spacer(modifier = Modifier.width(ClaySpacing.Xs))
                ClayBadge(
                    text = "Legacy",
                    tint = WeMadeColors.Warning.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
        Text(
            text = stage.explanation,
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
fun WhyChosenScenarioDialog(
    explanation: ScenarioExplanation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mengapa Alur Ini Dipilih",
                    fontWeight = FontWeight.Bold
                )
                if (explanation.isLegacy) {
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayBadge(
                        text = "Legacy",
                        tint = WeMadeColors.Warning
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = explanation.titleExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurface
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                explanation.stepExplanations.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        Column {
                            Text(
                                text = step.label,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Text(
                                text = step.explanation,
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            val carries = step.carriesExplanation
                            if (carries != null) {
                                Text(
                                    text = carries,
                                    fontSize = 11.sp,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
