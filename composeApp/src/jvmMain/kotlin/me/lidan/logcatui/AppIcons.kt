package me.lidan.logcatui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun AppIcon(
    icon: AppIconSymbol,
    modifier: Modifier = Modifier,
    tint: Color = IconTint,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        when (icon) {
            AppIconSymbol.Search -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.42f, size.height * 0.42f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.62f, size.height * 0.62f),
                    end = Offset(size.width * 0.86f, size.height * 0.86f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 35f,
                    sweepAngle = 255f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.13f),
                    end = Offset(size.width * 0.84f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.13f),
                    end = Offset(size.width * 0.74f, size.height * 0.29f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Clear -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.3f),
                    size = Size(size.width * 0.44f, size.height * 0.5f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.3f),
                    end = Offset(size.width * 0.76f, size.height * 0.3f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.38f, size.height * 0.2f),
                    end = Offset(size.width * 0.62f, size.height * 0.2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.43f, size.height * 0.4f),
                    end = Offset(size.width * 0.43f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.57f, size.height * 0.4f),
                    end = Offset(size.width * 0.57f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Close -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.24f),
                    end = Offset(size.width * 0.76f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.76f, size.height * 0.24f),
                    end = Offset(size.width * 0.24f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.ScrollToEnd -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.5f, size.height * 0.18f),
                    end = Offset(size.width * 0.5f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.28f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.72f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.84f),
                    end = Offset(size.width * 0.76f, size.height * 0.84f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.ChevronDown -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.36f),
                    end = Offset(size.width * 0.5f, size.height * 0.64f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.76f, size.height * 0.36f),
                    end = Offset(size.width * 0.5f, size.height * 0.64f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Pause -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.3f, size.height * 0.25f),
                    end = Offset(size.width * 0.3f, size.height * 0.75f),
                    strokeWidth = strokeWidth * 1.5f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.7f, size.height * 0.25f),
                    end = Offset(size.width * 0.7f, size.height * 0.75f),
                    strokeWidth = strokeWidth * 1.5f,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Resume -> {
                val path = Path().apply {
                    moveTo(size.width * 0.3f, size.height * 0.25f)
                    lineTo(size.width * 0.3f, size.height * 0.75f)
                    lineTo(size.width * 0.75f, size.height * 0.5f)
                    close()
                }
                drawPath(path, color = tint)
            }
        }
    }
}
