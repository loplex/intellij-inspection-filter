package cz.loplex.inspectionfilter

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
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
 * Like the profile "Run Inspection by Name" builds for its run, the copy lives until the project is closed: the
 * inspection context never releases the profile it was given.
 */
internal class SeverityFilteredProfile(
    source: InspectionProfileImpl,
    val severityNames: Set<String>,
    project: Project,
) : InspectionProfileModifiableModel(source) {

    init {
        initInspectionTools(project)
        for (toolList in tools) {
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
