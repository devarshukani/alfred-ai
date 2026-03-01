package com.alfredassistant.alfred_ai.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * A dynamic rich card that Alfred can display on screen.
 * Composed of ordered content blocks rendered top-to-bottom.
 */
data class RichCard(
    val blocks: List<RichBlock>,
    /** Abbreviated version for TTS */
    val spokenSummary: String = ""
)

/**
 * All possible content blocks for a RichCard.
 */
sealed class RichBlock {

    /** Section title */
    data class Title(val text: String) : RichBlock()

    /** Subtitle / secondary heading */
    data class Subtitle(val text: String) : RichBlock()

    /** Body text paragraph */
    data class Body(val text: String) : RichBlock()

    /** Small dim caption */
    data class Caption(val text: String) : RichBlock()

    /** Key-value info row (e.g. "Humidity" → "72%") */
    data class InfoRow(val label: String, val value: String) : RichBlock()

    /** Horizontal divider line */
    data object Divider : RichBlock()

    /** Spacer with configurable height in dp */
    data class Spacer(val heightDp: Int = 12) : RichBlock()

    /** Icon + label row (icon name from Material icons set) */
    data class IconLabel(val icon: String, val label: String) : RichBlock()

    /** Image from URL */
    data class Image(val url: String, val altText: String = "") : RichBlock()

    /** Primary action button */
    data class ButtonPrimary(val label: String, val actionId: String) : RichBlock()

    /** Secondary / outline button */
    data class ButtonSecondary(val label: String, val actionId: String) : RichBlock()

    /** Cancel / dismiss text button */
    data class ButtonCancel(val label: String, val actionId: String = "dismiss") : RichBlock()

    /** Toggle switch with label */
    data class Toggle(val label: String, val key: String, val defaultOn: Boolean = false) : RichBlock()

    /** Single-line text input */
    data class TextField(val placeholder: String, val key: String) : RichBlock()

    /** Horizontal carousel of sub-cards */
    data class Carousel(val items: List<CarouselItem>) : RichBlock()

    /** Chip row — horizontal scrollable tags */
    data class ChipRow(val chips: List<String>) : RichBlock()

    /** Progress bar (0.0 to 1.0) */
    data class ProgressBar(val progress: Float, val label: String = "") : RichBlock()

    /** Star rating display */
    data class Rating(val stars: Float, val label: String = "") : RichBlock()
}

/** A single item inside a Carousel */
data class CarouselItem(
    val title: String,
    val subtitle: String = "",
    val detail: String = "",
    val iconText: String = "",   // emoji or short text used as icon
    val imageUrl: String = "",   // image URL (shows placeholder if blank)
    val rating: Float = 0f,     // star rating (0 = hidden)
    val actionId: String = "",   // tappable action ID (e.g. "directions:lat,lon")
    val actionLabel: String = "" // button label (e.g. "Directions")
)
