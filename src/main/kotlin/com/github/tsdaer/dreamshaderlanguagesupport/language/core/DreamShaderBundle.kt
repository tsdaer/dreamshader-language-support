package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.DreamShaderBundle"

/**
 * $name 单例对象。
 */
internal object DreamShaderBundle {
    private val INSTANCE = DynamicBundle(DreamShaderBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String {
        return INSTANCE.getMessage(key, *params)
    }
}
