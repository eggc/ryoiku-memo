package net.eggc.ryoikumemo.data.local

import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.StampType

fun TimelineStampEntity.toModel(): StampItem {
    return StampItem(
        timestamp = timestamp,
        type = try {
            StampType.valueOf(type)
        } catch (_: Exception) {
            StampType.MEMO
        },
        note = note,
        operatorName = operatorName,
    )
}

fun StampItem.toEntity(
    ownerId: String,
    noteId: String,
    updatedAt: Long? = null,
): TimelineStampEntity {
    return TimelineStampEntity(
        ownerId = ownerId,
        noteId = noteId,
        timestamp = timestamp,
        type = type.name,
        note = note,
        operatorName = operatorName,
        updatedAt = updatedAt,
    )
}
