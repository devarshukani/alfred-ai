package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import com.alfredassistant.alfred_ai.skills.*
import com.alfredassistant.alfred_ai.tools.*
import com.alfredassistant.alfred_ai.ui.RichCard

class AlfredBrain(context: Context) {

    private val mistral = MistralClient()

    private val embeddingModel = EmbeddingModel(context)
    private val knowledgeGraph = KnowledgeGraph(embeddingModel)
    private val memoryStore = MemoryStore(context, embeddingModel, knowledgeGraph)

    private val skillRegistry = SkillRegistry(embeddingModel)

    private val displaySkill = DisplaySkill()
    private val paymentAction: PaymentAction

    var onDisplayCard: ((RichCard) -> Unit)? = null
        set(value) {
            field = value
            displaySkill.onDisplayCard = value
            paymentAction.onDisplayCard = value
        }

    var onRedirectingAction: (() -> Unit)? = null

    @Volatile
    var didRedirect: Boolean = false
        private set

    var lastSelectedTools: List<String> = emptyList()
        private set
    var lastExecutedTools: List<String> = emptyList()
        private set
    val lastSelectedSkills: List<SelectedSkillInfo>
        get() = skillRegistry.lastSelectedSkills

    init {
        ObjectBoxStore.init(context)
        embeddingModel.initialize()

        val phoneAction = PhoneAction(context)
        val searchAction = SearchAction(context)
        paymentAction = PaymentAction(context)

        skillRegistry.registerSkill(MemorySkill(memoryStore))
        skillRegistry.registerSkill(displaySkill)
        skillRegistry.registerSkill(PhoneSkill(phoneAction, searchAction))
        skillRegistry.registerSkill(SmsSkill(SmsAction(context), searchAction))
        skillRegistry.registerSkill(AlarmSkill(AlarmAction(context)))
        skillRegistry.registerSkill(CalculatorSkill(CalculatorAction()))
        skillRegistry.registerSkill(CalendarSkill(CalendarAction(context)))
        skillRegistry.registerSkill(MailSkill(MailAction(context)))
        skillRegistry.registerSkill(SearchSkill(searchAction))
        skillRegistry.registerSkill(MapsSkill(MapsAction(context)))
        skillRegistry.registerSkill(LocationSkill(LocationAction(context)))
        skillRegistry.registerSkill(WeatherSkill(WeatherAction()))
        skillRegistry.registerSkill(StockSkill(StockAction()))
        skillRegistry.registerSkill(PaymentSkill(paymentAction))
    }

    fun submitCardAction(actionId: String) {
        if (paymentAction.isAwaitingAction) {
            paymentAction.submitAction(actionId)
        } else {
            displaySkill.submitAction(actionId)
        }
    }

    val isAwaitingCardAction: Boolean
        get() = displaySkill.isAwaitingAction || paymentAction.isAwaitingAction

    companion object {
        private val REDIRECTING_TOOLS = setOf(
            "make_call", "dial_number",
            "launch_payment_app", "open_mail", "open_calendar",
            "compose_email", "share_via_email", "open_sms_app"
        )
    }

    suspend fun processInput(userSpeech: String): String {
        didRedirect = false

        val memoryContext = memoryStore.getRelevantContext(userSpeech)
        mistral.setMemoryContext(memoryContext)

        val relevantTools = skillRegistry.getRelevantTools(userSpeech)

        val toolNames = mutableListOf<String>()
        for (i in 0 until relevantTools.length()) {
            try { toolNames.add(relevantTools.getJSONObject(i).getJSONObject("function").getString("name")) }
            catch (_: Exception) {}
        }
        lastSelectedTools = toolNames

        val executedNames = mutableListOf<String>()
        var result = mistral.chat(userSpeech, relevantTools)

        var maxIterations = 5
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--
            val toolResults = result.toolCalls.map { call ->
                executedNames.add(call.functionName)
                val res = executeToolCall(call)
                android.util.Log.d("AlfredBrain", "  ${call.functionName} → ${res.take(100)}")
                if (call.functionName in REDIRECTING_TOOLS) didRedirect = true
                Pair(call.id, res)
            }
            result = mistral.sendToolResults(toolResults)
        }

        lastExecutedTools = executedNames
        return result.content ?: "All done!"
    }

    private suspend fun executeToolCall(call: ToolCall): String {
        return try {
            skillRegistry.executeTool(call.functionName, call.arguments)
        } catch (e: Exception) {
            val safeMsg = e.message?.take(80)?.replace(Regex("[{\"\\[\\]\\\\]"), "") ?: "unknown error"
            "Error executing ${call.functionName}: $safeMsg"
        }
    }

    fun resetConversation() { mistral.clearHistory() }

    fun getDebugMemories(): List<Pair<String, String>> = memoryStore.getDebugEntries()
    fun getDebugGraphNodes(): List<Triple<Long, String, String>> = knowledgeGraph.getDebugNodes()
    fun getDebugGraphEdges(): List<Triple<String, String, String>> = knowledgeGraph.getDebugEdges()
}
