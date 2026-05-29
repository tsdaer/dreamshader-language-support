package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderSettingsUiTestBase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JButton
import kotlin.io.path.absolutePathString

class DreamShaderPackageStoreDialogUiTest : DreamShaderSettingsUiTestBase() {
    fun testActionButtonsToggleBySelectionAndInstallState() {
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-installed",
                    "displayName": "Installed Pkg",
                    "description": "installed package",
                    "repository": "https://example.com/installed.git",
                    "version": "1.0.0",
                    "tags": ["installed"]
                  },
                  {
                    "name": "@typedreammoon/dream-new",
                    "displayName": "New Pkg",
                    "description": "new package",
                    "repository": "https://example.com/new.git",
                    "version": "1.0.0",
                    "tags": ["new"]
                  }
                ]
            """.trimIndent(),
            lockContent = """
                {
                  "packages": [
                    {
                      "name": "@typedreammoon/dream-installed",
                      "version": "1.0.0",
                      "repository": "https://example.com/installed.git",
                      "commit": "abc123",
                      "installPath": "DShader/Packages/@typedreammoon/dream-installed"
                    }
                  ]
                }
            """.trimIndent()
        )
        val packageList = dialogHarness.packageList
        val installButton = dialogHarness.installButton
        val updateButton = dialogHarness.updateButton
        val removeButton = dialogHarness.removeButton

        // Default selection should be first item (@typedreammoon/dream-installed).
        assertTrue("Expected installed package to disable install", !installButton.isEnabled)
        assertTrue("Expected installed package to enable update", updateButton.isEnabled)
        assertTrue("Expected installed package to enable remove", removeButton.isEnabled)

        packageList.selectedIndex = 1
        assertTrue("Expected new package to enable install", installButton.isEnabled)
        assertTrue("Expected new package to disable remove", !removeButton.isEnabled)
        assertTrue("Expected new package to disable update when not installed", !updateButton.isEnabled)

        packageList.clearSelection()
        assertTrue("Expected no selection to disable install", !installButton.isEnabled)
        assertTrue("Expected no selection to disable update", !updateButton.isEnabled)
        assertTrue("Expected no selection to disable remove", !removeButton.isEnabled)
    }

    fun testFiltersRefreshListAndActionButtons() {
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-installed-no-update",
                    "displayName": "Installed Without Repo",
                    "description": "installed package without repo",
                    "repository": "",
                    "version": "1.0.0",
                    "tags": ["installed"]
                  },
                  {
                    "name": "@typedreammoon/dream-new",
                    "displayName": "New Pkg",
                    "description": "new package",
                    "repository": "https://example.com/new.git",
                    "version": "1.0.0",
                    "tags": ["new"]
                  }
                ]
            """.trimIndent(),
            lockContent = """
                {
                  "packages": [
                    {
                      "name": "@typedreammoon/dream-installed-no-update",
                      "version": "1.0.0",
                      "repository": "",
                      "commit": "abc123",
                      "installPath": "DShader/Packages/@typedreammoon/dream-installed-no-update"
                    }
                  ]
                }
            """.trimIndent()
        )
        val packageList = dialogHarness.packageList
        val installedOnly = dialogHarness.installedOnlyCheckBox
        val updatesOnly = dialogHarness.updatesOnlyCheckBox
        val installButton = dialogHarness.installButton
        val updateButton = dialogHarness.updateButton
        val removeButton = dialogHarness.removeButton

        assertEquals("Expected full list without filters", 2, packageList.model.size)

        installedOnly.doClick()
        assertEquals("Expected installed-only filter to keep one entry", 1, packageList.model.size)

        installedOnly.doClick()
        assertEquals("Expected disabling installed-only filter to restore entries", 2, packageList.model.size)

        updatesOnly.doClick()
        assertEquals("Expected updates-only filter to hide non-updatable entries", 0, packageList.model.size)
        assertTrue("Expected install disabled when filtered list is empty", !installButton.isEnabled)
        assertTrue("Expected update disabled when filtered list is empty", !updateButton.isEnabled)
        assertTrue("Expected remove disabled when filtered list is empty", !removeButton.isEnabled)

        updatesOnly.doClick()
        assertEquals("Expected disabling updates-only filter to restore entries", 2, packageList.model.size)
    }

    fun testSearchFieldAndButtonRefreshListByQuery() {
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  },
                  {
                    "name": "@typedreammoon/dream-water",
                    "displayName": "Water Surface",
                    "description": "water rendering helpers",
                    "repository": "https://example.com/water.git",
                    "version": "1.0.0",
                    "tags": ["water", "surface"]
                  }
                ]
            """.trimIndent()
        )
        val queryField = dialogHarness.queryField
        val searchButton = dialogHarness.searchButton
        val packageList = dialogHarness.packageList

        assertEquals("Expected full list before search", 2, packageList.model.size)

        queryField.text = "procedural"
        searchButton.doClick()
        assertEquals("Expected filtered list by query", 1, packageList.model.size)
        assertTrue(
            "Expected filtered entry to be dream-noise",
            packageList.model.getElementAt(0).toString().contains("@typedreammoon/dream-noise")
        )

        queryField.text = ""
        searchButton.doClick()
        assertEquals("Expected full list after clearing query", 2, packageList.model.size)
    }

    fun testGitHubSearchEmptyQueryReturnsEmptyStatusAndDoesNotMutateList() {
        // [GH-SM][EMPTY_QUERY] Empty query should short-circuit without mutating current list.
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  }
                ]
            """.trimIndent()
        )
        val dialog = dialogHarness.dialog
        val packageList = dialogHarness.packageList
        assertEquals("Expected initial list to contain one entry", 1, packageList.model.size)

        val status = dialog.testExecuteGitHubSearch(
            queryRaw = "   ",
            result = DreamShaderGitHubSearchResult(
                entries = listOf(
                    DreamShaderPackageIndexEntry(
                        name = "@github/ignored/entry",
                        displayName = "Ignored",
                        description = "Ignored",
                        version = "1.0.0",
                        repository = "https://github.com/ignored/entry",
                        source = "github-search",
                        path = null,
                        tags = listOf("ignored")
                    )
                ),
                errorMessage = null
            )
        )

        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.EMPTY_QUERY, status)
        assertEquals("Expected list to remain unchanged on empty query", 1, packageList.model.size)
    }

    fun testGitHubSearchAppliedStatusRefreshesListEntries() {
        // [GH-SM][APPLIED] Successful GitHub search should replace list with returned entries.
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  }
                ]
            """.trimIndent()
        )
        val dialog = dialogHarness.dialog
        val packageList = dialogHarness.packageList
        assertEquals("Expected initial list to contain one entry", 1, packageList.model.size)

        val searchResult = DreamShaderGitHubSearchResult(
            entries = listOf(
                DreamShaderPackageIndexEntry(
                    name = "@github/TypeDreamMoon/dream-water",
                    displayName = "dream-water",
                    description = "Water package",
                    version = null,
                    repository = "https://github.com/TypeDreamMoon/dream-water",
                    source = "github-search",
                    path = null,
                    tags = listOf("dreamshader", "water")
                ),
                DreamShaderPackageIndexEntry(
                    name = "@github/TypeDreamMoon/dream-ice",
                    displayName = "dream-ice",
                    description = "Ice package",
                    version = null,
                    repository = "https://github.com/TypeDreamMoon/dream-ice",
                    source = "github-search",
                    path = null,
                    tags = listOf("dreamshader", "ice")
                )
            ),
            errorMessage = null
        )

        val status = dialog.testExecuteGitHubSearch(queryRaw = "water", result = searchResult)
        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.APPLIED, status)
        assertEquals("Expected GitHub search entries to replace list", 2, packageList.model.size)
        assertTrue(packageList.model.getElementAt(0).toString().contains("@github/TypeDreamMoon/dream-water"))
        assertTrue(packageList.model.getElementAt(1).toString().contains("@github/TypeDreamMoon/dream-ice"))
    }

    fun testGitHubSearchErrorStatusKeepsExistingList() {
        // [GH-SM][ERROR] Error status should preserve existing list content.
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  },
                  {
                    "name": "@typedreammoon/dream-water",
                    "displayName": "Water Surface",
                    "description": "water rendering helpers",
                    "repository": "https://example.com/water.git",
                    "version": "1.0.0",
                    "tags": ["water", "surface"]
                  }
                ]
            """.trimIndent()
        )
        val dialog = dialogHarness.dialog
        val packageList = dialogHarness.packageList
        assertEquals("Expected initial list to contain two entries", 2, packageList.model.size)
        val before = (0 until packageList.model.size).map { index -> packageList.model.getElementAt(index).toString() }

        val status = dialog.testExecuteGitHubSearch(
            queryRaw = "network-failure",
            result = DreamShaderGitHubSearchResult(
                entries = emptyList(),
                errorMessage = "GitHub search failed (500)."
            )
        )

        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.ERROR, status)
        assertEquals("Expected list size to remain unchanged on search error", 2, packageList.model.size)
        val after = (0 until packageList.model.size).map { index -> packageList.model.getElementAt(index).toString() }
        assertEquals("Expected list content to remain unchanged on search error", before, after)
    }

    fun testGitHubSearchNoResultsReplacesListWithEmptyState() {
        // [GH-SM][NO_RESULTS] APPLIED with empty entries should transition list to empty state.
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  }
                ]
            """.trimIndent()
        )
        val dialog = dialogHarness.dialog
        val packageList = dialogHarness.packageList
        val installButton = dialogHarness.installButton
        val updateButton = dialogHarness.updateButton
        val removeButton = dialogHarness.removeButton
        assertEquals("Expected initial list to contain one entry", 1, packageList.model.size)

        val status = dialog.testExecuteGitHubSearch(
            queryRaw = "no-result-query",
            result = DreamShaderGitHubSearchResult(
                entries = emptyList(),
                errorMessage = null
            )
        )

        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.APPLIED, status)
        assertEquals("Expected no-results search to clear list", 0, packageList.model.size)
        assertTrue("Expected install disabled in no-results state", !installButton.isEnabled)
        assertTrue("Expected update disabled in no-results state", !updateButton.isEnabled)
        assertTrue("Expected remove disabled in no-results state", !removeButton.isEnabled)
    }

    fun testGitHubSearchErrorAfterAppliedKeepsLastAppliedResults() {
        // [GH-SM][APPLIED_THEN_ERROR] Follow-up error should keep last successfully applied GitHub results.
        val dialogHarness = createDialogHarness(
            indexContent = """
                [
                  {
                    "name": "@typedreammoon/dream-noise",
                    "displayName": "Noise Toolkit",
                    "description": "procedural noise utilities",
                    "repository": "https://example.com/noise.git",
                    "version": "1.0.0",
                    "tags": ["noise", "procedural"]
                  }
                ]
            """.trimIndent()
        )
        val dialog = dialogHarness.dialog
        val packageList = dialogHarness.packageList
        assertEquals("Expected initial index list to contain one entry", 1, packageList.model.size)

        val appliedStatus = dialog.testExecuteGitHubSearch(
            queryRaw = "water",
            result = DreamShaderGitHubSearchResult(
                entries = listOf(
                    DreamShaderPackageIndexEntry(
                        name = "@github/TypeDreamMoon/dream-water",
                        displayName = "dream-water",
                        description = "Water package",
                        version = null,
                        repository = "https://github.com/TypeDreamMoon/dream-water",
                        source = "github-search",
                        path = null,
                        tags = listOf("dreamshader", "water")
                    ),
                    DreamShaderPackageIndexEntry(
                        name = "@github/TypeDreamMoon/dream-ice",
                        displayName = "dream-ice",
                        description = "Ice package",
                        version = null,
                        repository = "https://github.com/TypeDreamMoon/dream-ice",
                        source = "github-search",
                        path = null,
                        tags = listOf("dreamshader", "ice")
                    )
                ),
                errorMessage = null
            )
        )
        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.APPLIED, appliedStatus)
        assertEquals("Expected applied list size", 2, packageList.model.size)
        val appliedNames = (0 until packageList.model.size).map { index -> packageList.model.getElementAt(index).toString() }

        val errorStatus = dialog.testExecuteGitHubSearch(
            queryRaw = "follow-up-failure",
            result = DreamShaderGitHubSearchResult(
                entries = emptyList(),
                errorMessage = "GitHub search failed (500)."
            )
        )
        assertEquals(DreamShaderPackageStoreDialog.GitHubSearchActionStatus.ERROR, errorStatus)
        assertEquals("Expected list size to remain last-applied size", 2, packageList.model.size)
        val afterErrorNames = (0 until packageList.model.size).map { index -> packageList.model.getElementAt(index).toString() }
        assertEquals("Expected error path to preserve last applied GitHub entries", appliedNames, afterErrorNames)
    }

    private fun createIndex(content: String): Path {
        val file = Files.createTempFile("dreamshader-store-dialog-ui-index-", ".json")
        Files.writeString(file, content, StandardCharsets.UTF_8)
        file.toFile().deleteOnExit()
        return file
    }

    private fun writeLockFile(content: String) {
        val basePath = project.basePath ?: error("project base path is null")
        val lockPath = Path.of(basePath, "DShader", "dreamshader.lock.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(lockPath.parent.toString())
            val lockFile = parent.findOrCreateChildData(this, lockPath.fileName.toString())
            VfsUtil.saveText(lockFile, content)
        }
    }

    private fun createDialogHarness(
        indexContent: String,
        lockContent: String? = null
    ): DialogHarness {
        val source = createIndex(indexContent)
        val settings = project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java).state
        settings.packageStoreIndexUrls = mutableListOf(source.absolutePathString())
        settings.packageStoreIndexUrl = ""
        if (lockContent != null) {
            writeLockFile(lockContent)
        } else {
            clearLockFile()
        }

        val dialog = DreamShaderPackageStoreDialog(project)
        val root = dialog.testCenterPanel() ?: error("Expected center panel to be initialized")
        return DialogHarness(
            dialog = dialog,
            packageList = findComponentByName(root, DreamShaderPackageStoreDialog.PACKAGE_LIST_NAME, JBList::class.java) as JBList<*>,
            queryField = findComponentByName(root, DreamShaderPackageStoreDialog.QUERY_FIELD_NAME, JBTextField::class.java),
            searchButton = findComponentByName(root, DreamShaderPackageStoreDialog.SEARCH_BUTTON_NAME, JButton::class.java),
            installedOnlyCheckBox = findComponentByName(root, DreamShaderPackageStoreDialog.INSTALLED_ONLY_CHECKBOX_NAME, JCheckBox::class.java),
            updatesOnlyCheckBox = findComponentByName(root, DreamShaderPackageStoreDialog.UPDATES_ONLY_CHECKBOX_NAME, JCheckBox::class.java),
            installButton = findComponentByName(root, DreamShaderPackageStoreDialog.INSTALL_BUTTON_NAME, JButton::class.java),
            updateButton = findComponentByName(root, DreamShaderPackageStoreDialog.UPDATE_BUTTON_NAME, JButton::class.java),
            removeButton = findComponentByName(root, DreamShaderPackageStoreDialog.REMOVE_BUTTON_NAME, JButton::class.java)
        )
    }

    private fun clearLockFile() {
        val basePath = project.basePath ?: error("project base path is null")
        val lockPath = Path.of(basePath, "DShader", "dreamshader.lock.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val vf = VfsUtil.findFile(lockPath, false) ?: return@runWriteCommandAction
            vf.delete(this)
        }
    }

    private data class DialogHarness(
        val dialog: DreamShaderPackageStoreDialog,
        val packageList: JBList<*>,
        val queryField: JBTextField,
        val searchButton: JButton,
        val installedOnlyCheckBox: JCheckBox,
        val updatesOnlyCheckBox: JCheckBox,
        val installButton: JButton,
        val updateButton: JButton,
        val removeButton: JButton
    )
}
