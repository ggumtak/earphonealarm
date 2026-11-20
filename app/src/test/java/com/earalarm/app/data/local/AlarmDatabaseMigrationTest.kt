package com.earalarm.app.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class AlarmDatabaseMigrationTest {
    @Test fun retainsAlarmsWhenAddingDatedAlarms() {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("ear_alarm.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, repeatMask INTEGER NOT NULL, label TEXT NOT NULL, enabled INTEGER NOT NULL, useCustomSound INTEGER NOT NULL, soundRef TEXT, volumePercent INTEGER, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE custom_sounds (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uri TEXT NOT NULL, displayName TEXT NOT NULL, addedAt INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_custom_sounds_uri ON custom_sounds (uri)")
            db.execSQL("INSERT INTO alarms VALUES (1, 7, 30, 31, '출근', 1, 0, NULL, NULL, 1000)")
            db.version = 1
        }
        val db = AppDatabase.create(context)
        try {
            val alarm = runBlocking { requireNotNull(db.alarmDao().getById(1)) }
            assertEquals("출근", alarm.label)
            assertEquals(31, alarm.repeatMask)
            assertNull(alarm.onceAtMillis)
        } finally {
            db.close()
            context.deleteDatabase("ear_alarm.db")
        }
    }
}
