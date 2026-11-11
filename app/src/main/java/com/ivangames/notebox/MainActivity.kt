package com.ivangames.notebox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// 1. Модель
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// 2. Хранилище
class NoteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("notes_storage", Context.MODE_PRIVATE)

    fun getNotes(): List<Note> {
        val jsonString = prefs.getString("notes_key", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val notes = mutableListOf<Note>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            notes.add(
                Note(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        return notes.sortedByDescending { it.timestamp }
    }

    fun saveNotes(notes: List<Note>) {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("notes_key", jsonArray.toString()).apply()
    }
}

// 3. Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = NoteRepository(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NoteBoxApp(repository)
                }
            }
        }
    }
}

// 4. Навигация
@Composable
fun NoteBoxApp(repository: NoteRepository) {
    var notes by remember { mutableStateOf(repository.getNotes()) }
    var currentEditingNote by remember { mutableStateOf<Note?>(null) }

    if (currentEditingNote == null) {
        NoteListScreen(
            notes = notes,
            onAddNote = { currentEditingNote = Note(title = "", content = "") },
            onSelectNote = { currentEditingNote = it },
            onDeleteNote = { noteToDelete ->
                notes = notes.filter { it.id != noteToDelete.id }
                repository.saveNotes(notes)
            }
        )
    } else {
        NoteEditScreen(
            note = currentEditingNote!!,
            onSave = { updatedNote ->
                if (updatedNote.title.isNotBlank() || updatedNote.content.isNotBlank()) {
                    val finalNote = updatedNote.copy(
                        title = updatedNote.title.ifBlank { "Без названия" },
                        timestamp = System.currentTimeMillis()
                    )
                    val existingIndex = notes.indexOfFirst { it.id == finalNote.id }
                    notes = if (existingIndex >= 0) {
                        notes.toMutableList().apply { set(existingIndex, finalNote) }
                    } else {
                        notes + finalNote
                    }
                    repository.saveNotes(notes)
                }
                currentEditingNote = null
            },
            onCancel = { currentEditingNote = null }
        )
    }
}

// 5. Список
@Composable
fun NoteListScreen(
    notes: List<Note>,
    onAddNote: () -> Unit,
    onSelectNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NoteBox", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Icon(Icons.Default.Add, contentDescription = "Создать заметку")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет заметок. Нажмите +, чтобы создать.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectNote(note) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dateFormat.format(Date(note.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { onDeleteNote(note) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. Редактор
@Composable
fun NoteEditScreen(
    note: Note,
    onSave: (Note) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(note) { mutableStateOf(note.title) }
    var content by remember(note) { mutableStateOf(note.content) }

    BackHandler {
        onSave(note.copy(title = title, content = content))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note.title.isEmpty()) "Новая заметка" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад без сохранения")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(note.copy(title = title, content = content)) }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Текст заметки...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
