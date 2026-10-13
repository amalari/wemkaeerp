package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.designsystem.rememberClayTypography

@Composable
fun FollowUpModal(
    questions: List<PendingQuestion>,
    moduleId: String?,
    onSubmit: (List<Pair<String, String>>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    var answers by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(ClaySpacing.Md),
        shape = ClayShapes.Panel,
        outlineColor = WeMadeColors.Outline,
        offset = ClayOffset.Rest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Text(
                text = "Pertanyaan Clarification",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEA580C)
            )

            moduleId?.let {
                Text(
                    text = "- $it",
                    style = typography.bodyMedium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            Text(
                text = "Jawab beberapa pertanyaan berikut agar AI bisa membuat modul yang sesuai kebutuhan:",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            questions.forEach { question ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "${question.id}: ${question.text}",
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = answers[question.id] ?: "",
                        onValueChange = { newValue -> answers = answers + (question.id to newValue) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Ketik jawaban..."
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Lewati",
                    onClick = onSkip,
                    style = ClayButtonStyle.Secondary
                )
                Spacer(modifier = Modifier.width(ClaySpacing.Md))
                ClayButton(
                    text = "Kirim Jawaban",
                    onClick = { onSubmit(answers.toList()) },
                    enabled = answers.isNotEmpty()
                )
            }
        }
    }
}
