package com.pashaoleynik.droiddeploy

import com.pashaoleynik.droiddeploy.network.VersionDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionLogicTest {

    @Test
    fun `update is available when server version is higher`() {
        val installedVersionCode = 100L
        val serverVersion = VersionDto(
            versionName = "2.0.0",
            versionCode = 200L,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertTrue(updateAvailable)
    }

    @Test
    fun `update is not available when server version is equal`() {
        val installedVersionCode = 100L
        val serverVersion = VersionDto(
            versionName = "1.0.0",
            versionCode = 100L,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertFalse(updateAvailable)
    }

    @Test
    fun `update is not available when server version is lower`() {
        val installedVersionCode = 200L
        val serverVersion = VersionDto(
            versionName = "0.9.0",
            versionCode = 50L,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertFalse(updateAvailable)
    }

    @Test
    fun `update is not available when server version code is null`() {
        val installedVersionCode = 100L
        val serverVersion = VersionDto(
            versionName = "2.0.0",
            versionCode = null,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertFalse(updateAvailable)
    }

    @Test
    fun `update is not available when installed version code is null`() {
        val installedVersionCode: Long? = null
        val serverVersion = VersionDto(
            versionName = "2.0.0",
            versionCode = 200L,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertFalse(updateAvailable)
    }

    @Test
    fun `update is not available when both version codes are null`() {
        val installedVersionCode: Long? = null
        val serverVersion = VersionDto(
            versionName = "2.0.0",
            versionCode = null,
            stable = true
        )

        val updateAvailable = isUpdateAvailable(installedVersionCode, serverVersion)

        assertFalse(updateAvailable)
    }

    // Helper function extracted from the repository logic for testing
    private fun isUpdateAvailable(installedVersionCode: Long?, latestVersion: VersionDto): Boolean {
        val serverVersionCode = latestVersion.versionCode

        // If either version code is null, update is not available
        if (serverVersionCode == null || installedVersionCode == null) {
            return false
        }

        return serverVersionCode > installedVersionCode
    }
}
