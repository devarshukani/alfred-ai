package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * A directed edge in the knowledge graph.
 * Represents a relationship between two KnowledgeNodes.
 * e.g. (John) --[lives_in]--> (New York)
 */
@Entity
data class KnowledgeEdge(
    @Id var id: Long = 0,
    @Index var sourceNodeId: Long = 0,
    @Index var targetNodeId: Long = 0,
    /** Relationship type: lives_in, likes, works_at, is_a, has, knows, etc. */
    @Index var relationship: String = "",
    var createdAt: Long = System.currentTimeMillis()
)
