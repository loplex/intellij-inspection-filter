package cz.loplex.inspectionfilter

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ui.LevelChooserAction

private const val SELECTED_SEVERITIES_KEY = "cz.loplex.inspectionfilter.selectedSeverities"

/** Chosen when nothing has been chosen yet, or when none of what was chosen exists any more. */
private val DEFAULT_SEVERITY_NAMES = setOf(HighlightSeverity.ERROR.name, HighlightSeverity.WARNING.name)

/**
 * The severities an inspection can be given, in the order the inspection settings list them, custom ones included.
 *
 * "No highlighting" is left out: a batch run skips inspections at that level whatever the profile says, so offering
 * it would offer nothing.
 */
internal fun availableSeverities(project: Project): List<HighlightSeverity> =
    LevelChooserAction.getSeverities(SeverityRegistrar.getSeverityRegistrar(project), false)

/**
 * The names of the severities last chosen in [project], among those that still exist.
 *
 * Kept per project, because custom severities can be defined per project too.
 */
internal fun loadSelectedSeverityNames(project: Project): Set<String> {
    val available = availableSeverities(project).mapTo(HashSet()) { it.name }
    val stored = PropertiesComponent.getInstance(project).getList(SELECTED_SEVERITIES_KEY).orEmpty()
    return stored.filterTo(LinkedHashSet()) { it in available }
        .ifEmpty { DEFAULT_SEVERITY_NAMES.filterTo(LinkedHashSet()) { it in available } }
}

internal fun storeSelectedSeverityNames(project: Project, names: Collection<String>) {
    PropertiesComponent.getInstance(project).setList(SELECTED_SEVERITIES_KEY, names)
}
