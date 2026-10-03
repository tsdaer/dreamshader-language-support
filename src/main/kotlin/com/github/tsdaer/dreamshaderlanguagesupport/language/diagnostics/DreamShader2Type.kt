package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

/**
 * Conservative value-type model for the DreamShader 2.0 dialect (plan C2).
 *
 * Upstream types (`Docs/language-v2/index.md`): numeric scalars and their vectors/matrices,
 * texture kinds, `SamplerState`, `material`, `Substrate`, `void`, and user `struct` names.
 * Everything the inference cannot pin down is [Unknown] — and Unknown never produces a
 * diagnostic (silent degradation is the contract: the engine stays the referee).
 */
sealed class DreamShader2Type {

    /** Numerics: `float half double int uint bool` (double folds into float's family slot). */
    data class Scalar(val family: Family) : DreamShader2Type()

    data class Vector(val family: Family, val size: Int) : DreamShader2Type()

    data class Matrix(val family: Family, val rows: Int, val cols: Int) : DreamShader2Type()

    /** `Texture2D TextureCube Texture2DArray Texture3D VolumeTexture` and friends. */
    data class Texture(val kind: String) : DreamShader2Type()

    object Sampler : DreamShader2Type()

    object Material : DreamShader2Type()

    object Substrate : DreamShader2Type()

    object Void : DreamShader2Type()

    /** A user `struct` spelling; fields resolve through the symbol model when available. */
    data class StructType(val name: String) : DreamShader2Type()

    /** Not derivable — every consumer must treat this as "no opinion". */
    object Unknown : DreamShader2Type()

    enum class Family {
        FLOAT,
        HALF,
        INT,
        UINT,
        BOOL,
        ;

        /** The one-letter spelling used in `float3`-style names. */
        val spelling: String get() = name.lowercase()
    }

    /** The one-letter HLSL family spelling used in vector/constructor names (`float3` → FLOAT). */
    val familySpelling: String?
        get() = when (this) {
            is Scalar -> family.spelling
            is Vector -> family.spelling
            is Matrix -> family.spelling
            else -> null
        }

    val isNumeric: Boolean
        get() = this is Scalar || this is Vector || this is Matrix

    val isNumericScalar: Boolean
        get() = this is Scalar

    val isResource: Boolean
        get() = this is Texture || this == Sampler

    /** Component count for swizzle/index narrowing; 1 for scalars, null for non-numeric. */
    val componentCount: Int?
        get() = when (this) {
            is Scalar -> 1
            is Vector -> size
            else -> null
        }

    fun sameFamily(other: DreamShader2Type): Boolean {
        val mine = familySpelling ?: return false
        val theirs = other.familySpelling ?: return false
        return mine == theirs
    }

    /** Numeric category comparison ignoring vector shape (float vs float3 both FLOAT). */
    fun sameNumericFamilyIgnoringShape(other: DreamShader2Type): Boolean {
        val mine = (this as? Vector)?.family ?: (this as? Scalar)?.family ?: (this as? Matrix)?.family
        val theirs = (other as? Vector)?.family ?: (other as? Scalar)?.family ?: (other as? Matrix)?.family
        return mine != null && mine == theirs
    }

    enum class Category {
        NUMERIC,
        BOOL,
        TEXTURE,
        SAMPLER,
        MATERIAL,
        SUBSTRATE,
        STRUCT,
        VOID,
    }

    /** The coarse bucket mismatch warnings compare on (C2: warning-level, never error). */
    val category: Category
        get() = when {
            this is Scalar && family == Family.BOOL -> Category.BOOL
            isNumeric -> Category.NUMERIC
            this is Texture -> Category.TEXTURE
            this is Sampler -> Category.SAMPLER
            this is Material -> Category.MATERIAL
            this is Substrate -> Category.SUBSTRATE
            this is StructType -> Category.STRUCT
            this is Void -> Category.VOID
            else -> throw IllegalStateException("Unknown type has no category")
        }

    companion object {
        private val VECTOR_SIZES = 2..4

        /**
         * Parses a type spelling as the upstream declaration table spells them. Unrecognized
         * names return [StructType] (a user struct this file may or may not declare) — callers
         * that need certainty re-resolve via the symbol model.
         */
        fun fromSpelling(spelling: String?): DreamShader2Type {
            val name = spelling?.trim()?.takeIf { it.isNotEmpty() } ?: return Unknown
            return when (name.lowercase()) {
                "float", "double" -> Scalar(Family.FLOAT)
                "half" -> Scalar(Family.HALF)
                "int", "dword" -> Scalar(Family.INT)
                "uint" -> Scalar(Family.UINT)
                "bool" -> Scalar(Family.BOOL)
                "void" -> Void
                "material" -> Material
                "substrate" -> Substrate
                "samplerstate", "sampler" -> Sampler
                "texture2d", "texturecube", "texture2darray", "texture3d", "volumetexture" ->
                    Texture(kindName(name))
                else -> {
                    // Vector / matrix spellings: family stem + dims (`float3`, `int2`, `float4x4`).
                    val matrixMatch = Regex("^([A-Za-z]+)([2-4])x([2-4])$").find(name)
                    val vectorMatch = Regex("^([A-Za-z]+)([0-9])$").find(name)
                    when {
                        matrixMatch != null -> {
                            val family = familyFromStem(matrixMatch.groupValues[1])
                            if (family != null) {
                                Matrix(family, matrixMatch.groupValues[2][0] - '0', matrixMatch.groupValues[3][0] - '0')
                            } else {
                                StructType(name)
                            }
                        }
                        vectorMatch != null -> {
                            val family = familyFromStem(vectorMatch.groupValues[1])
                            val size = vectorMatch.groupValues[2][0] - '0'
                            when {
                                family == null -> StructType(name)
                                size == 1 -> Scalar(family)
                                size in VECTOR_SIZES -> Vector(family, size)
                                else -> Unknown
                            }
                        }
                        else -> StructType(name)
                    }
                }
            }
        }

        private fun kindName(lower: String): String = when (lower) {
            "texture2d" -> "Texture2D"
            "texturecube" -> "TextureCube"
            "texture2darray" -> "Texture2DArray"
            "texture3d" -> "Texture3D"
            "volumetexture" -> "VolumeTexture"
            else -> lower
        }

        private fun familyFromStem(stem: String): Family? = when (stem.lowercase()) {
            "float" -> Family.FLOAT
            "half" -> Family.HALF
            "int" -> Family.INT
            "uint" -> Family.UINT
            "bool" -> Family.BOOL
            else -> null
        }
    }
}
