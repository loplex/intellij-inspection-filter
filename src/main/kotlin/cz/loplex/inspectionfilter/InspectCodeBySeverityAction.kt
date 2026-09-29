package cz.loplex.inspectionfilter

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.application.options.schemes.SchemesCombo
import com.intellij.codeInspection.actions.CodeInspectionAction
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val GRAZIE_WARNING_SHOWN = "shown"
private const val GRAZIE_WARNING_HIDDEN = "hidden"

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
        // Read rather than myExternalProfile: a combo notifies the listener added last first, so while ours runs, the
        // one that sets myExternalProfile has not run yet.
        val profilesCombo = profileSettings?.let { UIUtil.findComponentOfType(it, SchemesCombo::class.java) }
        val selected = loadSelectedSeverityNames(project)
        val checkBoxes = availableSeverities(project).associate { severity ->
            severity.name to JBCheckBox(severity.displayCapitalizedName, severity.name in selected)
        }
        val baselineVersion = ApplicationInfo.getInstance().build.baselineVersion
        val grazieWarning = JBLabel(InspectionFilterBundle.message("dialog.grazie.warning.text"), AllIcons.General.Warning,
                                    JBLabel.LEADING)
        // The dialog sizes its content once, when it opens, and scrolls whatever grows later out of sight. The warning
        // therefore keeps its place while hidden: the layout is as large as the larger of its cards.
        val grazieWarningLayout = CardLayout()
        val grazieWarningCards = JPanel(grazieWarningLayout).apply {
            isOpaque = false
            add(grazieWarning, GRAZIE_WARNING_SHOWN)
            add(JPanel().apply { isOpaque = false }, GRAZIE_WARNING_HIDDEN)
        }

        fun updateGrazieWarning() {
            val profile = profilesCombo?.selectedScheme as? InspectionProfileImpl
                          ?: InspectionProjectProfileManager.getInstance(project).currentProfile
            val shown = filterBreaksGrazieRunner(profile, InspectionProjectProfileManager.getInstance(project).currentProfile,
                                                 checkBoxes.filterValues { it.isSelected }.keys, project, baselineVersion)
            if (shown) {
                grazieWarning.toolTipText = grazieWarningTooltip(profile, project)
            }
            grazieWarningLayout.show(grazieWarningCards, if (shown) GRAZIE_WARNING_SHOWN else GRAZIE_WARNING_HIDDEN)
        }

        for (checkBox in checkBoxes.values) {
            checkBox.addActionListener {
                // A run with no severity chosen would switch every inspection off, so the last one cannot be cleared.
                if (checkBoxes.values.none { it.isSelected }) {
                    checkBox.isSelected = true
                }
                storeSelectedSeverityNames(project, checkBoxes.filterValues { it.isSelected }.keys)
                updateGrazieWarning()
            }
        }
        profilesCombo?.addActionListener { updateGrazieWarning() }
        updateGrazieWarning()

        return panel {
            if (profileSettings != null) {
                row { cell(profileSettings).align(AlignX.FILL) }
            }
            // Just above the severities, where it is seen, taking over the gap before them; and only where the bug is,
            // so as not to leave an empty row everywhere else.
            val hasGrazieWarningRow = grazieRunnerWorkaroundApplies(
                InspectionProjectProfileManager.getInstance(project).currentProfile, project, baselineVersion)
            if (hasGrazieWarningRow) {
                row { cell(grazieWarningCards) }.topGap(TopGap.MEDIUM)
            }
            group(InspectionFilterBundle.message("dialog.severities.title")) {
                for (checkBox in checkBoxes.values) {
                    row { cell(checkBox) }
                }
            }.apply { if (hasGrazieWarningRow) topGap(TopGap.NONE) }
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

/** Names the Grazie inspections and their severities as [profile] has them, which is how the dialog lists them. */
private fun grazieWarningTooltip(profile: InspectionProfileImpl, project: Project): String {
    val children = grazieRunnerChildren(profile, project)
    return HtmlChunk.raw(InspectionFilterBundle.message(
        "dialog.grazie.warning.tooltip",
        children.joinToString(" and ") { it.defaultState.tool.displayName },
        children.joinToString(" and ") { it.defaultState.level.severity.displayCapitalizedName },
    )).wrapWith(HtmlChunk.html()).toString()
}
