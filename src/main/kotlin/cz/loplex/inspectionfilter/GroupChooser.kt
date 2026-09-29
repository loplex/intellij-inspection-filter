package cz.loplex.inspectionfilter

import com.intellij.CommonBundle
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultTreeModel

/** How many groups the summary names before it says only how many more there are; the tooltip names them all. */
private const val GROUPS_NAMED_IN_SUMMARY = 3

/**
 * The dialog's choice of the groups of inspections to report: one line that sums the choice up, and a button that
 * opens the groups as a tree with a search field above it.
 *
 * A node stands for the inspections listed directly under its group, and ticking it ticks every group under it; a
 * group whose own inspections and subgroups differ shows as partly ticked. The choice itself is a [GroupSelection],
 * stored at once on every change; the tree only shows it, since the search hides some of its nodes.
 */
internal class GroupChooser(private val project: Project) {

    /** The groups currently chosen. */
    var selection: GroupSelection = loadGroupSelection(project)
        private set

    /** The groups on offer: those of the profile the run is to use. */
    private var roots: List<GroupNode> = emptyList()

    private val summary = JBLabel()
    private val changeListeners = mutableListOf<() -> Unit>()

    val component: JComponent = panel {
        row {
            cell(summary)
            button(InspectionFilterBundle.message("dialog.groups.choose")) { event ->
                showPopup(event.source as JButton)
            }
        }
    }.apply { isOpaque = false }

    /** Calls [listener] whenever [selection] has changed. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    /** Offers the groups of [profile], the profile the run is to use. */
    fun setProfile(profile: InspectionProfileImpl) {
        roots = groupTreesOf(profile, project)
        updateSummary()
    }

    private fun select(changed: GroupSelection) {
        selection = changed
        storeGroupSelection(project, changed)
        updateSummary()
        changeListeners.forEach { it() }
    }

    private fun updateSummary() {
        val (included, excluded) = selection.summary(roots)
        val (key, parts) = when {
            excluded.isEmpty() -> null to emptyList()
            included.isEmpty() -> "dialog.groups.none" to emptyList()
            included.size < excluded.size -> "dialog.groups.only" to included
            else -> "dialog.groups.allExcept" to excluded
        }
        val names = parts.map(::partName)
        val named = names.take(GROUPS_NAMED_IN_SUMMARY).joinToString(", ")
        val listed = if (names.size > GROUPS_NAMED_IN_SUMMARY) {
            InspectionFilterBundle.message("dialog.groups.more", named, names.size - GROUPS_NAMED_IN_SUMMARY)
        }
        else {
            named
        }
        summary.text = InspectionFilterBundle.message(key ?: "dialog.groups.all", listed)
        // Allowed, as "Uncheck All" and ticking one is the quick way to a single group, but a run would report nothing.
        summary.icon = if (roots.isEmpty() || selection.includesAny(roots)) null else AllIcons.General.Warning
        summary.toolTipText = if (names.size > GROUPS_NAMED_IN_SUMMARY) {
            HtmlChunk.html().children(names.flatMap { listOf(HtmlChunk.text(it), HtmlChunk.br()) }.dropLast(1)).toString()
        }
        else {
            null
        }
    }

    private fun showPopup(owner: JComponent) {
        val rootNode = CheckedTreeNode(null)
        val tree = CheckboxTree(GroupRenderer(), rootNode, CheckboxTreeBase.CheckPolicy(false, false, false, false)).apply {
            isRootVisible = false
            showsRootHandles = true
        }
        val search = SearchTextField(false)
        var syncing = false

        fun showSelection() {
            syncing = true
            try {
                TreeUtil.treeNodeTraverser(rootNode).filter(CheckedTreeNode::class.java).forEach { node ->
                    (node.userObject as? GroupNode)?.let { node.isChecked = selection.includes(it.path) }
                }
            }
            finally {
                syncing = false
            }
            tree.repaint()
        }

        fun showGroups() {
            val text = search.text.trim()
            rootNode.removeAllChildren()
            roots.forEach { addMatching(rootNode, it, text, ancestorMatches = false) }
            (tree.model as DefaultTreeModel).reload()
            showSelection()
            if (text.isNotEmpty()) {
                TreeUtil.expandAll(tree)
            }
        }

        tree.addCheckboxTreeListener(object : CheckboxTreeListener {
            override fun nodeStateChanged(node: CheckedTreeNode) {
                if (syncing) return
                val group = node.userObject as? GroupNode ?: return
                select(selection.with(group, node.isChecked))
                // The whole subtree, and groups above it, may have changed with it.
                showSelection()
            }
        })

        // On the groups the search has found, so that a search can pick them out: all of them when there is none.
        fun checkFound(included: Boolean) {
            select(foundGroups(roots, search.text.trim()).fold(selection) { changed, group -> changed.with(group, included) })
            showSelection()
        }

        val checkAll = ActionLink(InspectionFilterBundle.message("dialog.groups.checkAll")) { checkFound(true) }
            .apply { icon = AllIcons.Actions.Selectall }
        val uncheckAll = ActionLink(InspectionFilterBundle.message("dialog.groups.uncheckAll")) { checkFound(false) }
            .apply { icon = AllIcons.Actions.Unselectall }
        val ok = JButton(CommonBundle.getOkButtonText()).apply {
            // As high as the links beside it rather than as a dialog's buttons; what the platform's own popups set.
            putClientProperty("ActionToolbar.smallVariant", true)
        }
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = showGroups()
        })
        showGroups()

        val content = JPanel(BorderLayout()).apply {
            add(search, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(tree).apply { preferredSize = JBUI.size(360, 400) }, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4)
                add(JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(12), 0)).apply {
                    add(checkAll)
                    add(uncheckAll)
                }, BorderLayout.WEST)
                add(ok, BorderLayout.EAST)
            }, BorderLayout.SOUTH)
        }
        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, search.textEditor)
            .setRequestFocus(true)
            .setResizable(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .createPopup()
        // Only closes: every change has been applied, and stored, as it was made.
        ok.addActionListener { popup.closeOk(null) }
        popup.showUnderneathOf(owner)
    }
}

/**
 * Adds [group] under [parent] if it is to be shown for the search [text]: when its name or a name above it matches, so
 * that a match shows all it holds, or when a name below it does, so that the match can be reached.
 */
private fun addMatching(parent: CheckedTreeNode, group: GroupNode, text: String, ancestorMatches: Boolean) {
    fun matches(node: GroupNode) = node.name.contains(text, ignoreCase = true)
    val shown = ancestorMatches || matches(group)
    if (!shown && group.selfAndDescendants().none(::matches)) return
    val node = CheckedTreeNode(group)
    parent.add(node)
    group.children.forEach { addMatching(node, it, text, shown) }
}

/** A group, or only its own inspections, as the summary names it: `Java › Probable bugs`. */
private fun partName(part: GroupSelection.Part): String {
    val name = part.path.joinToString(" › ")
    return if (part.whole) name else InspectionFilterBundle.message("dialog.groups.ownInspections", name)
}

private class GroupRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
    override fun customizeRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val group = (value as? CheckedTreeNode)?.userObject as? GroupNode ?: return
        textRenderer.append(group.name)
    }
}
