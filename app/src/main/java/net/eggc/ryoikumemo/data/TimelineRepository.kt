package net.eggc.ryoikumemo.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface TimelineRepository {
    fun getTimelineItems(): List<TimelineItem>
    fun saveDiary(date: String, text: String)
    fun saveStamp(stampType: StampType, note: String)
    fun deleteTimelineItem(item: TimelineItem)
}

class SharedPreferencesTimelineRepository(private val context: Context) : TimelineRepository {

    private val diaryPrefs = context.getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)
    private val stampPrefs = context.getSharedPreferences("stamp_prefs", Context.MODE_PRIVATE)
    private val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun getTimelineItems(): List<TimelineItem> {
        val diaries = diaryPrefs.all.mapNotNull { (key, value) ->
            try {
                val diaryTimestamp = LocalDate.parse(key, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                DiaryItem(timestamp = diaryTimestamp, text = value as String, date = key)
            } catch (e: Exception) {
                null
            }
        }

        val stamps = stampPrefs.all.mapNotNull { (key, value) ->
            try {
                val valueString = value as String
                val parts = valueString.split('|', limit = 2)
                val type = StampType.valueOf(parts[0])
                val note = if (parts.size > 1) parts[1] else ""
                StampItem(timestamp = key.toLong(), type = type, note = note)
            } catch (e: Exception) {
                null
            }
        }

        return (diaries + stamps).sortedByDescending { it.timestamp }
    }

    override fun saveDiary(date: String, text: String) {
        with(diaryPrefs.edit()) {
            putString(date, text)
            apply()
        }
    }

    override fun saveStamp(stampType: StampType, note: String) {
        with(stampPrefs.edit()) {
            putString(System.currentTimeMillis().toString(), "${stampType.name}|${note}")
            apply()
        }
    }

    override fun deleteTimelineItem(item: TimelineItem) {
        val keyToDelete = when (item) {
            is DiaryItem -> item.date
            is StampItem -> item.timestamp.toString()
        }
        val prefs = when (item) {
            is DiaryItem -> diaryPrefs
            is StampItem -> stampPrefs
        }
        with(prefs.edit()) {
            remove(keyToDelete)
            apply()
        }
    }
}
