package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==================== DATA MODELS ====================

data class MemoryItem(
    val id: Long,
    val key: String,
    val value: String,
    val type: String,
    val createdAt: Long
)

data class GraphNodeItem(
    val id: Long,
    val label: String,
    val nodeType: String,
    val attributes: String
)

data class GraphEdgeItem(
    val id: Long,
    val sourceLabel: String,
    val relationship: String,
    val targetLabel: String
)

private enum class DashboardTab { MEMORIES, PEOPLE_PLACES }

// ==================== MAIN SCREEN ====================

@Composable
fun MainDashboardScreen(
    memories: List<MemoryItem>,
    graphNodes: List<GraphNodeItem>,
    graphEdges: List<GraphEdgeItem>,
    onDeleteMemory: (Long) -> Unit,
    onDeleteNode: (Long) -> Unit,
    onDeleteEdge: (Long) -> Unit,
    onLaunchAssistant: () -> Unit
) {
    var activeTab by remember { mutableStateOf(DashboardTab.MEMORIES) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlfredBlack)
    ) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Spacer(modifier = Modifier.height(20.dp))
            HeaderSection(onLaunchAssistant)

            Spacer(modifier = Modifier.height(24.dp))

            // Tabs
            TabRow(activeTab, memories.size, graphNodes.size) { activeTab = it }

            Spacer(modifier = Modifier.height(4.dp))

            // Content
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(220)) {
                        if (targetState == DashboardTab.PEOPLE_PLACES) it / 5 else -it / 5
                    }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) {
                        if (targetState == DashboardTab.PEOPLE_PLACES) -it / 5 else it / 5
                    })
                },
                label = "tab"
            ) { tab ->
                when (tab) {
                    DashboardTab.MEMORIES -> MemoriesTab(memories, onDeleteMemory)
                    DashboardTab.PEOPLE_PLACES -> PeopleAndThingsTab(
                        graphNodes, graphEdges, onDeleteNode, onDeleteEdge
                    )
                }
            }
        }
    }
}

// ==================== AMBIENT GLOW ====================

@Composable
private fun AmbientGlow() {
    val inf = rememberInfiniteTransition(label = "glow")
    val d1 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), "d1"
    )
    val d2 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), "d2"
    )
    val p by inf.animateFloat(
        0.25f, 0.5f,
        infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse), "p"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // Warm top-left orange glow
        drawCircle(
            Brush.radialGradient(
                listOf(WaveOrange.copy(alpha = p * 0.10f), Color.Transparent),
                Offset(w * 0.15f + sin(d1.toDouble()).toFloat() * w * 0.05f, h * 0.08f),
                w * 0.55f
            ),
            w * 0.55f,
            Offset(w * 0.15f + sin(d1.toDouble()).toFloat() * w * 0.05f, h * 0.08f)
        )
        // Warm bottom-right yellow glow
        drawCircle(
            Brush.radialGradient(
                listOf(WaveYellow.copy(alpha = p * 0.07f), Color.Transparent),
                Offset(w * 0.85f + cos(d2.toDouble()).toFloat() * w * 0.04f, h * 0.75f),
                w * 0.5f
            ),
            w * 0.5f,
            Offset(w * 0.85f + cos(d2.toDouble()).toFloat() * w * 0.04f, h * 0.75f)
        )
    }
}

// ==================== HEADER ====================

