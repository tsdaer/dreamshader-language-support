package com.github.tsdaer.dreamshaderlanguagesupport.language.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*
import javax.swing.border.AbstractBorder

internal object DreamShaderUi {
    val panelBackground: Color
        get() = UIUtil.getPanelBackground()

    val cardBackground: Color = JBColor(Color(0xFA, 0xFB, 0xFC), Color(0x2B, 0x2F, 0x36))
    val elevatedBackground: Color = JBColor(Color(0xFF, 0xFF, 0xFF), Color(0x31, 0x36, 0x3F))
    val subtleBackground: Color = JBColor(Color(0xF2, 0xF5, 0xF8), Color(0x24, 0x28, 0x30))
    val stageBackground: Color = JBColor(Color(0xEC, 0xF1, 0xF5), Color(0x18, 0x1B, 0x21))
    val borderColor: Color = JBColor(Color(0xD7, 0xDD, 0xE5), Color(0x45, 0x4B, 0x56))
    val mutedForeground: Color
        get() = UIUtil.getContextHelpForeground()
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

    fun card(layout: java.awt.LayoutManager = BorderLayout()): JPanel {
        return RoundedPanel(cardBackground, borderColor, JBUI.scale(12)).apply {
            this.layout = layout
            border = JBUI.Borders.empty(14)
        }
    }

    fun section(title: String, description: String? = null, content: JComponent): JPanel {
        val panel = card(BorderLayout(JBUI.scale(10), JBUI.scale(10)))
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(sectionTitle(title), BorderLayout.NORTH)
            if (!description.isNullOrBlank()) {
                add(mutedLabel(description), BorderLayout.SOUTH)
            }
        }
        panel.add(header, BorderLayout.NORTH)
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

    fun vertical(gap: Int = 10, vararg components: Component): JPanel {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            components.forEachIndexed { index, component ->
                if (index > 0) add(Box.createVerticalStrut(JBUI.scale(gap)))
                if (component is JComponent) {
                    component.alignmentX = Component.LEFT_ALIGNMENT
                }
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
        val row = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }
        val label = JLabel(labelText).apply {
            foreground = UIUtil.getLabelForeground()
            toolTipText = tooltip
        }
        component.toolTipText = tooltip
        row.add(label, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 0, 2, 12)
        })
        row.add(component, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2, 0)
        })
        return row
    }

    fun checkRow(component: JComponent, tooltip: String? = null): JPanel {
        component.toolTipText = tooltip
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)
            add(component, BorderLayout.CENTER)
        }
    }

    fun buttonRow(vararg buttons: JButton): JPanel {
        return horizontal(6, FlowLayout.LEFT, *buttons)
    }

    fun showInputDialog(
        project: Project,
        title: String,
        label: String,
        emptyText: String? = null
    ): String? {
        val dialog = DreamShaderInputDialog(project, title, label, emptyText)
        if (!dialog.showAndGet()) return null
        return dialog.value.trim().takeIf { it.isNotBlank() }
    }

    fun pill(text: String, tone: Tone = Tone.NEUTRAL): JLabel {
        return PillLabel(text, tone).apply {
            border = JBUI.Borders.empty(3, 8)
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        }
    }

    fun installSurface(component: JComponent) {
        component.background = panelBackground
        component.border = JBUI.Borders.empty(12)
    }

    enum class Tone {
        NEUTRAL,
        ACCENT,
        SUCCESS,
        WARNING,
        DANGER
    }

    private class PillLabel(
        text: String,
        private val tone: Tone
    ) : JLabel(text) {
        init {
            isOpaque = false
            foreground = when (tone) {
                Tone.NEUTRAL -> UIUtil.getLabelForeground()
                Tone.ACCENT -> accent
                Tone.SUCCESS -> success
                Tone.WARNING -> warning
                Tone.DANGER -> danger
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = when (tone) {
                    Tone.NEUTRAL -> subtleBackground
                    Tone.ACCENT -> withAlpha(accent, 32)
                    Tone.SUCCESS -> withAlpha(success, 34)
                    Tone.WARNING -> withAlpha(warning, 34)
                    Tone.DANGER -> withAlpha(danger, 34)
                }
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private class RoundedPanel(
        private val fill: Color,
        private val stroke: Color,
        private val arc: Int
    ) : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = fill
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                g2.color = stroke
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    class RoundedBorder(
        private val color: Color = borderColor,
        private val arc: Int = JBUI.scale(10),
        private val padding: Insets = JBUI.insets(8)
    ) : AbstractBorder() {
        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
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

        val value: String
            get() = textField.text

        init {
            title = titleText
            init()
        }

        override fun createCenterPanel(): JComponent {
            if (!emptyText.isNullOrBlank()) {
                textField.emptyText.text = emptyText
            }
            return card(BorderLayout(JBUI.scale(10), JBUI.scale(10))).apply {
                preferredSize = JBUI.size(520, 112)
                add(vertical(8, titleLabel(labelText), textField), BorderLayout.CENTER)
            }
        }

        override fun getPreferredFocusedComponent(): JComponent = textField
    }
}
