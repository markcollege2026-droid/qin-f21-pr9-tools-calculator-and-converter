package com.qin.f21.toolbox.core.data

import android.content.Context
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.JsonParser
import com.qin.f21.toolbox.core.common.JsonWriter
import java.io.File
import java.util.UUID

/** One entry of the local history. */
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val toolId: String,
    val toolName: String,
    val title: String,
    val summary: String,
    val details: List<String> = emptyList()
) {
    fun toJson(): Json = Json.obj(
        "id" to Json.of(id),
        "timestamp" to Json.of(timestamp),
        "toolId" to Json.of(toolId),
        "toolName" to Json.of(toolName),
        "title" to Json.of(title),
        "summary" to Json.of(summary),
        "details" to Json.array(details.map { Json.of(it) })
    )

    companion object {
        fun fromJson(json: Json): HistoryEntry? {
            val id = json.string("id").ifEmpty { return null }
            return HistoryEntry(
                id = id,
                timestamp = json.long("timestamp"),
                toolId = json.string("toolId"),
                toolName = json.string("toolName"),
                title = json.string("title"),
                summary = json.string("summary"),
                details = json.arr("details").map { it.asString() }
            )
        }
    }
}

/** A pinned tool or unit pair. */
data class Favorite(
    val id: String = UUID.randomUUID().toString(),
    val toolId: String,
    val title: String,
    val subtitle: String = "",
    val payload: String = ""
) {
    fun toJson(): Json = Json.obj(
        "id" to Json.of(id),
        "toolId" to Json.of(toolId),
        "title" to Json.of(title),
        "subtitle" to Json.of(subtitle),
        "payload" to Json.of(payload)
    )

    companion object {
        fun fromJson(json: Json): Favorite? {
            val id = json.string("id").ifEmpty { return null }
            return Favorite(
                id = id,
                toolId = json.string("toolId"),
                title = json.string("title"),
                subtitle = json.string("subtitle"),
                payload = json.string("payload")
            )
        }
    }
}

/**
 * Local, file backed storage for the history and the favourites.
 *
 * The toolbox keeps everything on the device: a small JSON document that is rewritten
 * atomically. The history is capped so the file can never grow without bounds.
 */
class ToolboxStore(context: Context) {

    companion object {
        const val MAX_HISTORY = 300
        private const val HISTORY_FILE = "history.json"
        private const val FAVORITES_FILE = "favorites.json"
    }

    private val appContext = context.applicationContext
    private val historyFile = File(appContext.filesDir, HISTORY_FILE)
    private val favoritesFile = File(appContext.filesDir, FAVORITES_FILE)

    private val _history = ArrayList<HistoryEntry>()
    private val _favorites = ArrayList<Favorite>()

    init {
        load()
    }

    val history: List<HistoryEntry> get() = _history
    val favorites: List<Favorite> get() = _favorites

    // ---- history --------------------------------------------------------------

    fun addHistory(entry: HistoryEntry) {
        _history.removeAll { it.id == entry.id }
        _history.add(0, entry)
        while (_history.size > MAX_HISTORY) {
            _history.removeAt(_history.size - 1)
        }
        saveHistory()
    }

    fun removeHistory(id: String) {
        if (_history.removeAll { it.id == id }) saveHistory()
    }

    fun clearHistory() {
        _history.clear()
        saveHistory()
    }

    fun historyForTool(toolId: String): List<HistoryEntry> = _history.filter { it.toolId == toolId }

    // ---- favourites -----------------------------------------------------------

    fun addFavorite(favorite: Favorite) {
        _favorites.removeAll { it.id == favorite.id || (it.toolId == favorite.toolId && it.payload == favorite.payload) }
        _favorites.add(0, favorite)
        saveFavorites()
    }

    fun removeFavorite(id: String) {
        if (_favorites.removeAll { it.id == id }) saveFavorites()
    }

    fun toggleFavorite(favorite: Favorite): Boolean {
        val existing = _favorites.firstOrNull { it.toolId == favorite.toolId && it.payload == favorite.payload }
        return if (existing != null) {
            removeFavorite(existing.id)
            false
        } else {
            addFavorite(favorite)
            true
        }
    }

    fun isFavorite(toolId: String, payload: String = ""): Boolean =
        _favorites.any { it.toolId == toolId && it.payload == payload }

    // ---- persistence ----------------------------------------------------------

    private fun load() {
        _history.clear()
        _history.addAll(readJson(historyFile, "history").mapNotNull { HistoryEntry.fromJson(it) })
        _favorites.clear()
        _favorites.addAll(readJson(favoritesFile, "favorites").mapNotNull { Favorite.fromJson(it) })
    }

    private fun readJson(file: File, key: String): List<Json> {
        if (!file.exists()) return emptyList()
        return try {
            val parsed = JsonParser.parse(file.readText())
            when (val value = parsed.asObject()[key]) {
                is Json.Arr -> value.items
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory() {
        writeJson(historyFile, "history", _history.map { it.toJson() })
    }

    private fun saveFavorites() {
        writeJson(favoritesFile, "favorites", _favorites.map { it.toJson() })
    }

    private fun writeJson(file: File, key: String, items: List<Json>) {
        try {
            val document = Json.obj(
                "version" to Json.of(1),
                "exported" to Json.of(System.currentTimeMillis()),
                key to Json.array(items)
            )
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(JsonWriter.write(document, pretty = true))
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (e: Exception) {
            // storage is best effort; never crash the app because of it
        }
    }

    // ---- import / export ------------------------------------------------------

    fun exportDocument(includeHistory: Boolean = true, includeFavorites: Boolean = true): Json {
        val entries = LinkedHashMap<String, Json>()
        entries["version"] = Json.of(1)
        entries["app"] = Json.of("qin-f21-toolbox")
        entries["exported"] = Json.of(System.currentTimeMillis())
        if (includeHistory) entries["history"] = Json.array(_history.map { it.toJson() })
        if (includeFavorites) entries["favorites"] = Json.array(_favorites.map { it.toJson() })
        return Json.Obj(entries)
    }

    data class ImportResult(val historyAdded: Int, val favoritesAdded: Int, val error: String? = null)

    fun importDocument(json: Json, merge: Boolean = true): ImportResult {
        val historyItems = json.arr("history")
        val favoriteItems = json.arr("favorites")
        if (historyItems.isEmpty() && favoriteItems.isEmpty()) {
            return ImportResult(0, 0, "No toolbox data found in the file")
        }
        if (!merge) {
            _history.clear()
            _favorites.clear()
        }
        var historyAdded = 0
        for (item in historyItems) {
            val entry = HistoryEntry.fromJson(item) ?: continue
            if (_history.none { it.id == entry.id }) {
                _history.add(entry)
                historyAdded++
            }
        }
        while (_history.size > MAX_HISTORY) _history.removeAt(_history.size - 1)
        var favoritesAdded = 0
        for (item in favoriteItems) {
            val favorite = Favorite.fromJson(item) ?: continue
            if (_favorites.none { it.id == favorite.id }) {
                _favorites.add(favorite)
                favoritesAdded++
            }
        }
        saveHistory()
        saveFavorites()
        return ImportResult(historyAdded, favoritesAdded)
    }
}
