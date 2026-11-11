package com.ivangames.notebox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = NoteRepository(this)
        setContent {
            var themeMode by remember { mutableIntStateOf(repository.getThemeMode()) }
            NoteBoxTheme(themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NoteBoxApp(
                        repository = repository,
                        themeMode = themeMode,
                        onThemeChange = { newMode ->
                            themeMode = newMode
                            repository.saveThemeMode(newMode)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBoxApp(
    repository: NoteRepository,
    themeMode: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(repository.getNotes()) }
    var currentEditingNote by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("Все") }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showTrashScreen by remember { mutableStateOf(false) } // Для перехода в корзину
    var lastDeletedNote by remember { mutableStateOf<Note?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Фильтрация: не показываем удаленные
    val activeNotes = notes.filter { !it.isDeleted }
    val filteredNotes = activeNotes.filter { note ->
        val matchesSearch = note.title.contains(searchQuery, true) || note.content.contains(searchQuery, true)
        val matchesTag = if (selectedTag == "Все") true else note.tag == selectedTag
        matchesSearch && matchesTag
    }

    if (showTrashScreen) {
        TrashScreen(
            notes = notes,
            onRestore = { noteToRestore ->
                notes = notes.map { if (it.id == noteToRestore.id) it.copy(isDeleted = false) else it }
                repository.saveNotes(notes)
            },
            onDeletePermanently = { noteToDelete ->
                notes = notes.filter { it.id != noteToDelete.id }
                repository.saveNotes(notes)
            },
            onBack = { showTrashScreen = false }
        )
    } else if (currentEditingNote == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("NoteBox", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showStatsDialog = true }) {
                            Icon(Icons.Default.Star, "Достижения")
                        }
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Меню")
                            }
                            DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Корзина") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { showTrashScreen = true; showSettingsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт заметок") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { 
                                        exportNotes(context, activeNotes)
                                        showSettingsMenu = false 
                                    }
                                )
                                Divider()
                                DropdownMenuItem(text = { Text("Системная тема") }, onClick = { onThemeChange(0); showSettingsMenu = false }, trailingIcon = { if (themeMode == 0) Icon(Icons.Default.Check, null) })
                                DropdownMenuItem(text = { Text("Светлая тема") }, onClick = { onThemeChange(1); showSettingsMenu = false }, trailingIcon = { if (themeMode == 1) Icon(Icons.Default.Check, null) })
                                DropdownMenuItem(text = { Text("Тёмная тема") }, onClick = { onThemeChange(2); showSettingsMenu = false }, trailingIcon = { if (themeMode == 2) Icon(Icons.Default.Check, null) })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { currentEditingNote = Note(title = "", content = "") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "Создать заметку")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск заметок...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    singleLine = true
                )

                val allTags = listOf("Все", "Работа", "Личное", "Идеи")
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(selected = selectedTag == tag, onClick = { selectedTag = tag }, label = { Text(tag) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (filteredNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredNotes, key = { it.id }) { note ->
                            SwipeToDeleteContainer(
                                onDelete = {
                                    lastDeletedNote = note
                                    notes = notes.map { if (it.id == note.id) it.copy(isDeleted = true) else it }
                                    repository.saveNotes(notes)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Заметка в корзине",
                                            actionLabel = "Отменить",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            notes = notes.map { if (it.id == note.id) it.copy(isDeleted = false) else it }
                                            repository.saveNotes(notes)
                                        }
                                    }
                                }
                            ) {
                                NoteCard(note = note, onPin = {
                                    notes = notes.map { if (it.id == note.id) it.copy(isPinned = !it.isPinned) else it }
                                    repository.saveNotes(notes)
                                }, onClick = { currentEditingNote = note })
                            }
                        }
                    }
                }
            }
        }
        
        if (showStatsDialog) {
            StatsDialog(repository, activeNotes.size, onDismiss = { showStatsDialog = false })
        }
    } else {
        NoteEditScreen(
            note = currentEditingNote!!,
            onSave = { updatedNote ->
                if (updatedNote.title.isNotBlank() || updatedNote.content.isNotBlank()) {
                    val finalNote = updatedNote.copy(
                        title = updatedNote.title.ifBlank { "Без названия" },
                        timestamp = System.currentTimeMillis(),
                        wordCount = updatedNote.content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    )
                    if (updatedNote.id !in notes.map { it.id }) repository.addWordsCreated(finalNote.wordCount)
                    val existingIndex = notes.indexOfFirst { it.id == finalNote.id }
                    notes = if (existingIndex >= 0) notes.toMutableList().apply { set(existingIndex, finalNote) } else notes + finalNote
                    repository.saveNotes(notes)
                }
                currentEditingNote = null
            },
            onCancel = { currentEditingNote = null }
        )
    }
}

