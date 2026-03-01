package com.alfredassistant.alfred_ai.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * A memory entry (fact or preference) stored with its vector embedding
 * for semantic search via ObjectBox HNSW index.
 */
@Entity
data class MemoryEntity(
    @Id var id: Long = 0,
    @Index var key: String = "",
    var value: String = "",
    /** "fact" or "preference" */
    @Index var type: String = "fact",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    /** 384-dim embedding of "$key: $value" */
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
)
