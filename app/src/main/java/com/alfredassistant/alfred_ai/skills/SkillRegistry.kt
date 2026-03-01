package com.alfredassistant.alfred_ai.skills

import android.util.Log
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import org.json.JSONArray
import org.json.JSONObject

/** Debug info about a skill selected for a query. */
data class SelectedSkillInfo(
    val id: String,
    val name: String,
    val score: Float,       // cosine similarity score (1.0 for alwaysInclude)
    val alwaysInclude: Boolean,
    val toolCount: Int
)

/**
 * Skill-based registry that replaces the old ToolRegistry.
 *
 * Architecture: Brain → SkillRegistry → Skills → Tools
 *
 * Strategy:
 * - Skills marked alwaysInclude (Memory, Confirmation) are ALWAYS sent
 * - Other skills are selected via semantic similarity of their descriptions
 * - Each skill's tools are bundled together — if a skill is selected, ALL its tools go
 * - The registry also maintains a tool→skill lookup for routing execution
 */
class SkillRegistry(private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "SkillRegistry"
        private const val DEFAULT_TOP_K_SKILLS = 3
    }

    private val skills = mutableListOf<Skill>()
    private val toolToSkill = mutableMapOf<String, Skill>()
    private val skillEmbeddings = mutableMapOf<String, FloatArray>()

    /** Last set of skills selected for a query (for debug UI). */
    var lastSelectedSkills: List<SelectedSkillInfo> = emptyList()
        private set

    fun registerSkill(skill: Skill) {
        skills.add(skill)
        skill.getToolNames().forEach { toolToSkill[it] = skill }
        val embeddingText = "${skill.name}: ${skill.description}"
        skillEmbeddings[skill.id] = embeddingModel.embed(embeddingText)
        Log.d(TAG, "Registered skill: ${skill.id} (${skill.getToolNames().size} tools, alwaysInclude=${skill.alwaysInclude})")
    }

    fun getSkillForTool(toolName: String): Skill? = toolToSkill[toolName]

    suspend fun executeTool(functionName: String, arguments: JSONObject): String {
        val skill = toolToSkill[functionName]
            ?: return "Unknown function: $functionName"
        return skill.executeTool(functionName, arguments)
    }

    fun getRelevantTools(userPrompt: String, topK: Int = DEFAULT_TOP_K_SKILLS): JSONArray {
        val result = JSONArray()
        val addedSkillIds = mutableSetOf<String>()
        val selectedSkills = mutableListOf<SelectedSkillInfo>()

        // 1. Always include essential skills (memory + confirmation)
        for (skill in skills) {
            if (skill.alwaysInclude) {
                val defs = skill.getToolDefinitions()
                for (i in 0 until defs.length()) result.put(defs.getJSONObject(i))
                addedSkillIds.add(skill.id)
                selectedSkills.add(SelectedSkillInfo(skill.id, skill.name, 1.0f, true, skill.getToolNames().size))
            }
        }

        // 2. Semantic search for relevant non-essential skills
        val queryEmbedding = embeddingModel.embed(userPrompt)
        val scored = skills
            .filter { !it.alwaysInclude }
            .map { skill ->
                val emb = skillEmbeddings[skill.id] ?: return@map skill to 0f
                skill to cosineSimilarity(queryEmbedding, emb)
            }
            .sortedByDescending { it.second }

        var added = 0
        for ((skill, score) in scored) {
            if (added >= topK) break
            if (skill.id in addedSkillIds) continue
            val defs = skill.getToolDefinitions()
            for (i in 0 until defs.length()) result.put(defs.getJSONObject(i))
            addedSkillIds.add(skill.id)
            selectedSkills.add(SelectedSkillInfo(skill.id, skill.name, score, false, skill.getToolNames().size))
            added++
            Log.d(TAG, "  Selected skill: ${skill.id} (score=${String.format("%.3f", score)})")
        }

        lastSelectedSkills = selectedSkills
        Log.d(TAG, "Selected ${addedSkillIds.size} skills (${result.length()} tools) for: ${userPrompt.take(50)}...")
        return result
    }

    fun getAllToolDefinitions(): JSONArray {
        val result = JSONArray()
        for (skill in skills) {
            val defs = skill.getToolDefinitions()
            for (i in 0 until defs.length()) result.put(defs.getJSONObject(i))
        }
        return result
    }

    fun getAllSkills(): List<Skill> = skills.toList()

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }
}