// Вспомогательная функция для Экспорта
fun exportNotes(context: Context, notes: List<Note>) {
    if (notes.isEmpty()) {
        Toast.makeText(context, "Нет заметок для экспорта", Toast.LENGTH_SHORT).show()
        return
    }
    val text = StringBuilder()
    notes.forEach { note ->
        text.append("Заголовок: ${note.title}\n")
        if (note.tag.isNotBlank()) text.append("Тег: #${note.tag}\n")
        text.append("Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(note.timestamp))}\n")
        text.append("Текст:\n${note.content}\n")
        text.append("\n-------------------\n\n")
    }
    
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text.toString())
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Поделиться заметками"))
}

@Composable
fun NoteCard(note: Note, onPin: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(text = note.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = note.content, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (note.tag.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "#${note.tag}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            IconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, "Закрепить", tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, "В корзину", tint = MaterialTheme.colorScheme.error)
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(note: Note, onSave: (Note) -> Unit, onCancel: () -> Unit) {
    var title by remember(note) { mutableStateOf(note.title) }
    var content by remember(note) { mutableStateOf(note.content) }
    var isPinned by remember(note) { mutableStateOf(note.isPinned) }
    var tag by remember(note) { mutableStateOf(note.tag) }
    var showTagMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(title, content, isPinned, tag) {
        if (title.isNotBlank() || content.isNotBlank()) {
            delay(2000)
            onSave(note.copy(title = title, content = content, isPinned = isPinned, tag = tag))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор") },
                navigationIcon = {
                    IconButton(onClick = { onSave(note.copy(title = title, content = content, isPinned = isPinned, tag = tag)); onCancel() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(Icons.Default.PushPin, "Закрепить", tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { showTagMenu = true }) {
                            Icon(Icons.Default.Label, "Тег", tint = if (tag.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showTagMenu, onDismissRequest = { showTagMenu = false }) {
                            listOf("", "Работа", "Личное", "Идеи").forEach { t ->
                                DropdownMenuItem(text = { Text(if (t.isBlank()) "Без тега" else t) }, onClick = { tag = t; showTagMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = { onSave(note.copy(title = title, content = content, isPinned = isPinned, tag = tag)); onCancel() }) {
                        Icon(Icons.Default.Check, "Готово")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Заголовок") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Текст заметки...") }, modifier = Modifier.fillMaxWidth().weight(1f))
            Text(text = "Автосохранение активно", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).align(Alignment.End))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    notes: List<Note>,
    onRestore: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit,
    onBack: () -> Unit
) {
    val trashNotes = notes.filter { it.isDeleted }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Корзина") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        if (trashNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Корзина пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trashNotes, key = { it.id }) { note ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "Без названия" }, fontWeight = FontWeight.Bold)
                                Text(note.content, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row {
                                IconButton(onClick = { onRestore(note) }) { Icon(Icons.Default.Refresh, "Восстановить", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { onDeletePermanently(note) }) { Icon(Icons.Default.DeleteForever, "Удалить навсегда", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsDialog(repository: NoteRepository, totalNotes: Int, onDismiss: () -> Unit) {
    val totalWords = repository.getTotalWordsCreated()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Достижения и Статистика") },
        text = {
            Column {
                Text("📝 Всего заметок: $totalNotes")
                Text("✍️ Всего слов написано: $totalWords")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Достижения:", fontWeight = FontWeight.Bold)
                Text(if (totalNotes >= 1) "✅ Первая заметка" else "⬜ Первая заметка")
                Text(if (totalNotes >= 10) "✅ 10 заметок" else "⬜ 10 заметок")
                Text(if (totalWords >= 100) "✅ 100 слов" else "⬜ 100 слов")
                Text(if (totalWords >= 1000) "✅ 1000 слов (Писатель!)" else "⬜ 1000 слов (Писатель!)")
            }
        }
    )
}
