package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class DreamShaderCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings("DreamShaderCodeStyleSettings", container) {
    @JvmField
    var ALIGN_SECTION_ASSIGNMENTS: Boolean = false

    @JvmField
    var BLANK_LINES_BETWEEN_SECTIONS: Int = 1

    @JvmField
    var SPACE_AROUND_DOUBLE_COLON: Boolean = false
}
