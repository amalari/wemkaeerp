package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.roundToInt

/**
 * Komponen input tag/chip khas Claymorphism di mana tag yang sudah dipilih
 * berada DI DALAM kotak input (unified container surface) berdampingan dengan cursor:
 * - Enter / Done di keyboard atau koma (,) otomatis membuat tag baru.
 * - Tap chip saran (quick suggestions) di bawahnya untuk memasukkan tag dengan 1 ketukan.
 * - Seluruh badan chip (handle + teks) dapat diseret langsung (Drag & Drop Reorder) secara mulus.
 * - Tombol hapus (✕) pada tiap tag.
 */
@Composable
fun ClayTagInput(
    label: String,
    placeholder: String,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    numbered: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val focusManager = LocalFocusManager.current
    var input by remember(label) { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val currentTags by rememberUpdatedState(tags)
    val currentOnTagsChange by rememberUpdatedState(onTagsChange)

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun addTags(newItems: List<String>) {
        val filtered = newItems
            .map { it.trim().removeSuffix(",").removeSuffix("\t") }
            .filter { it.isNotBlank() }
        if (filtered.isNotEmpty()) {
            currentOnTagsChange(currentTags + filtered)
            input = ""
        }
    }

    fun addTag(text: String) {
        addTags(listOf(text))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            if (draggedIndex != null) {
                val currentShift = (dragOffset.x / 65f).roundToInt()
                val targetPos = ((draggedIndex ?: 0) + currentShift).coerceIn(0, tags.lastIndex) + 1
                Text(
                    text = "Lepas untuk pindah ke urutan #$targetPos",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
            } else if (tags.size > 1) {
                Text(
                    text = "Tahan & seret tag untuk atur urutan",
                    fontSize = 9.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        // Unified Input Surface: Tag yang terpilih berada DI DALAM kotak input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.Surface,
                    outline = when {
                        isError -> WeMadeColors.Error
                        isFocused -> WeMadeColors.Primary
                        else -> WeMadeColors.Outline
                    },
                    offset = if (isFocused || isError) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if (isFocused || isError) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = when {
                        isError -> WeMadeColors.Error
                        isFocused -> WeMadeColors.Primary
                        else -> WeMadeColors.Outline
                    },
                    innerShade = false
                )
                .padding(horizontal = ClaySpacing.Md, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            ClayFlowRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = ClaySpacing.Xs
            ) {
                // Tag yang sudah terpilih berada di dalam kotak input
                tags.forEachIndexed { index, tag ->
                    val isThisDragged = draggedIndex == index
                    val currentIndex by rememberUpdatedState(index)

                    Row(
                        modifier = Modifier
                            .zIndex(if (isThisDragged) 10f else 1f)
                            .offset {
                                if (isThisDragged) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                else IntOffset.Zero
                            }
                            .claySurface(
                                shape = ClayShapes.Pill,
                                background = if (isThisDragged) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                                outline = if (isThisDragged) WeMadeColors.Primary else WeMadeColors.Outline,
                                offset = if (isThisDragged) ClayOffset.Small else ClayOffset.Flat,
                                borderWidth = if (isThisDragged) ClayBorder.Medium else ClayBorder.Hairline,
                                shadowColor = if (isThisDragged) WeMadeColors.OutlineSoft else WeMadeColors.Outline,
                                innerShade = false
                            )
                            .padding(start = ClaySpacing.Sm, end = ClaySpacing.Xs, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        // Seluruh area badan tag (handle + nomor + label) dapat disentuh/diseret langsung
                        Row(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    try {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedIndex = currentIndex
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val from = draggedIndex
                                                if (from != null) {
                                                    val shift = (dragOffset.x / 65f).roundToInt()
                                                    val to = (from + shift).coerceIn(0, currentTags.lastIndex)
                                                    if (to != from) {
                                                        val mutable = currentTags.toMutableList()
                                                        val item = mutable.removeAt(from)
                                                        mutable.add(to, item)
                                                        currentOnTagsChange(mutable)
                                                    }
                                                }
                                                draggedIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    } finally {
                                        draggedIndex = null
                                        dragOffset = Offset.Zero
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            // 6-dot drag handle indicator
                            Canvas(modifier = Modifier.size(8.dp, 12.dp)) {
                                val dotRadius = 1.dp.toPx()
                                val col1 = size.width * 0.25f
                                val col2 = size.width * 0.75f
                                val dotColor = if (isThisDragged) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)
                                for (i in 0..2) {
                                    val y = size.height * (0.2f + i * 0.3f)
                                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(col1, y))
                                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(col2, y))
                                }
                            }

                            if (numbered) {
                                val shift = (dragOffset.x / 65f).roundToInt()
                                val targetNum = if (isThisDragged) (index + shift).coerceIn(0, tags.lastIndex) + 1 else index + 1
                                Text(
                                    text = "$targetNum.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                            }
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        // Tombol hapus tag (✕)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    onTagsChange(tags.filterIndexed { i, _ -> i != index })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            IconClose(
                                modifier = Modifier.size(10.dp),
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }

                // Input field berada inline langsung di samping/setelah tag di dalam kotak yang sama
                Row(
                    modifier = (if (tags.isEmpty()) Modifier.fillMaxWidth() else Modifier.defaultMinSize(minWidth = 90.dp))
                        .align(Alignment.CenterVertically)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Box(
                        modifier = Modifier.weight(1f, fill = tags.isEmpty()),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = if (tags.isEmpty()) placeholder else "Ketik...",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Normal
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { newVal ->
                                if (newVal.contains(",") || newVal.contains("\t")) {
                                    val parts = newVal.split(',', '\t')
                                    addTags(parts)
                                } else {
                                    input = newVal
                                }
                            },
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused && input.isNotBlank()) {
                                        addTag(input)
                                    }
                                    isFocused = focusState.isFocused
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
                                        if (input.isNotBlank()) {
                                            addTag(input)
                                        }
                                        focusManager.moveFocus(
                                            if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                                        )
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .widthIn(min = 60.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            ),
                            cursorBrush = SolidColor(WeMadeColors.Primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { addTag(input) }
                            )
                        )
                    }
                }
            }
        }

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
