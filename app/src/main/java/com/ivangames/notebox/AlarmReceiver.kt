package com.ivangames.notebox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteTitle = intent.getStringExtra("note_title") ?: "Заметка"
        val noteContent = intent.getStringExtra("note_content") ?: ""
        val notificationId = intent.getIntExtra("note_id", System.currentTimeMillis().toInt())

        // Создаем канал уведомлений (для Android 8+)
        val channelId = "notebox_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания NoteBox",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о напоминаниях в заметках"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Открываем NoteBox при нажатии на уведомление
        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Собираем само уведомление
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Используем нашу иконку
            .setContentTitle(noteTitle)
            .setContentText(if (noteContent.length > 50) noteContent.take(50) + "..." else noteContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(noteContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Если пользователь запретил уведомления, ничего не поделаешь
        }
    }
}
