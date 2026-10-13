package com.eventverse.app.presentation.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun LanguageSelector(
    currentLanguage: LanguageCode,
    onLanguageChange: (LanguageCode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .background(WeMadeColors.SurfaceMuted)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (currentLanguage) {
                    LanguageCode.ID -> "ID"
                    LanguageCode.EN -> "EN"
                    else -> "?"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Primary
            )
            Text(
                text = currentLanguage.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "v",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = true) { expanded = false }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 40.dp)
                    .width(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WeMadeColors.Surface)
                    .claySurface(
                        shape = ClayShapes.Panel,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        shadowColor = WeMadeColors.Outline,
                        offset = ClayOffset.Rest,
                        borderWidth = ClayBorder.Thick
                    )
                    .padding(ClaySpacing.Sm)
            ) {
                LanguageOption(
                    language = LanguageCode.ID,
                    flag = "ID",
                    label = "Bahasa Indonesia",
                    isSelected = currentLanguage == LanguageCode.ID,
                    onClick = {
                        onLanguageChange(LanguageCode.ID)
                        expanded = false
                    }
                )
                LanguageOption(
                    language = LanguageCode.EN,
                    flag = "EN",
                    label = "English",
                    isSelected = currentLanguage == LanguageCode.EN,
                    onClick = {
                        onLanguageChange(LanguageCode.EN)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    language: LanguageCode,
    flag: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = flag, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
        )
    }
}
