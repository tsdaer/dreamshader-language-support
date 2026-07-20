package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import java.sql.Connection
import java.sql.DriverManager

internal object DreamShaderBridgeDatabaseConnection {
    private var connected: Connection? = null
    private var connectedToPath: String? = null

    fun connect(dbPath: String): Connection? {
        val normalizedPath = dbPath.replace('\\', '/')
        if (connectedToPath == normalizedPath && connected != null && !connected!!.isClosed) {
            return connected
        }
        disconnect()
        return try {
            val conn = DriverManager.getConnection("jdbc:sqlite:$normalizedPath")
            connected = conn
            connectedToPath = normalizedPath
            conn
        } catch (_: Exception) {
            null
        }
    }

    fun disconnect() {
        try {
            connected?.close()
        } catch (_: Exception) {
        }
        connected = null
        connectedToPath = null
    }
}
