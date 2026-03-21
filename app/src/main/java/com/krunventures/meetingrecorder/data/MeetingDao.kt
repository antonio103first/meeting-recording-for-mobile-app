package com.krunventures.meetingrecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: Int): Meeting?

    @Insert
    suspend fun insert(meeting: Meeting): Long

    @Query("UPDATE meetings SET sttText = :sttText, summaryText = :summaryText, summaryLocalPath = :summaryPath WHERE id = :id")
    suspend fun updateSummary(id: Int, sttText: String, summaryText: String, summaryPath: String)

    @Query("UPDATE meetings SET driveMp3Link = :mp3Link, driveSttLink = :sttLink, driveSummaryLink = :summaryLink WHERE id = :id")
    suspend fun updateDriveLinks(id: Int, mp3Link: String, sttLink: String, summaryLink: String)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE meetings SET fileName = :newName WHERE id = :id")
    suspend fun updateFileName(id: Int, newName: String)

    @Query("UPDATE meetings SET mp3LocalPath = :mp3Path, sttLocalPath = :sttPath, summaryLocalPath = :summaryPath WHERE id = :id")
    suspend fun updateFilePaths(id: Int, mp3Path: String, sttPath: String, summaryPath: String)
}
