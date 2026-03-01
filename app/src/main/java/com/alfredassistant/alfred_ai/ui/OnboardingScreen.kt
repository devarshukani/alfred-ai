package com.alfredassistant.alfred_ai.ui

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.models.ModelDownloader
import com.alfredassistant.alfred_ai.ui.theme.*
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Download
import compose.icons.tablericons.MapPin
import compose.icons.tablericons.Message
import compose.icons.tablericons.Microphone
import compose.icons.tablericons.Phone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// ── Data ──

data class OnboardingStep(
    val title: String,
    val description: String,
    val permissions: List<String>,
    val accentColor: Color,
    val icon: ImageVector,
    val optional: Boolean = false
)

val onboardingSteps = listOf(
    OnboardingStep(
        title = "Voice",
        description = "Alfred listens and speaks.\nGrant microphone access to have a natural conversation.",
        permissions = listOf(Manifest.permission.RECORD_AUDIO),
        accentColor = WaveBlue,
        icon = TablerIcons.Microphone
    ),
    OnboardingStep(
        title = "Phone & Contacts",
        description = "Make calls and find contacts hands-free.\nAlfred can dial anyone in your phonebook.",
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
        accentColor = WaveGreen,
        icon = TablerIcons.Phone
    ),
    OnboardingStep(
        title = "Calendar",
        description = "Schedule events and check your agenda.\nAlfred manages your calendar by voice.",
        permissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        accentColor = WaveYellow,
        icon = TablerIcons.Calendar
    ),
    OnboardingStep(
        title = "Messages",
        description = "Send texts without touching your phone.\nJust tell Alfred who and what.",
        permissions = listOf(Manifest.permission.SEND_SMS),
        accentColor = WavePurple,
        icon = TablerIcons.Message
    ),
    OnboardingStep(
        title = "Location",
        description = "Weather, directions, and local info.\nAlfred uses your location to stay relevant.",
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
        accentColor = WaveCyan,
        icon = TablerIcons.MapPin,
        optional = true
    )
)

// ── Main composable ──

@Composable
fun OnboardingScreen(
    grantedPermissions: Set<String>,
    onRequestPermissions: (List<String>) -> Unit,
    onFinish: () -> Unit
) {
    // Skip permission pages if all permissions already granted (e.g. model re-download)
    val allPermsGranted = onboardingSteps.all { step ->
        step.permissions.all { it in grantedPermissions }
    }
    // Two phases: permission steps, then model download
    var showDownload by remember { mutableStateOf(allPermsGranted) }

    AnimatedContent(
        targetState = showDownload,
        transitionSpec = {
            fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 3 } togetherWith
                fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 3 }
        },
        label = "phase"
    ) { downloading ->
        if (!downloading) {
            PermissionPages(
                grantedPermissions = grantedPermissions,
                onRequestPermissions = onRequestPermissions,
                onPermissionsDone = { showDownload = true }
            )
        } else {
            ModelDownloadPage(onFinish = onFinish)
        }
    }
}

// ── Permission pages (existing flow) ──

