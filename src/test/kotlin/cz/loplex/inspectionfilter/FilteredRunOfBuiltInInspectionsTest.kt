package cz.loplex.inspectionfilter

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Runs "Inspect Code" with every inspection the IDE the tests run in registers - Java's among them - over a Java
 * file, as a check that the properties the filter asks of an inspection hold for inspections as they are registered,
 * and not only for those the other tests make up.
 *
 * The findings of a filtered run are compared with those of an unfiltered run of the inspections with the property.
 * Two inspections known to report on the file anchor the comparison: one with the property, which the filtered run
 * must report, and one without it, which it must not.
 */
class FilteredRunOfBuiltInInspectionsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Without it a unit-test run leaves a profile's tools uninitialized, and the profile empty.
        InspectionProfileImpl.INIT_INSPECTIONS = true
        myFixture.addFileToProject("Foo.java", """
            public final class Foo {
                private int written;

                public Foo() { written = 1; }

                public Foo(int value) { written = value; }

                int read() { return written; }

                boolean pointless(boolean value) { return value && true; }
            }
        """.trimIndent())
    }

    override fun tearDown() {
        try {
            InspectionProfileImpl.INIT_INSPECTIONS = false
        }
        finally {
            super.tearDown()
        }
    }

    fun testARunReportsOnlyBatchModeInspectionsWhenAsked() {
        // "Field can be final" is a global inspection with no counterpart in the editor.
        checkFilteredBy(InspectionProperty.BATCH_ONLY, kept = "CanBeFinal", leftOut = "PointlessBooleanExpression") {
            (it as? GlobalInspectionToolWrapper)?.worksInBatchModeOnly() == true
        }
    }

    fun testARunReportsOnlyCleanupInspectionsWhenAsked() {
        checkFilteredBy(InspectionProperty.CLEANUP, kept = "PointlessBooleanExpression", leftOut = "CanBeFinal") {
            it.isCleanupTool
        }
    }

    private fun checkFilteredBy(
        property: InspectionProperty,
        kept: String,
        leftOut: String,
        hasProperty: (InspectionToolWrapper<*, *>) -> Boolean,
    ) {
        // Every inspection registered, as it is by default.
        val source = InspectionProfileImpl("Built-in")
        val unfiltered = runInspections(project, source, ::problemsByInspection)
        assertContainsElements(unfiltered.keys, kept, leftOut)

        val allSeverities = availableSeverities(project).mapTo(HashSet()) { it.name }
        val filtered = runInspections(project, FilteredProfile(source, InspectionFilter(allSeverities, requiredProperties = setOf(property)),
                                                               project), ::problemsByInspection)

        assertEquals(unfiltered.filterKeys { hasProperty(source.getInspectionTool(it, project)!!) }, filtered)
        assertContainsElements(filtered.keys, kept)
        assertDoesntContain(filtered.keys, leftOut)
    }

    /** The problems [context]'s last run reported, as "line: description", by inspection. */
    private fun problemsByInspection(context: GlobalInspectionContextImpl): Map<String, List<String>> =
        problemsOf(context).groupBy({ (wrapper, _) -> wrapper.shortName }, { (_, problem) -> describe(problem) })
            .mapValues { it.value.sorted() }

    private fun describe(problem: CommonProblemDescriptor): String =
        "${(problem as? ProblemDescriptor)?.let { it.lineNumber + 1 }}: ${problem.descriptionTemplate}"
}
