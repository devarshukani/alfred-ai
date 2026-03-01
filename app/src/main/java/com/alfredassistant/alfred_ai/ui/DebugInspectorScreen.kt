package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*

/** Which tab is active in the debug inspector */
enum class DebugTab { MEMORY, GRAPH, TOOLS }

/**
 * Debug inspector overlay — shows memories, knowledge graph, and tool routing info.
 * Only visible in debug builds, toggled from the existing debug panel.
 */
@Composable
fun DebugInspectorScreen(
    visible: Boolean,
    memories: List<Pair<String, String>>,
    graphNodes: List<Triple<Long, String, String>>,
    graphEdges: List<Triple<String, String, String>>,
    selectedTools: List<String>,
    executedTools: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 3 },
        modifier = modifier
    ) {
        var activeTab by remember { mutableStateOf(DebugTab.MEMORY) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0101018))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "🔍 Debug Inspector",
                    color = AlfredTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✕",
                    color = AlfredTextDim,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
            }

            // Tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TabChip("Memory (${memories.size})", activeTab == DebugTab.MEMORY, WaveBlue) {
                    activeTab = DebugTab.MEMORY
                }
                TabChip("Graph (${graphNodes.size})", activeTab == DebugTab.GRAPH, WaveGreen) {
                    activeTab = DebugTab.GRAPH
                }
                TabChip("Tools (${selectedTools.size})", activeTab == DebugTab.TOOLS, WavePurple) {
                    activeTab = DebugTab.TOOLS
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            when (activeTab) {
                DebugTab.MEMORY -> MemoryTab(memories)
                DebugTab.GRAPH -> GraphTab(graphNodes, graphEdges)
                DebugTab.TOOLS -> ToolsTab(selectedTools, executedTools)
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) color else AlfredTextDim,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                0.5.dp,
                if (active) color.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

// ==================== MEMORY TAB ====================

@Composable
private fun MemoryTab(memories: List<Pair<String, String>>) {
    if (memories.isEmpty()) {
        EmptyState("No memories stored yet")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(memories) { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(10.dp)
            ) {
                Text(
                    text = key,
                    color = WaveBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.4f)
                )
                Text(
                    text = value,
                    color = AlfredTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}

// ==================== GRAPH TAB ====================

@Composable
private fun GraphTab(
    nodes: List<Triple<Long, String, String>>,
    edges: List<Triple<String, String, String>>
) {
    if (nodes.isEmpty()) {
        EmptyState("Knowledge graph is empty")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Section: Nodes
        item {
            SectionHeader("Nodes", WaveGreen)
        }
        items(nodes) { (id, label, type) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                val typeEmoji = when (type) {
                    "person" -> "👤"
                    "place" -> "📍"
                    "thing" -> "📦"
                    "concept" -> "💡"
                    "event" -> "📅"
                    else -> "•"
                }
                Text(
                    text = typeEmoji,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = label,
                        color = AlfredTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = type,
                        color = AlfredTextDim,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Section: Edges
        if (edges.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Relationships", WaveCyan)
            }
            items(edges) { (source, rel, target) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source,
                        color = WaveGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " → ",
                        color = AlfredTextDim,
                        fontSize = 12.sp
                    )
                    Text(
                        text = rel,
                        color = WaveYellow,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WaveYellow.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = " → ",
                        color = AlfredTextDim,
                        fontSize = 12.sp
                    )
                    Text(
                        text = target,
                        color = WaveCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ==================== TOOLS TAB ====================

@Composable
private fun ToolsTab(selectedTools: List<String>, executedTools: List<String>) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Executed tools
        item {
            SectionHeader(
                if (executedTools.isEmpty()) "Executed Tools (none yet)"
                else "Executed Tools (${executedTools.size})",
                WaveOrange
            )
        }
        if (executedTools.isNotEmpty()) {
            items(executedTools) { name ->
                ToolRow(name, WaveOrange, executed = true)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Selected tools (sent to LLM)
        item {
            SectionHeader(
                if (selectedTools.isEmpty()) "Selected Tools (none yet)"
                else "Selected for LLM (${selectedTools.size})",
                WavePurple
            )
        }
        if (selectedTools.isEmpty()) {
            item {
                Text(
                    text = "Send a message to see which tools get selected",
                    color = AlfredTextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        } else {
            items(selectedTools) { name ->
                val wasExecuted = name in executedTools
                ToolRow(name, if (wasExecuted) WaveGreen else WavePurple, executed = wasExecuted)
            }
        }
    }
}

@Composable
private fun ToolRow(name: String, color: Color, executed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (executed) "⚡" else "○",
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = name,
            color = color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (executed) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== SHARED ====================

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Text(
            text = message,
            color = AlfredTextDim,
            fontSize = 13.sp
        )
    }
}
