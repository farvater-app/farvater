package app.farvater.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal data object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
}

private class PressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val presses = mutableSetOf<PressInteraction.Press>()
    private val focuses = mutableSetOf<FocusInteraction.Focus>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { event ->
                when (event) {
                    is PressInteraction.Press -> presses.add(event)
                    is PressInteraction.Release -> presses.remove(event.press)
                    is PressInteraction.Cancel -> presses.remove(event.press)
                    is FocusInteraction.Focus -> focuses.add(event)
                    is FocusInteraction.Unfocus -> focuses.remove(event.focus)
                    is HoverInteraction.Enter -> hovers.add(event)
                    is HoverInteraction.Exit -> hovers.remove(event.enter)
                }
                invalidateDraw()
            }
        }
    }

    override fun onDetach() {
        presses.clear()
        focuses.clear()
        hovers.clear()
    }

    override fun ContentDrawScope.draw() {
        scale(if (presses.isNotEmpty()) 0.97f else if (hovers.isNotEmpty()) 0.99f else 1f) {
            this@draw.drawContent()
        }
        if (focuses.isNotEmpty()) {
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = currentValueOf(LocalIos).tint,
                topLeft = Offset(inset, inset),
                size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}
