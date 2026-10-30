package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScanDatabase
import com.example.data.ScanReportEntity
import com.example.security.CmsDetectionResult
import com.example.security.DetectedCms
import com.example.security.OwnershipCheck
import com.example.security.OwnershipGate
import com.example.security.detectCms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: ScanDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `app name is Scan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Scan", appName)
    }

    @Test
    fun `direct authorization verifies without DNS TXT`() {
        val check = OwnershipGate.directAuthorization("target-domain.com")
        assertTrue(check is OwnershipCheck.Verified)
        assertEquals("target-domain.com", (check as OwnershipCheck.Verified).domain)
    }

    @Test
    fun `detect wordpress cms from html markers`() {
        val html = "<html><head><link rel='stylesheet' href='/wp-content/themes/twentytwenty/style.css'/></head></html>"
        val result = detectCms(html, emptyMap())
        assertEquals(DetectedCms.WORDPRESS, result.cms)
    }

    @Test
    fun `room database stores and retrieves scan reports`() = runBlocking {
        val dao = db.scanReportDao()
        val sample = ScanReportEntity(
            domain = "securesite.org",
            cms = "WORDPRESS",
            score = 92,
            totalCritical = 0,
            totalWarnings = 2,
            totalPassed = 12,
            totalFindings = 14,
            jsonContent = "{\"score\": 92}"
        )
        val id = dao.insertReport(sample)
        assertTrue(id > 0)

        val retrieved = dao.getReportById(id)
        assertNotNull(retrieved)
        assertEquals("securesite.org", retrieved?.domain)
        val all = dao.getAllReports().first()
        assertEquals(1, all.size)
    }

    @Test
    fun `report export helper creates valid PDF and JSON documents`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sample = ScanReportEntity(
            domain = "example-corp.com",
            cms = "LARAVEL",
            score = 78,
            totalCritical = 1,
            totalWarnings = 3,
            totalPassed = 10,
            totalFindings = 14,
            jsonContent = """
                {
                  "metadata": {"target_domain": "example-corp.com", "detected_cms": "LARAVEL"},
                  "findings": [
                    {
                      "step": "SSL / TLS",
                      "title": "HSTS manquant",
                      "severity": "AVERTISSEMENT",
                      "description": "L'en-tête Strict-Transport-Security est absent.",
                      "recommendation": "Activer HSTS dans la configuration Nginx / Apache."
                    }
                  ]
                }
            """.trimIndent()
        )

        // Generate PDF
        val pdfFile = com.example.util.ReportExportHelper.generatePdfReport(context, sample)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
        assertTrue(pdfFile.name.endsWith(".pdf"))

        // Generate JSON
        val jsonFile = com.example.util.ReportExportHelper.generateJsonReport(context, sample)
        assertTrue(jsonFile.exists())
        assertTrue(jsonFile.length() > 0)
        assertTrue(jsonFile.name.endsWith(".json"))
    }
}
