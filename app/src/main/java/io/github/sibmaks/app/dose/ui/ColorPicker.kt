package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ColorWheelSelection
import io.github.sibmaks.app.dose.data.MedicationColors
import java.util.Locale

private val HexColor = Regex("#[0-9A-F]{6}")

@Composable
internal fun ColorPicker(initial: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    val initialSelection = remember(initial) { ColorWheelSelection.fromColor(initial) }
    var hue by rememberSaveable { mutableFloatStateOf(initialSelection.hue) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialSelection.saturation) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialSelection.brightness) }
    var hex by rememberSaveable { mutableStateOf(MedicationColors.hex(initial)) }
    val selection = ColorWheelSelection(hue, saturation, brightness)
    val validHex = HexColor.matches(hex)
    fun choose(value: ColorWheelSelection) {
        hue = value.hue
        saturation = value.saturation
        brightness = value.brightness
        hex = value.hex()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(R.string.pick_a_color)) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorWheel(selection, ::choose)
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(android.graphics.Color.parseColor(selection.hex())))
                .border(1.dp, PanelBorder, RoundedCornerShape(12.dp)))
            Text(context.getString(R.string.brightness), fontSize = 13.sp)
            Slider(value = brightness, onValueChange = { choose(selection.copy(brightness = it)) },
                modifier = Modifier.semantics { contentDescription = context.getString(R.string.brightness) })
            OutlinedTextField(hex, { input ->
                hex = input.take(7).uppercase(Locale.ROOT)
                if (HexColor.matches(hex)) {
                    val parsed = ColorWheelSelection.fromColor(hex)
                    if (parsed.saturation > 0f) hue = parsed.hue
                    saturation = parsed.saturation
                    brightness = parsed.brightness
                }
            }, label = { Text(context.getString(R.string.hex_color_rrggbb)) }, singleLine = true,
                isError = !validHex, supportingText = { if (!validHex) Text(context.getString(R.string.color_hex_error)) },
                modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { onChoose(hex) }, enabled = validHex) { Text(context.getString(R.string.use_color)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } })
}

@Composable
private fun ColorWheel(selection: ColorWheelSelection, onChoose: (ColorWheelSelection) -> Unit) {
    val context = LocalContext.current
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChoose by rememberUpdatedState(onChoose)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
        .semantics {
            contentDescription = context.getString(R.string.color_wheel_hint)
            stateDescription = selection.hex()
        }
        .pointerInput(Unit) {
            fun chooseAt(position: Offset) {
                val radius = (minOf(size.width, size.height) / 2f - inset).coerceAtLeast(1f)
                currentOnChoose(currentSelection.atPoint(position.x - size.width / 2f, position.y - size.height / 2f, radius))
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                chooseAt(down.position)
                drag(down.id) { change ->
                    chooseAt(change.position)
                    change.consume()
                }
            }
        }) {
        val radius = (size.minDimension / 2f - inset).coerceAtLeast(1f)
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - selection.brightness), radius)
        val (dx, dy) = selection.point(radius)
        val marker = center + Offset(dx, dy)
        drawCircle(Color(android.graphics.Color.parseColor(selection.hex())), 8.dp.toPx(), marker)
        drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
    }
}
