package cz.loplex.inspectionfilter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GroupSelectionTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            forgetStoredFilter(project)
        }
        finally {
            super.tearDown()
        }
    }

    // Java has inspections of its own and two subgroups; Proofreading has none of its own.
    private val roots = GroupNode.treesOf(listOf(
        listOf("Java"),
        listOf("Java", "Probable bugs"),
        listOf("Java", "Code style issues"),
        listOf("Proofreading", "Grammar"),
        listOf("Proofreading", "Spelling"),
        listOf("Markdown"),
    ))

    private fun node(vararg path: String): GroupNode =
        roots.flatMap { it.selfAndDescendants().toList() }.single { it.path == path.asList() }

    fun testTheTreesAreSortedByNameAndKnowWhichGroupsHaveInspectionsOfTheirOwn() {
        assertEquals(listOf("Java", "Markdown", "Proofreading"), roots.map { it.name })
        assertEquals(listOf("Code style issues", "Probable bugs"), node("Java").children.map { it.name })
        assertTrue(node("Java").hasOwnInspections)
        assertFalse(node("Proofreading").hasOwnInspections)
    }

    fun testEverythingIsIncludedUntilSomethingIsLeftOut() {
        val selection = GroupSelection()

        assertTrue(selection.includes(listOf("Java", "Probable bugs")))
        assertEquals(GroupSelection.Summary(roots.map { GroupSelection.Part(it.path, whole = true) }, emptyList()),
                     selection.summary(roots))
    }

    fun testLeavingOutAGroupLeavesOutEverythingUnderIt() {
        val selection = GroupSelection().with(node("Java"), false)

        assertFalse(selection.includes(listOf("Java")))
        assertFalse(selection.includes(listOf("Java", "Probable bugs")))
        // Also a subgroup the profile at hand does not have.
        assertFalse(selection.includes(listOf("Java", "Some future group")))
        assertTrue(selection.includes(listOf("Markdown")))
        assertEquals(mapOf(listOf("Java") to false), selection.overrides)
    }

    fun testIncludingAGroupAgainClearsWhatWasChosenUnderIt() {
        val selection = GroupSelection()
            .with(node("Java", "Probable bugs"), false)
            .with(node("Java"), false)
            .with(node("Java"), true)

        assertEquals(GroupSelection(), selection)
    }

    fun testAGroupWithInspectionsOfItsOwnKeepsThemApartFromItsSubgroups() {
        val selection = GroupSelection()
            .with(node("Java"), false)
            .with(node("Java", "Probable bugs"), true)
            .with(node("Java", "Code style issues"), true)

        // Every subgroup is back, but the inspections listed directly under Java stay out.
        assertFalse(selection.includes(listOf("Java")))
        assertTrue(selection.includes(listOf("Java", "Probable bugs")))
        assertEquals(listOf(GroupSelection.Part(listOf("Java"), whole = false)), selection.summary(roots).excluded)
    }

    fun testAGroupWithNoInspectionsOfItsOwnFollowsItsSubgroups() {
        val selection = GroupSelection()
            .with(node("Proofreading", "Grammar"), false)
            .with(node("Proofreading", "Spelling"), false)

        // Stored as the group itself, so that a subgroup added to it later is left out too.
        assertEquals(mapOf(listOf("Proofreading") to false), selection.overrides)
        assertEquals(listOf(GroupSelection.Part(listOf("Proofreading"), whole = true)), selection.summary(roots).excluded)

        val includedAgain = selection.with(node("Proofreading", "Grammar"), true)
            .with(node("Proofreading", "Spelling"), true)

        assertEquals(GroupSelection(), includedAgain)
    }

    fun testTheSummaryNamesTheLargestGroupsWhollyInOrOut() {
        val summary = GroupSelection().with(node("Java", "Code style issues"), false).summary(roots)

        assertEquals(listOf(listOf("Java", "Code style issues")), summary.excluded.map { it.path })
        assertEquals(listOf(listOf("Java"), listOf("Java", "Probable bugs"), listOf("Markdown"), listOf("Proofreading")),
                     summary.included.map { it.path })
        assertEquals(listOf(false, true, true, true), summary.included.map { it.whole })
    }

    fun testWhetherAnythingIsIncludedCountsOnlyGroupsWithInspections() {
        val allOut = roots.fold(GroupSelection()) { selection, root -> selection.with(root, false) }

        assertFalse(allOut.includesAny(roots))
        assertTrue(allOut.with(node("Proofreading", "Spelling"), true).includesAny(roots))
    }

    fun testTheSearchFindsTheHighestGroupsThatMatch() {
        assertEquals(roots, foundGroups(roots, ""))
        // Not "Proofreading" as well: it is found only through its subgroup.
        assertEquals(listOf(node("Java", "Probable bugs")), foundGroups(roots, "bug"))
        assertEquals(listOf(node("Java")), foundGroups(roots, "JAVA"))
    }

    fun testUncheckingAllAndCheckingOneLeavesOnlyThatOne() {
        val found = foundGroups(roots, "gram")
        val selection = foundGroups(roots, "").fold(GroupSelection()) { changed, group -> changed.with(group, false) }
            .let { allOut -> found.fold(allOut) { changed, group -> changed.with(group, true) } }

        assertEquals(listOf(GroupSelection.Part(listOf("Proofreading", "Grammar"), whole = true)),
                     selection.summary(roots).included)
    }

    fun testTheChoiceIsKeptPerProject() {
        val selection = GroupSelection().with(node("Java"), false).with(node("Java", "Probable bugs"), true)

        storeGroupSelection(project, selection)

        assertEquals(selection, loadGroupSelection(project))
    }

    fun testAPathIsStoredWhateverItsNamesHold() {
        val path = listOf("A/B", "C\\D", "", "E")

        assertEquals(path to false, decodeOverride(encodeOverride(path, false)))
        assertEquals(listOf("Java") to true, decodeOverride(encodeOverride(listOf("Java"), true)))
        assertNull(decodeOverride("Java"))
        assertNull(decodeOverride("+Java\\"))
    }
}
