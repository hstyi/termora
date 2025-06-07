package app.termora

import java.awt.*
import java.awt.geom.AffineTransform
import kotlin.math.max
import kotlin.math.min


private val states = ArrayDeque<State>()

private data class State(
    val stroke: Stroke,
    val composite: Composite,
    val color: Color,
    val transform: AffineTransform,
    val clip: Shape,
    val font: Font,
    val renderingHints: RenderingHints
)

fun Graphics2D.save() {
    states.addFirst(
        State(
            stroke = this.stroke,
            composite = this.composite,
            color = this.color,
            transform = this.transform,
            clip = this.clip,
            font = this.font,
            renderingHints = this.renderingHints
        )
    )
}

fun Graphics2D.restore() {
    val state = states.removeFirst()
    this.stroke = state.stroke
    this.composite = state.composite
    this.color = state.color
    this.transform = state.transform
    this.clip = state.clip
    this.font = state.font
    this.setRenderingHints(state.renderingHints)
}

fun Graphics2D.drawRect(bounds: Rectangle) {
    drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
}

fun Graphics2D.fillRect(bounds: Rectangle) {
    fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
}


fun setupAntialiasing(graphics: Graphics2D) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
}


fun getBoundingBox(rectangles: Collection<Rectangle>): Rectangle {

    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE

    for (rect in rectangles) {
        minX = min(minX, rect.x)
        minY = min(minY, rect.y)
        maxX = max(maxX, rect.x + rect.width)
        maxY = max(maxY, rect.y + rect.height)
    }

    return Rectangle(minX, minY, maxX - minX, maxY - minY)
}