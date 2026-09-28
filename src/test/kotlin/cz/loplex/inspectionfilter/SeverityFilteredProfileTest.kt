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
}
