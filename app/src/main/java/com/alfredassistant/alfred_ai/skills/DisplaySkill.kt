package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.ui.RichCard
import com.alfredassistant.alfred_ai.ui.RichCardParser
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/**
 * Display Skill — ALWAYS included.
 * Shows rich visual cards on screen: weather, restaurants, info panels, etc.
 * The AI builds a dynamic layout from block primitives.
 */
class DisplaySkill : Skill {

    override val id = "display"
    override val name = "Visual Display"
    override val alwaysInclude = true

    override val description = """Show rich visual cards on screen with dynamic layouts.
Use when information is better understood visually: weather, restaurants, search results,
directions, lists, settings panels, or any structured data.
Build layouts from blocks: title, subtitle, body, caption, info_row, divider, spacer,
icon_label, image, button_primary, button_secondary, button_cancel, toggle, text_field,
carousel, chip_row, progress_bar, rating."""

    var onDisplayCard: ((RichCard) -> Unit)? = null
    var pendingAction: CompletableDeferred<String>? = null

    override val tools = listOf(
        ToolDef(
            name = "show_card",
            description = """Display a rich visual card on screen. Build the layout from ordered blocks.
Available block types:
- title: {type:"title", text:"..."}
- subtitle: {type:"subtitle", text:"..."}
- body: {type:"body", text:"..."}
- caption: {type:"caption", text:"..."}
- info_row: {type:"info_row", label:"...", value:"..."}
- divider: {type:"divider"}
- spacer: {type:"spacer", height_dp:12}
- icon_label: {type:"icon_label", icon:"☀️", label:"..."}
- image: {type:"image", url:"...", alt_text:"..."}
- button_primary: {type:"button_primary", label:"...", action_id:"..."}
- button_secondary: {type:"button_secondary", label:"...", action_id:"..."}
- button_cancel: {type:"button_cancel", label:"Dismiss"}
- toggle: {type:"toggle", label:"...", key:"...", default_on:false}
- text_field: {type:"text_field", placeholder:"...", key:"..."}
- carousel: {type:"carousel", items:[{title:"...", subtitle:"...", detail:"...", icon_text:"🍕", image_url:"...", rating:4.5, action_id:"directions:lat,lon", action_label:"Directions"}]}
- chip_row: {type:"chip_row", chips:["tag1","tag2"]}
- progress_bar: {type:"progress_bar", progress:0.7, label:"..."}
- rating: {type:"rating", stars:4.5, label:"..."}
- line_chart: {type:"line_chart", points:[100.5, 102.3, ...], label:"1 Month", min_label:"Low: ₹480", max_label:"High: ₹520", color:"green|red|blue|gold"}
- score_card: {type:"score_card", home_team:"Team A", away_team:"Team B", home_score:"3", away_score:"1", home_icon:"🏏", away_icon:"🏏", status:"LIVE|FT|2nd Innings", sport:"cricket|football", detail:"IPL 2026 • Match 12", home_extra:"142/3 (18.2 ov)", away_extra:"138/10 (19.4 ov)"}

Use this whenever visual display helps the user understand information better.""",
            parameters = listOf(
                Param(
                    name = "blocks",
                    type = "array",
                    description = "Ordered list of content blocks to render",
                    items = Param(
                        name = "block",
                        type = "object",
                        description = "A content block with 'type' and type-specific fields",
                        properties = listOf(
                            Param(name = "type", type = "string", description = "Block type")
                        ),
                        additionalProperties = Param(name = "value", type = "string", description = "Block-specific fields")
                    )
                ),
                Param(
                    name = "spoken_summary",
                    type = "string",
                    description = "Short spoken summary for TTS (what Alfred says while showing the card)"
                ),
                Param(
                    name = "wait_for_action",
                    type = "boolean",
                    description = "If true, wait for user to tap a button before continuing. Default false."
                )
            ),
            required = listOf("blocks", "spoken_summary")
        ) { args -> executeShowCard(args) }
    )

    private suspend fun executeShowCard(arguments: JSONObject): String {
        val blocksJson = try {
            arguments.getJSONArray("blocks")
        } catch (e: Exception) {
            return "Card display failed — invalid blocks data."
        }
        val spokenSummary = arguments.optString("spoken_summary", "")
        val waitForAction = arguments.optBoolean("wait_for_action", false)

        val card = try {
            RichCardParser.parse(blocksJson, spokenSummary)
        } catch (e: Exception) {
            return "Card display failed — could not parse layout."
        }

        if (card.blocks.isEmpty()) {
            return "Card display failed — no valid blocks."
        }

        if (waitForAction) {
            // Set up deferred BEFORE invoking callback so isAwaitingAction is true
            // when the UI checks it
            val deferred = CompletableDeferred<String>()
            pendingAction = deferred
            onDisplayCard?.invoke(card)
            val action = deferred.await()
            pendingAction = null
            return "User action: $action"
        } else {
            onDisplayCard?.invoke(card)
            return "Card displayed successfully. Now give a brief spoken summary of the key info."
        }
    }

    fun submitAction(actionId: String) {
        pendingAction?.complete(actionId)
    }

    val isAwaitingAction: Boolean
        get() = pendingAction?.isActive == true
}
