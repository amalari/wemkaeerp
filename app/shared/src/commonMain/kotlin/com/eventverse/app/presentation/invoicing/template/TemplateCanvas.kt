package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TemplateCanvas(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomFactor = state.zoomPercent / 100f
    val mmToDp = 3f * zoomFactor // 1mm = 3dp at 100% zoom
    val paperWidthDp = (state.template.paperSize.width.value / 10f * mmToDp).dp
    val paperHeightDp = (state.template.paperSize.height.value / 10f * mmToDp).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ClayOffset.Rest),
        contentAlignment = Alignment.Center
    ) {
        // A4/Letter Paper Sheet with Claymorphism Shadow
        Box(
            modifier = Modifier
                .size(paperWidthDp, paperHeightDp)
                .claySurface(
                    shape = RoundedCornerShape(4.dp),
                    background = WeMadeColors.Surface,
                    outline = WeMadeColors.Outline,
                    offset = ClayOffset.Rest,
                    borderWidth = ClayBorder.Medium
                )
                .clickable { onEvent(TemplateDesignerUiEvent.SelectElement(null)) }
        ) {
            // Background Grid (10mm squares)
            if (state.showGrid) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepPx = 10f * mmToDp * density
                    val gridColor = Color(0xFFE2E8F0)
                    var x = stepPx
                    while (x < size.width) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += stepPx
                    }
                    var y = stepPx
                    while (y < size.height) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += stepPx
                    }
                }
            }

            // Elements Layer
            state.template.elements.forEach { element ->
                val isSelected = element.elementId == state.selectedElementId
                val xDp = (element.rect.x.value / 10f * mmToDp).dp
                val yDp = (element.rect.y.value / 10f * mmToDp).dp
                val wDp = (element.rect.width.value / 10f * mmToDp).dp
                val hDp = (element.rect.height.value / 10f * mmToDp).dp

                var dragAccumX by remember(element.elementId) { mutableStateOf(0f) }
                var dragAccumY by remember(element.elementId) { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .size(width = wDp, height = hDp)
                        .then(
                            if (isSelected) {
                                Modifier.border(ClayBorder.Thick, WeMadeColors.Primary, RoundedCornerShape(2.dp))
                            } else {
                                Modifier.border(1.dp, Color(0xFF94A3B8).copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                            }
                        )
                        .pointerInput(element.elementId) {
                            detectDragGestures(
                                onDragStart = {
                                    dragAccumX = 0f
                                    dragAccumY = 0f
                                    onEvent(TemplateDesignerUiEvent.SelectElement(element.elementId))
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumX += dragAmount.x
                                    dragAccumY += dragAmount.y

                                    val dxMm10 = (dragAccumX / mmToDp * 10f).toInt()
                                    val dyMm10 = (dragAccumY / mmToDp * 10f).toInt()

                                    if (kotlin.math.abs(dxMm10) >= 10 || kotlin.math.abs(dyMm10) >= 10) {
                                        val maxX = (state.template.paperSize.width.value - element.rect.width.value).coerceAtLeast(0)
                                        val maxY = (state.template.paperSize.height.value - element.rect.height.value).coerceAtLeast(0)
                                        val newX = (element.rect.x.value + dxMm10).coerceIn(0, maxX)
                                        val newY = (element.rect.y.value + dyMm10).coerceIn(0, maxY)

                                        val snap = state.snapGridMm * 10
                                        val snappedX = if (snap > 0) (newX / snap) * snap else newX
                                        val snappedY = if (snap > 0) (newY / snap) * snap else newY

                                        val newRect = element.rect.copy(
                                            x = Mm10(snappedX),
                                            y = Mm10(snappedY)
                                        )
                                        onEvent(TemplateDesignerUiEvent.UpdateElementRect(element.elementId, newRect))
                                        dragAccumX = 0f
                                        dragAccumY = 0f
                                    }
                                }
                            )
                        }
                        .clickable { onEvent(TemplateDesignerUiEvent.SelectElement(element.elementId)) }
                        .padding(2.dp)
                ) {
                    RenderElementContent(
                        element = element,
                        state = state,
                        zoomFactor = zoomFactor
                    )

                    // Corner indicator when selected
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .align(Alignment.BottomEnd)
                                .background(WeMadeColors.Primary, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderElementContent(
    element: TemplateElement,
    state: TemplateDesignerUiState,
    zoomFactor: Float
) {
    val invoice = state.previewInvoice
    val totalPaid = Money.idr(0)

    when (element) {
        is TemplateElement.StaticText -> {
            Text(
                text = element.text,
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                fontWeight = if (element.style.isBold) FontWeight.Bold else FontWeight.Normal,
                color = Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                modifier = Modifier.fillMaxSize()
            )
        }
        is TemplateElement.BoundField -> {
            val resolved = InvoiceBindingResolver.resolve(element.binding, invoice, null, totalPaid)
            val valueText = when (resolved) {
                is ResolvedBindingValue.Text -> resolved.value
                is ResolvedBindingValue.Image -> "[Logo]"
                ResolvedBindingValue.Empty -> "{{${element.binding.value}}}"
            }
            val displayText = "${element.prefix}$valueText${element.suffix}"
            Text(
                text = displayText,
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                fontWeight = if (element.style.isBold) FontWeight.Bold else FontWeight.Normal,
                color = Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                modifier = Modifier.fillMaxSize()
            )
        }
        is TemplateElement.RectShape -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (element.strokeHex != null && element.strokeMm10 > 0) {
                            Modifier.border(1.dp, Color(element.strokeHex!!))
                        } else Modifier
                    )
                    .then(
                        if (element.fillHex != null) {
                            Modifier.background(Color(element.fillHex!!))
                        } else Modifier
                    )
            )
        }
        is TemplateElement.LineShape -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(element.strokeHex))
            )
        }
        is TemplateElement.ImageBox -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.SurfaceMuted)
                    .border(1.dp, WeMadeColors.Border),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🖼 LOGO PERUSAHAAN",
                    fontSize = (9 * zoomFactor).sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        is TemplateElement.ItemTable -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.Surface)
                    .border(1.dp, WeMadeColors.Border)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.PrimaryContainer)
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    element.columns.forEach { col ->
                        val weight = (col.widthRatio.numerator.toFloat() / col.widthRatio.denominator.toFloat()).coerceAtLeast(0.05f)
                        Text(
                            text = col.header,
                            fontSize = (element.headerStyle.fontSizePt * zoomFactor).sp,
                            fontWeight = if (element.headerStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                            color = Color(element.headerStyle.colorHex),
                            textAlign = toComposeTextAlign(col.align),
                            modifier = Modifier.weight(weight)
                        )
                    }
                }

                // Sample Rows (from preview invoice)
                invoice.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 6.dp)
                    ) {
                        element.columns.forEach { col ->
                            val weight = (col.widthRatio.numerator.toFloat() / col.widthRatio.denominator.toFloat()).coerceAtLeast(0.05f)
                            val res = InvoiceBindingResolver.resolve(col.binding, invoice, line, totalPaid)
                            val value = when (res) {
                                is ResolvedBindingValue.Text -> res.value
                                else -> "-"
                            }
                            Text(
                                text = value,
                                fontSize = (element.bodyStyle.fontSizePt * zoomFactor).sp,
                                fontWeight = if (element.bodyStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                                color = Color(element.bodyStyle.colorHex),
                                textAlign = toComposeTextAlign(col.align),
                                modifier = Modifier.weight(weight)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toComposeTextAlign(align: TextAlign): ComposeTextAlign = when (align) {
    TextAlign.LEFT -> ComposeTextAlign.Left
    TextAlign.CENTER -> ComposeTextAlign.Center
    TextAlign.RIGHT -> ComposeTextAlign.Right
}
