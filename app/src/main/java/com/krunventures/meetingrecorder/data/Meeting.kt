package com.krunventures.meetingrecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val createdAt: String = "",
    val fileName: String = "",
    val mp3LocalPath: String = "",
    val sttLocalPath: String = "",
    val summaryLocalPath: String = "",
    val sttText: String = "",
    val summaryText: String = "",
    val driveMp3Link: String = "",
    val driveSttLink: String = "",
    val driveSummaryLink: String = "",
    val fileSizeMb: Double = 0.0
)
