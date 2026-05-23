package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderBundle
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

internal class DreamShaderPackageStoreDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val storeService = project.getService(DreamShaderPackageStoreService::class.java)
    private val packageManager = DreamShaderPackageManager(project)

    private val queryField = JBTextField()
    private val listModel = DefaultListModel<DreamShaderPackageIndexEntry>()
    private val packageList = JBList(listModel)
    private val detailArea = JBTextArea()
    private val installedOnlyCheckBox = JCheckBox(DreamShaderBundle.message("package.store.dialog.installedOnly"))
    private val updatesPossibleOnlyCheckBox = JCheckBox(DreamShaderBundle.message("package.store.dialog.updatesOnly"))
    private val installButton = JButton(DreamShaderBundle.message("package.store.dialog.button.install"))
    private val updateButton = JButton(DreamShaderBundle.message("package.store.dialog.button.update"))
    private val removeButton = JButton(DreamShaderBundle.message("package.store.dialog.button.remove"))
    private val showRepoButton = JButton(DreamShaderBundle.message("package.store.dialog.button.showRepo"))
    private var installedByName: Map<String, DreamShaderPackageLockEntry> = emptyMap()
    private var gitAvailableForLifecycle: Boolean = false
    private var operationInProgress: Boolean = false
    private var pendingSelectPackageName: String? = null

    private var snapshot: DreamShaderPackageStoreSnapshot = DreamShaderPackageStoreSnapshot(
        sources = emptyList(),
        entries = emptyList(),
        errors = emptyList()
    )

    init {
        title = DreamShaderBundle.message("packages.store.title")
        init()
        refreshData()
    }

    override fun createCenterPanel(): JPanel {
        val root = JPanel(BorderLayout(8, 8))
        root.preferredSize = Dimension(980, 600)

        val toolbar = JPanel(BorderLayout(8, 0))
        val leftToolbar = JPanel(BorderLayout(8, 0))
        queryField.toolTipText = DreamShaderBundle.message("package.store.dialog.searchTooltip")
        leftToolbar.add(queryField, BorderLayout.CENTER)

        val searchButton = JButton(DreamShaderBundle.message("package.store.dialog.button.search"))
        searchButton.addActionListener { refreshData() }
        leftToolbar.add(searchButton, BorderLayout.EAST)
        val githubSearchButton = JButton(DreamShaderBundle.message("package.store.dialog.button.githubSearch"))
        githubSearchButton.addActionListener { searchOnGitHub() }
        leftToolbar.add(githubSearchButton, BorderLayout.WEST)

        val rightToolbar = JPanel()
        installedOnlyCheckBox.addActionListener { refreshData() }
        updatesPossibleOnlyCheckBox.toolTipText = DreamShaderBundle.message("package.store.dialog.updatesOnlyTooltip")
        updatesPossibleOnlyCheckBox.addActionListener { refreshData() }
        val refreshButton = JButton(DreamShaderBundle.message("package.store.dialog.button.refresh"))
        refreshButton.addActionListener { refreshData() }
        val addSourceButton = JButton(DreamShaderBundle.message("package.store.dialog.button.addSource"))
        addSourceButton.addActionListener { addSource() }
        val removeSourceButton = JButton(DreamShaderBundle.message("package.store.dialog.button.removeSource"))
        removeSourceButton.addActionListener { removeSource() }
        rightToolbar.add(installedOnlyCheckBox)
        rightToolbar.add(updatesPossibleOnlyCheckBox)
        rightToolbar.add(refreshButton)
        rightToolbar.add(addSourceButton)
        rightToolbar.add(removeSourceButton)

        toolbar.add(leftToolbar, BorderLayout.CENTER)
        toolbar.add(rightToolbar, BorderLayout.EAST)
        root.add(toolbar, BorderLayout.NORTH)

        packageList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        packageList.cellRenderer = DefaultListCellRenderer().apply {
            horizontalAlignment = SwingConstants.LEFT
        }
        packageList.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
            val renderer = DefaultListCellRenderer().getListCellRendererComponent(
                list,
                value,
                index,
                isSelected,
                cellHasFocus
            ) as DefaultListCellRenderer
            val display = value.displayName?.takeIf { it.isNotBlank() } ?: value.name
            val installedLock = installedByName[value.name]
            val installedPrefix = if (installedLock != null) {
                "${DreamShaderBundle.message("package.store.dialog.list.installedPrefix")} "
            } else {
                ""
            }
            val version = installedLock?.version ?: value.version?.takeIf { it.isNotBlank() } ?: "-"
            val tagPreview = if (value.tags.isEmpty()) {
                ""
            } else {
                val preview = value.tags.take(3).joinToString(", ")
                val suffix = if (value.tags.size > 3) {
                    ", ${DreamShaderBundle.message("package.store.dialog.list.moreTags", value.tags.size - 3)}"
                } else {
                    ""
                }
                " | ${DreamShaderBundle.message("package.store.dialog.list.tagsPrefix")}: $preview$suffix"
            }
            renderer.text = DreamShaderBundle.message(
                "package.store.dialog.list.item",
                installedPrefix,
                display,
                value.name,
                version,
                tagPreview
            )
            renderer
        }
        packageList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                renderDetails(packageList.selectedValue)
                updateActionButtons()
            }
        }
        packageList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 2) {
                    installSelectedPackage()
                }
            }
        })

        detailArea.isEditable = false
        detailArea.lineWrap = true
        detailArea.wrapStyleWord = true

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(packageList),
            JBScrollPane(detailArea)
        )
        split.resizeWeight = 0.45
        root.add(split, BorderLayout.CENTER)

        val bottomBar = JPanel()
        installButton.addActionListener { installSelectedPackage() }
        updateButton.addActionListener { updateSelectedPackage() }
        removeButton.addActionListener { removeSelectedPackage() }
        showRepoButton.addActionListener { showSelectedRepository() }
        bottomBar.add(installButton)
        bottomBar.add(updateButton)
        bottomBar.add(removeButton)
        bottomBar.add(showRepoButton)
        root.add(bottomBar, BorderLayout.SOUTH)
        updateActionButtons()

        return root
    }

    override fun createActions() = arrayOf(cancelAction)

    private fun refreshData() {
        val previousSelection = packageList.selectedValue?.name
        snapshot = storeService.loadStore(queryField.text)
        installedByName = packageManager.listLockEntries().associateBy { it.name }
        gitAvailableForLifecycle = packageManager.isGitAvailable()
        val filteredEntries = snapshot.entries.filter { entry ->
            val matchesInstalled = !installedOnlyCheckBox.isSelected || installedByName.containsKey(entry.name)
            val matchesUpdatePossible = !updatesPossibleOnlyCheckBox.isSelected || canUpdateEntry(entry)
            matchesInstalled && matchesUpdatePossible
        }
        listModel.clear()
        filteredEntries.forEach { listModel.addElement(it) }

        if (!snapshot.errors.isEmpty()) {
            val first = snapshot.errors.first()
            DreamShaderPackageNotifier.error(
                project,
                DreamShaderBundle.message("packages.store.title"),
                first.message
            )
        }

        if (listModel.size > 0) {
            val preferred = pendingSelectPackageName ?: previousSelection
            val preferredIndex = preferred?.let { name ->
                (0 until listModel.size).firstOrNull { idx -> listModel[idx].name == name }
            } ?: -1
            val index = if (preferredIndex >= 0) preferredIndex else 0
            packageList.selectedIndex = index
            packageList.ensureIndexIsVisible(index)
        } else {
            renderDetails(null)
        }
        pendingSelectPackageName = null
        updateActionButtons()
    }

    private fun renderDetails(entry: DreamShaderPackageIndexEntry?) {
        if (entry == null) {
            val sourceInfo = if (snapshot.sources.isEmpty()) {
                DreamShaderBundle.message("package.store.dialog.noSources")
            } else {
                "${DreamShaderBundle.message("package.store.dialog.sources", snapshot.sources.size)}\n${snapshot.sources.joinToString("\n")}"
            }
            val gitInfo = if (gitAvailableForLifecycle) {
                DreamShaderBundle.message("package.store.dialog.gitAvailable")
            } else {
                DreamShaderBundle.message("package.store.dialog.gitUnavailable")
            }
            detailArea.text = "${DreamShaderBundle.message("package.store.dialog.noSelection")}\n\n$gitInfo\n\n$sourceInfo"
            return
        }

        val tags = if (entry.tags.isEmpty()) "-" else entry.tags.joinToString(", ")
        val installed = installedByName[entry.name]
        val installedLine = if (installed != null) {
            DreamShaderBundle.message("package.store.dialog.details.installedValue", installed.version, installed.commit)
        } else {
            DreamShaderBundle.message("package.store.dialog.details.no")
        }
        val versionValue = installed?.version ?: entry.version ?: "-"
        val displayValue = entry.displayName ?: "-"
        val pathValue = entry.path ?: "-"
        val updatePossibleValue = if (canUpdateEntry(entry)) {
            DreamShaderBundle.message("package.store.dialog.details.yes")
        } else {
            DreamShaderBundle.message("package.store.dialog.details.no")
        }
        detailArea.text = buildString {
            appendLine(DreamShaderBundle.message("package.store.dialog.details.name", entry.name))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.display", displayValue))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.version", versionValue))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.installed", installedLine))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.updatePossible", updatePossibleValue))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.repository", entry.repository))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.source", entry.source))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.path", pathValue))
            appendLine(DreamShaderBundle.message("package.store.dialog.details.tags", tags))
            appendLine()
            appendLine(DreamShaderBundle.message("package.store.dialog.details.description"))
            appendLine(entry.description ?: "-")
        }
    }

    private fun installSelectedPackage() {
        val selected = packageList.selectedValue
        if (selected == null) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.selectPackage"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }
        val source = DreamShaderPackageIndexLoader.resolveInstallSource(selected)
        runLifecycleTask(
            title = DreamShaderBundle.message("package.store.dialog.lifecycle.installTitle"),
            selectedName = selected.name,
            action = { indicator ->
                indicator.text = DreamShaderBundle.message("package.store.dialog.lifecycle.installing", selected.name)
                indicator.text2 = source.sourcePathOrUrl
                indicator.checkCanceled()
                packageManager.installFromRepository(source.sourcePathOrUrl)
            },
            onSuccess = { result ->
                if (result.success) {
                    pendingSelectPackageName = selected.name
                    DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.store.title"), result.message)
                } else {
                    DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.store.title"), result.message)
                }
            }
        )
    }

    private fun updateSelectedPackage() {
        val selected = packageList.selectedValue
        if (selected == null) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.selectPackage"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }
        if (!canUpdateEntry(selected)) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.cannotUpdate"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }
        runLifecycleTask(
            title = DreamShaderBundle.message("package.store.dialog.lifecycle.updateTitle"),
            selectedName = selected.name,
            action = { indicator ->
                indicator.text = DreamShaderBundle.message("package.store.dialog.lifecycle.updating", selected.name)
                indicator.checkCanceled()
                packageManager.updateInstalledPackage(selected.name)
            },
            onSuccess = { result ->
                if (result.success) {
                    pendingSelectPackageName = selected.name
                    DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.store.title"), result.message)
                } else {
                    DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.store.title"), result.message)
                }
            }
        )
    }

    private fun removeSelectedPackage() {
        val selected = packageList.selectedValue
        if (selected == null) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.selectPackage"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }
        val confirm = Messages.showYesNoDialog(
            project,
            DreamShaderBundle.message("package.store.dialog.removeConfirm", selected.name),
            DreamShaderBundle.message("packages.dialog.remove.title"),
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        runLifecycleTask(
            title = DreamShaderBundle.message("package.store.dialog.lifecycle.removeTitle"),
            selectedName = selected.name,
            action = { indicator ->
                indicator.text = DreamShaderBundle.message("package.store.dialog.lifecycle.removing", selected.name)
                indicator.checkCanceled()
                packageManager.removeInstalledPackage(selected.name)
            },
            onSuccess = { result ->
                if (result.success) {
                    DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.store.title"), result.message)
                } else {
                    DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.store.title"), result.message)
                }
            }
        )
    }

    private fun showSelectedRepository() {
        val selected = packageList.selectedValue
        if (selected == null) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.selectPackage"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }
        Messages.showInfoMessage(project, selected.repository, DreamShaderBundle.message("package.store.dialog.repositoryTitle"))
    }

    private fun addSource() {
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.addSource.input"),
            DreamShaderBundle.message("packages.dialog.addSource.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = storeService.addIndexSource(input)
        if (result.changed) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.sources.title"), result.message)
            refreshData()
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        }
    }

    private fun removeSource() {
        if (snapshot.sources.isEmpty()) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.noSourcesToRemove"),
                DreamShaderBundle.message("packages.sources.title")
            )
            return
        }
        val selected = Messages.showEditableChooseDialog(
            DreamShaderBundle.message("package.store.dialog.selectSourceToRemove"),
            DreamShaderBundle.message("packages.dialog.removeSource.title"),
            Messages.getQuestionIcon(),
            snapshot.sources.toTypedArray(),
            snapshot.sources.firstOrNull(),
            null
        )?.trim().orEmpty()
        if (selected.isBlank()) return

        val result = storeService.removeIndexSource(selected)
        if (result.changed) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.sources.title"), result.message)
            refreshData()
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        }
    }

    private fun <T> JBList<T>.setCellRenderer(
        renderer: (list: JList<out T>, value: T, index: Int, isSelected: Boolean, cellHasFocus: Boolean) -> DefaultListCellRenderer
    ) {
        cellRenderer = javax.swing.ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
            @Suppress("UNCHECKED_CAST")
            renderer(list as JList<out T>, value as T, index, isSelected, cellHasFocus)
        }
    }

    private fun updateActionButtons() {
        val selected = packageList.selectedValue
        if (selected == null) {
            installButton.isEnabled = false
            updateButton.isEnabled = false
            removeButton.isEnabled = false
            showRepoButton.isEnabled = !operationInProgress
            return
        }

        val installed = installedByName.containsKey(selected.name)
        installButton.isEnabled = !operationInProgress && !installed
        updateButton.isEnabled = !operationInProgress && canUpdateEntry(selected)
        removeButton.isEnabled = !operationInProgress && installed
        showRepoButton.isEnabled = !operationInProgress
    }

    private fun canUpdateEntry(entry: DreamShaderPackageIndexEntry): Boolean {
        val installed = installedByName[entry.name] ?: return false
        if (!gitAvailableForLifecycle) return false
        return installed.repository.isNotBlank() || entry.repository.isNotBlank()
    }

    private fun runLifecycleTask(
        title: String,
        selectedName: String,
        action: (ProgressIndicator) -> DreamShaderPackageOperationResult,
        onSuccess: (DreamShaderPackageOperationResult) -> Unit
    ) {
        if (operationInProgress) return
        operationInProgress = true
        updateActionButtons()
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, title, true) {
                private var result: DreamShaderPackageOperationResult? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    result = action(indicator)
                }

                override fun onSuccess() {
                    onSuccess(
                        result ?: DreamShaderPackageOperationResult(
                            false,
                            DreamShaderBundle.message("package.store.dialog.lifecycle.noResult")
                        )
                    )
                }

                override fun onCancel() {
                    DreamShaderPackageNotifier.info(
                        project,
                        DreamShaderBundle.message("packages.store.title"),
                        DreamShaderBundle.message("package.store.dialog.lifecycle.cancelled", selectedName)
                    )
                }

                override fun onThrowable(error: Throwable) {
                    if (error is ProcessCanceledException) {
                        DreamShaderPackageNotifier.info(
                            project,
                            DreamShaderBundle.message("packages.store.title"),
                            DreamShaderBundle.message("package.store.dialog.lifecycle.cancelled", selectedName)
                        )
                    } else {
                        DreamShaderPackageNotifier.error(
                            project,
                            DreamShaderBundle.message("packages.store.title"),
                            error.message ?: DreamShaderBundle.message("package.store.dialog.lifecycle.failed", selectedName)
                        )
                    }
                }

                override fun onFinished() {
                    operationInProgress = false
                    refreshData()
                    updateActionButtons()
                }
            }
        )
    }

    private fun searchOnGitHub() {
        val query = queryField.text.trim()
        if (query.isBlank()) {
            Messages.showInfoMessage(
                project,
                DreamShaderBundle.message("package.store.dialog.githubSearch.emptyQuery"),
                DreamShaderBundle.message("packages.store.title")
            )
            return
        }

        val result = storeService.searchGitHubPackages(query)
        if (result.errorMessage != null) {
            DreamShaderPackageNotifier.error(
                project,
                DreamShaderBundle.message("packages.store.title"),
                result.errorMessage
            )
            return
        }

        listModel.clear()
        result.entries.forEach { listModel.addElement(it) }
        snapshot = snapshot.copy(entries = result.entries)
        installedByName = packageManager.listLockEntries().associateBy { it.name }
        if (listModel.size > 0) {
            packageList.selectedIndex = 0
            packageList.ensureIndexIsVisible(0)
        } else {
            renderDetails(null)
            DreamShaderPackageNotifier.info(
                project,
                DreamShaderBundle.message("packages.store.title"),
                DreamShaderBundle.message("package.store.dialog.githubSearch.noResults", query)
            )
        }
        updateActionButtons()
    }
}
