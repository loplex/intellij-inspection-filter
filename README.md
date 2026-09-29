# Inspect Code with Filters

*Inspect Code, reporting only the problems you asked for.*

An IntelliJ Platform plugin that adds **Inspect Code with Filters…** next to **Inspect Code…**.\
It runs the same inspections over the same scope, but reports only those of the severities, groups and kinds of
inspection chosen in its dialog. For example:

- errors and warnings, without the weak warnings and typos around them;
- everything but Proofreading;
- only the batch-mode inspections, which the editor never shows.

The inspections left out are not run at all, so a filtered run is also a shorter one.\
The profile itself is not changed: the run uses a copy of it with the other inspections switched off.

- [Using it](#using-it) — the dialog, what each filter means, how they combine.
- [Known issue in IntelliJ IDEA 2025.3](#known-issue-in-intellij-idea-20253-reporting-only-one-of-grazies-two-checks-fails-the-run)
  — Grazie, and the warning in the dialog.
- [Installing](#installing) — requirements, building the plugin archive.
- [Development](#development) — running, testing and verifying it.
- [License](#license) — AGPL-3.0-or-later.

## Using it

Choose **Code | Inspect Code with Filters…**, or the same item in the Project view's context menu.\
In IntelliJ IDEA it is under **Analyze** there, beside **Inspect Code…**.

The dialog is the one **Inspect Code…** shows, with a **Report Only** group added under the inspection profile.\
Each row sums up one filter; **Choose…** opens it.

| Row         | Reports only the inspections                                  |
|-------------|---------------------------------------------------------------|
| Severities  | of the severities chosen                                      |
| Groups      | in the groups chosen                                          |
| Inspections | that are cleanup, modified or batch-mode ones, as chosen      |

A problem is reported only when its inspection passes all three.

### Severities

- **This and higher** reports one severity and every severity above it.
- **Selected** reports the severities ticked one by one.
- **Edit Severities…** opens the IDE's editor of severities, where their order can be changed and custom ones added.

#### Caveat: the severity is the one the inspection is configured with, not the one a problem is shown with

The filter goes by the severity an inspection has in the profile, as **Group by Severity** in the results does.\
An inspection configured as a warning is left out with the warnings, even when some of its problems are highlighted
as weak warnings, and the other way round.

Where the profile gives an inspection another severity in some scope, it is reported only in the scopes whose
severity is chosen.

### Groups

The groups are those of the inspection settings, as a tree with a search field.

- Ticking a group ticks every group under it.
- **Check All** and **Uncheck All** act on the groups the search has found, or on all of them when there is no search.
- Only groups with at least one inspection switched on in the chosen profile are offered.

### Inspections

These are the filters of the inspection settings' own filter menu:

- **Cleanup inspections only**: those whose fixes **Code Cleanup** applies.
- **Modified in the profile only**: those whose settings differ from the defaults: switched on or off, severity,
  scopes or options.
- **Batch-mode inspections only**: those that run only in **Inspect Code**, and never highlight code in the editor.

### What is remembered

- The choices are remembered per project.
- **Rerun** in the results repeats the run with the filter it had, even when the choice has changed since.

## Known issue in IntelliJ IDEA 2025.3: reporting only one of Grazie's two checks fails the run

In IntelliJ IDEA 2025.3, Grazie checks grammar and style with one inspection.\
It reports problems of each check switched on in the project's profile, even of one the filter left out.

The run then fails on those problems:

- the IDE reports an error;
- other results in the same files are missing.

The dialog shows a warning when the choice would do this.\
To avoid it, report both Grazie inspections, or neither.\
The bug is fixed in IntelliJ IDEA 2026.1.

## Installing

The plugin needs an IntelliJ-based IDE of version 2025.3 (build 253) or later.\
It is built and tested against IntelliJ IDEA 2025.3.5.

It is not published to JetBrains Marketplace; build the archive from the sources:

```sh
./gradlew buildPlugin
```

Then install `build/distributions/ij-inspection-filter-<version>.zip` with
**Settings | Plugins | ⚙ | Install Plugin from Disk…**.

Gradle 9 itself needs a JDK 17 or later to run.\
The JDK 21 the plugin is compiled with is downloaded if none is found.

## Development

**Skip this unless you are changing the plugin.**

| Command                  | Does                                                                |
|--------------------------|---------------------------------------------------------------------|
| `./gradlew runIde`       | starts a separate IDE with the plugin installed, in its own sandbox |
| `./gradlew test`         | runs the tests, which include real **Inspect Code** runs            |
| `./gradlew verifyPlugin` | checks binary compatibility with the recommended IDE builds         |

`verifyPlugin` downloads each IDE build it checks.

## License

Copyright (C) 2026 Martin Lopatář

GNU Affero General Public License, version 3 or any later version; see [LICENSE](LICENSE).\
The plugin uses only APIs of the open-source IntelliJ Platform,
[intellij-community](https://github.com/JetBrains/intellij-community), which is under the Apache License 2.0.
