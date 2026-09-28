package cz.loplex.inspectionfilter

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.codeInspection.actions.CodeInspectionAction
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * "Inspect Code", with a choice of the severities to report.
 *
 * The dialog is the one "Inspect Code" shows, with the severities added under the profile. The inspections are then
 * run with a copy of the chosen profile in which those of any other severity are switched off - see
 * [SeverityFilteredProfile] - so the results hold nothing else, and "Rerun" in the results keeps the same filter.
 */
internal class InspectCodeBySeverityAction : CodeInspectionAction() {

    override fun getAdditionalActionSettings(project: Project, dialog: BaseAnalysisActionDialog): JComponent {
        val profileSettings = super.getAdditionalActionSettings(project, dialog)
        val selected = loadSelectedSeverityNames(project)
        val checkBoxes = availableSeverities(project).associate { severity ->
            severity.name to JBCheckBox(severity.displayCapitalizedName, severity.name in selected)
        }
        for (checkBox in checkBoxes.values) {
            checkBox.addActionListener {
                // A run with no severity chosen would switch every inspection off, so the last one cannot be cleared.
                if (checkBoxes.values.none { it.isSelected }) {
                    checkBox.isSelected = true
                }
                storeSelectedSeverityNames(project, checkBoxes.filterValues { it.isSelected }.keys)
            }
        }
        return panel {
            if (profileSettings != null) {
                row { cell(profileSettings).align(AlignX.FILL) }
            }
            group(InspectionFilterBundle.message("dialog.severities.title")) {
                for (checkBox in checkBoxes.values) {
                    row { cell(checkBox) }
                }
            }
        }
    }

    override fun runInspections(project: Project, scope: AnalysisScope) {
        val profile = myExternalProfile ?: InspectionProjectProfileManager.getInstance(project).currentProfile
        // A run the platform repeats itself, such as one stopped for a missing JDK and repeated once a JDK is chosen,
        // comes back here with the profile of the run it repeats, already filtered. Filtering it again by what is
        // chosen now could only narrow it, and a repeated run is meant to repeat the run. ("Rerun" in the results does
        // not come back here: it runs the same inspection context, profile and all, again.)
        if (profile !is SeverityFilteredProfile) {
            myExternalProfile = SeverityFilteredProfile(profile, loadSelectedSeverityNames(project), project)
        }
        super.runInspections(project, scope)
    }
}
