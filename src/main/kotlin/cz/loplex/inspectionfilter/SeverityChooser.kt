package cz.loplex.inspectionfilter

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * The dialog's choice of the severities to report: those ticked one by one, or one and every severity above it, in
 * which case the check boxes are disabled and show which that is.
 *
 * Every change is stored at once (see SeveritySelection.kt), so that the choice survives the dialog.
 */
internal class SeverityChooser(private val project: Project) {

    /** The names of the severities the choice currently reports. */
    var reportedSeverityNames: Set<String> = emptySet()
        private set

    private val changeListeners = mutableListOf<() -> Unit>()

    val component: JComponent = buildContent()

    /** Calls [listener] whenever [reportedSeverityNames] may have changed. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    private fun buildContent(): JComponent {
        val severities = availableSeverities(project)
        val checkBoxes = severities.associate { it.name to JBCheckBox(it.displayCapitalizedName) }
        val lowest = ComboBox(CollectionComboBoxModel(severities, severities.first { it.name == loadLowestSeverityName(project) }))
        lowest.renderer = SimpleListCellRenderer.create("") { it.displayCapitalizedName }
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
                for (checkBox in checkBoxes.values) {
                    row { cell(checkBox) }
                }
            }
        }.apply { isOpaque = false }
    }
}
