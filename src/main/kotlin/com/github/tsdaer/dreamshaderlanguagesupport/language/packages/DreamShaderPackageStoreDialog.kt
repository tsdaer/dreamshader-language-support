package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 包商店对话框。
 *
 * 提供包列表浏览、筛选、安装/更新/移除、源管理与 GitHub 搜索入口。
 */
internal class DreamShaderPackageStoreDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val storeService = project.getService(DreamShaderPackageStoreService::class.java)
    private val packageManager = DreamShaderPackageManager(project)

    private val queryField = JBTextField()
    private val searchButton = JButton(DreamShaderBundle.message("package.store.dialog.button.search"))
    private val githubSearchButton = JButton(DreamShaderBundle.message("package.store.dialog.button.githubSearch"))
    private val listModel = DefaultListModel<DreamShaderPackageIndexEntry>()
    private val packageList = JBList(listModel)
    private val installedOnlyCheckBox = JCheckBox(DreamShaderBundle.message("package.store.dialog.installedOnly"))
    private val updatesPossibleOnlyCheckBox = JCheckBox(DreamShaderBundle.message("package.store.dialog.updatesOnly"))
    private val installButton = JButton(DreamShaderBundle.message("package.store.dialog.button.install"))
    private val updateButton = JButton(DreamShaderBundle.message("package.store.dialog.button.update"))
    private val removeButton = JButton(DreamShaderBundle.message("package.store.dialog.button.remove"))
    private val showRepoButton = JButton(DreamShaderBundle.message("package.store.dialog.button.showRepo"))
    private val detailPanel = JPanel(BorderLayout())
    private var installedByName: Map<String, DreamShaderPackageLockEntry> = emptyMap()
    private var gitAvailableForLifecycle: Boolean = false
    private var operationInProgress: Boolean = false
    private var githubSearchInProgress: Boolean = false
    private var pendingSelectPackageName: String? = null

    private var snapshot: DreamShaderPackageStoreSnapshot = DreamShaderPackageStoreSnapshot(
        sources = emptyList(),
        entries = emptyList(),
        errors = emptyList()
    )
    private var centerPanel: JPanel? = null

    init {
        title = DreamShaderBundle.message("packages.store.title")
        init()
        refreshData()
    }

    override fun createCenterPanel(): JPanel {
        val root = JPanel(BorderLayout(JBUI.scale(12), JBUI.scale(12)))
        centerPanel = root
        root.preferredSize = Dimension(1080, 680)
        DreamShaderUi.installSurface(root)

        queryField.name = QUERY_FIELD_NAME
        packageList.name = PACKAGE_LIST_NAME
        installedOnlyCheckBox.name = INSTALLED_ONLY_CHECKBOX_NAME
        updatesPossibleOnlyCheckBox.name = UPDATES_ONLY_CHECKBOX_NAME
        installButton.name = INSTALL_BUTTON_NAME
        updateButton.name = UPDATE_BUTTON_NAME
        removeButton.name = REMOVE_BUTTON_NAME
        showRepoButton.name = SHOW_REPOSITORY_BUTTON_NAME

        queryField.emptyText.text = DreamShaderBundle.message("package.store.dialog.searchTooltip")

        val toolbar = DreamShaderUi.card(BorderLayout(JBUI.scale(12), 0)).apply {
            border = JBUI.Borders.empty(12)
        }
        val leftToolbar = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
        }
        queryField.toolTipText = DreamShaderBundle.message("package.store.dialog.searchTooltip")
        leftToolbar.add(queryField, BorderLayout.CENTER)

        searchButton.name = SEARCH_BUTTON_NAME
        searchButton.addActionListener { refreshData() }
        leftToolbar.add(searchButton, BorderLayout.EAST)
        githubSearchButton.name = GITHUB_SEARCH_BUTTON_NAME
        githubSearchButton.icon = AllIcons.Vcs.Vendors.Github
        githubSearchButton.addActionListener { searchOnGitHub() }
        leftToolbar.add(githubSearchButton, BorderLayout.WEST)

        val rightToolbar = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
            isOpaque = false
        }
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
        packageList.visibleRowCount = 12
        packageList.fixedCellHeight = JBUI.scale(92)
        packageList.border = JBUI.Borders.empty()
        packageList.cellRenderer = PackageCardRenderer()
        packageList.emptyText.text = DreamShaderBundle.message("package.store.dialog.noSelection")
        packageList.background = DreamShaderUi.panelBackground
        val packageListCard = DreamShaderUi.section(
            title = DreamShaderBundle.message("packages.store.title"),
            description = DreamShaderBundle.message("package.store.dialog.searchTooltip"),
            content = JBScrollPane(packageList).apply {
                border = JBUI.Borders.empty()
                viewport.background = DreamShaderUi.panelBackground
            }
        )
        detailPanel.isOpaque = false
        val detailCard = DreamShaderUi.section(
            title = DreamShaderBundle.message("package.store.dialog.repositoryTitle"),
            description = DreamShaderBundle.message("package.store.dialog.updatesOnlyTooltip"),
            content = JBScrollPane(detailPanel).apply {
                border = JBUI.Borders.empty()
                viewport.isOpaque = false
                viewport.background = DreamShaderUi.cardBackground
            }
        )

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            packageListCard,
            detailCard
        ).apply {
            resizeWeight = 0.46
            border = JBUI.Borders.empty()
            dividerSize = JBUI.scale(10)
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
        root.add(split, BorderLayout.CENTER)

        val bottomBar = DreamShaderUi.card(BorderLayout()).apply {
            border = JBUI.Borders.empty(10, 12)
        }
        installButton.addActionListener { installSelectedPackage() }
        updateButton.addActionListener { updateSelectedPackage() }
        removeButton.addActionListener { removeSelectedPackage() }
        showRepoButton.addActionListener { showSelectedRepository() }
        bottomBar.add(DreamShaderUi.mutedLabel(DreamShaderBundle.message("package.store.dialog.selectPackage")), BorderLayout.WEST)
        bottomBar.add(
            DreamShaderUi.horizontal(
                8,
                FlowLayout.RIGHT,
                showRepoButton,
                removeButton,
                updateButton,
                installButton
            ),
            BorderLayout.EAST
        )
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
        detailPanel.removeAll()
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
            detailPanel.add(DreamShaderUi.vertical(
                12,
                EmptyStatePanel(DreamShaderBundle.message("package.store.dialog.noSelection"), gitInfo),
                sourceBlock(sourceInfo)
            ), BorderLayout.NORTH)
            detailPanel.revalidate()
            detailPanel.repaint()
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
        val pathValue = entry.path ?: "-"
        val updatePossibleValue = if (canUpdateEntry(entry)) {
            DreamShaderBundle.message("package.store.dialog.details.yes")
        } else {
            DreamShaderBundle.message("package.store.dialog.details.no")
        }
        val header = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            isOpaque = false
            val display = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.name
            add(DreamShaderUi.sectionTitle(display), BorderLayout.CENTER)
            add(
                DreamShaderUi.pill(
                    if (installed != null) DreamShaderBundle.message("package.store.dialog.list.installedPrefix")
                    else DreamShaderBundle.message("package.store.dialog.details.no"),
                    if (installed != null) DreamShaderUi.Tone.SUCCESS else DreamShaderUi.Tone.NEUTRAL
                ),
                BorderLayout.EAST
            )
        }
        val meta = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }
        var row = 0
        fun addMeta(label: String, value: String) {
            meta.add(JLabel(label).apply {
                foreground = DreamShaderUi.mutedForeground
            }, GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.NORTHWEST
                insets = JBUI.insets(4, 0, 4, 12)
            })
            meta.add(JLabel("<html>${escapeHtml(value)}</html>").apply {
                foreground = UIUtil.getLabelForeground()
            }, GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                insets = JBUI.insets(4, 0, 4, 0)
            })
            row++
        }
        addMeta(DreamShaderBundle.message("package.store.dialog.details.name", "").trim(), entry.name)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.version", "").trim(), versionValue)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.installed", "").trim(), installedLine)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.updatePossible", "").trim(), updatePossibleValue)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.repository", "").trim(), entry.repository)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.source", "").trim(), entry.source)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.path", "").trim(), pathValue)
        addMeta(DreamShaderBundle.message("package.store.dialog.details.tags", "").trim(), tags)

        val description = JBTextArea(entry.description ?: "-").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = JBUI.Borders.empty()
            foreground = UIUtil.getLabelForeground()
        }
        detailPanel.add(
            DreamShaderUi.vertical(
                14,
                header,
                meta,
                DreamShaderUi.section(
                    DreamShaderBundle.message("package.store.dialog.details.description"),
                    null,
                    description
                )
            ),
            BorderLayout.NORTH
        )
        detailPanel.revalidate()
        detailPanel.repaint()
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
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.addSource.title"),
            DreamShaderBundle.message("packages.dialog.addSource.input"),
            "https://.../index.json or local index path"
        ) ?: return

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

    private fun updateActionButtons() {
        val busy = operationInProgress || githubSearchInProgress
        queryField.isEnabled = !busy
        searchButton.isEnabled = !busy
        githubSearchButton.isEnabled = !busy
        installedOnlyCheckBox.isEnabled = !busy
        updatesPossibleOnlyCheckBox.isEnabled = !busy

        val selected = packageList.selectedValue
        if (selected == null) {
            installButton.isEnabled = false
            updateButton.isEnabled = false
            removeButton.isEnabled = false
            showRepoButton.isEnabled = !busy
            return
        }

        val installed = installedByName.containsKey(selected.name)
        installButton.isEnabled = !busy && !installed
        updateButton.isEnabled = !busy && canUpdateEntry(selected)
        removeButton.isEnabled = !busy && installed
        showRepoButton.isEnabled = !busy
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
        if (operationInProgress || githubSearchInProgress) return

        githubSearchInProgress = true
        updateActionButtons()
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, DreamShaderBundle.message("packages.dialog.githubSearch.title"), true) {
                private var result: DreamShaderGitHubSearchResult? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = DreamShaderBundle.message("packages.dialog.githubSearch.title")
                    indicator.text2 = query
                    result = storeService.searchGitHubPackages(query)
                }

                override fun onSuccess() {
                    val resolved = result ?: DreamShaderGitHubSearchResult(
                        entries = emptyList(),
                        errorMessage = DreamShaderBundle.message("common.unknownError")
                    )
                    applyGitHubSearchResult(query, resolved, showFeedback = true)
                }

                override fun onThrowable(error: Throwable) {
                    DreamShaderPackageNotifier.error(
                        project,
                        DreamShaderBundle.message("packages.store.title"),
                        error.message ?: DreamShaderBundle.message("common.unknownError")
                    )
                }

                override fun onFinished() {
                    githubSearchInProgress = false
                    updateActionButtons()
                }
            }
        )
    }

    private fun applyGitHubSearchResult(
        query: String,
        result: DreamShaderGitHubSearchResult,
        showFeedback: Boolean
    ): GitHubSearchActionStatus {
        if (result.errorMessage != null) {
            if (showFeedback) {
                DreamShaderPackageNotifier.error(
                    project,
                    DreamShaderBundle.message("packages.store.title"),
                    result.errorMessage
                )
            }
            return GitHubSearchActionStatus.ERROR
        }
        applyGitHubSearchEntries(query, result.entries, showFeedback)
        return GitHubSearchActionStatus.APPLIED
    }

    private fun executeGitHubSearch(
        queryRaw: String,
        showFeedback: Boolean,
        search: (String) -> DreamShaderGitHubSearchResult
    ): GitHubSearchActionStatus {
        val query = queryRaw.trim()
        if (query.isBlank()) {
            if (showFeedback) {
                Messages.showInfoMessage(
                    project,
                    DreamShaderBundle.message("package.store.dialog.githubSearch.emptyQuery"),
                    DreamShaderBundle.message("packages.store.title")
                )
            }
            return GitHubSearchActionStatus.EMPTY_QUERY
        }

        val result = search(query)
        return applyGitHubSearchResult(query, result, showFeedback)
    }

    private fun applyGitHubSearchEntries(
        query: String,
        entries: List<DreamShaderPackageIndexEntry>,
        showFeedback: Boolean
    ) {
        listModel.clear()
        entries.forEach { listModel.addElement(it) }
        snapshot = snapshot.copy(entries = entries)
        installedByName = packageManager.listLockEntries().associateBy { it.name }
        if (listModel.size > 0) {
            packageList.selectedIndex = 0
            packageList.ensureIndexIsVisible(0)
        } else {
            renderDetails(null)
            if (showFeedback) {
                DreamShaderPackageNotifier.info(
                    project,
                    DreamShaderBundle.message("packages.store.title"),
                    DreamShaderBundle.message("package.store.dialog.githubSearch.noResults", query)
                )
            }
        }
        updateActionButtons()
    }

    internal fun testCenterPanel(): JPanel? = centerPanel
    internal fun testSetGitHubSearchInProgress(inProgress: Boolean) {
        githubSearchInProgress = inProgress
        updateActionButtons()
    }
    internal fun testExecuteGitHubSearch(
        queryRaw: String,
        result: DreamShaderGitHubSearchResult
    ): GitHubSearchActionStatus = executeGitHubSearch(queryRaw, showFeedback = false) { result }

    internal enum class GitHubSearchActionStatus {
        EMPTY_QUERY,
        ERROR,
        APPLIED
    }

    private fun sourceBlock(sourceInfo: String): JComponent {
        return DreamShaderUi.card(BorderLayout()).apply {
            add(JBTextArea(sourceInfo).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                border = JBUI.Borders.empty()
                foreground = UIUtil.getLabelForeground()
            }, BorderLayout.CENTER)
        }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private inner class PackageCardRenderer : ListCellRenderer<DreamShaderPackageIndexEntry> {
        override fun getListCellRendererComponent(
            list: JList<out DreamShaderPackageIndexEntry>,
            value: DreamShaderPackageIndexEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val installed = installedByName[value.name]
            val display = value.displayName?.takeIf { it.isNotBlank() } ?: value.name
            val version = installed?.version ?: value.version?.takeIf { it.isNotBlank() } ?: "-"
            val tags = if (value.tags.isEmpty()) "" else value.tags.take(3).joinToString("  ")
            val panel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
                border = BorderFactory.createCompoundBorder(
                    JBUI.Borders.empty(4, 2),
                    DreamShaderUi.RoundedBorder(
                        if (isSelected) DreamShaderUi.accent else DreamShaderUi.borderColor,
                        JBUI.scale(12),
                        JBUI.insets(10, 12)
                    )
                )
                background = if (isSelected) {
                    JBColor(Color(0xE8, 0xF2, 0xFF), Color(0x22, 0x35, 0x4D))
                } else {
                    DreamShaderUi.elevatedBackground
                }
                isOpaque = true
            }
            val title = JLabel(display).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = UIUtil.getLabelForeground()
            }
            val name = JLabel(value.name).apply {
                foreground = DreamShaderUi.mutedForeground
            }
            val top = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(title, BorderLayout.CENTER)
                add(DreamShaderUi.pill("v$version", DreamShaderUi.Tone.ACCENT), BorderLayout.EAST)
            }
            val summary = JLabel("<html>${escapeHtml(value.description ?: value.repository)}</html>").apply {
                foreground = DreamShaderUi.mutedForeground
            }
            val bottom = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(name, BorderLayout.WEST)
                if (installed != null) {
                    add(DreamShaderUi.pill(DreamShaderBundle.message("package.store.dialog.list.installedPrefix"), DreamShaderUi.Tone.SUCCESS), BorderLayout.EAST)
                }
            }
            val body = DreamShaderUi.vertical(4, top, summary, bottom)
            panel.add(body, BorderLayout.CENTER)
            if (tags.isNotBlank()) {
                panel.add(JLabel(tags).apply {
                    foreground = DreamShaderUi.mutedForeground
                    font = font.deriveFont(font.size2D - 1f)
                }, BorderLayout.SOUTH)
            }
            return panel
        }
    }

    private class EmptyStatePanel(
        title: String,
        message: String
    ) : JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(6))) {
        init {
            isOpaque = false
            border = JBUI.Borders.empty(16)
            add(DreamShaderUi.sectionTitle(title), BorderLayout.NORTH)
            add(JLabel("<html>${message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</html>").apply {
                foreground = DreamShaderUi.mutedForeground
            }, BorderLayout.CENTER)
        }
    }

    companion object {
        internal const val QUERY_FIELD_NAME = "dreamshader.packageStore.queryField"
        internal const val SEARCH_BUTTON_NAME = "dreamshader.packageStore.searchButton"
        internal const val GITHUB_SEARCH_BUTTON_NAME = "dreamshader.packageStore.githubSearchButton"
        internal const val PACKAGE_LIST_NAME = "dreamshader.packageStore.packageList"
        internal const val INSTALLED_ONLY_CHECKBOX_NAME = "dreamshader.packageStore.installedOnlyCheckBox"
        internal const val UPDATES_ONLY_CHECKBOX_NAME = "dreamshader.packageStore.updatesOnlyCheckBox"
        internal const val INSTALL_BUTTON_NAME = "dreamshader.packageStore.installButton"
        internal const val UPDATE_BUTTON_NAME = "dreamshader.packageStore.updateButton"
        internal const val REMOVE_BUTTON_NAME = "dreamshader.packageStore.removeButton"
        internal const val SHOW_REPOSITORY_BUTTON_NAME = "dreamshader.packageStore.showRepositoryButton"
    }
}
