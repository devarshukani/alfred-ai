package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*

private val CardBg = Color(0xFF1E1E1E)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val PillShape = RoundedCornerShape(50)
private val CardShape = RoundedCornerShape(20.dp)
private val InnerCardShape = RoundedCornerShape(14.dp)

@Composable
fun RichCardBox(
    richCard: RichCard?,
    onAction: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onTextInput: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = richCard != null,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier
    ) {
        richCard?.let { card ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .clip(CardShape)
                    .background(CardBg)
                    .border(0.5.dp, CardBorder, CardShape)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top padding
                Spacer(modifier = Modifier.height(20.dp))
                card.blocks.forEachIndexed { index, block ->
                    val isCarousel = block is RichBlock.Carousel
                    val isChipRow = block is RichBlock.ChipRow
                    if (isCarousel || isChipRow) {
                        // Edge-to-edge: no horizontal padding
                        RenderBlock(block, onAction, onToggle, onTextInput)
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            RenderBlock(block, onAction, onToggle, onTextInput)
                        }
                    }
                }
                // Bottom padding inside the card
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


@Composable
private fun RenderBlock(
    block: RichBlock,
    onAction: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onTextInput: (String, String) -> Unit
) {
    when (block) {
        is RichBlock.Title -> Text(
            text = block.text,
            color = AlfredTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        is RichBlock.Subtitle -> Text(
            text = block.text,
            color = AlfredTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        is RichBlock.Body -> Text(
            text = block.text,
            color = AlfredTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        is RichBlock.Caption -> Text(
            text = block.text,
            color = AlfredTextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        is RichBlock.InfoRow -> InfoRowBlock(block.label, block.value)

        is RichBlock.Divider -> HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        is RichBlock.Spacer -> Spacer(modifier = Modifier.height(block.heightDp.dp))

        is RichBlock.IconLabel -> IconLabelBlock(block.icon, block.label)

        is RichBlock.Image -> ImageBlock(block.url, block.altText)

        is RichBlock.ButtonPrimary -> PrimaryPillButton(block.label) { onAction(block.actionId) }
        is RichBlock.ButtonSecondary -> SecondaryPillButton(block.label) { onAction(block.actionId) }
        is RichBlock.ButtonCancel -> CancelTextButton(block.label) { onAction(block.actionId) }

        is RichBlock.Toggle -> ToggleBlock(block.label, block.key, block.defaultOn, onToggle)
        is RichBlock.TextField -> TextFieldBlock(block.placeholder, block.key, block.defaultValue, onTextInput)

        is RichBlock.Carousel -> CarouselBlock(block.items, onAction)
        is RichBlock.ChipRow -> ChipRowBlock(block.chips)
        is RichBlock.ProgressBar -> ProgressBarBlock(block.progress, block.label)
        is RichBlock.Rating -> RatingBlock(block.stars, block.label)
        is RichBlock.LineChart -> LineChartBlock(block.points, block.label, block.minLabel, block.maxLabel, block.color)
        is RichBlock.ScoreCard -> ScoreCardBlock(block)
    }
}

@Composable
private fun InfoRowBlock(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = AlfredTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IconLabelBlock(icon: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        // Use emoji/text as icon placeholder — works without Material Icons dependency
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Text(text = icon, fontSize = 14.sp, color = AlfredTextPrimary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = AlfredTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun ImageBlock(url: String, altText: String) {
    // Placeholder box — actual image loading requires Coil/Glide
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Text(
            text = altText.ifBlank { "Image" },
            color = AlfredTextDim,
            fontSize = 13.sp
        )
    }
}


@Composable
private fun PrimaryPillButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(0.5.dp, Color.White.copy(alpha = 0.20f), PillShape)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SecondaryPillButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CancelTextButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToggleBlock(label: String, key: String, defaultOn: Boolean, onToggle: (String, Boolean) -> Unit) {
    var checked by remember { mutableStateOf(defaultOn) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                onToggle(key, it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AlfredGold,
                checkedTrackColor = AlfredGold.copy(alpha = 0.3f),
                uncheckedThumbColor = AlfredTextDim,
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
    }
}

@Composable
private fun TextFieldBlock(placeholder: String, key: String, defaultValue: String, onTextInput: (String, String) -> Unit) {
    var text by remember { mutableStateOf(defaultValue) }
    // Emit initial value so it's captured even if user doesn't edit
    LaunchedEffect(Unit) { if (defaultValue.isNotBlank()) onTextInput(key, defaultValue) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onTextInput(key, it)
        },
        placeholder = {
            Text(text = placeholder, color = AlfredTextDim, fontSize = 14.sp)
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AlfredTextPrimary,
            unfocusedTextColor = AlfredTextPrimary,
            cursorColor = AlfredGold,
            focusedBorderColor = AlfredGold.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}

@Composable
private fun CarouselBlock(items: List<CarouselItem>, onAction: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .padding(horizontal = 20.dp) // match card padding so first/last cards align
            .height(IntrinsicSize.Max)    // all cards same height
    ) {
        items.forEach { item ->
            CarouselCard(item, onAction)
        }
    }
}

@Composable
private fun CarouselCard(item: CarouselItem, onAction: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .fillMaxHeight() // stretch to match tallest sibling
            .clip(InnerCardShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), InnerCardShape)
    ) {
        // Image area — always present for consistent height
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color.White.copy(alpha = 0.04f))
        ) {
            if (item.iconText.isNotBlank()) {
                Text(text = item.iconText, fontSize = 32.sp)
            } else {
                Text(
                    text = if (item.imageUrl.isNotBlank()) "📷" else "📍",
                    fontSize = 24.sp,
                    color = AlfredTextDim
                )
            }
        }

        // Content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // take remaining space
                .padding(12.dp)
        ) {
            Text(
                text = item.title,
                color = AlfredTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    color = AlfredTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            if (item.rating > 0f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val full = item.rating.toInt()
                    val half = (item.rating - full) >= 0.5f
                    Text(
                        text = "★".repeat(full) + (if (half) "½" else "") + "☆".repeat(5 - full - if (half) 1 else 0),
                        color = WaveYellow,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = " ${item.rating}",
                        color = AlfredTextDim,
                        fontSize = 11.sp
                    )
                }
            }

            if (item.detail.isNotBlank()) {
                Text(
                    text = item.detail,
                    color = AlfredTextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Action button at bottom — always anchored
        if (item.actionLabel.isNotBlank() && item.actionId.isNotBlank()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .clip(PillShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { onAction(item.actionId) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = item.actionLabel,
                    color = AlfredGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ChipRowBlock(chips: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
            .padding(horizontal = 20.dp) // match card padding
    ) {
        chips.forEach { chip ->
            Text(
                text = chip,
                color = AlfredTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(PillShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), PillShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProgressBarBlock(progress: Float, label: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (label.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Text(text = label, color = AlfredTextSecondary, fontSize = 12.sp)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = AlfredTextDim,
                    fontSize = 12.sp
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = AlfredGold,
            trackColor = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun RatingBlock(stars: Float, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        val fullStars = stars.toInt()
        val hasHalf = (stars - fullStars) >= 0.5f
        val emptyStars = 5 - fullStars - if (hasHalf) 1 else 0
        Text(
            text = "★".repeat(fullStars) + (if (hasHalf) "½" else "") + "☆".repeat(emptyStars),
            color = WaveYellow,
            fontSize = 16.sp,
            letterSpacing = 2.sp
        )
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = AlfredTextSecondary, fontSize = 13.sp)
        }
    }
}


@Composable
private fun LineChartBlock(
    points: List<Float>,
    label: String,
    minLabel: String,
    maxLabel: String,
    colorName: String
) {
    if (points.size < 2) return

    val lineColor = when (colorName) {
        "red" -> AlfredRed
        "blue" -> AlfredGold
        "gold" -> WaveYellow
        else -> AlfredGreen
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = AlfredTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Min/max labels row
        if (maxLabel.isNotBlank() || minLabel.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Text(text = minLabel, color = AlfredTextDim, fontSize = 11.sp)
                Text(text = maxLabel, color = AlfredTextDim, fontSize = 11.sp)
            }
        }

        val minVal = points.min()
        val maxVal = points.max()
        val range = (maxVal - minVal).coerceAtLeast(0.01f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.03f))
        ) {
            val w = size.width
            val h = size.height
            val padTop = 8f
            val padBottom = 8f
            val chartH = h - padTop - padBottom

            // Draw grid lines
            for (i in 0..3) {
                val y = padTop + chartH * (i / 3f)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // Build path
            val path = Path()
            val stepX = w / (points.size - 1).toFloat()

            points.forEachIndexed { i, value ->
                val x = i * stepX
                val y = padTop + chartH * (1f - (value - minVal) / range)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5f)
            )

            // Draw glow under the line (filled area)
            val fillPath = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = fillPath,
                color = lineColor.copy(alpha = 0.08f)
            )

            // Draw dot at last point
            val lastX = (points.size - 1) * stepX
            val lastY = padTop + chartH * (1f - (points.last() - minVal) / range)
            drawCircle(
                color = lineColor,
                radius = 4f,
                center = Offset(lastX, lastY)
            )
        }
    }
}


/** Map team code to a pair of (background, text) colors for the rectangle flag */
private fun teamFlagColors(code: String): Pair<Color, Color> {
    return when (code.uppercase()) {
        "IND" -> Color(0xFF138808) to Color.White          // India green
        "AUS" -> Color(0xFFFFCD00) to Color(0xFF003F87)    // Australia gold
        "ENG" -> Color(0xFF003F87) to Color.White          // England blue
        "WI"  -> Color(0xFF7B0041) to Color(0xFFFFD700)    // West Indies maroon
        "PAK" -> Color(0xFF01411C) to Color.White          // Pakistan green
        "SA"  -> Color(0xFF007749) to Color(0xFFFFB81C)    // South Africa green
        "NZ"  -> Color(0xFF000000) to Color.White          // New Zealand black
        "SL"  -> Color(0xFF0F1A5F) to Color(0xFFFFB700)    // Sri Lanka blue
        "BAN" -> Color(0xFF006A4E) to Color(0xFFF42A41)    // Bangladesh green
        "AFG" -> Color(0xFF000000) to Color(0xFFD32011)    // Afghanistan
        // Football clubs
        "MCI" -> Color(0xFF6CABDD) to Color.White
        "ARS" -> Color(0xFFEF0107) to Color.White
        "LIV" -> Color(0xFFC8102E) to Color.White
        "BAR" -> Color(0xFFA50044) to Color(0xFF004D98)
        "RMA" -> Color.White to Color(0xFF00529F)
        "CHE" -> Color(0xFF034694) to Color.White
        "MUN" -> Color(0xFFDA291C) to Color(0xFFFBE122)
        // IPL teams
        "CSK" -> Color(0xFFFDB913) to Color(0xFF0081E9)
        "MI"  -> Color(0xFF004BA0) to Color(0xFFD1AB3E)
        "RCB" -> Color(0xFFEC1C24) to Color(0xFF2B2A29)
        "KKR" -> Color(0xFF3A225D) to Color(0xFFF2C230)
        "DC"  -> Color(0xFF004C93) to Color(0xFFEF1B23)
        "SRH" -> Color(0xFFFF822A) to Color(0xFF000000)
        "RR"  -> Color(0xFFE73895) to Color(0xFF254AA5)
        "GT"  -> Color(0xFF1C1C1C) to Color(0xFF0B4EA2)
        "PBKS", "PK" -> Color(0xFFED1B24) to Color.White
        "LSG" -> Color(0xFF0057E2) to Color(0xFFA7E6FF)
        else  -> Color(0xFF3A3A4A) to AlfredTextPrimary    // default dark
    }
}

@Composable
private fun TeamFlagRect(code: String) {
    val (bg, fg) = teamFlagColors(code)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(52.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = code.uppercase(),
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ScoreCardBlock(card: RichBlock.ScoreCard) {
    val isLive = card.status.contains("LIVE", ignoreCase = true)
    val statusColor = if (isLive) AlfredRed else AlfredTextDim

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Detail line (tournament / match info)
        if (card.detail.isNotBlank()) {
            Text(
                text = card.detail,
                color = AlfredTextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
        }

        // Status badge
        if (card.status.isNotBlank()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(PillShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(0.5.dp, statusColor.copy(alpha = 0.3f), PillShape)
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = card.status.uppercase(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Versus row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Home team
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                TeamFlagRect(card.homeIcon.ifBlank { card.homeTeam.take(3) })
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = card.homeTeam,
                    color = AlfredTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.homeScore,
                    color = AlfredTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (card.homeExtra.isNotBlank()) {
                    Text(
                        text = card.homeExtra,
                        color = AlfredTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // VS
            Text(
                text = "VS",
                color = AlfredGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // Away team
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                TeamFlagRect(card.awayIcon.ifBlank { card.awayTeam.take(3) })
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = card.awayTeam,
                    color = AlfredTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.awayScore,
                    color = AlfredTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (card.awayExtra.isNotBlank()) {
                    Text(
                        text = card.awayExtra,
                        color = AlfredTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
