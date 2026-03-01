package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import com.alfredassistant.alfred_ai.skills.*
import com.alfredassistant.alfred_ai.tools.*
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import kotlinx.coroutines.CompletableDeferred

class AlfredBrain(context: Context) {

    private val mistral = MistralClient()

    // Vector DB + Embedding infrastructure
    private val embeddingModel = EmbeddingModel(context)
    private val knowledgeGraph = KnowledgeGraph(embeddingModel)
    private val memoryStore = MemoryStore(context, embeddingModel, knowledgeGraph)

    // Skill registry (replaces ToolRegistry)
    private val skillRegistry = SkillRegistry(embeddingModel)

    // Confirmation skill needs special wiring for UI callbacks
    private val confirmationSkill = ConfirmationSkill()

    // Callback for when AI wants to show options to the user
    var onConfirmationNeeded: ((ConfirmationRequest) -> Unit)? = null
        set(value) {
            field = value
            confirmationSkill.onConfirmationNeeded = value
        }

    // Callback for when a redirecting action was performed
    var onRedirectingAction: (() -> Unit)? = null

    // Set to true when a tool call redirects to another app/screen
    @Volatile
    var didRedirect: Boolean = false
        private set

    /** Last set of tool names selected for a query (for debug UI) */
    var lastSelectedTools: List<String> = emptyList()
        private set
    /** Last set of tool calls executed (for debug UI) */
    var lastExecutedTools: List<String> = emptyList()
        private set
    /** Last set of skills selected for a query (for debug UI) */
    val lastSelectedSkills: List<SelectedSkillInfo>
        get() = skillRegistry.lastSelectedSkills

    init {
        ObjectBoxStore.init(context)
        embeddingModel.initialize()

        // Shared action instances
        val phoneAction = PhoneAction(context)

        // Register all skills — each skill owns its tool definitions and execution
        skillRegistry.registerSkill(MemorySkill(memoryStore))
        skillRegistry.registerSkill(confirmationSkill)
        skillRegistry.registerSkill(PhoneSkill(phoneAction))
        skillRegistry.registerSkill(SmsSkill(SmsAction(context), phoneAction))
        skillRegistry.registerSkill(AlarmSkill(AlarmAction(context)))
        skillRegistry.registerSkill(CalculatorSkill(CalculatorAction()))
        skillRegistry.registerSkill(CalendarSkill(CalendarAction(context)))
        skillRegistry.registerSkill(MailSkill(MailAction(context)))
        skillRegistry.registerSkill(SearchSkill(SearchAction(context)))
        skillRegistry.registerSkill(WeatherSkill(WeatherAction(context)))
        skillRegistry.registerSkill(PaymentSkill(PaymentAction(context)))
        skillRegistry.registerSkill(NotificationSkill(NotificationAction(context)))
    }

    fun submitOptionSelection(selectedOption: String) {
        confirmationSkill.pendingSelection?.complete(selectedOption)
    }

    val isAwaitingSelection: Boolean
        get() = confirmationSkill.pendingSelection?.isActive == true

    companion object {
        private val REDIRECTING_TOOLS = setOf(
            "make_call", "dial_number", "launch_app", "open_settings",
            "launch_payment_app", "upi_payment", "open_mail", "open_calendar",
            "compose_email", "share_via_email", "open_sms_app"
        )
    }

    suspend fun processInput(userSpeech: String): String {
        didRedirect = false

        // Inject relevant memory + graph context (unified retrieval)
        val memoryContext = memoryStore.getRelevantContext(userSpeech)
        mistral.setMemoryContext(memoryContext)

        // Get relevant tools via skill-based routing
        val relevantTools = skillRegistry.getRelevantTools(userSpeech)

        // Track selected tools for debug UI
        val toolNames = mutableListOf<String>()
        for (i in 0 until relevantTools.length()) {
            try {
                toolNames.add(relevantTools.getJSONObject(i).getJSONObject("function").getString("name"))
            } catch (_: Exception) {}
        }
        lastSelectedTools = toolNames

        val executedNames = mutableListOf<String>()

        var result = mistral.chat(userSpeech, relevantTools)

        var maxIterations = 5
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--
            val callNames = result.toolCalls.joinToString(", ") { it.functionName }
            android.util.Log.d("AlfredBrain", "Tool loop iteration ${5 - maxIterations}: [$callNames]")

            val toolResults = result.toolCalls.map { call ->
                executedNames.add(call.functionName)
                val res = executeToolCall(call)
                android.util.Log.d("AlfredBrain", "  ${call.functionName} → ${res.take(100)}")
                if (call.functionName in REDIRECTING_TOOLS) {
                    didRedirect = true
                }
                Pair(call.id, res)
            }
            result = mistral.sendToolResults(toolResults)
        }

        if (maxIterations == 0 && result.toolCalls.isNotEmpty()) {
            android.util.Log.w("AlfredBrain", "Tool loop hit max iterations, forcing stop")
        }

        lastExecutedTools = executedNames
        return result.content ?: "All done!"
    }

    private suspend fun executeToolCall(call: ToolCall): String {
        return try {
            skillRegistry.executeTool(call.functionName, call.arguments)
        } catch (e: Exception) {
            "Error executing ${call.functionName}: ${e.message}"
        }
    }

    fun resetConversation() {
        mistral.clearHistory()
    }

    fun getDebugMemories(): List<Pair<String, String>> = memoryStore.getDebugEntries()
    fun getDebugGraphNodes(): List<Triple<Long, String, String>> = knowledgeGraph.getDebugNodes()
    fun getDebugGraphEdges(): List<Triple<String, String, String>> = knowledgeGraph.getDebugEdges()
}
