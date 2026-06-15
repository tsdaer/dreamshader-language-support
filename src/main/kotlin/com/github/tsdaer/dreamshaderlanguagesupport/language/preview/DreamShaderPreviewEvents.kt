package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.messages.Topic

internal interface DreamShaderPreviewListener {
    fun previewBridgeChanged()

    companion object {
        val TOPIC: Topic<DreamShaderPreviewListener> = Topic.create(
            "DreamShader material preview changed",
            DreamShaderPreviewListener::class.java
        )
    }
}

@Service(Service.Level.PROJECT)
internal class DreamShaderMaterialPreviewPanelService : Disposable {
    @Volatile
    var panel: DreamShaderMaterialPreviewPanel? = null

    override fun dispose() {
        panel = null
    }
}
