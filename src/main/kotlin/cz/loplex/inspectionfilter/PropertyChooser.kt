package cz.loplex.inspectionfilter

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * The dialog's choice of the properties an inspection must have to be reported: a check box for each, stored at once
 * on every change, like the other choices.
 */
internal class PropertyChooser(private val project: Project) {

    /** The properties currently required. */
    var requiredProperties: Set<InspectionProperty> = loadRequiredProperties(project)
        private set

    private val changeListeners = mutableListOf<() -> Unit>()

    private val checkBoxes: Map<InspectionProperty, JBCheckBox> = InspectionProperty.entries.associateWith { property ->
        JBCheckBox(InspectionFilterBundle.message(property.messageKey), property in requiredProperties).apply {
            toolTipText = InspectionFilterBundle.message(property.descriptionKey)
        }
    }

    init {
        for (checkBox in checkBoxes.values) {
            checkBox.addActionListener {
                requiredProperties = checkBoxes.filterValues { it.isSelected }.keys
                storeRequiredProperties(project, requiredProperties)
                changeListeners.forEach { it() }
            }
        }
    }

    val component: JComponent = panel {
        for (checkBox in checkBoxes.values) {
            row { cell(checkBox) }
        }
    }.apply { isOpaque = false }

    /** Calls [listener] whenever [requiredProperties] has changed. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }
}
