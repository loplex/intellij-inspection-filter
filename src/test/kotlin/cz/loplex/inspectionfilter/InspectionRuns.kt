package cz.loplex.inspectionfilter

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.ui.UIUtil

/**
 * Runs the inspections of [profile] over the whole of [project] the way "Inspect Code" runs them, and passes what
 * they reported to [read] before the context lets go of it.
 */
internal fun <T> runInspections(project: Project, profile: InspectionProfileImpl, read: (GlobalInspectionContextImpl) -> T): T {
    val context = (InspectionManager.getInstance(project) as InspectionManagerEx).createNewGlobalContext()
    try {
        context.setExternalProfile(profile)
        runAndWait(context, AnalysisScope(project))
        return read(context)
    }
    finally {
        context.cleanup()
    }
}

/** Runs [context] over [scope], as "Rerun" in the results runs it again, and waits for the run to end. */
internal fun runAndWait(context: GlobalInspectionContextImpl, scope: AnalysisScope) {
    IndexingTestUtil.waitUntilIndexesAreReady(context.project)
    context.doInspections(scope)
    // In a unit test the run is launched on the event queue, and then runs to its end without leaving it.
    UIUtil.dispatchAllInvocationEvents()
}

/** Every problem [context]'s last run reported, with the inspection that reported it. */
internal fun problemsOf(context: GlobalInspectionContextImpl): List<Pair<InspectionToolWrapper<*, *>, CommonProblemDescriptor>> =
    // An inspection with another severity in a scope has a tool of its own there, which reports on its own.
    context.tools.values.flatMap { tools -> tools.tools.map { it.tool } }.distinct().flatMap { wrapper ->
        context.getPresentation(wrapper).problemDescriptors.map { wrapper to it }
    }