@Composable
private fun HeaderSection(onLaunchAssistant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Alfred",
                color = AlfredTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "What I remember about you",
                color = AlfredTextDim,
                fontSize = 14.sp
            )
        }

        // Mic button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(WaveOrange, WaveYellow)))
                .clickable { onLaunchAssistant() }
        ) {
            Icon(
                imageVector = TablerIcons.Microphone,
                contentDescription = "Talk to Alfred",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== TAB ROW ====================

@Composable
private fun TabRow(
    activeTab: DashboardTab,
    memoryCount: Int,
    nodeCount: Int,
    onTabChange: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabPill(
            label = "Memories",
            count = memoryCount,
            icon = TablerIcons.Note,
            active = activeTab == DashboardTab.MEMORIES,
            color = WaveBlue,
            modifier = Modifier.weight(1f)
        ) { onTabChange(DashboardTab.MEMORIES) }

        TabPill(
            label = "People & Things",
            count = nodeCount,
            icon = TablerIcons.Users,
            active = activeTab == DashboardTab.PEOPLE_PLACES,
            color = WaveGreen,
            modifier = Modifier.weight(1f)
        ) { onTabChange(DashboardTab.PEOPLE_PLACES) }
    }
}

@Composable
private fun TabPill(
    label: String,
    count: Int,
    icon: ImageVector,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
        tween(200), label = "tbg"
    )
    val border by animateColorAsState(
        if (active) color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        tween(200), label = "tbr"
    )
    val tint by animateColorAsState(
        if (active) color else AlfredTextDim, tween(200), label = "tt"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = tint, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        if (count > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                color = tint.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tint.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

// ==================== MEMORIES TAB ====================

@Composable
private fun MemoriesTab(memories: List<MemoryItem>, onDelete: (Long) -> Unit) {
    if (memories.isEmpty()) {
        EmptyState(
            icon = TablerIcons.Note,
            color = WaveBlue,
            title = "Nothing here yet",
            subtitle = "Just talk to Alfred — he'll remember\nthe important stuff for you"
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(memories, key = { it.id }) { memory ->
            MemoryRow(memory, onDelete)
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MemoryRow(memory: MemoryItem, onDelete: (Long) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = if (memory.type == "fact") WaveBlue else WavePurple

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { /* could expand later */ }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Colored dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.value,
                color = AlfredTextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = friendlyTimestamp(memory.createdAt),
                color = AlfredTextDim,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Delete
        AnimatedContent(confirmDelete, label = "del",
            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) }
        ) { confirming ->
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton(TablerIcons.Trash, AlfredRed, AlfredRed.copy(alpha = 0.12f)) {
                        onDelete(memory.id); confirmDelete = false
                    }
                    MiniButton(TablerIcons.X, AlfredTextDim, GlassWhite) {
                        confirmDelete = false
                    }
                }
            } else {
                MiniButton(TablerIcons.X, AlfredTextDim.copy(alpha = 0.5f), Color.Transparent) {
                    confirmDelete = true
                }
            }
        }
    }

    // Subtle divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.04f))
    )
}

// ==================== PEOPLE & THINGS TAB ====================

@Composable
private fun PeopleAndThingsTab(
    nodes: List<GraphNodeItem>,
    edges: List<GraphEdgeItem>,
    onDeleteNode: (Long) -> Unit,
    onDeleteEdge: (Long) -> Unit
) {
    if (nodes.isEmpty()) {
        EmptyState(
            icon = TablerIcons.Users,
            color = WaveGreen,
            title = "No one here yet",
            subtitle = "Tell Alfred about people, places, and things\nyou care about"
        )
        return
    }

    // Group nodes by type, sorted by friendly order
    val sortedGroups = remember(nodes) {
        nodes.groupBy { friendlyType(it.nodeType) }
            .entries
            .sortedBy { typeOrder(it.key) }
    }

    // Flatten into a stable list of items for LazyColumn
    val flatItems = remember(sortedGroups, edges) {
        buildList<Any> {
            for ((groupLabel, groupNodes) in sortedGroups) {
                add(groupLabel)                     // String = section header
                addAll(groupNodes)                  // GraphNodeItem = node row
            }
            if (edges.isNotEmpty()) {
                add("CONNECTIONS")
                addAll(edges)                       // GraphEdgeItem = edge row
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = flatItems.size,
            key = { i ->
                when (val item = flatItems[i]) {
                    is String -> "header_$item"
                    is GraphNodeItem -> "node_${item.id}"
                    is GraphEdgeItem -> "edge_${item.id}"
                    else -> i
                }
            }
        ) { i ->
            when (val item = flatItems[i]) {
                is String -> {
                    Text(
                        text = item,
                        color = AlfredTextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(
                            top = if (i == 0) 4.dp else 12.dp,
                            bottom = 4.dp,
                            start = 4.dp
                        )
                    )
                }
                is GraphNodeItem -> NodeRow(item, onDeleteNode)
                is GraphEdgeItem -> EdgeRow(item, onDeleteEdge)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun NodeRow(node: GraphNodeItem, onDelete: (Long) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val color = nodeColor(node.nodeType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.10f))
        ) {
            Icon(
                imageVector = nodeIcon(node.nodeType),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.label.replaceFirstChar { it.uppercase() },
                color = AlfredTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            val attrText = friendlyAttributes(node.attributes)
            if (attrText.isNotBlank()) {
                Text(
                    text = attrText,
                    color = AlfredTextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedContent(confirmDelete, label = "ndel",
            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) }
        ) { confirming ->
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton(TablerIcons.Trash, AlfredRed, AlfredRed.copy(alpha = 0.12f)) {
                        onDelete(node.id); confirmDelete = false
                    }
                    MiniButton(TablerIcons.X, AlfredTextDim, GlassWhite) {
                        confirmDelete = false
                    }
                }
            } else {
                MiniButton(TablerIcons.X, AlfredTextDim.copy(alpha = 0.5f), Color.Transparent) {
                    confirmDelete = true
                }
            }
        }
    }
}

@Composable
private fun EdgeRow(edge: GraphEdgeItem, onDelete: (Long) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // source → relation → target
        Text(
            text = edge.sourceLabel.replaceFirstChar { it.uppercase() },
            color = WaveGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.25f)
        )

        Text(text = " → ", color = AlfredTextDim, fontSize = 12.sp)

        Text(
            text = edge.relationship.replace("_", " "),
            color = WaveYellow,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(WaveYellow.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )

        Text(text = " → ", color = AlfredTextDim, fontSize = 12.sp)

        Text(
            text = edge.targetLabel.replaceFirstChar { it.uppercase() },
            color = WaveCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.25f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        AnimatedContent(confirmDelete, label = "edel",
            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) }
        ) { confirming ->
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton(TablerIcons.Trash, AlfredRed, AlfredRed.copy(alpha = 0.12f)) {
                        onDelete(edge.id); confirmDelete = false
                    }
                    MiniButton(TablerIcons.X, AlfredTextDim, GlassWhite) {
                        confirmDelete = false
                    }
                }
            } else {
                MiniButton(TablerIcons.X, AlfredTextDim.copy(alpha = 0.5f), Color.Transparent) {
                    confirmDelete = true
                }
            }
        }
    }
}

