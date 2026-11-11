package com.ivangames.notebox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val tag: String = "",
    val wordCount: Int = 0,
    val isDeleted: Boolean = false,
    val reminderTime: Long = 0L,
    val color: String = "",
    val isChecklist: Boolean = false
)

class NoteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("notes_storage_v2", Context.MODE_PRIVATE)

    fun getNotes(): List<Note> {
        val jsonString = prefs.getString("notes_key", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val notes = mutableListOf<Note>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            notes.add(
                Note(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isPinned = obj.optBoolean("isPinned", false),
                    tag = obj.optString("tag", ""),
                    wordCount = obj.optInt("wordCount", 0),
                    isDeleted = obj.optBoolean("isDeleted", false),
                    reminderTime = obj.optLong("reminderTime", 0L),
                    color = obj.optString("color", ""),
                    isChecklist = obj.optBoolean("isChecklist", false)
                )
            )
        }
        return notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })
    }

    fun saveNotes(notes: List<Note>) {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id); put("title", note.title); put("content", note.content); put("timestamp", note.timestamp)
                put("isPinned", note.isPinned); put("tag", note.tag); put("wordCount", note.wordCount)
                put("isDeleted", note.isDeleted); put("reminderTime", note.reminderTime); put("color", note.color)
                put("isChecklist", note.isChecklist)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("notes_key", jsonArray.toString()).apply()
    }

    fun getTotalWordsCreated(): Int = prefs.getInt("total_words_created", 0)
    fun addWordsCreated(count: Int) { prefs.edit().putInt("total_words_created", getTotalWordsCreated() + count).apply() }
    fun getThemeMode(): Int = prefs.getInt("theme_mode", 0)
    fun saveThemeMode(mode: Int) { prefs.edit().putInt("theme_mode", mode).apply() }
    fun isGridView(): Boolean = prefs.getBoolean("is_grid_view", false)
    fun saveGridView(isGrid: Boolean) { prefs.edit().putBoolean("is_grid_view", isGrid).apply() }

    fun getPinCode(): String = prefs.getString("pin_code", "") ?: ""
    fun savePinCode(pin: String) { prefs.edit().putString("pin_code", pin).apply() }
    fun getFontScale(): Float = prefs.getFloat("font_scale", 1.0f)
    fun saveFontScale(scale: Float) { prefs.edit().putFloat("font_scale", scale).apply() }

    // НОВОЕ: Сортировка (0 - новые, 1 - старые, 2 - А-Я)
    fun getSortMode(): Int = prefs.getInt("sort_mode", 0)
    fun saveSortMode(mode: Int) { prefs.edit().putInt("sort_mode", mode).apply() }
}
