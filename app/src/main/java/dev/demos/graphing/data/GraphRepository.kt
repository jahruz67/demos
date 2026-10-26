package dev.demos.graphing.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class StoredExpression(val id: Long, val text: String, val color: Int, val hidden: Boolean = false, val folderId: Long? = null)

@Serializable
data class GraphDocument(
    val formatVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Graph",
    val expressions: List<StoredExpression> = emptyList(),
    val is3D: Boolean = false,
    val degreeMode: Boolean = false,
    val showGrid: Boolean = true,
    val showAxes: Boolean = true,
    val precisionDigits: Int = 15,
    val complexMode: Boolean = false,
    val parameters: Map<String, Double> = emptyMap(),
    val viewBounds: Map<String, Double> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sliderSettings: Map<String, dev.demos.graphing.ui.SliderConfiguration> = emptyMap(),
)

class GraphRepository(context: Context) {
    private val root by lazy { File(context.noBackupFilesDir, "graphs").apply { mkdirs() } }
    private val recovery by lazy { File(context.noBackupFilesDir, "recovery.json") }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val validId = Regex("[0-9a-fA-F-]{36}")

    @Synchronized
    fun list(): List<GraphDocument> = root.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    @Synchronized
    fun save(document: GraphDocument): GraphDocument {
        require(validId.matches(document.id)) { "Invalid graph identifier" }
        val updated = document.copy(updatedAt = System.currentTimeMillis())
        val destination = File(root, "${document.id}.json")
        if (destination.exists()) {
            val revisions = File(root, document.id).apply { mkdirs() }
            atomicWrite(File(revisions, "${System.currentTimeMillis()}.json"), destination.readText())
            revisions.listFiles().orEmpty().sortedByDescending { it.name }.drop(20).forEach { it.delete() }
        }
        atomicWrite(destination, encode(updated))
        saveRecovery(updated)
        return updated
    }

    @Synchronized
    fun load(id: String): GraphDocument {
        require(validId.matches(id)) { "Invalid graph identifier" }
        return decode(File(root, "$id.json").readText())
    }

    @Synchronized
    fun delete(id: String) {
        require(validId.matches(id)) { "Invalid graph identifier" }
        File(root, "$id.json").delete()
        File(root, id).takeIf { it.isDirectory }?.listFiles()?.forEach { it.delete() }
        File(root, id).delete()
    }

    @Synchronized
    fun revisions(id: String): List<GraphDocument> {
        require(validId.matches(id))
        return File(root, id).listFiles().orEmpty().sortedByDescending { it.name }
            .mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
    }

    @Synchronized
    fun saveRecovery(document: GraphDocument) = atomicWrite(recovery, encode(document))

    @Synchronized
    fun restoreRecovery(): GraphDocument? = runCatching { decode(recovery.readText()) }.getOrNull()

    fun encode(document: GraphDocument): String = json.encodeToString(document)

    fun decode(source: String): GraphDocument {
        require(source.length <= 5_000_000) { "This graph file is too large" }
        val result = json.decodeFromString<GraphDocument>(source)
        require(result.formatVersion == 1) { "Unsupported graph file version" }
        require(result.expressions.size <= 2000) { "Too many expressions" }
        require(result.expressions.all { it.text.length <= 100_000 }) { "An expression is too large" }
        require(result.expressions.map { it.id }.toSet().size == result.expressions.size) { "Duplicate expression identifiers" }
        return result
    }

    private fun atomicWrite(file: File, contents: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(contents.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }
}
