package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * A node in the on-device knowledge graph.
 * Represents an entity (person, place, thing, concept, event).
 */
@Entity
data class KnowledgeNode(
    @Id var id: Long = 0,
    /** Display label, e.g. "John", "New York", "Python" */
    @Index var label: String = "",
    /** Node type: person, place, thing, concept, event */
    @Index var nodeType: String = "",
    /** Optional metadata as JSON string */
    var metadata: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    /** 384-dim embedding of the label + type + metadata */
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
)
