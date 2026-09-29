package cz.loplex.inspectionfilter

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.search.scope.TestsScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SeverityFilteredProfileTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Without it a unit-test run leaves a profile's tools uninitialized, and the profile empty.
        InspectionProfileImpl.INIT_INSPECTIONS = true
    }

    override fun tearDown() {
        try {
            InspectionProfileImpl.INIT_INSPECTIONS = false
        }
        finally {
            super.tearDown()
        }
    }

    fun testInspectionsOfOtherSeveritiesAreSwitchedOff() {
        val source = profileOf(AnError(), AWarning(), AWeakWarning())

        val filtered = SeverityFilteredProfile(source, setOf(HighlightSeverity.ERROR.name), project)

        assertEquals(listOf("AnError"), enabledToolNames(filtered))
    }

    fun testAnInspectionStaysOnInTheScopesWhereItsSeverityIsWanted() {
        val inspection = AWeakWarning()
        val source = profileOf(inspection)
        source.addScope(source.getInspectionTool(inspection.shortName, project)!!, TestsScope.INSTANCE,
                        HighlightDisplayLevel.WARNING, true, project)

        val filtered = SeverityFilteredProfile(source, setOf(HighlightSeverity.WARNING.name), project)

        val tools = filtered.getTools(inspection.shortName, project)
        assertTrue(tools.isEnabled)
        assertEquals(
            mapOf(TestsScope.INSTANCE.scopeId to true, null to false),
            tools.tools.associate { (if (it === tools.defaultState) null else it.scopeName) to it.isEnabled },
        )
    }

    fun testTheSourceProfileIsLeftAsItWas() {
        val source = profileOf(AnError(), AWarning())

        SeverityFilteredProfile(source, setOf(HighlightSeverity.ERROR.name), project)

        assertEquals(listOf("AWarning", "AnError"), enabledToolNames(source))
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

    fun testBefore261TheGrazieRunnerIsSwitchedOffWithNoneOfItsChildren() {
        val source = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())

        val filtered = SeverityFilteredProfile(source, setOf(HighlightSeverity.WARNING.name), project, BUGGY_BUILD)

        assertEquals(emptyList<String>(), enabledToolNames(filtered))
    }

    fun testBefore261TheGrazieRunnerRunsForAChildWhateverItsOwnSeverity() {
        val source = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())

        val filtered = SeverityFilteredProfile(source, setOf(severityOf(source, "GrazieInspection")), project, BUGGY_BUILD)

        assertEquals(listOf("GrazieInspection", "GrazieInspectionRunner"), enabledToolNames(filtered))
    }

    fun testBefore261AGrazieRunnerSwitchedOffInTheSourceStaysOff() {
        val source = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())
        source.getToolsOrNull(GRAZIE_RUNNER, project)!!.isEnabled = false

        val grammar = severityOf(source, "GrazieInspection")

        assertEquals(listOf("GrazieInspection"),
                     enabledToolNames(SeverityFilteredProfile(source, setOf(grammar), project, BUGGY_BUILD)))
        assertFalse(breaksGrazieRunner(source, source, grammar, build = BUGGY_BUILD))
    }

    fun testFrom261TheGrazieRunnerIsFilteredBySeverityLikeAnyOther() {
        val source = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())

        assertEquals(listOf("GrazieInspection"),
                     enabledToolNames(SeverityFilteredProfile(source, setOf(severityOf(source, "GrazieInspection")), project,
                                                              FIXED_BUILD)))
        assertEquals(listOf("GrazieInspectionRunner"),
                     enabledToolNames(SeverityFilteredProfile(source, setOf(HighlightSeverity.WARNING.name), project, FIXED_BUILD)))
    }

    fun testBefore261ChoosingOneGrazieChildWithoutTheOtherBreaksTheRun() {
        val source = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())

        val grammar = severityOf(source, "GrazieInspection")
        val style = severityOf(source, "GrazieStyle")

        assertTrue(breaksGrazieRunner(source, source, grammar, build = BUGGY_BUILD))
        assertFalse(breaksGrazieRunner(source, source, grammar, style, build = BUGGY_BUILD))
        // Neither child: the runner does not run.
        assertFalse(breaksGrazieRunner(source, source, HighlightSeverity.WARNING.name, build = BUGGY_BUILD))
        assertFalse(breaksGrazieRunner(source, source, grammar, build = FIXED_BUILD))
    }

    fun testWhetherTheGrazieRunnerBreaksTheRunIsDecidedByTheProjectProfile() {
        val chosen = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())
        val projectProfile = profileOf(GrazieInspectionRunner(), GrazieInspection(), GrazieStyle())
        val grammar = severityOf(chosen, "GrazieInspection")

        // The runner reports only under the children that are on in the project's profile.
        projectProfile.getToolsOrNull("GrazieStyle", project)!!.isEnabled = false
        assertFalse(breaksGrazieRunner(chosen, projectProfile, grammar, build = BUGGY_BUILD))

        // And under those whatever the chosen profile says.
        chosen.getToolsOrNull("GrazieStyle", project)!!.isEnabled = false
        projectProfile.getToolsOrNull("GrazieStyle", project)!!.isEnabled = true
        assertTrue(breaksGrazieRunner(chosen, projectProfile, grammar, build = BUGGY_BUILD))
    }

    fun testWithoutGrazieTheWorkaroundDoesNothing() {
        val source = profileOf(AnError(), AWarning())

        assertEquals(listOf("AnError"),
                     enabledToolNames(SeverityFilteredProfile(source, setOf(HighlightSeverity.ERROR.name), project, BUGGY_BUILD)))
        assertFalse(breaksGrazieRunner(source, source, HighlightSeverity.ERROR.name, build = BUGGY_BUILD))
    }

    private fun breaksGrazieRunner(
        profile: InspectionProfileImpl,
        projectProfile: InspectionProfileImpl,
        vararg severityNames: String,
        build: Int,
    ): Boolean = filterBreaksGrazieRunner(profile, projectProfile, severityNames.toSet(), project, build)

    private fun severityOf(profile: InspectionProfileImpl, shortName: String): String =
        profile.getToolsOrNull(shortName, project)!!.level.severity.name

    private fun profileOf(vararg inspections: LocalInspectionTool): InspectionProfileImpl {
        val supplier = object : InspectionToolsSupplier() {
            override fun createTools(): List<InspectionToolWrapper<*, *>> = inspections.map { LocalInspectionToolWrapper(it) }
        }
        val profile = InspectionProfileImpl("Source", supplier, InspectionProfileManager.getInstance() as BaseInspectionProfileManager)
        profile.initInspectionTools(project)
        return profile
    }

    private fun enabledToolNames(profile: InspectionProfileImpl): List<String> =
        profile.getAllEnabledInspectionTools(project).map { it.tool.shortName }.sorted()

    // One class per inspection, each with a constructor taking nothing: copying a profile copies its inspections by
    // instantiating their classes anew, the way the platform instantiates those registered in plugin.xml.
    private abstract class TestInspection(private val level: HighlightDisplayLevel) : LocalInspectionTool() {
        override fun getShortName(): String = javaClass.simpleName
        override fun getDisplayName(): String = shortName
        override fun getGroupDisplayName(): String = "Test"
        override fun getDefaultLevel(): HighlightDisplayLevel = level
        override fun isEnabledByDefault(): Boolean = true
    }

    private class AnError : TestInspection(HighlightDisplayLevel.ERROR)
    private class AWarning : TestInspection(HighlightDisplayLevel.WARNING)
    private class AWeakWarning : TestInspection(HighlightDisplayLevel.WEAK_WARNING)

    // Named as Grazie names them. A wrapper takes the level registered under its inspection's name, so where Grazie is
    // loaded, as it is in the IDE the tests run in, these get Grazie's own levels; the tests therefore read the
    // children's severities from the profile. Either way the runner is a warning and each child is something else.
    private class GrazieInspectionRunner : TestInspection(HighlightDisplayLevel.WARNING)
    private class GrazieInspection : TestInspection(HighlightDisplayLevel.ERROR)
    private class GrazieStyle : TestInspection(HighlightDisplayLevel.WEAK_WARNING)

    private companion object {
        const val BUGGY_BUILD = 253
        const val FIXED_BUILD = 261
    }
}
