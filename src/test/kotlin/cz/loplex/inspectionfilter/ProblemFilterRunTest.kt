package cz.loplex.inspectionfilter

import com.intellij.analysis.AnalysisScope
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.GlobalSimpleInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptionsProcessor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Runs the action for real over two files, with a filter of problems, and compares what it reports with what the
 * filter keeps - for each of the three ways an inspection can report: a local one through a visitor, an external one
 * such as ShellCheck with `checkFile`, and a global one to the run.
 *
 * The local and the external inspection report the same three problems in each file, told apart by their message:
 * "apple", "banana" - the one with a quick fix - and "cherry". The global one reports "apple" and "cherry" in a.txt,
 * and "banana", with a quick fix, in b.txt.
 */
class ProblemFilterRunTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Without it a unit-test run leaves a profile's tools uninitialized, and the profile empty.
        InspectionProfileImpl.INIT_INSPECTIONS = true
        myFixture.addFileToProject("a.txt", "Text of a")
        myFixture.addFileToProject("b.txt", "Text of b")
        useAsProjectProfile(project, newProfile("Source", supplierOf(Local(), External(), Global()), InspectionProfileImpl.BASE_PROFILE.get(),
                                                project), testRootDisposable)
        storeSelectedSeverityNames(project, setOf(HighlightSeverity.ERROR.name))
    }

    override fun tearDown() {
        try {
            InspectionProfileImpl.INIT_INSPECTIONS = false
            forgetStoredFilter(project)
        }
        finally {
            super.tearDown()
        }
    }

    fun testARunWithNoFilterOfProblemsReportsEveryProblem() {
        assertEquals((LOCAL + EXTERNAL + GLOBAL).sorted(), findingsOf(runAction()))
    }

    fun testARunReportsOnlyTheProblemsWithAQuickFix() {
        storeProblemFilter(project, ProblemFilter(withQuickFix = true))

        assertEquals(listOf("Local: banana in a.txt", "Local: banana in b.txt", "External: banana in a.txt", "External: banana in b.txt",
                            "Global: banana in b.txt").sorted(), findingsOf(runAction()))
    }

    fun testARunReportsOnlyTheProblemsWhoseMessageContainsTheTextInAnyCase() {
        storeProblemFilter(project, ProblemFilter(messageContains = "ANA"))

        assertEquals(listOf("Local: banana in a.txt", "Local: banana in b.txt", "External: banana in a.txt", "External: banana in b.txt",
                            "Global: banana in b.txt").sorted(), findingsOf(runAction()))
    }

    fun testARunReportsOnlyTheProblemsBothFiltersKeep() {
        storeProblemFilter(project, ProblemFilter(withQuickFix = true, messageContains = "apple"))

        assertEquals(emptyList<String>(), findingsOf(runAction()))
    }

    fun testTheProblemsOfAGlobalInspectionInOneElementAreKeptTogether() {
        // The run decides for all the problems a global inspection reports in a.txt at once: "cherry" comes with "apple".
        storeProblemFilter(project, ProblemFilter(messageContains = "apple"))

        assertEquals(listOf("Local: apple in a.txt", "Local: apple in b.txt", "External: apple in a.txt", "External: apple in b.txt",
                            "Global: apple in a.txt", "Global: cherry in a.txt").sorted(), findingsOf(runAction()))
    }

    fun testARerunKeepsTheFilterOfProblems() {
        storeProblemFilter(project, ProblemFilter(withQuickFix = true))
        val context = runAction()
        val findings = findingsOf(context)

        // A filter chosen since does not change what a rerun reports.
        storeProblemFilter(project, ProblemFilter())
        runAndWait(context, AnalysisScope(project))

        assertEquals(findings, findingsOf(context))
    }

    /** Runs the action over the whole project, and returns the context it ran in. */
    private fun runAction(): GlobalInspectionContextImpl {
        val contexts = (InspectionManager.getInstance(project) as InspectionManagerEx).runningContexts
        val contextsBefore = contexts.toSet()
        runAction(InspectCodeWithFiltersAction(), project, AnalysisScope(project))
        return (contexts - contextsBefore).single()
    }

    /** The findings of [context]'s last run, as "inspection: message in file", sorted. */
    private fun findingsOf(context: GlobalInspectionContextImpl): List<String> =
        problemsOf(context).map { (wrapper, problem) ->
            "${wrapper.shortName}: ${problem.descriptionTemplate} in ${(problem as ProblemDescriptor).psiElement.containingFile.name}"
        }.sorted()

    private abstract class ErrorInspection : LocalInspectionTool() {
        override fun getShortName(): String = javaClass.simpleName
        override fun getDisplayName(): String = shortName
        override fun getGroupDisplayName(): String = "Test"
        override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
        override fun isEnabledByDefault(): Boolean = true
    }

    private class Local : ErrorInspection() {
        override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                holder.registerProblem(file, "apple")
                holder.registerProblem(file, "banana", NoFix())
                // Not every inspection registers a problem the same way.
                holder.problem(file, "cherry").register()
            }
        }
    }

    /** An external tool, such as ShellCheck, which the run finds by its type, and runs with [checkFile] alone. */
    private class External : ErrorInspection(), ExternalAnnotatorBatchInspection {
        override fun checkFile(file: PsiFile, context: GlobalInspectionContext, manager: InspectionManager): Array<ProblemDescriptor> =
            arrayOf(problem(file, manager, "apple"), problem(file, manager, "banana", NoFix()), problem(file, manager, "cherry"))
    }

    private class Global : GlobalSimpleInspectionTool() {
        override fun getShortName(): String = javaClass.simpleName
        override fun getDisplayName(): String = shortName
        override fun getGroupDisplayName(): String = "Test"
        override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
        override fun isEnabledByDefault(): Boolean = true

        override fun checkFile(
            psiFile: PsiFile,
            manager: InspectionManager,
            problemsHolder: ProblemsHolder,
            globalContext: GlobalInspectionContext,
            problemDescriptionsProcessor: ProblemDescriptionsProcessor,
        ) {
            if (psiFile.name == "a.txt") {
                problemsHolder.registerProblem(psiFile, "apple")
                problemsHolder.registerProblem(psiFile, "cherry")
            }
            else {
                problemsHolder.registerProblem(psiFile, "banana", NoFix())
            }
        }
    }

    private class NoFix : LocalQuickFix {
        override fun getFamilyName(): String = "Do nothing"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}
    }

    private companion object {
        val LOCAL = findings("Local")
        val EXTERNAL = findings("External")
        val GLOBAL = listOf("Global: apple in a.txt", "Global: cherry in a.txt", "Global: banana in b.txt")

        /** What [inspection] reports, when it reports all three problems in both files. */
        fun findings(inspection: String): List<String> =
            listOf("a.txt", "b.txt").flatMap { file -> listOf("apple", "banana", "cherry").map { "$inspection: $it in $file" } }

        fun problem(file: PsiFile, manager: InspectionManager, message: String, vararg fixes: LocalQuickFix): ProblemDescriptor =
            manager.createProblemDescriptor(file, message, false, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
}
