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
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device knowledge graph backed by ObjectBox.
 *
 * Design principles:
 * - Entities are loosely defined (any attributes via JSON) with tight coupling (unique keys, typed relations)
 * - Every entity has a unique key (label:type) so "john:person" can't be duplicated
 * - Attributes are flexible JSON maps — a person can have {age, phone, email}, a place can have {country, population}
 * - Relations also carry attributes — "works_at" can have {role, since}
 * - "friend" is also a "person" entity — both sides of a relationship are full entities with their own attributes
 * - Keys (uniqueKey) are embedded in the vector DB so the embedding model finds similar entities
 * - Graph traversal enriches LLM context by walking 2 hops from matched nodes
 */
class KnowledgeGraph(private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "KnowledgeGraph"
        private const val MAX_TRAVERSE_DEPTH = 2
    }

    private val nodeBox: Box<KnowledgeNode> by lazy {
        ObjectBoxStore.store.boxFor(KnowledgeNode::class.java)
    }
    private val edgeBox: Box<KnowledgeEdge> by lazy {
        ObjectBoxStore.store.boxFor(KnowledgeEdge::class.java)
    }

    // ==================== NODE CRUD ====================

    /**
     * Create or update a node. Uses uniqueKey (label:type) for dedup.
     * Merges attributes if node already exists.
     */
    fun upsertNode(
        label: String,
        nodeType: String,
        attributes: Map<String, String> = emptyMap(),
        metadata: String = ""
    ): KnowledgeNode {
        val normLabel = label.lowercase().trim()
        val normType = nodeType.lowercase().trim()
        val uniqueKey = KnowledgeNode.buildUniqueKey(normLabel, normType)

        val existing = nodeBox.query(
            KnowledgeNode_.uniqueKey.equal(uniqueKey, StringOrder.CASE_SENSITIVE)
        ).build().findFirst()

        if (existing != null) {
            // Merge attributes
            val merged = mergeAttributes(existing.attributes, attributes)
            existing.attributes = merged
            if (metadata.isNotBlank()) existing.metadata = metadata
            existing.updatedAt = System.currentTimeMillis()
            existing.embedding = computeNodeEmbedding(normLabel, normType, merged)
            nodeBox.put(existing)
            return existing
        }

        val attrsJson = JSONObject(attributes as Map<*, *>).toString()
        val node = KnowledgeNode(
            label = normLabel,
            nodeType = normType,
            uniqueKey = uniqueKey,
            attributes = attrsJson,
            metadata = metadata,
            embedding = computeNodeEmbedding(normLabel, normType, attrsJson)
        )
        nodeBox.put(node)
        Log.d(TAG, "Created node: $uniqueKey with ${attributes.size} attributes")
        return node
    }

    /**
     * Get a node by its unique key.
     */
    fun getNode(label: String, nodeType: String): KnowledgeNode? {
        val uniqueKey = KnowledgeNode.buildUniqueKey(label, nodeType)
        return nodeBox.query(
            KnowledgeNode_.uniqueKey.equal(uniqueKey, StringOrder.CASE_SENSITIVE)
        ).build().findFirst()
    }

    /**
     * Get a node by ID.
     */
    fun getNodeById(id: Long): KnowledgeNode? = nodeBox.get(id)

    /**
     * Update attributes on an existing node. Merges with existing attributes.
     */
    fun updateNodeAttributes(label: String, nodeType: String, attributes: Map<String, String>): KnowledgeNode? {
        val node = getNode(label, nodeType) ?: return null
        node.attributes = mergeAttributes(node.attributes, attributes)
        node.updatedAt = System.currentTimeMillis()
        node.embedding = computeNodeEmbedding(node.label, node.nodeType, node.attributes)
        nodeBox.put(node)
        return node
    }

    /**
     * Delete a node and all its edges.
     */
    fun deleteNode(label: String, nodeType: String): Boolean {
        val node = getNode(label, nodeType) ?: return false
        // Remove all edges connected to this node
        val outgoing = edgeBox.query(KnowledgeEdge_.sourceNodeId.equal(node.id)).build().find()
        val incoming = edgeBox.query(KnowledgeEdge_.targetNodeId.equal(node.id)).build().find()
        edgeBox.remove(outgoing)
        edgeBox.remove(incoming)
        nodeBox.remove(node)
        Log.d(TAG, "Deleted node ${node.uniqueKey} and ${outgoing.size + incoming.size} edges")
        return true
    }

    /**
     * Semantic search for nodes similar to the query.
     * The embedding model finds similar uniqueKeys/attributes.
     */
    fun searchNodes(query: String, maxResults: Int = 5): List<KnowledgeNode> {
        val queryEmbedding = embeddingModel.embed(query)
        return nodeBox.query(
            KnowledgeNode_.embedding.nearestNeighbors(queryEmbedding, maxResults)
        ).build().findWithScores().map { it.get() }
    }

    // ==================== EDGE CRUD ====================

    /**
     * Create or update a relationship between two entities.
     * Creates both entities if they don't exist.
     * Merges edge attributes if edge already exists.
     *
     * Example: addRelationship("john", "person", "knows", "jane", "person", mapOf("since" to "2020"))
     * This creates john:person, jane:person, and a "knows" edge between them.
     */
    fun addRelationship(
        sourceLabel: String, sourceType: String,
        relationship: String,
        targetLabel: String, targetType: String,
        sourceAttrs: Map<String, String> = emptyMap(),
        targetAttrs: Map<String, String> = emptyMap(),
        edgeAttrs: Map<String, String> = emptyMap()
    ): KnowledgeEdge {
        val source = upsertNode(sourceLabel, sourceType, sourceAttrs)
        val target = upsertNode(targetLabel, targetType, targetAttrs)
        val rel = relationship.lowercase().trim()
        val edgeKey = KnowledgeEdge.buildUniqueKey(source.id, rel, target.id)

        val existing = edgeBox.query(
            KnowledgeEdge_.uniqueKey.equal(edgeKey, StringOrder.CASE_SENSITIVE)
        ).build().findFirst()

        if (existing != null) {
            if (edgeAttrs.isNotEmpty()) {
                existing.attributes = mergeAttributes(existing.attributes, edgeAttrs)
                existing.updatedAt = System.currentTimeMillis()
                edgeBox.put(existing)
            }
            return existing
        }

        val edge = KnowledgeEdge(
            sourceNodeId = source.id,
            targetNodeId = target.id,
            relationship = rel,
            uniqueKey = edgeKey,
            attributes = JSONObject(edgeAttrs as Map<*, *>).toString()
        )
        edgeBox.put(edge)
        Log.d(TAG, "Created edge: (${source.label}) --[$rel]--> (${target.label})")
        return edge
    }

    /**
     * Remove a specific relationship.
     */
    fun removeRelationship(sourceLabel: String, sourceType: String, relationship: String, targetLabel: String, targetType: String): Boolean {
        val source = getNode(sourceLabel, sourceType) ?: return false
        val target = getNode(targetLabel, targetType) ?: return false
        val edgeKey = KnowledgeEdge.buildUniqueKey(source.id, relationship.lowercase().trim(), target.id)
        val edge = edgeBox.query(
            KnowledgeEdge_.uniqueKey.equal(edgeKey, StringOrder.CASE_SENSITIVE)
        ).build().findFirst() ?: return false
        edgeBox.remove(edge)
        return true
    }

    /**
     * Get all relationships for a given node (outgoing and incoming).
     */
    fun getRelationships(nodeId: Long): List<Triple<KnowledgeNode?, String, KnowledgeNode?>> {
        val outgoing = edgeBox.query(KnowledgeEdge_.sourceNodeId.equal(nodeId)).build().find()
        val incoming = edgeBox.query(KnowledgeEdge_.targetNodeId.equal(nodeId)).build().find()

        val results = mutableListOf<Triple<KnowledgeNode?, String, KnowledgeNode?>>()
        outgoing.forEach { edge ->
            results.add(Triple(nodeBox.get(edge.sourceNodeId), edge.relationship, nodeBox.get(edge.targetNodeId)))
        }
        incoming.forEach { edge ->
            results.add(Triple(nodeBox.get(edge.sourceNodeId), edge.relationship, nodeBox.get(edge.targetNodeId)))
        }
        return results
    }

    /**
     * Get edges with their attributes for a node.
     */
    fun getEdgesWithAttributes(nodeId: Long): List<Pair<KnowledgeEdge, Pair<KnowledgeNode?, KnowledgeNode?>>> {
        val outgoing = edgeBox.query(KnowledgeEdge_.sourceNodeId.equal(nodeId)).build().find()
        val incoming = edgeBox.query(KnowledgeEdge_.targetNodeId.equal(nodeId)).build().find()
        return (outgoing + incoming).map { edge ->
            edge to Pair(nodeBox.get(edge.sourceNodeId), nodeBox.get(edge.targetNodeId))
        }
    }

    // ==================== GRAPH CONTEXT (for LLM) ====================

    /**
     * Get enriched knowledge graph context for a query.
     * 1. Semantic search finds relevant nodes via embedding similarity
     * 2. Traverse up to 2 hops from each matched node
     * 3. Collect entity attributes + relationship attributes
     * 4. Build a rich natural-language context string
     */
    fun getGraphContext(query: String, maxNodes: Int = 5): String {
        val nodes = searchNodes(query, maxNodes)
        if (nodes.isEmpty()) return ""

        val parts = mutableListOf<String>()
        val visitedNodes = mutableSetOf<Long>()
        val visitedEdges = mutableSetOf<String>()

        for (node in nodes) {
            traverseAndCollect(node, 0, MAX_TRAVERSE_DEPTH, visitedNodes, visitedEdges, parts)
        }

        return if (parts.isEmpty()) "" else "Knowledge graph:\n${parts.joinToString("\n")}"
    }

    /**
     * Recursively traverse the graph from a node, collecting context.
     */
    private fun traverseAndCollect(
        node: KnowledgeNode,
        depth: Int,
        maxDepth: Int,
        visitedNodes: MutableSet<Long>,
        visitedEdges: MutableSet<String>,
        parts: MutableList<String>
    ) {
        if (depth > maxDepth || !visitedNodes.add(node.id)) return

        // Collect node info with attributes
        val attrs = parseAttributes(node.attributes)
        if (attrs.isNotEmpty() || depth == 0) {
            val attrStr = if (attrs.isNotEmpty()) {
                " [${attrs.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
            } else ""
            parts.add("${node.label} (${node.nodeType})$attrStr")
        }

        // Traverse edges
        val edges = getEdgesWithAttributes(node.id)
        for ((edge, nodePair) in edges) {
            val (source, target) = nodePair
            if (source == null || target == null) continue

            val edgeId = edge.uniqueKey
            if (!visitedEdges.add(edgeId)) continue

            val edgeAttrs = parseAttributes(edge.attributes)
            val edgeAttrStr = if (edgeAttrs.isNotEmpty()) {
                " (${edgeAttrs.entries.joinToString(", ") { "${it.key}=${it.value}" }})"
            } else ""

            parts.add("${source.label} ${edge.relationship} ${target.label}$edgeAttrStr")

            // Continue traversal to the other end
            val nextNode = if (source.id == node.id) target else source
            traverseAndCollect(nextNode, depth + 1, maxDepth, visitedNodes, visitedEdges, parts)
        }
    }

    // ==================== LLM-STRUCTURED STORAGE ====================

    /**
     * Store entities and relations extracted by the LLM.
     * The LLM does the NLP — we just persist the structured data.
     *
     * @param entities JSONArray of {name: String, type: String, attributes?: {k:v}}
     * @param relations JSONArray of {source: String, relation: String, target: String}
     */
    fun storeStructured(entities: JSONArray?, relations: JSONArray?) {
        if (entities == null && relations == null) return

        try {
            // 1. Upsert all entities — build a name→type lookup for relations
            val entityTypes = mutableMapOf<String, String>()

            if (entities != null) {
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    val name = e.getString("name").lowercase().trim()
                    val type = e.getString("type").lowercase().trim()
                    val attrs = mutableMapOf<String, String>()

                    if (e.has("attributes") && !e.isNull("attributes")) {
                        val attrObj = e.getJSONObject("attributes")
                        attrObj.keys().forEach { key ->
                            attrs[key] = attrObj.optString(key, "")
                        }
                    }

                    upsertNode(name, type, attrs)
                    entityTypes[name] = type
                }
            }

            // 2. Create all relations
            if (relations != null) {
                for (i in 0 until relations.length()) {
                    val r = relations.getJSONObject(i)
                    val sourceName = r.getString("source").lowercase().trim()
                    val relation = r.getString("relation").lowercase().trim()
                    val targetName = r.getString("target").lowercase().trim()

                    // Look up types from the entities we just created, fallback to "concept"
                    val sourceType = entityTypes[sourceName] ?: guessExistingType(sourceName) ?: "concept"
                    val targetType = entityTypes[targetName] ?: guessExistingType(targetName) ?: "concept"

                    addRelationship(sourceName, sourceType, relation, targetName, targetType)
                }
            }

            Log.d(TAG, "Stored ${entities?.length() ?: 0} entities, ${relations?.length() ?: 0} relations")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store structured entities/relations", e)
        }
    }

    /**
     * Try to find an existing node's type by label (for when the LLM references
     * an entity in relations that wasn't in the entities array).
     */
    private fun guessExistingType(label: String): String? {
        val existing = nodeBox.query(
            KnowledgeNode_.label.equal(label.lowercase().trim(), StringOrder.CASE_SENSITIVE)
        ).build().findFirst()
        return existing?.nodeType
    }

    // ==================== TOOL-CALLABLE METHODS ====================

    /**
     * Query the knowledge graph for entities related to a topic.
     * Returns a human-readable summary with attributes.
     */
    fun queryGraph(topic: String): String {
        val nodes = searchNodes(topic, 5)
        if (nodes.isEmpty()) return "No knowledge graph entries found for '$topic'."

        val sb = StringBuilder()
        val visited = mutableSetOf<Long>()

        for (node in nodes) {
            if (!visited.add(node.id)) continue
            val attrs = parseAttributes(node.attributes)
            sb.append("${node.label} (${node.nodeType})")
            if (attrs.isNotEmpty()) {
                sb.append(" [${attrs.entries.joinToString(", ") { "${it.key}=${it.value}" }}]")
            }

            val edges = getEdgesWithAttributes(node.id)
            if (edges.isNotEmpty()) {
                sb.append(": ")
                sb.append(edges.joinToString(", ") { (edge, nodePair) ->
                    val (src, tgt) = nodePair
                    val edgeAttrs = parseAttributes(edge.attributes)
                    val attrStr = if (edgeAttrs.isNotEmpty()) {
                        " (${edgeAttrs.entries.joinToString(", ") { "${it.key}=${it.value}" }})"
                    } else ""
                    "${src?.label ?: "?"} ${edge.relationship} ${tgt?.label ?: "?"}$attrStr"
                })
            }
            sb.append(". ")
        }
        return sb.toString().trim()
    }

    // ==================== HELPERS ====================

    private fun computeNodeEmbedding(label: String, nodeType: String, attributes: String): FloatArray {
        val attrs = parseAttributes(attributes)
        val text = buildString {
            append("$label $nodeType")
            if (attrs.isNotEmpty()) {
                append(" ")
                append(attrs.entries.joinToString(" ") { "${it.key} ${it.value}" })
            }
        }
        return embeddingModel.embed(text)
    }

    private fun mergeAttributes(existingJson: String, newAttrs: Map<String, String>): String {
        return try {
            val existing = JSONObject(existingJson)
            newAttrs.forEach { (k, v) -> existing.put(k, v) }
            existing.toString()
        } catch (e: Exception) {
            JSONObject(newAttrs as Map<*, *>).toString()
        }
    }

    private fun parseAttributes(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.optString(key, "") }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ==================== DEBUG ====================

    /**
     * Remove all nodes and edges from the graph.
     */
    fun clearAll() {
        val nodeCount = nodeBox.count()
        val edgeCount = edgeBox.count()
        nodeBox.removeAll()
        edgeBox.removeAll()
        Log.d(TAG, "Cleared graph: $nodeCount nodes, $edgeCount edges removed")
    }

    fun getDebugNodes(): List<Triple<Long, String, String>> {
        return nodeBox.all.map { Triple(it.id, it.label, it.nodeType) }
    }

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
