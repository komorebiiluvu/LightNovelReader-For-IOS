package indi.dmzz_yyhyy.lightnovelreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import indi.dmzz_yyhyy.lightnovelreader.R

@Composable
fun DefaultBookCover(
    title: String,
    width: Dp,
    height: Dp
) {
    val contentColor = Color(0xFFC9C7CF)
    Box(
        modifier = Modifier
            .size(width, height)
            .background(Color(0xFF302F36))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val borderColor = Color(0xFF45434B)
            drawRect(
                color = borderColor,
                topLeft = Offset(size.width * 0.055f, size.height * 0.045f),
                size = Size(size.width * 0.89f, size.height * 0.91f),
                style = Stroke(width = size.width * 0.009f)
            )
            drawRect(
                color = borderColor,
                topLeft = Offset(size.width * 0.105f, size.height * 0.08f),
                size = Size(size.width * 0.79f, size.height * 0.84f),
                style = Stroke(width = size.width * 0.004f)
            )

            fun line(yFraction: Float, widthFraction: Float) {
                val halfLength = size.width * widthFraction / 2f
                drawLine(
                    color = Color(0xFF44424A),
                    start = Offset(size.width / 2f - halfLength, size.height * yFraction),
                    end = Offset(size.width / 2f + halfLength, size.height * yFraction),
                    strokeWidth = size.width * 0.006f,
                    cap = StrokeCap.Round
                )
            }
            line(0.115f, 0.28f)
            line(0.16f, 0.20f)
            line(0.565f, 0.42f)
            line(0.80f, 0.46f)
            line(0.88f, 0.30f)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(height),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(height * 0.24f))
            Box(
                modifier = Modifier
                    .width(width * 0.72f)
                    .height(height * 0.25f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.ifBlank { "未命名" },
                    color = contentColor,
                    fontSize = (width.value * when {
                        title.length <= 6 -> 0.13f
                        title.length <= 12 -> 0.105f
                        else -> 0.085f
                    }).coerceIn(9f, 28f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(height * 0.12f))
            Icon(
                painter = painterResource(R.drawable.menu_book_24px),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(width * 0.17f)
            )
        }
    }
}
