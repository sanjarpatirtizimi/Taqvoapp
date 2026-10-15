package uz.namozvaqti.app.qibla

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A small vector Kaaba: black cube seen in three-quarter view, gold kiswa band and gold door,
 * on a soft gold halo. Drawn with Canvas so it is crisp at any size and needs no image asset.
 *
 * @param center middle of the icon
 * @param size   icon width/height in px
 */
fun DrawScope.drawKaaba(center: Offset, size: Float, gold: Color) {
    // Halo behind the cube so the black shape stays visible on any dial colour.
    drawCircle(gold.copy(alpha = 0.22f), radius = size * 0.62f, center = center)
    drawCircle(gold.copy(alpha = 0.9f), radius = size * 0.62f, center = center, style = Stroke(width = size * 0.04f))

    val totalW = size * 0.84f
    val frontW = size * 0.60f
    val depthDx = totalW - frontW
    val depthDy = size * 0.18f
    val frontH = size * 0.58f
    val totalH = frontH + depthDy

    val left = center.x - totalW / 2f
    val top = center.y - totalH / 2f
    val frontTop = top + depthDy
    val frontBottom = frontTop + frontH
    val frontRight = left + frontW
    val rightEdge = left + totalW

    // Top face
    drawPath(
        Path().apply {
            moveTo(left, frontTop)
            lineTo(frontRight, frontTop)
            lineTo(rightEdge, top)
            lineTo(left + depthDx, top)
            close()
        },
        color = Color(0xFF3B3B3B)
    )
    // Right face (in shade)
    drawPath(
        Path().apply {
            moveTo(frontRight, frontTop)
            lineTo(rightEdge, top)
            lineTo(rightEdge, top + frontH)
            lineTo(frontRight, frontBottom)
            close()
        },
        color = Color(0xFF0E0E0E)
    )
    // Front face
    drawRect(Color(0xFF1C1C1C), topLeft = Offset(left, frontTop), size = Size(frontW, frontH))

    // Gold kiswa band: a rectangle on the front, a parallelogram on the side
    val bandTop = frontTop + frontH * 0.14f
    val bandBottom = bandTop + frontH * 0.14f
    drawRect(gold, topLeft = Offset(left, bandTop), size = Size(frontW, bandBottom - bandTop))
    drawPath(
        Path().apply {
            moveTo(frontRight, bandTop)
            lineTo(rightEdge, bandTop - depthDy)
            lineTo(rightEdge, bandBottom - depthDy)
            lineTo(frontRight, bandBottom)
            close()
        },
        color = gold.copy(alpha = 0.75f)
    )

    // Door
    val doorW = frontW * 0.16f
    val doorH = frontH * 0.36f
    drawRoundRect(
        color = gold,
        topLeft = Offset(left + frontW * 0.60f, frontBottom - doorH - frontH * 0.03f),
        size = Size(doorW, doorH),
        cornerRadius = CornerRadius(doorW * 0.2f, doorW * 0.2f)
    )

    // Hairline highlight on the front edge
    drawRect(
        color = Color.White.copy(alpha = 0.15f),
        topLeft = Offset(left, frontTop),
        size = Size(frontW, frontH),
        style = Stroke(width = size * 0.012f)
    )
}
