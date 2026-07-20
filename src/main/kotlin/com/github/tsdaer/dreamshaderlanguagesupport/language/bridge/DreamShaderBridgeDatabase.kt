package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

internal object DreamShaderBridgeDatabaseConnection {
    private var driverLoaded = false

    private fun ensureDriver() {
        if (driverLoaded) return
        try {
            Class.forName("org.sqlite.JDBC")
            driverLoaded = true
        } catch (_: Exception) {
        }
    }

    fun connect(dbPath: String): Connection? {
        val normalizedPath = dbPath.replace('\\', '/')
        ensureDriver()
        return try {
            DriverManager.getConnection("jdbc:sqlite:$normalizedPath")
        } catch (_: Exception) {
            null
        }
    }

    fun disconnect(conn: Connection?) {
        try { conn?.close() } catch (_: Exception) {}
    }
}
