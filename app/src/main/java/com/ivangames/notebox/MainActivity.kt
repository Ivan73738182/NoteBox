package com.ivangames.notebox

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            var isUnlocked by remember { mutableStateOf(repository.getPinCode().isEmpty()) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }

            NoteBoxTheme(themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!isUnlocked) LockScreen(repository, onUnlock = { isUnlocked = true })
                    else NoteBoxApp(repository = repository, themeMode = themeMode, onThemeChange = { m -> themeMode = m; repository.saveThemeMode(m) })
                }
            }
        }
    }
}

@Composable
fun LockScreen(repository: NoteRepository, onUnlock: () -> Unit) {
    var inputPin by remember { mutableStateOf("") }
    val correctPin = repository.getPinCode()
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("NoteBox заблокирован", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = inputPin, onValueChange = { if (it.length <= 4) inputPin = it }, label = { Text("Введите PIN-код") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { if (inputPin == correctPin) onUnlock() else inputPin = "" }, shape = RoundedCornerShape(16.dp)) { Text("Войти") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBoxApp(repository: NoteRepository, themeMode: Int, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(repository.getNotes()) }
    var currentEditingNote by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("Все") }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTrashScreen by remember { mutableStateOf(false) }
    var longPressedNote by remember { mutableStateOf<Note?>(null) }
    var isGridView by remember { mutableStateOf(repository.isGridView()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(repository.getFontScale()) }
    var showAddMenu by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(repository.getSortMode()) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val activeNotes = notes.filter { !it.isDeleted }
    val sortedNotes = remember(activeNotes, sortMode) {
        when (sortMode) {
            1 -> activeNotes.sortedWith(compareByDescending<Note> { it.isPinned }.thenBy { it.timestamp })
            2 -> activeNotes.sortedWith(compareByDescending<Note> { it.isPinned }.thenBy { it.title.lowercase() })
            else -> activeNotes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })
        }
    }

    val filteredNotes = sortedNotes.filter { note ->
        val matchesSearch = note.title.contains(searchQuery, true) || note.content.contains(searchQuery, true)
        val matchesTag = if (selectedTag == "Все") true else note.tag == selectedTag
        matchesSearch && matchesTag
    }

    // МЕНЮ ВЫБОРА ТИПА ЗАМЕТКИ (+)
    if (showAddMenu) {
        ModalBottomSheet(onDismissRequest = { showAddMenu = false }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Создать заметку", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(modifier = Modifier.weight(1f).clickable { currentEditingNote = Note(title = "", content = "", isChecklist = false); showAddMenu = false }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Notes, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp)); Text("Текст", fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(modifier = Modifier.weight(1f).clickable { currentEditingNote = Note(title = "", content = "", isChecklist = true); showAddMenu = false }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Checklist, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp)); Text("Список", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // КРАСИВОЕ МЕНЮ ДЛЯ ДОЛГОГО НАЖАТИЯ
    if (longPressedNote != null) {
        ModalBottomSheet(onDismissRequest = { longPressedNote = null }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text(longPressedNote!!.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                ListItem(
                    headlineContent = { Text("Копировать текст") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { 
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Note", longPressedNote!!.content))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        longPressedNote = null 
                    }
                )
                ListItem(
                    headlineContent = { Text(if (longPressedNote!!.isPinned) "Открепить" else "Закрепить") },
                    leadingContent = { Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { 
                        val n = longPressedNote!!
                        notes = notes.map { if (it.id == n.id) it.copy(isPinned = !it.isPinned) else it }
                        repository.saveNotes(notes)
                        longPressedNote = null 
                    }
                )
                ListItem(
                    headlineContent = { Text("Удалить в корзину", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { 
                        val n = longPressedNote!!
                        notes = notes.map { if (it.id == n.id) it.copy(isDeleted = true) else it }
                        repository.saveNotes(notes)
                        longPressedNote = null 
                    }
                )
            }
        }
    }

    if (showTrashScreen) {
        TrashScreen(notes = notes, onRestore = { n -> notes = notes.map { if (it.id == n.id) it.copy(isDeleted = false) else it }; repository.saveNotes(notes) }, onDeletePermanently = { n -> notes = notes.filter { it.id != n.id }; repository.saveNotes(notes) }, onClearAll = { notes = notes.filter { !it.isDeleted }; repository.saveNotes(notes) }, onBack = { showTrashScreen = false })
    } else if (currentEditingNote == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(title = { Text("NoteBox", fontWeight = FontWeight.Bold) }, actions = {
                    IconButton(onClick = { showStatsDialog = true }) { Icon(Icons.Default.Star, "Достижения") }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Сначала новые") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { sortMode = 0; repository.saveSortMode(0); showSortMenu = false }, trailingIcon = { if (sortMode == 0) Icon(Icons.Default.Check, null) })
                            DropdownMenuItem(text = { Text("Сначала старые") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { sortMode = 1; repository.saveSortMode(1); showSortMenu = false }, trailingIcon = { if (sortMode == 1) Icon(Icons.Default.Check, null) })
                            DropdownMenuItem(text = { Text("По алфавиту (А-Я)") }, leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }, onClick = { sortMode = 2; repository.saveSortMode(2); showSortMenu = false }, trailingIcon = { if (sortMode == 2) Icon(Icons.Default.Check, null) })
                        }
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                        DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                            DropdownMenuItem(text = { Text(if (isGridView) "Вид: Список" else "Вид: Сетка") }, leadingIcon = { Icon(if (isGridView) Icons.Default.List else Icons.Default.GridView, null) }, onClick = { isGridView = !isGridView; repository.saveGridView(isGridView); showSettingsMenu = false })
                            DropdownMenuItem(text = { Text("Корзина") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showTrashScreen = true; showSettingsMenu = false })
                            DropdownMenuItem(text = { Text("Экспорт заметок") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { exportNotes(context, activeNotes); showSettingsMenu = false })
                            Divider()
                            DropdownMenuItem(text = { Text("Размер шрифта") }, leadingIcon = { Icon(Icons.Default.FormatSize, null) }, onClick = { showFontDialog = true; showSettingsMenu = false })
                            DropdownMenuItem(text = { Text(if (repository.getPinCode().isEmpty()) "Установить пароль" else "Сменить пароль") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, onClick = { showPinDialog = true; showSettingsMenu = false })
                            Divider()
                            DropdownMenuItem(text = { Text("Системная тема") }, onClick = { onThemeChange(0); showSettingsMenu = false }, trailingIcon = { if (themeMode == 0) Icon(Icons.Default.Check, null) })
                            DropdownMenuItem(text = { Text("Светлая тема") }, onClick = { onThemeChange(1); showSettingsMenu = false }, trailingIcon = { if (themeMode == 1) Icon(Icons.Default.Check, null) })
                            DropdownMenuItem(text = { Text("Тёмная тема") }, onClick = { onThemeChange(2); showSettingsMenu = false }, trailingIcon = { if (themeMode == 2) Icon(Icons.Default.Check, null) })
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { 
                    try { val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; if (v.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (e: Exception) { }
                    showAddMenu = true 
                }, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp), elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)) { Icon(Icons.Default.Add, "Создать") }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Поиск...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), singleLine = true, shape = RoundedCornerShape(24.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Все", "Работа", "Личное", "Идеи").forEach { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = tag }, label = { Text(tag) }, shape = RoundedCornerShape(16.dp)) }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (filteredNotes.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Book, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp)); Text("Здесь пока пусто", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Нажми +, чтобы создать заметку", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredNotes, key = { it.id }) { note -> NoteCard(note = note, onPin = { notes = notes.map { if (it.id == note.id) it.copy(isPinned = !it.isPinned) else it }; repository.saveNotes(notes) }, onClick = { currentEditingNote = note }, onLongClick = { longPressedNote = note }, isGrid = true, fontScale = fontScale) }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredNotes, key = { it.id }) { note ->
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { delay(50); visible = true }
                                AnimatedVisibility(visible = visible, enter = fadeIn()) {
                                    SwipeToDeleteContainer(onDelete = {
                                        notes = notes.map { if (it.id == note.id) it.copy(isDeleted = true) else it }
                                        repository.saveNotes(notes)
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar("В корзине", "Отменить", duration = SnackbarDuration.Short)
                                            if (res == SnackbarResult.ActionPerformed) { notes = notes.map { if (it.id == note.id) it.copy(isDeleted = false) else it }; repository.saveNotes(notes) }
                                        }
                                    }) { NoteCard(note = note, onPin = { notes = notes.map { if (it.id == note.id) it.copy(isPinned = !it.isPinned) else it }; repository.saveNotes(notes) }, onClick = { currentEditingNote = note }, onLongClick = { longPressedNote = note }, isGrid = false, fontScale = fontScale) }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showStatsDialog) StatsDialog(repository, activeNotes.size, onDismiss = { showStatsDialog = false })
        if (showPinDialog) {
            var newPin by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { showPinDialog = false }, title = { Text("Установить PIN-код") }, text = { OutlinedTextField(value = newPin, onValueChange = { if (it.length <= 4) newPin = it }, label = { Text("4 цифры (пусто = удалить)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = RoundedCornerShape(16.dp)) }, shape = RoundedCornerShape(24.dp),
                confirmButton = { TextButton(onClick = { repository.savePinCode(newPin); showPinDialog = false }) { Text("Сохранить") } }
            )
        }
        if (showFontDialog) {
            AlertDialog(onDismissRequest = { showFontDialog = false }, title = { Text("Размер текста") }, shape = RoundedCornerShape(24.dp),
                text = { Column { listOf(0.85f to "Маленький", 1.0f to "Обычный", 1.15f to "Большой", 1.3f to "Огромный").forEach { (scale, name) -> TextButton(onClick = { fontScale = scale; repository.saveFontScale(scale); showFontDialog = false }, modifier = Modifier.fillMaxWidth()) { Text(name, fontSize = (16 * scale).sp) } } } },
                confirmButton = { TextButton(onClick = { showFontDialog = false }) { Text("Закрыть") } }
            )
        }
    } else {
        NoteEditScreen(
            note = currentEditingNote!!,
            onSave = { updatedNote ->
                if (updatedNote.title.isNotBlank() || updatedNote.content.isNotBlank()) {
                    val finalNote = updatedNote.copy(title = updatedNote.title.ifBlank { "Без названия" }, timestamp = System.currentTimeMillis(), wordCount = updatedNote.content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size)
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

fun setAlarm(context: Context, note: Note, timeInMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply { putExtra("note_title", note.title.ifBlank { "Без названия" }); putExtra("note_content", note.content); putExtra("note_id", note.id.hashCode()) }
    val pendingIntent = PendingIntent.getBroadcast(context, note.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            else context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${context.packageName}") })
        } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
    } catch (e: SecurityException) { }
}

fun cancelAlarm(context: Context, note: Note) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = PendingIntent.getBroadcast(context, note.id.hashCode(), Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarmManager.cancel(pendingIntent)
}

fun exportNotes(context: Context, notes: List<Note>) {
    if (notes.isEmpty()) { Toast.makeText(context, "Нет заметок", Toast.LENGTH_SHORT).show(); return }
    val text = StringBuilder()
    notes.forEach { note -> text.append("Заголовок: ${note.title}\nДата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(note.timestamp))}\nТекст:\n${note.content}\n\n---\n\n") }
    context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, text.toString()); type = "text/plain" }, "Поделиться"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(note: Note, onSave: (Note) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var title by remember(note) { mutableStateOf(note.title) }
    var content by remember(note) { mutableStateOf(note.content) }
    var isPinned by remember(note) { mutableStateOf(note.isPinned) }
    var tag by remember(note) { mutableStateOf(note.tag) }
    var reminderTime by remember(note) { mutableStateOf(note.reminderTime) }
    var cardColor by remember(note) { mutableStateOf(note.color) }
    var isChecklist by remember(note) { mutableStateOf(note.isChecklist) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showColorMenu by remember { mutableStateOf(false) }
    var newChecklistItem by remember { mutableStateOf("") }

    val wordCount = remember(content) { content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size }
    val charCount = remember(content) { content.length }

    fun saveAndExit() { onSave(note.copy(title = title, content = content, isPinned = isPinned, tag = tag, reminderTime = reminderTime, color = cardColor, isChecklist = isChecklist)); onCancel() }
    BackHandler { saveAndExit() }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) { content = if (content.isBlank()) spokenText else "$content $spokenText" }
        }
    }

    fun showDateTimePicker() {
        val calendar = Calendar.getInstance(); if (reminderTime > 0) calendar.timeInMillis = reminderTime
        DatePickerDialog(context, { _, year, month, day ->
            calendar.set(year, month, day)
            TimePickerDialog(context, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); calendar.set(Calendar.SECOND, 0)
                reminderTime = calendar.timeInMillis
                setAlarm(context, note.copy(title = title, content = content, isPinned = isPinned, tag = tag, reminderTime = reminderTime), reminderTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { }, navigationIcon = { IconButton(onClick = { saveAndExit() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Сохранить и выйти") } },
                actions = {
                    IconButton(onClick = { isChecklist = !isChecklist }, modifier = Modifier.size(40.dp)) { Icon(if (isChecklist) Icons.Default.Checklist else Icons.Default.FormatListBulleted, "Чек-лист", tint = if (isChecklist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { isPinned = !isPinned }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.PushPin, "Закрепить", tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Box {
                        IconButton(onClick = { showColorMenu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Palette, "Цвет") }
                        DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                            listOf("" to "Стандарт", "#FFCDD2" to "Красный", "#F8BBD0" to "Розовый", "#FFF9C4" to "Желтый", "#C8E6C9" to "Зеленый", "#BBDEFB" to "Синий").forEach { (hex, name) ->
                                DropdownMenuItem(text = { Text(name) }, leadingIcon = { Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(if (hex.isEmpty()) MaterialTheme.colorScheme.surface else Color(android.graphics.Color.parseColor(hex)))) }, onClick = { cardColor = hex; showColorMenu = false })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showTagMenu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Label, "Тег", tint = if (tag.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        DropdownMenu(expanded = showTagMenu, onDismissRequest = { showTagMenu = false }) {
                            listOf("", "Работа", "Личное", "Идеи").forEach { t -> DropdownMenuItem(text = { Text(if (t.isBlank()) "Без тега" else t) }, onClick = { tag = t; showTagMenu = false }) }
                        }
                    }
                    IconButton(onClick = { showDateTimePicker() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Notifications, "Напоминание", tint = if (reminderTime > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("Note", content)); Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, "Копировать") }
                    IconButton(onClick = { saveAndExit() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Check, "Готово") }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Заголовок") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isChecklist) {
                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
                    val items = content.lines().filter { it.isNotBlank() }
                    items.forEachIndexed { index, item ->
                        val isChecked = item.startsWith("[x] ") || item.startsWith("✅ ")
                        val text = item.removePrefix("[x] ").removePrefix("[ ] ").removePrefix("✅ ").removePrefix("⬜ ")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isChecked, onCheckedChange = { val newLines = items.toMutableList(); newLines[index] = if (it) "[x] $text" else "[ ] $text"; content = newLines.joinToString("\n") })
                            Text(text, modifier = Modifier.weight(1f), textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = newChecklistItem, onValueChange = { newChecklistItem = it }, placeholder = { Text("Новый пункт...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (newChecklistItem.isNotBlank()) { val currentLines = content.lines().filter { it.isNotBlank() }; content = (currentLines + "[ ] $newChecklistItem").joinToString("\n"); newChecklistItem = "" } })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { if (newChecklistItem.isNotBlank()) { val currentLines = content.lines().filter { it.isNotBlank() }; content = (currentLines + "[ ] $newChecklistItem").joinToString("\n"); newChecklistItem = "" } }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))) { Icon(Icons.Default.Add, "Добавить", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { if (content.isEmpty()) Text("Начни писать что-нибудь...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp))
                    FloatingActionButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU") }
                        try { speechLauncher.launch(intent) } catch (e: Exception) { Toast.makeText(context, "Нет голосового ввода", Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Mic, "Голос") }
                }
            }
            if (reminderTime > 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⏰ ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(reminderTime))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { cancelAlarm(context, note); reminderTime = 0 }) { Text("Отменить", color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Start) { Text("$wordCount слов, $charCount симв.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: Note, onPin: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, isGrid: Boolean = false, fontScale: Float = 1.0f) {
    val context = LocalContext.current
    val bgColor = if (note.color.isNotEmpty()) Color(android.graphics.Color.parseColor(note.color)) else MaterialTheme.colorScheme.surface
    val textColor = if (note.color.isNotEmpty()) Color.Black else MaterialTheme.colorScheme.onSurface

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { 
        try { val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; if (v.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (e: Exception) { }
        onLongClick() 
    }), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isPinned) { Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = if (note.color.isNotEmpty()) Color.Black else MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)) }
                        Text(note.title.ifBlank { "Без названия" }, fontWeight = FontWeight.Bold, color = textColor, maxLines = if (isGrid) 2 else 1, overflow = TextOverflow.Ellipsis, fontSize = (16 * fontScale).sp)
                    }
                    if (note.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (note.isChecklist) {
                            Text(note.content.lines().take(3).joinToString("\n") { line ->
                                val checked = line.startsWith("[x] ") || line.startsWith("✅ ")
                                val txt = line.removePrefix("[x] ").removePrefix("[ ] ").removePrefix("✅ ").removePrefix("⬜ ")
                                (if (checked) "☑ " else "☐ ") + txt
                            }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.8f), fontSize = (14 * fontScale).sp)
                        } else {
                            Text(note.content, maxLines = if (isGrid) 4 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.8f), fontSize = (14 * fontScale).sp)
                        }
                    }
                }
                if (!isGrid) { IconButton(onClick = onPin) { Icon(Icons.Default.PushPin, "Закрепить", tint = if (note.isPinned) (if (note.color.isNotEmpty()) Color.Black else MaterialTheme.colorScheme.primary) else textColor.copy(alpha = 0.5f)) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
                if (note.tag.isNotBlank()) { Spacer(modifier = Modifier.width(8.dp)); Text("#${note.tag}", style = MaterialTheme.typography.labelSmall, color = textColor) }
                if (note.reminderTime > 0) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.Notifications, null, modifier = Modifier.size(12.dp), tint = textColor) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false })
    SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false, backgroundContent = {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
    }, content = { content() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(notes: List<Note>, onRestore: (Note) -> Unit, onDeletePermanently: (Note) -> Unit, onClearAll: () -> Unit, onBack: () -> Unit) {
    val trashNotes = notes.filter { it.isDeleted }
    Scaffold(topBar = { TopAppBar(title = { Text("Корзина") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }, actions = { if (trashNotes.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Очистить", color = MaterialTheme.colorScheme.error) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) }) { padding ->
        if (trashNotes.isEmpty()) Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Корзина пуста") }
        else LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trashNotes, key = { it.id }) { note ->
                Card(shape = RoundedCornerShape(20.dp)) { Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(note.title, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRestore(note) }) { Icon(Icons.Default.Refresh, "Восстановить", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { onDeletePermanently(note) }) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                } }
            }
        }
    }
}

@Composable
fun StatsDialog(repository: NoteRepository, totalNotes: Int, onDismiss: () -> Unit) {
    val totalWords = repository.getTotalWordsCreated()
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }, title = { Text("Статистика") }, shape = RoundedCornerShape(24.dp),
        text = { Column { Text("📝 Заметок: $totalNotes"); Text("✍️ Слов: $totalWords") } }
    )
}
