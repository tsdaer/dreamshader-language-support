package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Singleton for DreamShaderIcons.
 */
object DreamShaderIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/dreamshaderFile.svg", DreamShaderIcons::class.java)

    @JvmField
    val DECLARATION: Icon = IconLoader.getIcon("/icons/dreamshaderDeclaration.svg", DreamShaderIcons::class.java)

    @JvmField
    val FUNCTION: Icon = IconLoader.getIcon("/icons/dreamshaderFunction.svg", DreamShaderIcons::class.java)

    @JvmField
    val SHADER: Icon = IconLoader.getIcon("/icons/dreamshaderShader.svg", DreamShaderIcons::class.java)

    @JvmField
    val SECTION: Icon = IconLoader.getIcon("/icons/dreamshaderSection.svg", DreamShaderIcons::class.java)

    @JvmField
    val SECTION_SETTINGS: Icon = IconLoader.getIcon("/icons/dreamshaderSectionSettings.svg", DreamShaderIcons::class.java)

    @JvmField
    val SECTION_INPUTS: Icon = IconLoader.getIcon("/icons/dreamshaderSectionInputs.svg", DreamShaderIcons::class.java)

    @JvmField
    val SECTION_OUTPUTS: Icon = IconLoader.getIcon("/icons/dreamshaderSectionOutputs.svg", DreamShaderIcons::class.java)

    @JvmField
    val SECTION_GRAPH: Icon = IconLoader.getIcon("/icons/dreamshaderSectionGraph.svg", DreamShaderIcons::class.java)

    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/dreamshaderToolWindow.svg", DreamShaderIcons::class.java)
}
