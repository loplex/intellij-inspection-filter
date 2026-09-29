package cz.loplex.inspectionfilter

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProblemFilterTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            forgetStoredFilter(project)
        }
        finally {
            super.tearDown()
        }
    }

    fun testWithNoConditionNothingIsLeftOut() {
        assertFalse(ProblemFilter().isActive)
        assertFalse(ProblemFilter(messageContains = "  ").isActive)
        assertTrue(ProblemFilter().accepts(problem("Anything")))
    }

    fun testOnlyAProblemWithAQuickFixIsKeptWhenOneIsRequired() {
        val filter = ProblemFilter(withQuickFix = true)

        assertTrue(filter.accepts(problem("Fixable", NoFix())))
        assertFalse(filter.accepts(problem("Not fixable")))
    }

    fun testTheMessageIsSearchedIgnoringCase() {
        val filter = ProblemFilter(messageContains = "APPLE")

        assertTrue(filter.accepts(problem("An apple a day")))
        assertFalse(filter.accepts(problem("A pear a day")))
    }

    fun testTheMessageIsSearchedAsTheResultsShowIt() {
        // The code the problem is about in place of #ref, and nothing after #treeend, which only the tooltip shows.
        val problem = problem("Found #ref here#treeend, and more")

        assertEquals("Found Apple pie here", messageOf(problem))
        assertTrue(ProblemFilter(messageContains = "found apple pie").accepts(problem))
        assertFalse(ProblemFilter(messageContains = "more").accepts(problem))
    }

    fun testTheFilterStoredIsTheOneLoaded() {
        val filter = ProblemFilter(withQuickFix = true, messageContains = "apple")

        storeProblemFilter(project, filter)

        assertEquals(filter, loadProblemFilter(project))
    }

    fun testWithNothingStoredNothingIsLeftOut() {
        assertEquals(ProblemFilter(), loadProblemFilter(project))
    }

    private fun problem(message: String, vararg fixes: LocalQuickFix): ProblemDescriptor {
        val file = myFixture.configureByText("a.txt", "Apple pie")
        return InspectionManager.getInstance(project).createProblemDescriptor(file, message, false, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }

    private class NoFix : LocalQuickFix {
        override fun getFamilyName(): String = "Do nothing"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}
    }
}
