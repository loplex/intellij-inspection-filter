package cz.loplex.inspectionfilter

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
import com.intellij.codeInspection.ex.Tools
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project

/** What a filtered run reports: problems of inspections of [severityNames], in the groups [groups] includes. */
internal data class InspectionFilter(val severityNames: Set<String>, val groups: GroupSelection = GroupSelection())

/** The filter last chosen in [project]. */
internal fun loadInspectionFilter(project: Project): InspectionFilter =
    InspectionFilter(loadReportedSeverityNames(project), loadGroupSelection(project))

/**
 * A copy of [source] in which every inspection that [filter] leaves out is switched off, so that a run with it neither
 * runs those inspections nor reports anything of theirs. [source] itself is left untouched.
 *
 * Severity is configured per scope: an inspection can be a warning in production code and a weak warning in tests.
 * The filter therefore switches off each scope's state on its own, and an inspection stays on wherever its severity
 * is one of those wanted. One left with no state switched on is switched off as a whole, so that a global inspection,
 * which runs over the whole scope rather than file by file, is not started for nothing. A group, on the other hand,
 * is the inspection's own, so an inspection of a group left out is switched off as a whole.
 *
 * The one exception is the Grazie runner in builds before 261, which follows its children instead - see
 * [grazieRunnerEnabled]. [baselineVersion] is the build's, a parameter so that tests can choose it.
 *
 * Like the profile "Run Inspection by Name" builds for its run, the copy lives until the project is closed: the
 * inspection context never releases the profile it was given.
 */
internal class FilteredProfile(
    source: InspectionProfileImpl,
    val filter: InspectionFilter,
    project: Project,
    baselineVersion: Int = ApplicationInfo.getInstance().build.baselineVersion,
) : InspectionProfileModifiableModel(source) {

    init {
        initInspectionTools(project)
        // Decided before anything is switched off, while the copy still reads as the source.
        val grazieRunnerEnabled = grazieRunnerEnabled(this, filter, project, baselineVersion)
        for (toolList in tools) {
            if (grazieRunnerEnabled != null && toolList.shortName == GRAZIE_RUNNER) {
                // Left as in the source when on, severities and scopes included: it reports nothing of its own.
                if (!grazieRunnerEnabled) {
                    toolList.isEnabled = false
                }
                continue
            }
            if (!filter.groups.includes(toolList.groupPath)) {
                toolList.isEnabled = false
                continue
            }
            val states = toolList.tools
            for (state in states) {
                if (state.level.severity.name !in filter.severityNames) {
                    state.isEnabled = false
                }
            }
            if (states.none { it.isEnabled }) {
                toolList.isEnabled = false
            }
        }
    }
}

/** Whether these tools stay on in a copy of their profile filtered by [filter], without making the copy. */
internal fun Tools.isKeptBy(filter: InspectionFilter): Boolean =
    isEnabled && filter.groups.includes(groupPath) && tools.any { it.isEnabled && it.level.severity.name in filter.severityNames }
