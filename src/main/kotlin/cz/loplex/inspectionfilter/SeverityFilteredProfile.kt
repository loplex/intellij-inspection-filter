package cz.loplex.inspectionfilter

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
import com.intellij.codeInspection.ex.Tools
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project

/**
 * A copy of [source] in which every inspection whose severity is not among [severityNames] is switched off, so that a
 * run with it neither runs those inspections nor reports anything of theirs. [source] itself is left untouched.
 *
 * Severity is configured per scope: an inspection can be a warning in production code and a weak warning in tests.
 * The filter therefore switches off each scope's state on its own, and an inspection stays on wherever its severity
 * is one of those wanted. One left with no state switched on is switched off as a whole, so that a global inspection,
 * which runs over the whole scope rather than file by file, is not started for nothing.
 *
 * The one exception is the Grazie runner in builds before 261, which follows its children instead - see
 * [grazieRunnerEnabled]. [baselineVersion] is the build's, a parameter so that tests can choose it.
 *
 * Like the profile "Run Inspection by Name" builds for its run, the copy lives until the project is closed: the
 * inspection context never releases the profile it was given.
 */
internal class SeverityFilteredProfile(
    source: InspectionProfileImpl,
    val severityNames: Set<String>,
    project: Project,
    baselineVersion: Int = ApplicationInfo.getInstance().build.baselineVersion,
) : InspectionProfileModifiableModel(source) {

    init {
        initInspectionTools(project)
        // Decided before anything is switched off, while the copy still reads as the source.
        val grazieRunnerEnabled = grazieRunnerEnabled(this, severityNames, project, baselineVersion)
        for (toolList in tools) {
            if (grazieRunnerEnabled != null && toolList.shortName == GRAZIE_RUNNER) {
                // Left as in the source when on, severities and scopes included: it reports nothing of its own.
                if (!grazieRunnerEnabled) {
                    toolList.isEnabled = false
                }
                continue
            }
            val states = toolList.tools
            for (state in states) {
                if (state.level.severity.name !in severityNames) {
                    state.isEnabled = false
                }
            }
            if (states.none { it.isEnabled }) {
                toolList.isEnabled = false
            }
        }
    }
}

/** Whether these tools stay on in a copy of their profile filtered to [severityNames], without making the copy. */
internal fun Tools.isKeptBy(severityNames: Set<String>): Boolean =
    isEnabled && tools.any { it.isEnabled && it.level.severity.name in severityNames }
