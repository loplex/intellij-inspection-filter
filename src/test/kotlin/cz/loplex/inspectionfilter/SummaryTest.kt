package cz.loplex.inspectionfilter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SummaryTest : BasePlatformTestCase() {

    fun testAFewNamesAreAllGivenWithNoTooltip() {
        assertEquals(Summary("A, B, C"), summaryOf(listOf("A", "B", "C")))
    }

    fun testManyNamesAreCountedAndAllGivenInTheTooltip() {
        val summary = summaryOf(listOf("A", "B", "C", "D", "E"))

        assertEquals("A, B, C and 2 more", summary.text)
        assertEquals(linesOf(listOf("A", "B", "C", "D", "E")), summary.tooltip)
    }

    fun testNoPropertyRequiredMeansAllInspections() {
        assertEquals(Summary("All inspections"), propertySummary(emptySet()))
    }

    fun testThePropertiesRequiredAreNamedInTheirOwnOrder() {
        val summary = propertySummary(setOf(InspectionProperty.BATCH_ONLY, InspectionProperty.CLEANUP))

        assertEquals("Cleanup, batch-mode only", summary.text)
        assertNotNull(summary.tooltip)
    }
}
