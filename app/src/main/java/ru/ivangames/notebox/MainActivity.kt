package ru.ivangames.notebox

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.ivangames.notebox.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState: Bundle?)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка Toolbar
        setSupportActionBar(binding.toolbar)

        // Обработка нажатия на кнопку (+)
        binding.fabAddNote.setOnClickListener {
            Toast.makeText(this, "Тут будет создание заметки!", Toast.LENGTH_SHORT).show()
        }

        // Позже настроим RecyclerView и Adapter
    }
}
