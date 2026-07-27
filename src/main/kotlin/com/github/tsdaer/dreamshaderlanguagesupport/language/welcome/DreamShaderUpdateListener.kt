package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.intellij.util.messages.Topic

interface DreamShaderUpdateListener {
    fun onPostUpdate(hasUpdate: Boolean) {}

    companion object {
        @Topic.AppLevel
        val TOPIC: Topic<DreamShaderUpdateListener> = Topic.create(
            "DreamShaderUpdateListener",
            DreamShaderUpdateListener::class.java
        )
    }
}
