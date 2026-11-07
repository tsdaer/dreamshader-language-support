package com.github.tsdaer.dreamshaderlanguagesupport.language.core

/** The file pragmas shared by the legacy and 2.x front ends since DreamShader 2.2. */
internal object DreamShaderPragmas {
    const val NO_GENERATION = "nogeneration"
    const val DISABLE_WARNING = "disable_warning"
    const val DISABLE_ERROR = "disable_error"
    val shared = listOf(NO_GENERATION, DISABLE_WARNING, DISABLE_ERROR)

    private val directive = Regex("""#[ \t]*pragma[ \t]+([A-Za-z_]\w*)""")

    fun isSharedAt(text: CharSequence, hash: Int): Boolean =
        directive.matchAt(text, hash)?.groupValues?.get(1) in shared
}