@Composable
private fun PermissionPages(
    grantedPermissions: Set<String>,
    onRequestPermissions: (List<String>) -> Unit,
    onPermissionsDone: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val step = onboardingSteps[currentPage]
    val isLast = currentPage == onboardingSteps.lastIndex
    val stepGranted = step.permissions.all { it in grantedPermissions }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlfredBlack)
    ) {
        AnimatedGlow(color = step.accentColor)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.weight(0.15f))

            AnimatedIcon(icon = step.icon, color = step.accentColor, key = currentPage)

            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = step.title,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 3 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 3 })
                },
                label = "title"
            ) { title ->
                Text(
                    text = title,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    color = AlfredTextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = step.description,
                transitionSpec = {
                    (fadeIn(tween(500, delayMillis = 100)))
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "desc"
            ) { desc ->
                Text(
                    text = desc,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = AlfredTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(0.15f))

            PermissionButton(
                granted = stepGranted,
                accentColor = step.accentColor,
                optional = step.optional,
                onClick = {
                    if (!stepGranted) {
                        onRequestPermissions(step.permissions)
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentPage > 0) {
                    Text(
                        text = "Back",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = AlfredTextDim,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { currentPage-- }
                            .padding(12.dp)
                    )
                } else {
                    Spacer(Modifier.width(60.dp))
                }

                PageDots(
                    total = onboardingSteps.size,
                    current = currentPage,
                    accentColor = step.accentColor
                )

                Text(
                    text = if (isLast) "Next" else "Next",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = step.accentColor,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (isLast) onPermissionsDone() else currentPage++
                        }
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Model download page ──

@Composable
private fun ModelDownloadPage(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Ready) }
    var currentStep by remember { mutableIntStateOf(0) }
    var totalSteps by remember { mutableIntStateOf(5) }
    var currentLabel by remember { mutableStateOf("") }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-start download, or skip if already complete
    LaunchedEffect(Unit) {
        if (ModelDownloader.isComplete(context)) {
            downloadState = DownloadState.Done
        }
    }

    val accentColor = WaveOrange
    val progress = if (totalSteps > 0) currentStep.toFloat() / totalSteps else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlfredBlack)
    ) {
        AnimatedGlow(color = accentColor)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.weight(0.15f))

            AnimatedIcon(icon = TablerIcons.Download, color = accentColor, key = 99)

            Spacer(Modifier.height(40.dp))

            Text(
                text = when (downloadState) {
                    DownloadState.Ready -> "AI Models"
                    DownloadState.Downloading -> "Downloading"
                    DownloadState.Done -> "Ready"
                    DownloadState.Error -> "Download Failed"
                },
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                color = AlfredTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (downloadState) {
                    DownloadState.Ready ->
                        "Alfred needs to download voice and language models.\nThis is a one-time setup (~150 MB)."
                    DownloadState.Downloading ->
                        currentLabel.ifBlank { "Preparing download..." }
                    DownloadState.Done ->
                        "All models are ready.\nAlfred is good to go."
                    DownloadState.Error ->
                        errorMessage ?: "Something went wrong. Check your connection and try again."
                },
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = AlfredTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Progress bar (visible during download)
            if (downloadState == DownloadState.Downloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    val mbDownloaded = bytesDownloaded / (1024f * 1024f)
                    Text(
                        text = "Step ${currentStep + 1} of $totalSteps  •  ${"%.1f".format(mbDownloaded)} MB",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = AlfredTextDim
                    )
                }
            }

            Spacer(Modifier.weight(0.15f))

            // Action button
            when (downloadState) {
                DownloadState.Ready -> {
                    DownloadActionButton(
                        label = "Download Models",
                        color = accentColor,
                        onClick = {
                            downloadState = DownloadState.Downloading
                            scope.launch {
                                val result = ModelDownloader.downloadAll(context) { step, total, label, bytes ->
                                    currentStep = step
                                    totalSteps = total
                                    currentLabel = label
                                    if (bytes >= 0) bytesDownloaded = bytes
                                }
                                result.fold(
                                    onSuccess = { downloadState = DownloadState.Done },
                                    onFailure = { e ->
                                        errorMessage = e.message
                                        downloadState = DownloadState.Error
                                    }
                                )
                            }
                        }
                    )
                }
                DownloadState.Downloading -> {
                    // Show a disabled/dim button while downloading
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accentColor.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = "Downloading...",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = AlfredTextDim
                        )
                    }
                }
                DownloadState.Done -> {
                    DownloadActionButton(
                        label = "Get Started",
                        color = WaveGreen,
                        onClick = onFinish
                    )
                }
                DownloadState.Error -> {
                    DownloadActionButton(
                        label = "Retry Download",
                        color = AlfredRed,
                        onClick = {
                            errorMessage = null
                            downloadState = DownloadState.Downloading
                            scope.launch {
                                val result = ModelDownloader.downloadAll(context) { step, total, label, bytes ->
                                    currentStep = step
                                    totalSteps = total
                                    currentLabel = label
                                    if (bytes >= 0) bytesDownloaded = bytes
                                }
                                result.fold(
                                    onSuccess = { downloadState = DownloadState.Done },
                                    onFailure = { e ->
                                        errorMessage = e.message
                                        downloadState = DownloadState.Error
                                    }
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

private enum class DownloadState { Ready, Downloading, Done, Error }

@Composable
private fun DownloadActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = color
        )
    }
}

// ── Animated background glow ──

@Composable
private fun AnimatedGlow(color: Color) {
    val inf = rememberInfiniteTransition(label = "glow")
    val breathe by inf.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse),
        label = "breathe"
    )
    val drift by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )

    val animColor by animateColorAsState(
        targetValue = color.copy(alpha = 0.15f),
        animationSpec = tween(600),
        label = "glowColor"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f + sin(drift) * size.width * 0.08f
        val cy = size.height * 0.30f
        val radius = size.width * 0.9f * breathe

        val stops = Array(48) { i ->
            val t = i / 47f
            val falloff = exp(-(t / 0.45f).pow(2)).toFloat()
            t to lerp(Color.Transparent, animColor, falloff)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = stops,
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}

// ── Animated icon with pulse ring ──

@Composable
private fun AnimatedIcon(icon: ImageVector, color: Color, key: Int) {
    val inf = rememberInfiniteTransition(label = "iconPulse")
    val ringScale by inf.animateFloat(
        1f, 1.5f,
        infiniteRepeatable(tween(2000, easing = EaseOut), RepeatMode.Restart),
        label = "ring"
    )
    val ringAlpha by inf.animateFloat(
        0.4f, 0f,
        infiniteRepeatable(tween(2000, easing = EaseOut), RepeatMode.Restart),
        label = "ringA"
    )

    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        visible = false
        delay(50)
        visible = true
    }
    val entryScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "entryScale"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "entryAlpha"
    )

    val animColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(500),
        label = "iconColor"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
                .clip(CircleShape)
                .background(animColor.copy(alpha = 0.15f))
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .scale(entryScale)
                .alpha(entryAlpha)
                .clip(CircleShape)
                .background(animColor.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animColor,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

// ── Permission button ──

@Composable
private fun PermissionButton(
    granted: Boolean,
    accentColor: Color,
    optional: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (granted) WaveGreen.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f),
        animationSpec = tween(400),
        label = "btnBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (granted) WaveGreen else AlfredTextPrimary,
        animationSpec = tween(400),
        label = "btnText"
    )

    val label = when {
        granted -> "✓  Granted"
        optional -> "Grant Access (Optional)"
        else -> "Grant Access"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .then(
                if (!granted) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = textColor
        )
    }
}

// ── Page indicator dots ──

@Composable
private fun PageDots(total: Int, current: Int, accentColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val isActive = i == current
            val dotColor by animateColorAsState(
                targetValue = if (isActive) accentColor else AlfredSlate,
                animationSpec = tween(300),
                label = "dot$i"
            )
            val dotWidth by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "dotW$i"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
