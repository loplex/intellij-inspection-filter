package cz.loplex.inspectionfilter

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project

private const val WITH_QUICK_FIX_KEY = "cz.loplex.inspectionfilter.problems.withQuickFix"
private const val MESSAGE_CONTAINS_KEY = "cz.loplex.inspectionfilter.problems.messageContains"

/** Every key the filter of problems is stored under in a project's [PropertiesComponent]. */
internal val PROBLEM_FILTER_KEYS = listOf(WITH_QUICK_FIX_KEY, MESSAGE_CONTAINS_KEY)

/**
 * Which of the problems the inspections report are reported: those with a quick fix, if [withQuickFix], and those
 * whose message contains [messageContains], ignoring case, if it is not blank.
 *
 * Unlike the rest of an [InspectionFilter], this one decides problem by problem rather than inspection by inspection -
 * see [wrapForProblemFilter].
 */
internal data class ProblemFilter(val withQuickFix: Boolean = false, val messageContains: String = "") {

    /** Whether it leaves out any problem at all. */
    val isActive: Boolean get() = withQuickFix || messageContains.isNotBlank()

    fun accepts(problem: CommonProblemDescriptor): Boolean =
        (!withQuickFix || !problem.fixes.isNullOrEmpty()) &&
        (messageContains.isBlank() || messageOf(problem).contains(messageContains, ignoreCase = true))
}

/**
 * The message of [problem] as the results of "Inspect Code" show it: with the code it is about in place of `#ref`,
 * and neither markup nor the part meant for the tooltip only.
 */
internal fun messageOf(problem: CommonProblemDescriptor): String = ReadAction.compute<String, RuntimeException> {
    ProblemDescriptorUtil.renderDescriptionMessage(problem, (problem as? ProblemDescriptor)?.psiElement,
                                                   ProblemDescriptorUtil.TRIM_AT_TREE_END)
}

// Kept per project, like the rest of the filter.

/** The filter of problems last chosen in [project]. */
internal fun loadProblemFilter(project: Project): ProblemFilter {
    val properties = PropertiesComponent.getInstance(project)
    return ProblemFilter(properties.getBoolean(WITH_QUICK_FIX_KEY), properties.getValue(MESSAGE_CONTAINS_KEY).orEmpty())
}

internal fun storeProblemFilter(project: Project, filter: ProblemFilter) {
    val properties = PropertiesComponent.getInstance(project)
    properties.setValue(WITH_QUICK_FIX_KEY, filter.withQuickFix)
    properties.setValue(MESSAGE_CONTAINS_KEY, filter.messageContains, "")
}
