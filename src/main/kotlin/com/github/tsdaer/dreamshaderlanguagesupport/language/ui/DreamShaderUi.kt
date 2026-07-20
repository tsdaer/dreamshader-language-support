package com.github.tsdaer.dreamshaderlanguagesupport.language.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

internal object DreamShaderUi {
    val panelBackground: Color get() = UIUtil.getPanelBackground()
    val cardBackground: Color get() = UIUtil.getPanelBackground()
    val elevatedBackground: Color get() = UIUtil.getPanelBackground()
    val stageBackground: Color get() = UIUtil.getPanelBackground()
    val borderColor: Color get() = JBColor.border()
    val mutedForeground: Color get() = UIUtil.getContextHelpForeground()
    val accent: Color = JBColor(Color(0x1D, 0x6F, 0xC7), Color(0x6A, 0xA7, 0xFF))
    val success: Color = JBColor(Color(0x1F, 0x7A, 0x3F), Color(0x7A, 0xD6, 0x99))
    val warning: Color = JBColor(Color(0x9A, 0x68, 0x12), Color(0xE8, 0xB8, 0x55))
    val danger: Color = JBColor(Color(0xB3, 0x2D, 0x2D), Color(0xFF, 0x8A, 0x8A))

    fun titleLabel(text: String): JLabel = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 1f)
        foreground = UIUtil.getLabelForeground()
    }

    fun sectionTitle(text: String): JLabel = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 2f)
        foreground = UIUtil.getLabelForeground()
    }

    fun mutedLabel(text: String = ""): JLabel = JLabel(text).apply {
        foreground = mutedForeground
    }

    fun card(layout: LayoutManager = BorderLayout(JBUI.scale(8), JBUI.scale(8))): JPanel {
        return JPanel(layout).apply {
            isOpaque = false
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(12, 14),
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
            )
        }
    }

    fun section(title: String, description: String? = null, content: JComponent): JPanel {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            isOpaque = false
            if (description.isNullOrBlank()) {
                add(TitledSeparator(title), BorderLayout.NORTH)
            } else {
                val header = JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(sectionTitle(title), BorderLayout.NORTH)
                    add(mutedLabel(description), BorderLayout.SOUTH)
                }
                add(header, BorderLayout.NORTH)
            }
        }
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

    fun vertical(gap: Int = 10, vararg components: Component): JPanel {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            components.forEachIndexed { index, component ->
                if (index > 0) add(Box.createVerticalStrut(JBUI.scale(gap)))
                if (component is JComponent) component.alignmentX = Component.LEFT_ALIGNMENT
                add(component)
            }
        }
    }

    fun horizontal(gap: Int = 8, alignment: Int = FlowLayout.LEFT, vararg components: Component): JPanel {
        return JPanel(FlowLayout(alignment, JBUI.scale(gap), 0)).apply {
            isOpaque = false
            components.forEach { add(it) }
        }
    }

    fun formRow(labelText: String, component: JComponent, tooltip: String? = null): JPanel {
        val row = JPanel(GridBagLayout()).apply { isOpaque = false }
        val label = JLabel(labelText).apply { foreground = UIUtil.getLabelForeground(); toolTipText = tooltip }
        component.toolTipText = tooltip
        row.add(label, GridBagConstraints().apply {
            gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 0, 2, 12)
        })
        row.add(component, GridBagConstraints().apply {
            gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(2, 0)
        })
        return row
    }

    fun checkRow(component: JComponent, tooltip: String? = null): JPanel {
        component.toolTipText = tooltip
        return JPanel(BorderLayout()).apply { isOpaque = false; border = JBUI.Borders.empty(2, 0); add(component, BorderLayout.CENTER) }
    }

    fun buttonRow(vararg buttons: JButton): JPanel = horizontal(6, FlowLayout.LEFT, *buttons)

    fun showInputDialog(project: Project, title: String, label: String, emptyText: String? = null): String? {
        val dialog = DreamShaderInputDialog(project, title, label, emptyText)
        return if (dialog.showAndGet()) dialog.value.trim().takeIf { it.isNotBlank() } else null
    }

    fun pill(text: String, tone: Tone = Tone.NEUTRAL): JLabel {
        val bg = when (tone) {
            Tone.NEUTRAL -> JBColor(Color(0xE8, 0xEB, 0xEE), Color(0x36, 0x3B, 0x44))
            Tone.ACCENT -> withAlpha(accent, 32)
            Tone.SUCCESS -> withAlpha(success, 34)
            Tone.WARNING -> withAlpha(warning, 34)
            Tone.DANGER -> withAlpha(danger, 34)
        }
        val fg = when (tone) {
            Tone.NEUTRAL -> UIUtil.getLabelForeground()
            Tone.ACCENT -> accent
            Tone.SUCCESS -> success
            Tone.WARNING -> warning
            Tone.DANGER -> danger
        }
        return JLabel(text).apply {
            isOpaque = true
            background = bg
            foreground = fg
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            border = JBUI.Borders.empty(2, 6)
            horizontalAlignment = SwingConstants.CENTER
        }
    }

    fun installSurface(component: JComponent) {
        component.background = panelBackground
        component.border = JBUI.Borders.empty(12)
    }

    enum class Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

    class RoundedBorder(
        color: Color = JBColor.border(),
        arc: Int = JBUI.scale(6),
        private val padding: Insets = JBUI.insets(8)
    ) : javax.swing.border.AbstractBorder() {
        private val borderColor = color
        private val borderArc = arc

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = borderColor
                g2.drawRoundRect(x, y, width - 1, height - 1, borderArc, borderArc)
            } finally {
                g2.dispose()
            }
        }

        override fun getBorderInsets(c: Component): Insets = padding
        override fun getBorderInsets(c: Component, insets: Insets): Insets {
            insets.set(padding.top, padding.left, padding.bottom, padding.right)
            return insets
        }
    }

    private fun withAlpha(color: Color, alpha: Int): Color = Color(color.red, color.green, color.blue, alpha)

    private class DreamShaderInputDialog(
        project: Project,
        titleText: String,
        private val labelText: String,
        private val emptyText: String?
    ) : DialogWrapper(project) {
        private val textField = JBTextField()
        val value: String get() = textField.text

        init { title = titleText; init() }

        override fun createCenterPanel(): JComponent {
            if (!emptyText.isNullOrBlank()) textField.emptyText.text = emptyText
            return JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(10))).apply {
                isOpaque = false
                preferredSize = JBUI.size(520, 112)
                add(vertical(8, titleLabel(labelText), textField), BorderLayout.CENTER)
            }
        }

        override fun getPreferredFocusedComponent(): JComponent = textField
    }
}
