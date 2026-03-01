package com.alfredassistant.alfred_ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.BuildConfig
import com.alfredassistant.alfred_ai.assistant.AlfredBrain

@Composable
fun WaveAssistantScreen(
    state: AssistantState,
    audioLevel: Float,
    confirmation: ConfirmationRequest?,
    richCard: RichCard?,
    brain: AlfredBrain?,
    onMicTap: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onCardAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug overrides
    var debugState by remember { mutableStateOf<AssistantState?>(null) }
    var debugConfirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
    var showInspector by remember { mutableStateOf(false) }

    // Debug data — refreshed when inspector is shown or state changes
    var debugMemories by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var debugNodes by remember { mutableStateOf<List<Triple<Long, String, String>>>(emptyList()) }
    var debugEdges by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var debugSelectedTools by remember { mutableStateOf<List<String>>(emptyList()) }
    var debugExecutedTools by remember { mutableStateOf<List<String>>(emptyList()) }
    var debugSelectedSkills by remember { mutableStateOf<List<com.alfredassistant.alfred_ai.skills.SelectedSkillInfo>>(emptyList()) }

    // Refresh debug data when inspector is visible or state changes
    LaunchedEffect(showInspector, state) {
        if (showInspector && brain != null) {
            debugMemories = brain.getDebugMemories()
            debugNodes = brain.getDebugGraphNodes()
            debugEdges = brain.getDebugGraphEdges()
            debugSelectedTools = brain.lastSelectedTools
            debugExecutedTools = brain.lastExecutedTools
            debugSelectedSkills = brain.lastSelectedSkills
        }
    }

    val activeState = debugState ?: state
    val activeConfirmation = debugConfirmation ?: confirmation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (activeState == AssistantState.IDLE) onDismiss()
            }
    ) {
        // Main content at bottom
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            // Wave bar at the very bottom (renders first = behind)
            AlfredWaveBar(
                state = activeState,
                audioLevel = audioLevel,
                onClick = onMicTap,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Confirmation box above the wave bar (renders second = in front)
            ConfirmationBox(
                confirmation = activeConfirmation,
                onOptionSelected = { option ->
                    if (debugConfirmation != null) {
                        debugConfirmation = null
                        debugState = null
                    } else {
                        onOptionSelected(option)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
            )

            // Rich card above the wave bar
            RichCardBox(
                richCard = richCard,
                onAction = onCardAction,
                onToggle = { _, _ -> },
                onTextInput = { _, _ -> },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
            )
        }

        // Debug buttons — top right, only in debug builds
        if (BuildConfig.DEBUG) {
            // Debug inspector overlay
            DebugInspectorScreen(
                visible = showInspector,
                memories = debugMemories,
                graphNodes = debugNodes,
                graphEdges = debugEdges,
                selectedTools = debugSelectedTools,
                executedTools = debugExecutedTools,
                selectedSkills = debugSelectedSkills,
                onDismiss = { showInspector = false },
                modifier = Modifier.fillMaxSize()
            )

            DebugPanel(
                onStateChange = { debugState = it },
                onShowConfirmation = {
                    debugState = AssistantState.AWAITING_CONFIRMATION
                    debugConfirmation = ConfirmationRequest(
                        prompt = "I found three contacts for \"mom\". Which one should I call?\n\n1. Mom — +91REDACTED\n2. Rehanatik Shaikh Mom — +918829209160\n3. Sukhmeet Bawa Mom — +91REDACTED",
                        options = listOf(
                            "Call Mom",
                            "Rehanatik Shaikh",
                            "Sukhmeet Bawa",
                            "Cancel"
                        ),
                        buttonStyles = listOf("primary", "primary", "primary", "cancel")
                    )
                },
                onToggleInspector = {
                    showInspector = !showInspector
                    // Refresh data when opening
                    if (showInspector && brain != null) {
                        debugMemories = brain.getDebugMemories()
                        debugNodes = brain.getDebugGraphNodes()
                        debugEdges = brain.getDebugGraphEdges()
                        debugSelectedTools = brain.lastSelectedTools
                        debugExecutedTools = brain.lastExecutedTools
                        debugSelectedSkills = brain.lastSelectedSkills
                    }
                },
                inspectorOpen = showInspector,
                onReset = {
                    debugState = null
                    debugConfirmation = null
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp)
            )
        }
    }
}

@Composable
private fun DebugPanel(
    onStateChange: (AssistantState) -> Unit,
    onShowConfirmation: () -> Unit,
    onToggleInspector: () -> Unit,
    inspectorOpen: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        DebugChip("Reset", Color(0xFFFF6B6B)) { onReset() }
        DebugChip(
            if (inspectorOpen) "Close" else "Inspect",
            if (inspectorOpen) Color(0xFF5CD07E) else Color(0xFF4DD8E8)
        ) { onToggleInspector() }
        DebugChip("Idle", Color(0xFF5B9BFF)) { onStateChange(AssistantState.IDLE) }
        DebugChip("Listen", Color(0xFF5CD07E)) { onStateChange(AssistantState.LISTENING) }
        DebugChip("Process", Color(0xFFA78BFA)) { onStateChange(AssistantState.PROCESSING) }
        DebugChip("Speak", Color(0xFFFFAB5E)) { onStateChange(AssistantState.SPEAKING) }
        DebugChip("Confirm", Color(0xFFFFD166)) { onShowConfirmation() }
    }
}

@Composable
private fun DebugChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
