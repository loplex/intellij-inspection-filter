package cz.loplex.inspectionfilter

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SeveritySelectionTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            forgetStoredFilter(project)
        }
        finally {
            super.tearDown()
        }
    }

    fun testErrorsAndWarningsAreChosenUntilSomethingElseIs() {
        assertEquals(setOf(HighlightSeverity.ERROR.name, HighlightSeverity.WARNING.name),
                     loadSelectedSeverityNames(project))

        storeSelectedSeverityNames(project, listOf(HighlightSeverity.WEAK_WARNING.name))

        assertEquals(setOf(HighlightSeverity.WEAK_WARNING.name), loadSelectedSeverityNames(project))
    }

    fun testAChoiceOfSeveritiesThatNoLongerExistFallsBackToTheDefault() {
        storeSelectedSeverityNames(project, listOf("A severity since removed"))

        assertEquals(setOf(HighlightSeverity.ERROR.name, HighlightSeverity.WARNING.name),
                     loadSelectedSeverityNames(project))
    }

    fun testTheSelectedSeveritiesAreReportedUntilAnotherModeIsChosen() {
        storeSelectedSeverityNames(project, listOf(HighlightSeverity.WEAK_WARNING.name))

        assertEquals(SeverityMode.SELECTED, loadSeverityMode(project))
        assertEquals(setOf(HighlightSeverity.WEAK_WARNING.name), loadReportedSeverityNames(project))
    }

    fun testThisAndHigherReportsTheChosenSeverityAndThoseAboveIt() {
        storeSeverityMode(project, SeverityMode.THIS_AND_HIGHER)
        storeLowestSeverityName(project, HighlightSeverity.WARNING.name)

        val reported = loadReportedSeverityNames(project)

        assertTrue(reported.containsAll(setOf(HighlightSeverity.ERROR.name, HighlightSeverity.WARNING.name)))
        assertFalse(HighlightSeverity.WEAK_WARNING.name in reported)
    }

    fun testSwitchingBetweenModesKeepsWhatWasSelected() {
        storeSelectedSeverityNames(project, listOf(HighlightSeverity.WEAK_WARNING.name))
        storeSeverityMode(project, SeverityMode.THIS_AND_HIGHER)
        storeLowestSeverityName(project, HighlightSeverity.ERROR.name)
        assertFalse(HighlightSeverity.WEAK_WARNING.name in loadReportedSeverityNames(project))

        storeSeverityMode(project, SeverityMode.SELECTED)

        assertEquals(setOf(HighlightSeverity.WEAK_WARNING.name), loadReportedSeverityNames(project))
    }

    fun testTheChooserIsBuiltWithWhatIsStored() {
        storeSeverityMode(project, SeverityMode.THIS_AND_HIGHER)
        storeLowestSeverityName(project, HighlightSeverity.WARNING.name)

        val chooser = SeverityChooser(project)

        assertEquals(loadReportedSeverityNames(project), chooser.reportedSeverityNames)
    }

    fun testALowestSeverityThatNoLongerExistsFallsBackToWarning() {
        storeLowestSeverityName(project, "A severity since removed")

        assertEquals(HighlightSeverity.WARNING.name, loadLowestSeverityName(project))
    }
}
