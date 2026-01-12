package net.eggc.ryoikumemo.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvImportManager(
    private val context: Context,
    private val noteRepository: NoteRepository
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun importCsv(uri: Uri, targetNote: Note): Result<Int> {
        return try {
            val lines = mutableListOf<String>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip Header
                    var line: String? = reader.readLine()
                    while (line != null) {
                        lines.add(line)
                        line = reader.readLine()
                    }
                }
            }

            if (lines.isEmpty()) {
                return Result.failure(Exception("ファイルが空か、データがありません"))
            }

            val stampsToSave = mutableListOf<StampItem>()
            lines.forEach { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size >= 2) {
                    val dateTimeStr = parts[0]
                    val label = parts[1]
                    val memo = if (parts.size >= 3) parts[2] else ""

                    val stampType = StampType.entries.find { it.label == label }
                    if (stampType != null) {
                        try {
                            val timestamp = LocalDateTime.parse(dateTimeStr, dateTimeFormatter)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()

                            stampsToSave.add(
                                StampItem(
                                    timestamp = timestamp,
                                    type = stampType,
                                    note = memo,
                                    operatorName = null
                                )
                            )
                        } catch (e: Exception) {
                            // Skip invalid date formats
                        }
                    }
                }
            }

            // 100件ずつのチャンクに分けて一括保存を実行
            stampsToSave.chunked(100).forEach { chunk ->
                noteRepository.saveStamps(targetNote.ownerId, targetNote.id, chunk)
            }

            Result.success(stampsToSave.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
