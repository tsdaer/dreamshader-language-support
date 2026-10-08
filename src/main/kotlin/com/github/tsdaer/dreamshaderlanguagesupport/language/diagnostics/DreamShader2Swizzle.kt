package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

/** DreamShader 2.1.1 channel rules, shared by inference and completion. */
internal object DreamShader2Swizzle {
    fun type(base: DreamShader2Type, member: String): DreamShader2Type {
        val family = when (base) {
            is DreamShader2Type.Scalar -> base.family
            is DreamShader2Type.Vector -> base.family
            else -> return DreamShader2Type.Unknown
        }
        val letters = member.lowercase()
        if (letters.length !in 1..4) return DreamShader2Type.Unknown
        val set = listOf("xyzw", "rgba").firstOrNull { chars -> letters.all { it in chars } }
            ?: return DreamShader2Type.Unknown
        if (letters.any { set.indexOf(it) >= (base.componentCount ?: return DreamShader2Type.Unknown) }) return DreamShader2Type.Unknown
        return if (letters.length == 1) DreamShader2Type.Scalar(family)
            else DreamShader2Type.Vector(family, letters.length)
    }
}
