package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * A tool definition stored with its vector embedding for semantic matching.
 * Used to select only relevant tools for a given user prompt.
 */
@Entity
data class ToolEntity(
    @Id var id: Long = 0,
    @Unique var name: String = "",
    var description: String = "",
    /** Full JSON definition of the tool (for sending to Mistral) */
    var toolJson: String = "",
    /** Whether this tool should always be included regardless of similarity */
    var alwaysInclude: Boolean = false,
    /** 384-dim embedding of "name: description" */
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
)
