package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DreamShaderPreprocessorModelTest {
    @Test fun localDefinesAndKnownConditions() {
        val text = "#define FEATURE 2\n#if FEATURE >= 2\nactive\n#else\ninactive\n#endif\n"
        val model = DreamShaderPreprocessorModel.analyze(text)
        assertEquals(DreamShaderPreprocessorModel.State.ACTIVE, model.stateAt(text.indexOf("active")))
        assertEquals(DreamShaderPreprocessorModel.State.INACTIVE, model.stateAt(text.indexOf("inactive")))
        assertTrue(model.issues.isEmpty())
        assertEquals(setOf("FEATURE"), model.definitionsAt(text.length))
    }
    @Test fun externalDefinesStayUnknown() {
        val text = "#if DS_ENGINE_MINOR >= 8\none\n#else\ntwo\n#endif\n"
        val model = DreamShaderPreprocessorModel.analyze(text)
        assertEquals(DreamShaderPreprocessorModel.State.UNKNOWN, model.stateAt(text.indexOf("one")))
        assertEquals(DreamShaderPreprocessorModel.State.UNKNOWN, model.stateAt(text.indexOf("two")))
        assertTrue(model.issues.isEmpty())
    }
    @Test fun sourceDefinesDoNotLeakBetweenFiles() {
        assertEquals(setOf("LOCAL"), DreamShaderPreprocessorModel.analyze("#define LOCAL 1\n").definitionsAt(100))
        val second = DreamShaderPreprocessorModel.analyze("#ifdef LOCAL\nvalue\n#endif")
        assertTrue(second.definitionsAt(100).isEmpty())
        assertEquals(DreamShaderPreprocessorModel.State.UNKNOWN, second.stateAt(15))
    }
    @Test fun structureAndReservedPrefixDiagnostics() {
        val issues = DreamShaderPreprocessorModel.analyze("#endif\n#else\n#define DS_TEST 1\n#undef DS_PLATFORM\n#if 1\n#else\n#else\n").issues.map { it.code }
        assertTrue(issues.containsAll(listOf("DSH1031", "DSH1032", "DSH1039", "DSH1033", "DSH1030")))
        assertEquals(2, issues.count { it == "DSH1039" })
    }
    @Test fun commentsAndQuotedHashLinesAreIgnored() {
        val text = "/*\n#define DS_BAD 1\n*/\nconst String = \"#undef DS_TEST\";\n// #endif\n"
        assertTrue(DreamShaderPreprocessorModel.analyze(text).issues.isEmpty())
    }
    @Test fun unknownBranchDefinitionsDoNotBecomeKnown() {
        val text = "#if EXTERNAL\n#define MAYBE 1\n#endif\n#if MAYBE\nvalue\n#endif"
        assertEquals(DreamShaderPreprocessorModel.State.UNKNOWN, DreamShaderPreprocessorModel.analyze(text).stateAt(text.indexOf("value")))
    }
    @Test fun shortCircuitCanProveInactiveWithoutExternalEnvironment() {
        val text = "#if 0 && UNKNOWN\nno\n#elif 1 || UNKNOWN\nyes\n#endif"
        val model = DreamShaderPreprocessorModel.analyze(text)
        assertEquals(DreamShaderPreprocessorModel.State.INACTIVE, model.stateAt(text.indexOf("no")))
        assertEquals(DreamShaderPreprocessorModel.State.ACTIVE, model.stateAt(text.indexOf("yes")))
    }
    @Test fun definedUndefAndNestedBranches() {
        val text = "#define A\n#undef B\n#if defined(A) && !defined B\n#if (0)\nno\n#else\nyes\n#endif\n#endif"
        val model = DreamShaderPreprocessorModel.analyze(text)
        assertEquals(DreamShaderPreprocessorModel.State.INACTIVE, model.stateAt(text.indexOf("no")))
        assertEquals(DreamShaderPreprocessorModel.State.ACTIVE, model.stateAt(text.indexOf("yes")))
    }
    @org.junit.jupiter.api.Test
    fun `builtin presence is known while environment values stay unknown`() {
        val model = DreamShaderPreprocessorModel.analyze("#ifdef DS_PLATFORM\nBuiltin\n#endif\n#if DS_ENGINE_MINOR > 5\nVersion\n#endif")
        org.junit.jupiter.api.Assertions.assertEquals(DreamShaderPreprocessorModel.State.ACTIVE, model.stateAt(21))
        org.junit.jupiter.api.Assertions.assertEquals(DreamShaderPreprocessorModel.State.UNKNOWN, model.stateAt(69))
    }

    @org.junit.jupiter.api.Test
    fun `quoted urls and directive trailing tokens follow upstream rules`() {
        val text = "#define URL \"http://build\"\n#if URL == \"http://build\"\nSelected\n#else junk\n#endif label\n#IF FOO"
        val model = DreamShaderPreprocessorModel.analyze(text)
        org.junit.jupiter.api.Assertions.assertEquals(DreamShaderPreprocessorModel.State.ACTIVE, model.stateAt(text.indexOf("Selected")))
        org.junit.jupiter.api.Assertions.assertEquals(listOf("DSH1042", "DSH1042", "DSH1035"), model.issues.map { it.code })
    }

}