// ==================== SHARED ====================

@Composable
private fun MiniButton(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, color: Color, title: String, subtitle: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(40.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.06f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                color = AlfredTextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = AlfredTextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// ==================== HELPERS ====================

private fun nodeIcon(type: String): ImageVector = when (type) {
    "person" -> TablerIcons.User
    "place" -> TablerIcons.MapPin
    "thing" -> TablerIcons.Box
    "concept" -> TablerIcons.Bulb
    "event" -> TablerIcons.Calendar
    "time" -> TablerIcons.Clock
    "preference" -> TablerIcons.Star
    else -> TablerIcons.Atom2
}

private fun nodeColor(type: String): Color = when (type) {
    "person" -> WaveBlue
    "place" -> WaveGreen
    "thing" -> WaveOrange
    "concept" -> WavePurple
    "event" -> WaveYellow
    "time" -> WaveCyan
    "preference" -> WavePink
    else -> AlfredTextDim
}

/** Turn internal type names into friendly group headers */
private fun friendlyType(type: String): String = when (type) {
    "person" -> "PEOPLE"
    "place" -> "PLACES"
    "thing" -> "THINGS"
    "concept" -> "IDEAS"
    "event" -> "EVENTS"
    "time" -> "TIMES"
    "preference" -> "PREFERENCES"
    else -> "OTHER"
}

/** Sort order for groups */
private fun typeOrder(label: String): Int = when (label) {
    "PEOPLE" -> 0; "PLACES" -> 1; "THINGS" -> 2; "EVENTS" -> 3
    "IDEAS" -> 4; "PREFERENCES" -> 5; "TIMES" -> 6; else -> 7
}

private fun friendlyAttributes(json: String): String {
    return try {
        val obj = org.json.JSONObject(json)
        if (obj.length() == 0) return ""
        obj.keys().asSequence().take(3).map { key ->
            "${key.replace("_", " ")}: ${obj.optString(key, "")}"
        }.joinToString("  ·  ")
    } catch (_: Exception) { "" }
}

private fun friendlyTimestamp(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
