package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight, zero-bloat Material vector icons used in place of the ~30MB
 * `material-icons-extended` dependency so dexing and compilation stay fast.
 */
object AranIcons {

    val Shield: ImageVector by lazy {
        icon("Shield") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
                moveTo(18f, 11.09f)
                curveTo(18f, 15.09f, 15.45f, 18.79f, 12f, 19.92f)
                curveTo(8.55f, 18.79f, 6f, 15.09f, 6f, 11.09f)
                verticalLineTo(6.39f)
                lineTo(12f, 4.14f)
                lineTo(18f, 6.39f)
                verticalLineTo(11.09f)
                close()
            }
        }
    }

    val ShieldFilled: ImageVector by lazy {
        icon("ShieldFilled") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
            }
        }
    }

    val Key: ImageVector by lazy {
        icon("Key") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.65f, 10f)
                curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
                curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f)
                curveTo(1f, 15.31f, 3.69f, 18f, 7f, 18f)
                curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f)
                horizontalLineTo(17f)
                verticalLineTo(18f)
                horizontalLineTo(21f)
                verticalLineTo(14f)
                horizontalLineTo(23f)
                verticalLineTo(10f)
                horizontalLineTo(12.65f)
                close()
                moveTo(7f, 14f)
                curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f)
                curveTo(5f, 10.9f, 5.9f, 10f, 7f, 10f)
                curveTo(8.1f, 10f, 9f, 10.9f, 9f, 12f)
                curveTo(9f, 13.1f, 8.1f, 14f, 7f, 14f)
                close()
            }
        }
    }

    val Visibility: ImageVector by lazy {
        icon("Visibility") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4.5f)
                curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
                curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
                curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
                curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
                close()
                moveTo(12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
                close()
                moveTo(12f, 9f)
                curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        icon("VisibilityOff") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.58f, 10.58f)
                curveTo(9.8f, 11.36f, 9.8f, 12.64f, 10.58f, 13.42f)
                curveTo(11.36f, 14.2f, 12.64f, 14.2f, 13.42f, 13.42f)
                moveTo(6.5f, 6.5f)
                curveTo(4.3f, 7.9f, 2.7f, 9.8f, 2f, 12f)
                curveTo(3.7f, 16.4f, 7.5f, 19f, 12f, 19f)
                curveTo(14f, 19f, 15.8f, 18.4f, 17.4f, 17.4f)
                moveTo(9.9f, 5.2f)
                curveTo(10.6f, 5.07f, 11.3f, 5f, 12f, 5f)
                curveTo(16.5f, 5f, 20.3f, 7.6f, 22f, 12f)
                curveTo(21.4f, 13.5f, 20.5f, 14.8f, 19.4f, 15.9f)
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        icon("ContentCopy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineTo(3f)
                horizontalLineTo(16f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                verticalLineTo(21f)
                curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                horizontalLineTo(19f)
                curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                close()
            }
        }
    }

    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 10f)
                verticalLineTo(16f)
                moveTo(8.5f, 11.5f)
                curveTo(8.5f, 9.5f, 10f, 8f, 12f, 8f)
                curveTo(14f, 8f, 15.5f, 9.5f, 15.5f, 11.5f)
                verticalLineTo(15f)
                moveTo(5.5f, 12f)
                curveTo(5.5f, 8.2f, 8.4f, 5f, 12f, 5f)
                curveTo(15.6f, 5f, 18.5f, 8.2f, 18.5f, 12f)
                verticalLineTo(14.5f)
                moveTo(9f, 15f)
                curveTo(9f, 17f, 10f, 19f, 11.5f, 20.5f)
                moveTo(15f, 17.5f)
                curveTo(14.7f, 18.8f, 14.1f, 19.9f, 13.3f, 20.8f)
                moveTo(6.5f, 16f)
                curveTo(6.8f, 17.8f, 7.6f, 19.4f, 8.8f, 20.5f)
            }
        }
    }

    val Devices: ImageVector by lazy {
        icon("Devices") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 6f)
                horizontalLineTo(22f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineTo(17f)
                horizontalLineTo(0f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(23f, 8f)
                horizontalLineTo(17f)
                curveTo(16.45f, 8f, 16f, 8.45f, 16f, 9f)
                verticalLineTo(19f)
                curveTo(16f, 19.55f, 16.45f, 20f, 17f, 20f)
                horizontalLineTo(23f)
                curveTo(23.55f, 20f, 24f, 19.55f, 24f, 19f)
                verticalLineTo(9f)
                curveTo(24f, 8.45f, 23.55f, 8f, 23f, 8f)
                close()
                moveTo(22f, 17f)
                horizontalLineTo(18f)
                verticalLineTo(10f)
                horizontalLineTo(22f)
                verticalLineTo(17f)
                close()
            }
        }
    }

    val PhoneAndroid: ImageVector by lazy {
        icon("PhoneAndroid") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(8f)
                curveTo(6.34f, 1f, 5f, 2.34f, 5f, 4f)
                verticalLineTo(20f)
                curveTo(5f, 21.66f, 6.34f, 23f, 8f, 23f)
                horizontalLineTo(16f)
                curveTo(17.66f, 23f, 19f, 21.66f, 19f, 20f)
                verticalLineTo(4f)
                curveTo(19f, 2.34f, 17.66f, 1f, 16f, 1f)
                close()
                moveTo(14f, 21f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                verticalLineTo(21f)
                close()
                moveTo(17.25f, 18f)
                horizontalLineTo(6.75f)
                verticalLineTo(4f)
                horizontalLineTo(17.25f)
                verticalLineTo(18f)
                close()
            }
        }
    }

    val Laptop: ImageVector by lazy {
        icon("Laptop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 18f)
                curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
                verticalLineTo(6f)
                curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineTo(16f)
                curveTo(2f, 17.1f, 2.9f, 18f, 4f, 18f)
                horizontalLineTo(0f)
                verticalLineTo(20f)
                horizontalLineTo(24f)
                verticalLineTo(18f)
                horizontalLineTo(20f)
                close()
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                verticalLineTo(16f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    val CloudOff: ImageVector by lazy {
        icon("CloudOff") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(7.5f, 18f)
                horizontalLineTo(17.5f)
                curveTo(19.5f, 18f, 21f, 16.5f, 21f, 14.5f)
                curveTo(21f, 12.6f, 19.6f, 11.1f, 17.7f, 11f)
                curveTo(17.2f, 8.2f, 14.8f, 6f, 11.8f, 6f)
                curveTo(10.8f, 6f, 9.8f, 6.2f, 9f, 6.7f)
                moveTo(5.6f, 9.2f)
                curveTo(4.1f, 9.8f, 3f, 11.3f, 3f, 13.1f)
                curveTo(3f, 15.8f, 5f, 18f, 7.5f, 18f)
            }
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                verticalLineTo(8f)
                lineTo(14f, 2f)
                close()
                moveTo(16f, 18f)
                horizontalLineTo(8f)
                verticalLineTo(16f)
                horizontalLineTo(16f)
                verticalLineTo(18f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(8f)
                verticalLineTo(12f)
                horizontalLineTo(16f)
                verticalLineTo(14f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    val CreditCard: ImageVector by lazy {
        icon("CreditCard") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                horizontalLineTo(4f)
                curveTo(2.89f, 4f, 2f, 4.89f, 2f, 6f)
                verticalLineTo(18f)
                curveTo(2f, 19.11f, 2.89f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.11f, 20f, 22f, 19.11f, 22f, 18f)
                verticalLineTo(6f)
                curveTo(22f, 4.89f, 21.11f, 4f, 20f, 4f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(12f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                close()
                moveTo(20f, 8f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                close()
            }
        }
    }

    val Badge: ImageVector by lazy {
        icon("Badge") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 7f)
                horizontalLineTo(15f)
                verticalLineTo(4f)
                curveTo(15f, 2.9f, 14.1f, 2f, 13f, 2f)
                horizontalLineTo(11f)
                curveTo(9.9f, 2f, 9f, 2.9f, 9f, 4f)
                verticalLineTo(7f)
                horizontalLineTo(4f)
                curveTo(2.9f, 7f, 2f, 7.9f, 2f, 9f)
                verticalLineTo(20f)
                curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
                horizontalLineTo(20f)
                curveTo(21.1f, 22f, 22f, 21.1f, 22f, 20f)
                verticalLineTo(9f)
                curveTo(22f, 7.9f, 21.1f, 7f, 20f, 7f)
                close()
                moveTo(11f, 4f)
                horizontalLineTo(13f)
                verticalLineTo(9f)
                horizontalLineTo(11f)
                verticalLineTo(4f)
                close()
                moveTo(20f, 20f)
                horizontalLineTo(4f)
                verticalLineTo(9f)
                horizontalLineTo(9f)
                verticalLineTo(11f)
                horizontalLineTo(15f)
                verticalLineTo(9f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                close()
            }
        }
    }

    val Globe: ImageVector by lazy {
        icon("Globe") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, true, false, 12f, 22f)
                arcTo(10f, 10f, 0f, true, false, 12f, 2f)
                moveTo(2f, 12f)
                horizontalLineTo(22f)
                moveTo(12f, 2f)
                curveTo(14.5f, 4.7f, 16f, 8.2f, 16f, 12f)
                curveTo(16f, 15.8f, 14.5f, 19.3f, 12f, 22f)
                curveTo(9.5f, 19.3f, 8f, 15.8f, 8f, 12f)
                curveTo(8f, 8.2f, 9.5f, 4.7f, 12f, 2f)
            }
        }
    }

    val Restore: ImageVector by lazy {
        icon("Restore") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(8.8f, 21f, 6f, 19.3f, 4.4f, 16.8f)
                moveTo(3f, 4f)
                verticalLineTo(9f)
                horizontalLineTo(8f)
                moveTo(12f, 7f)
                verticalLineTo(12f)
                lineTo(15.5f, 14f)
            }
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(14.4f, 9.6f)
                lineTo(22f, 12f)
                lineTo(14.4f, 14.4f)
                lineTo(12f, 22f)
                lineTo(9.6f, 14.4f)
                lineTo(2f, 12f)
                lineTo(9.6f, 9.6f)
                close()
            }
        }
    }

    val Minus: ImageVector by lazy {
        icon("Minus") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 11f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    val HealthPulse: ImageVector by lazy {
        icon("HealthPulse") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 12f)
                horizontalLineTo(7f)
                lineTo(10f, 5f)
                lineTo(14f, 19f)
                lineTo(17f, 12f)
                horizontalLineTo(21f)
            }
        }
    }

    val DoubleChevronRight: ImageVector by lazy {
        icon("DoubleChevronRight") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 7f)
                lineTo(11f, 12f)
                lineTo(6f, 17f)
                moveTo(13f, 7f)
                lineTo(18f, 12f)
                lineTo(13f, 17f)
            }
        }
    }

    val StarOutline: ImageVector by lazy {
        icon("StarOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                lineTo(14.8f, 8.7f)
                lineTo(21f, 9.6f)
                lineTo(16.5f, 14f)
                lineTo(17.6f, 20.2f)
                lineTo(12f, 17.2f)
                lineTo(6.4f, 20.2f)
                lineTo(7.5f, 14f)
                lineTo(3f, 9.6f)
                lineTo(9.2f, 8.7f)
                close()
            }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, true, false, 12f, 22f)
                arcTo(10f, 10f, 0f, true, false, 12f, 2f)
                moveTo(12f, 7f)
                verticalLineTo(12f)
                lineTo(15.5f, 14f)
            }
        }
    }

    private fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
}
