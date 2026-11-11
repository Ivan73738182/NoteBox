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
    val wordCount: Int = 0
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
                    wordCount = obj.optInt("wordCount", 0)
                )
            )
        }
        // Сначала закрепленные, потом по времени
        return notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })
    }

    fun saveNotes(notes: List<Note>) {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
                put("isPinned", note.isPinned)
                put("tag", note.tag)
                put("wordCount", note.wordCount)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("notes_key", jsonArray.toString()).apply()
    }

    // Достижения
    fun getTotalWordsCreated(): Int = prefs.getInt("total_words_created", 0)
    fun addWordsCreated(count: Int) {
        prefs.edit().putInt("total_words_created", getTotalWordsCreated() + count).apply()
    }
}
