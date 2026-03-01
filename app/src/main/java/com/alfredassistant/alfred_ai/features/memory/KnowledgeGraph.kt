package com.alfredassistant.alfred_ai.features.memory

import android.util.Log
import com.alfredassistant.alfred_ai.db.KnowledgeEdge
import com.alfredassistant.alfred_ai.db.KnowledgeEdge_
import com.alfredassistant.alfred_ai.db.KnowledgeNode
import com.alfredassistant.alfred_ai.db.KnowledgeNode_
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import io.objectbox.Box
import io.objectbox.query.QueryBuilder.StringOrder

/**
 * On-device knowledge graph backed by ObjectBox.
 * Stores entities (nodes) and relationships (edges) extracted from user conversations.
 * Supports semantic search over nodes via vector embeddings.
 */
class KnowledgeGraph(private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "KnowledgeGraph"
    }

    private val nodeBox: Box<KnowledgeNode> by lazy {
        ObjectBoxStore.store.boxFor(KnowledgeNode::class.java)
    }
    private val edgeBox: Box<KnowledgeEdge> by lazy {
        ObjectBoxStore.store.boxFor(KnowledgeEdge::class.java)
    }

    // ==================== NODE OPERATIONS ====================

    /**
     * Add or update a node in the knowledge graph.
     * If a node with the same label and type exists, update it.
     */
    fun addNode(label: String, nodeType: String, metadata: String = ""): KnowledgeNode {
        val normalizedLabel = label.lowercase().trim()
        val normalizedType = nodeType.lowercase().trim()

        val existing = nodeBox.query(
            KnowledgeNode_.label.equal(normalizedLabel, StringOrder.CASE_SENSITIVE)
                .and(KnowledgeNode_.nodeType.equal(normalizedType, StringOrder.CASE_SENSITIVE))
        ).build().findFirst()

        if (existing != null) {
            if (metadata.isNotEmpty()) {
                existing.metadata = metadata
                existing.embedding = embeddingModel.embed("$normalizedLabel $normalizedType $metadata")
            }
            nodeBox.put(existing)
            return existing
        }

        val embeddingText = "$normalizedLabel $normalizedType $metadata".trim()
        val node = KnowledgeNode(
            label = normalizedLabel,
            nodeType = normalizedType,
            metadata = metadata,
            embedding = embeddingModel.embed(embeddingText)
        )
        nodeBox.put(node)
        return node
    }

    /**
     * Find a node by exact label and type.
     */
    fun findNode(label: String, nodeType: String? = null): KnowledgeNode? {
        val condition = if (nodeType != null) {
            KnowledgeNode_.label.equal(label.lowercase().trim(), StringOrder.CASE_SENSITIVE)
                .and(KnowledgeNode_.nodeType.equal(nodeType.lowercase().trim(), StringOrder.CASE_SENSITIVE))
        } else {
            KnowledgeNode_.label.equal(label.lowercase().trim(), StringOrder.CASE_SENSITIVE)
        }
        return nodeBox.query(condition).build().findFirst()
    }

    /**
     * Semantic search for nodes similar to the query.
     */
    fun searchNodes(query: String, maxResults: Int = 5): List<KnowledgeNode> {
        val queryEmbedding = embeddingModel.embed(query)
        return nodeBox.query(
            KnowledgeNode_.embedding.nearestNeighbors(queryEmbedding, maxResults)
        ).build().findWithScores().map { it.get() }
    }

    // ==================== EDGE OPERATIONS ====================

    /**
     * Add a relationship between two nodes.
     * Creates nodes if they don't exist.
     */
    fun addRelationship(
        sourceLabel: String, sourceType: String,
        relationship: String,
        targetLabel: String, targetType: String
    ): KnowledgeEdge {
        val source = addNode(sourceLabel, sourceType)
        val target = addNode(targetLabel, targetType)

        // Check if edge already exists
        val existing = edgeBox.query(
            KnowledgeEdge_.sourceNodeId.equal(source.id)
                .and(KnowledgeEdge_.targetNodeId.equal(target.id))
                .and(KnowledgeEdge_.relationship.equal(
                    relationship.lowercase().trim(), StringOrder.CASE_SENSITIVE
                ))
        ).build().findFirst()

        if (existing != null) return existing

        val edge = KnowledgeEdge(
            sourceNodeId = source.id,
            targetNodeId = target.id,
            relationship = relationship.lowercase().trim()
        )
        edgeBox.put(edge)
        return edge
    }

    /**
     * Get all relationships for a given node (outgoing and incoming).
     */
    fun getRelationships(nodeId: Long): List<Triple<KnowledgeNode?, String, KnowledgeNode?>> {
        val outgoing = edgeBox.query(
            KnowledgeEdge_.sourceNodeId.equal(nodeId)
        ).build().find()
        val incoming = edgeBox.query(
            KnowledgeEdge_.targetNodeId.equal(nodeId)
        ).build().find()

        val results = mutableListOf<Triple<KnowledgeNode?, String, KnowledgeNode?>>()

        outgoing.forEach { edge ->
            val target = nodeBox.get(edge.targetNodeId)
            val source = nodeBox.get(edge.sourceNodeId)
            results.add(Triple(source, edge.relationship, target))
        }
        incoming.forEach { edge ->
            val source = nodeBox.get(edge.sourceNodeId)
            val target = nodeBox.get(edge.targetNodeId)
            results.add(Triple(source, edge.relationship, target))
        }

        return results
    }

    // ==================== GRAPH CONTEXT ====================

    /**
     * Get knowledge graph context relevant to a query.
     * Finds related nodes and their relationships.
     */
    fun getGraphContext(query: String, maxNodes: Int = 5): String {
        val nodes = searchNodes(query, maxNodes)
        if (nodes.isEmpty()) return ""

        val parts = mutableListOf<String>()
        val seenEdges = mutableSetOf<Long>()

        for (node in nodes) {
            val rels = getRelationships(node.id)
            for ((source, rel, target) in rels) {
                if (source == null || target == null) continue
                val edgeKey = "${source.id}-$rel-${target.id}".hashCode().toLong()
                if (seenEdges.add(edgeKey)) {
                    parts.add("${source.label} $rel ${target.label}")
                }
            }
        }

        return if (parts.isEmpty()) "" else "Knowledge graph: ${parts.joinToString("; ")}"
    }

    // ==================== TOOL-CALLABLE METHODS ====================

    /**
     * Extract entities and relationships from a fact statement.
     * Called by AlfredBrain when user stores a fact.
     * Simple pattern-based extraction.
     */
    fun extractAndStore(key: String, value: String) {
        try {
            val k = key.lowercase().trim()
            val v = value.lowercase().trim()

            addNode("user", "person")

            // Infer relationship from key
            val relType = when {
                k.contains("name") -> "has_name"
                k.contains("city") || k.contains("location") || k.contains("address") -> "lives_in"
                k.contains("work") || k.contains("job") || k.contains("company") -> "works_at"
                k.contains("like") || k.contains("favorite") || k.contains("prefer") -> "likes"
                k.contains("age") || k.contains("birthday") || k.contains("born") -> "has_attribute"
                k.contains("pet") || k.contains("dog") || k.contains("cat") -> "has_pet"
                k.contains("friend") || k.contains("know") -> "knows"
                k.contains("phone") || k.contains("email") || k.contains("contact") -> "has_contact"
                else -> "has_attribute"
            }

            // Infer target node type
            val targetType = when {
                k.contains("city") || k.contains("location") || k.contains("country") -> "place"
                k.contains("company") || k.contains("work") || k.contains("school") -> "place"
                k.contains("friend") || k.contains("name") && !k.contains("pet") -> "person"
                k.contains("pet") || k.contains("dog") || k.contains("cat") -> "thing"
                k.contains("birthday") || k.contains("date") -> "event"
                else -> "concept"
            }

            addNode(v, targetType, "source_key=$k")
            addRelationship("user", "person", relType, v, targetType)

            Log.d(TAG, "Knowledge graph: (user) --[$relType]--> ($v, $targetType)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract knowledge from: $key=$value", e)
        }
    }

    /**
     * Query the knowledge graph for entities related to a topic.
     * Returns a human-readable summary.
     */
    fun queryGraph(topic: String): String {
        val nodes = searchNodes(topic, 5)
        if (nodes.isEmpty()) return "No knowledge graph entries found for '$topic'."

        val sb = StringBuilder()
        for (node in nodes) {
            sb.append("${node.label} (${node.nodeType})")
            val rels = getRelationships(node.id)
            if (rels.isNotEmpty()) {
                sb.append(": ")
                sb.append(rels.joinToString(", ") { (src, rel, tgt) ->
                    "${src?.label ?: "?"} $rel ${tgt?.label ?: "?"}"
                })
            }
            sb.append(". ")
        }
        return sb.toString().trim()
    }

    // ==================== DEBUG ====================

    /** Get all nodes as (id, label, type) for debug UI */
    fun getDebugNodes(): List<Triple<Long, String, String>> {
        return nodeBox.all.map { Triple(it.id, it.label, it.nodeType) }
    }

    /** Get all edges as (sourceLabel, relationship, targetLabel) for debug UI */
    fun getDebugEdges(): List<Triple<String, String, String>> {
        return edgeBox.all.mapNotNull { edge ->
            val source = nodeBox.get(edge.sourceNodeId)
            val target = nodeBox.get(edge.targetNodeId)
            if (source != null && target != null) {
                Triple(source.label, edge.relationship, target.label)
            } else null
        }
    }
}
