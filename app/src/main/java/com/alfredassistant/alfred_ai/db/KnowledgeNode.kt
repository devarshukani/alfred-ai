package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * A node in the on-device knowledge graph.
 * Represents a loosely-defined entity (person, place, thing, concept, event).
 *
 * Design: Entities are loosely defined with tight coupling.
 * - Each entity has a unique key (label + nodeType combo enforced via uniqueKey)
 * - Attributes are stored as a JSON map, so any entity can have arbitrary properties
 * - A "friend" is also a "person" entity — both can have attributes and relations between them
 * - The uniqueKey ensures no duplicate entities (e.g. only one "john:person")
 * - Embedding is computed from label + type + attributes for semantic search
 */
@Entity
data class KnowledgeNode(
    @Id var id: Long = 0,
    /** Display label, e.g. "john", "new york", "python" */
    @Index var label: String = "",
    /** Node type: person, place, thing, concept, event */
    @Index var nodeType: String = "",
    /** Unique key = "label:nodeType" — prevents duplicate entities */
    @Unique var uniqueKey: String = "",
    /** Arbitrary attributes as JSON object string, e.g. {"age":"25","phone":"555-1234"} */
    var attributes: String = "{}",
    /** Optional metadata/notes */
    var metadata: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    /** 384-dim embedding of label + type + attributes for semantic search */
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
) {
    companion object {
        fun buildUniqueKey(label: String, nodeType: String): String =
            "${label.lowercase().trim()}:${nodeType.lowercase().trim()}"
    }
}
