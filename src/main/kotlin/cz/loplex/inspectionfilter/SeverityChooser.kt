package cz.loplex.inspectionfilter

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/** Registered by the platform; its classes are internal, its ID is not. */
private const val EDIT_SEVERITIES_ACTION_ID = "SeverityEditorDialog"

/**
 * The dialog's choice of the severities to report: those ticked one by one, or one and every severity above it, in
 * which case the check boxes are disabled and show which that is.
 *
 * Every change is stored at once (see SeveritySelection.kt), so that the choice survives the dialog, and so that the
 * whole of it can be built anew from what is stored after "Edit Severities…" has added, removed or reordered severities.
 */
internal class SeverityChooser(private val project: Project) {

    val component: JComponent = JPanel(BorderLayout()).apply { isOpaque = false }

    /** The names of the severities the choice currently reports. */
    var reportedSeverityNames: Set<String> = emptySet()
        private set

    private val changeListeners = mutableListOf<() -> Unit>()

    init {
        rebuild()
    }

    /** Calls [listener] whenever [reportedSeverityNames] may have changed. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    private fun rebuild() {
        component.removeAll()
        component.add(buildContent())
        component.revalidate()
        component.repaint()
    }

    private fun buildContent(): JComponent {
        val severities = availableSeverities(project)
        val checkBoxes = severities.associate { it.name to JBCheckBox() }
        // Beside the check box rather than its text, which cannot carry an icon: a check box's own icon replaces its box.
        val labels = severities.associate { severity ->
            val checkBox = checkBoxes.getValue(severity.name)
            severity.name to JBLabel(severity.displayCapitalizedName, severityIcon(severity), JBLabel.LEADING).apply {
                labelFor = checkBox
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (checkBox.isEnabled) checkBox.doClick()
                    }
                })
            }
        }
        val lowest = ComboBox(CollectionComboBoxModel(severities, severities.first { it.name == loadLowestSeverityName(project) }))
        lowest.renderer = SimpleListCellRenderer.create { label, severity, _ ->
            label.text = severity?.displayCapitalizedName.orEmpty()
            label.icon = severity?.let(::severityIcon)
        }
        val selectedMode = JBRadioButton(InspectionFilterBundle.message("dialog.severities.mode.selected"))
        val thisAndHigherMode = JBRadioButton(InspectionFilterBundle.message("dialog.severities.mode.thisAndHigher"))
        when (loadSeverityMode(project)) {
            SeverityMode.SELECTED -> selectedMode.isSelected = true
            SeverityMode.THIS_AND_HIGHER -> thisAndHigherMode.isSelected = true
        }

        fun update() {
            val thisAndHigher = thisAndHigherMode.isSelected
            lowest.isEnabled = thisAndHigher
            reportedSeverityNames = loadReportedSeverityNames(project)
            for ((name, checkBox) in checkBoxes) {
                checkBox.isEnabled = !thisAndHigher
                checkBox.isSelected = name in reportedSeverityNames
                labels.getValue(name).isEnabled = !thisAndHigher
            }
            changeListeners.forEach { it() }
        }

        selectedMode.addActionListener {
            storeSeverityMode(project, SeverityMode.SELECTED)
            update()
        }
        thisAndHigherMode.addActionListener {
            storeSeverityMode(project, SeverityMode.THIS_AND_HIGHER)
            update()
        }
        lowest.addActionListener {
            (lowest.selectedItem as? HighlightSeverity)?.let { storeLowestSeverityName(project, it.name) }
            update()
        }
        for (checkBox in checkBoxes.values) {
            checkBox.addActionListener {
                // A run with no severity chosen would switch every inspection off, so the last one cannot be cleared.
                if (checkBoxes.values.none { it.isSelected }) {
                    checkBox.isSelected = true
                }
                storeSelectedSeverityNames(project, checkBoxes.filterValues { it.isSelected }.keys)
                update()
            }
        }
        update()

        return panel {
            // Unbound: the DSL only makes the two exclusive, the mode is stored by the listeners above.
            buttonsGroup {
                row { cell(selectedMode) }
                row {
                    cell(thisAndHigherMode)
                    cell(lowest)
                }
            }
            indent {
                for ((name, checkBox) in checkBoxes) {
                    row {
                        cell(checkBox).gap(RightGap.SMALL)
                        cell(labels.getValue(name))
                    }
                }
            }
            row {
                link(InspectionFilterBundle.message("dialog.severities.edit")) { editSeverities() }
            }
        }.apply { isOpaque = false }
    }

    private fun editSeverities() {
        val action = ActionManager.getInstance().getAction(EDIT_SEVERITIES_ACTION_ID) ?: return
        val event = AnActionEvent.createEvent(action, SimpleDataContext.getProjectContext(project), null,
                                              ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        // The editor is a modal dialog, so it is closed by the time this returns.
        ActionUtil.performAction(action, event)
        rebuild()
        growDialogToFit(component)
    }
}

/** The icon the inspection settings show beside [severity], custom severities included. */
private fun severityIcon(severity: HighlightSeverity): Icon? = HighlightDisplayLevel.find(severity)?.icon

/**
 * Makes the dialog around [component] large enough for what it now holds, without ever shrinking it.
 *
 * The analysis dialog puts its content in a scroll pane whose preferred size it fixes when it opens
 * (`BaseAnalysisActionDialog.createCenterPanel`), so content that grows later only makes it scroll. A dialog built
 * otherwise is left as it is, and scrolls if it has to.
 */
private fun growDialogToFit(component: JComponent) {
    val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, component) as? JScrollPane ?: return
    val window = SwingUtilities.getWindowAncestor(component) ?: return
    // Computed from what the pane shows again, rather than fixed.
    scrollPane.preferredSize = null
    val preferred = window.preferredSize
    window.setSize(maxOf(preferred.width, window.width), maxOf(preferred.height, window.height))
    window.validate()
}
