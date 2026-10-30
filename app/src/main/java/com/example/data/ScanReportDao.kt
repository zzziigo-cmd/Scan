package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanReportDao {
    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ScanReportEntity>>

    @Query("SELECT * FROM scan_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: Long): ScanReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ScanReportEntity): Long

    @Delete
    suspend fun deleteReport(report: ScanReportEntity)

    @Query("DELETE FROM scan_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM scan_reports")
    suspend fun clearAllReports()

    @Query("UPDATE scan_reports SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}
