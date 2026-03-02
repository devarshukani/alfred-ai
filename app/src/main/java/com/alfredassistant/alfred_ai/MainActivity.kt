package com.alfredassistant.alfred_ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.db.KnowledgeEdge
import com.alfredassistant.alfred_ai.db.KnowledgeEdge_
import com.alfredassistant.alfred_ai.db.KnowledgeNode
import com.alfredassistant.alfred_ai.db.KnowledgeNode_
import com.alfredassistant.alfred_ai.db.MemoryEntity
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.models.ModelDownloader
import com.alfredassistant.alfred_ai.ui.*
import com.alfredassistant.alfred_ai.ui.theme.AlfredaiTheme
import io.objectbox.Box

class MainActivity : ComponentActivity() {

    private lateinit var memoryBox: Box<MemoryEntity>
    private lateinit var nodeBox: Box<KnowledgeNode>
    private lateinit var edgeBox: Box<KnowledgeEdge>

    // Mutable state holders so we can refresh from onResume
    private var memoriesState: MutableState<List<MemoryItem>>? = null
    private var nodesState: MutableState<List<GraphNodeItem>>? = null
    private var edgesState: MutableState<List<GraphEdgeItem>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ObjectBoxStore.init(this)
        memoryBox = ObjectBoxStore.store.boxFor(MemoryEntity::class.java)
        nodeBox = ObjectBoxStore.store.boxFor(KnowledgeNode::class.java)
        edgeBox = ObjectBoxStore.store.boxFor(KnowledgeEdge::class.java)

        val prefs = getSharedPreferences("alfred_onboarding", Context.MODE_PRIVATE)
        val grantedPermissions = mutableStateOf(getCurrentGrantedPermissions())

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grantedPermissions.value = getCurrentGrantedPermissions() }

        setContent {
            AlfredaiTheme {
                var onboardingDone by remember {
                    mutableStateOf(
                        prefs.getBoolean("onboarding_complete", false)
                                && ModelDownloader.isComplete(this@MainActivity)
                    )
                }
                val granted by grantedPermissions

                AnimatedContent(
                    targetState = onboardingDone,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                    label = "onboarding"
                ) { done ->
                    if (!done) {
                        OnboardingScreen(
                            grantedPermissions = granted,
                            onRequestPermissions = { perms ->
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            onFinish = {
                                prefs.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingDone = true
                                val remaining = getAllRequiredPermissions()
                                    .filter {
                                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                                                PackageManager.PERMISSION_GRANTED
                                    }
                                if (remaining.isNotEmpty()) {
                                    permissionLauncher.launch(remaining.toTypedArray())
                                }

                                // First launch: auto-start assistant in onboarding chat mode
                                val chatPrefs = getSharedPreferences("alfred_prefs", Context.MODE_PRIVATE)
                                if (!chatPrefs.getBoolean("first_launch_chat_done", false)) {
                                    chatPrefs.edit().putBoolean("first_launch_chat_done", true).apply()
                                    startActivity(
                                        Intent(this@MainActivity, OverlayAssistActivity::class.java)
                                            .putExtra("onboarding_chat", true)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                    finish()
                                }
                            }
                        )
                    } else {
                        DashboardContent()
                    }
                }
            }
        }
    }

    @Composable
    private fun DashboardContent() {
        val memories = remember { mutableStateOf(loadMemories()) }
        val nodes = remember { mutableStateOf(loadNodes()) }
        val edges = remember { mutableStateOf(loadEdges()) }

        // Store refs so onResume can refresh
        memoriesState = memories
        nodesState = nodes
        edgesState = edges

        MainDashboardScreen(
            memories = memories.value,
            graphNodes = nodes.value,
            graphEdges = edges.value,
            onDeleteMemory = { id ->
                val memory = memoryBox.get(id)
                memoryBox.remove(id)

                // Cascade: remove graph nodes whose labels appear in this memory
                if (memory != null) {
                    val words = memory.value.lowercase()
                    val allNodes = nodeBox.all
                    val toRemove = allNodes.filter { node ->
                        // Only remove if the node label is meaningfully in the memory text
                        // Skip generic nodes like "user"
                        node.label.length > 2 && node.label != "user" &&
                                words.contains(node.label.lowercase())
                    }
                    for (node in toRemove) {
                        // Check this node isn't referenced by other memories
                        val otherMemories = memoryBox.all.any { other ->
                            other.id != id && other.value.lowercase().contains(node.label.lowercase())
                        }
                        if (!otherMemories) {
                            edgeBox.remove(edgeBox.query(KnowledgeEdge_.sourceNodeId.equal(node.id)).build().find())
                            edgeBox.remove(edgeBox.query(KnowledgeEdge_.targetNodeId.equal(node.id)).build().find())
                            nodeBox.remove(node)
                        }
                    }
                }

                memories.value = loadMemories()
                nodes.value = loadNodes()
                edges.value = loadEdges()
            },
            onDeleteNode = { id ->
                edgeBox.remove(edgeBox.query(KnowledgeEdge_.sourceNodeId.equal(id)).build().find())
                edgeBox.remove(edgeBox.query(KnowledgeEdge_.targetNodeId.equal(id)).build().find())
                nodeBox.remove(id)
                nodes.value = loadNodes()
                edges.value = loadEdges()
            },
            onDeleteEdge = { id ->
                edgeBox.remove(id)
                edges.value = loadEdges()
            },
            onLaunchAssistant = {
                startActivity(
                    Intent(this, OverlayAssistActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::memoryBox.isInitialized) {
            memoriesState?.value = loadMemories()
            nodesState?.value = loadNodes()
            edgesState?.value = loadEdges()
        }
    }

    private fun loadMemories(): List<MemoryItem> =
        memoryBox.all.map { e ->
            MemoryItem(e.id, e.key, e.value, e.type, e.createdAt)
        }.sortedByDescending { it.createdAt }

    private fun loadNodes(): List<GraphNodeItem> =
        nodeBox.all.map { n ->
            GraphNodeItem(n.id, n.label, n.nodeType, n.attributes)
        }.sortedBy { it.label }

    private fun loadEdges(): List<GraphEdgeItem> =
        edgeBox.all.mapNotNull { edge ->
            val src = nodeBox.get(edge.sourceNodeId)
            val tgt = nodeBox.get(edge.targetNodeId)
            if (src != null && tgt != null) {
                GraphEdgeItem(edge.id, src.label, edge.relationship, tgt.label)
            } else null
        }

    private fun getCurrentGrantedPermissions(): Set<String> =
        onboardingSteps.flatMap { it.permissions }.toSet()
            .filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
            .toSet()

    private fun getAllRequiredPermissions(): List<String> {
        val base = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base.add(Manifest.permission.READ_MEDIA_IMAGES)
            base.add(Manifest.permission.READ_MEDIA_VIDEO)
            base.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            base.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return base
    }
}
