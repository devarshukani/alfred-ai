package com.alfredassistant.alfred_ai.skills

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single tool definition with its description, parameters, and execution logic.
 * The JSON schema is auto-built from these fields — no manual JSONObject construction needed.
 */
data class ToolDef(
    val name: String,
    val description: String,
    val parameters: List<Param> = emptyList(),
    val required: List<String> = emptyList(),
    val execute: suspend (JSONObject) -> String
) {
    /** Build the Mistral-format JSON tool definition automatically. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    parameters.forEach { p -> put(p.name, p.toJson()) }
                })
                if (required.isNotEmpty()) {
                    put("required", JSONArray(required))
                }
            })
        })
    }
}

/** A single parameter in a tool definition. */
data class Param(
    val name: String,
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val items: Param? = null,
    val maxItems: Int? = null,
    val properties: List<Param>? = null,
    val required: List<String>? = null,
    val additionalProperties: Param? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("description", description)
        this@Param.enum?.let { put("enum", JSONArray(it)) }
        items?.let { put("items", it.toJson()) }
        maxItems?.let { put("maxItems", it) }
        properties?.let { props ->
            put("properties", JSONObject().apply {
                props.forEach { p -> put(p.name, p.toJson()) }
            })
        }
        this@Param.required?.let { put("required", JSONArray(it)) }
        additionalProperties?.let { put("additionalProperties", it.toJson()) }
    }
}

/**
 * Base interface for all Alfred skills.
 * A skill declares a description (for semantic routing) and a list of ToolDefs.
 * The JSON schemas and execution routing are handled automatically.
 */
interface Skill {
    val id: String
    val name: String
    val description: String
    val alwaysInclude: Boolean get() = false

    /** All tools this skill provides. */
    val tools: List<ToolDef>

    /** Auto-built from tools list. */
    fun getToolDefinitions(): JSONArray {
        val arr = JSONArray()
        tools.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun getToolNames(): List<String> = tools.map { it.name }

    suspend fun executeTool(functionName: String, arguments: JSONObject): String {
        val tool = tools.find { it.name == functionName }
            ?: return "Unknown tool: $functionName"
        return tool.execute(arguments)
    }
}
