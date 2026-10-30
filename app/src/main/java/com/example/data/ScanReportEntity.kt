package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_reports")
data class ScanReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domain: String,
    val cms: String,
    val score: Int,
    val totalCritical: Int,
    val totalWarnings: Int,
    val totalPassed: Int,
    val totalFindings: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val jsonContent: String,
    val isSynced: Boolean = false
)
