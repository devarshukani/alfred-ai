package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * A directed edge in the knowledge graph.
 * Represents a relationship between two KnowledgeNodes.
 * e.g. (John) --[lives_in]--> (New York)
 *
 * Edges can also carry attributes (JSON map) for relationship properties.
 * e.g. "works_at" edge might have {"role":"engineer","since":"2020"}
 *
 * uniqueKey = "sourceId:relationship:targetId" prevents duplicate edges.
 */
@Entity
data class KnowledgeEdge(
    @Id var id: Long = 0,
    @Index var sourceNodeId: Long = 0,
    @Index var targetNodeId: Long = 0,
    /** Relationship type: lives_in, likes, works_at, is_a, has, knows, etc. */
    @Index var relationship: String = "",
    /** Unique key = "sourceId:relationship:targetId" */
    @Unique var uniqueKey: String = "",
    /** Arbitrary attributes as JSON object string, e.g. {"since":"2020","role":"best friend"} */
    var attributes: String = "{}",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun buildUniqueKey(sourceId: Long, relationship: String, targetId: Long): String =
            "$sourceId:${relationship.lowercase().trim()}:$targetId"
    }
}
