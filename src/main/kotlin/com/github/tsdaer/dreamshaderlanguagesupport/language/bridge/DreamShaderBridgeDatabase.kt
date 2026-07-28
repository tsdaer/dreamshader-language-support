package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.intellij.openapi.diagnostic.Logger
import java.sql.Connection
import java.sql.DriverManager

internal object DreamShaderBridgeDatabaseConnection {
    private val logger = Logger.getInstance(DreamShaderBridgeDatabaseConnection::class.java)
    private var driverLoaded = false

    private fun ensureDriver() {
        if (driverLoaded) return
        try {
            Class.forName("org.sqlite.JDBC")
            driverLoaded = true
        } catch (e: Exception) {
            logger.warn("Failed to load SQLite JDBC driver", e)
        }
    }

    fun connect(dbPath: String): Connection? {
        val normalizedPath = dbPath.replace('\\', '/')
        ensureDriver()
        return try {
            DriverManager.getConnection("jdbc:sqlite:$normalizedPath")
        } catch (e: Exception) {
            logger.warn("Failed to open SQLite database: $normalizedPath", e)
            null
        }
    }

    fun disconnect(conn: Connection?) {
        try { conn?.close() } catch (e: Exception) {
            logger.warn("Failed to close SQLite connection", e)
        }
    }
}
