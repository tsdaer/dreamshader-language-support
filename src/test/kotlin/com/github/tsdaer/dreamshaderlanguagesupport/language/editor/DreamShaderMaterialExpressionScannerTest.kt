package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DreamShaderMaterialExpressionScannerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `scans a direct UMaterialExpression subclass`() {
        val header = """
            UCLASS()
            class ENGINE_API UMaterialExpressionSine : public UMaterialExpression
            {
                GENERATED_UCLASS_BODY()

                UPROPERTY()
                FExpressionInput Input;
            };
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionScanner.scanHeaderText(header).single()

        assertEquals("UMaterialExpressionSine", entry.className)
        assertEquals("Sine", entry.ueName)
        assertEquals("UE", entry.namespace)
        assertEquals(DreamShaderMaterialExpressionSource.SCANNED_CACHE, entry.source)
        assertEquals("Input", entry.parameters.single().name)
    }

    @Test
    fun `uses DisplayName meta as ueName when present`() {
        val header = """
            UCLASS(MinimalAPI, meta=(DisplayName="World Position"))
            class UMaterialExpressionWorldPosition : public UMaterialExpression
            {
            };
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionScanner.scanHeaderText(header).single()

        assertEquals("WorldPosition", entry.ueName)
    }

    @Test
    fun `recognizes indirect subclass by base prefix`() {
        val header = """
            UCLASS()
            class UMaterialExpressionTextureBase : public UMaterialExpression {};

            UCLASS()
            class USomeRenamedNode : public UMaterialExpressionTextureBase {};
        """.trimIndent()

        val names = DreamShaderMaterialExpressionScanner.scanHeaderText(header).map { it.className }

        assertTrue(names.contains("UMaterialExpressionTextureBase"))
        assertTrue(names.contains("USomeRenamedNode"))
    }

    @Test
    fun `ignores unrelated UCLASS declarations`() {
        val header = """
            UCLASS()
            class UMyActor : public AActor {};
        """.trimIndent()

        assertTrue(DreamShaderMaterialExpressionScanner.scanHeaderText(header).isEmpty())
    }

    @Test
    fun `attaches leading doc comment as description`() {
        val header = """
            /** Creates a sine material expression. */
            UCLASS()
            class UMaterialExpressionSine : public UMaterialExpression {};
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionScanner.scanHeaderText(header).single()

        assertEquals("Creates a sine material expression.", entry.description)
    }

    @Test
    fun `does not attach unrelated doc comment`() {
        val header = """
            /** Unrelated helper. */
            int Helper();

            UCLASS()
            class UMaterialExpressionSine : public UMaterialExpression {};
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionScanner.scanHeaderText(header).single()

        assertEquals(
            "Material expression 'UMaterialExpressionSine' exposed as UE.Sine.",
            entry.description
        )
    }

    @Test
    fun `scans directory and skips generated folders`() {
        val sourceDir = tempFolder.newFolder("Source")
        File(sourceDir, "MaterialExpressionSine.h").writeText(
            """
                UCLASS()
                class UMaterialExpressionSine : public UMaterialExpression {};
            """.trimIndent()
        )
        val intermediate = tempFolder.newFolder("Intermediate")
        File(intermediate, "MaterialExpression.generated.h").writeText(
            """
                UCLASS()
                class UMaterialExpressionShouldBeSkipped : public UMaterialExpression {};
            """.trimIndent()
        )

        val names = DreamShaderMaterialExpressionScanner.scanDirectory(tempFolder.root).map { it.className }

        assertTrue(names.contains("UMaterialExpressionSine"))
        assertFalse(names.contains("UMaterialExpressionShouldBeSkipped"))
    }

    @Test
    fun `serialized cache JSON round-trips through the catalog parser`() {
        val header = """
            UCLASS(meta=(DisplayName="Texture Sample"))
            class UMaterialExpressionTextureSample : public UMaterialExpression
            {
                UPROPERTY()
                FExpressionInput Coordinates;
            };
        """.trimIndent()

        val scanned = DreamShaderMaterialExpressionScanner.scanHeaderText(header)
        val json = DreamShaderMaterialExpressionScanner.toManifestJson(scanned)
        val reparsed = DreamShaderMaterialExpressionManifest.parseCatalogEntries(
            json,
            DreamShaderMaterialExpressionSource.SCANNED_CACHE
        )
        val entry = reparsed.single()

        assertEquals("UMaterialExpressionTextureSample", entry.className)
        assertEquals("TextureSample", entry.ueName)
        assertEquals("Coordinates", entry.parameters.single().name)
    }

    @Test
    fun `returns empty for a missing directory`() {
        assertTrue(DreamShaderMaterialExpressionScanner.scanDirectory(File(tempFolder.root, "missing")).isEmpty())
    }
}
