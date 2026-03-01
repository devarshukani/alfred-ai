package com.alfredassistant.alfred_ai.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses a JSON blocks array into a RichCard.
 * Used by DisplaySkill to convert AI tool-call arguments into UI.
 */
object RichCardParser {

    fun parse(blocksJson: JSONArray, spokenSummary: String = ""): RichCard {
        val blocks = mutableListOf<RichBlock>()
        for (i in 0 until blocksJson.length()) {
            try {
                val obj = blocksJson.getJSONObject(i)
                parseBlock(obj)?.let { blocks.add(it) }
            } catch (_: Exception) {
                // Skip malformed blocks
            }
        }
        return RichCard(blocks, spokenSummary)
    }

    private fun parseBlock(obj: JSONObject): RichBlock? {
        return when (obj.getString("type")) {
            "title" -> RichBlock.Title(obj.getString("text"))
            "subtitle" -> RichBlock.Subtitle(obj.getString("text"))
            "body" -> RichBlock.Body(obj.getString("text"))
            "caption" -> RichBlock.Caption(obj.getString("text"))
            "info_row" -> RichBlock.InfoRow(obj.getString("label"), obj.getString("value"))
            "divider" -> RichBlock.Divider
            "spacer" -> RichBlock.Spacer(obj.optInt("height_dp", 12))
            "icon_label" -> RichBlock.IconLabel(obj.getString("icon"), obj.getString("label"))
            "image" -> RichBlock.Image(obj.getString("url"), obj.optString("alt_text", ""))
            "button_primary" -> RichBlock.ButtonPrimary(obj.getString("label"), obj.getString("action_id"))
            "button_secondary" -> RichBlock.ButtonSecondary(obj.getString("label"), obj.getString("action_id"))
            "button_cancel" -> RichBlock.ButtonCancel(obj.getString("label"), obj.optString("action_id", "dismiss"))
            "toggle" -> RichBlock.Toggle(obj.getString("label"), obj.getString("key"), obj.optBoolean("default_on", false))
            "text_field" -> RichBlock.TextField(obj.getString("placeholder"), obj.getString("key"))
            "chip_row" -> {
                val arr = obj.getJSONArray("chips")
                val chips = (0 until arr.length()).map { arr.getString(it) }
                RichBlock.ChipRow(chips)
            }
            "progress_bar" -> RichBlock.ProgressBar(obj.getDouble("progress").toFloat(), obj.optString("label", ""))
            "rating" -> RichBlock.Rating(obj.getDouble("stars").toFloat(), obj.optString("label", ""))
            "carousel" -> {
                val items = obj.getJSONArray("items")
                val parsed = (0 until items.length()).map { idx ->
                    val item = items.getJSONObject(idx)
                    CarouselItem(
                        title = item.getString("title"),
                        subtitle = item.optString("subtitle", ""),
                        detail = item.optString("detail", ""),
                        iconText = item.optString("icon_text", ""),
                        imageUrl = item.optString("image_url", ""),
                        rating = item.optDouble("rating", 0.0).toFloat(),
                        actionId = item.optString("action_id", ""),
                        actionLabel = item.optString("action_label", "")
                    )
                }
                RichBlock.Carousel(parsed)
            }
            else -> null
        }
    }
}
