package net.eggc.ryoikumemo.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvExportManager(
    private val context: Context,
    private val noteRepository: NoteRepository
) {
    private val tag = "CsvExportManager"
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    suspend fun exportCsv(uri: Uri, targetNote: Note): Result<Unit> {
        Log.d(tag, "Starting CSV export for note: ${targetNote.name} (ID: ${targetNote.id})")
        return try {
            Log.d(tag, "Fetching all stamp items from repository...")
            val stamps = noteRepository.getAllStampItems(targetNote.ownerId, targetNote.id)
            Log.d(tag, "Fetched ${stamps.size} stamps.")

            val csvContent = buildString {
                append("日時,種類,メモ,操作者\n")
                stamps.forEach { stamp ->
                    val dateStr = dateTimeFormatter.format(Instant.ofEpochMilli(stamp.timestamp))
                    val typeStr = stamp.type.label
                    val noteStr = stamp.note.replace("\n", " ").replace(",", " ")
                    val operatorStr = stamp.operatorName ?: ""
                    append("$dateStr,$typeStr,$noteStr,$operatorStr\n")
                }
            }
            Log.d(tag, "CSV content generated successfully (length: ${csvContent.length} chars).")

            Log.d(tag, "Opening output stream for URI: $uri")
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvContent.toByteArray())
                outputStream.flush()
            } ?: throw Exception("Could not open output stream for the selected location.")

            Log.d(tag, "CSV export completed successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error during CSV export", e)
            Result.failure(e)
        }
    }
}
