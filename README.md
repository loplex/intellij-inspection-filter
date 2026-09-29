# Inspect Code with Filters

*Inspect Code, reporting only the problems you asked for.*

An IntelliJ Platform plugin that adds **Inspect Code with Filters…** next to **Inspect Code…**.\
It runs the same inspections over the same scope, but reports only those of the severities, groups and kinds of
inspection chosen in its dialog.\
Of their problems it can also report only those on lines changed since the last commit, with a quick fix, or with a
given text.
For example:

- errors and warnings, without the weak warnings and typos around them;
- everything but Proofreading;
- only the batch-mode inspections, which the editor never shows;
- only the problems whose message mentions `Optional`;
- only the problems on the lines changed since the last commit.

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

| Row         | Reports only                                                     |
|-------------|------------------------------------------------------------------|
| Severities  | the inspections of the severities chosen                         |
| Groups      | the inspections in the groups chosen                             |
| Inspections | the inspections that are cleanup, modified or batch-mode ones    |
| Problems    | the problems on changed lines, with a quick fix, or with a text  |

A problem is reported only when its inspection passes the first three rows, and the problem itself the fourth.

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

### Problems

These filter the problems themselves, one by one, rather than the inspections that report them:

- **On lines changed since the last commit**: those on a line added or changed since.
  See [what counts as a changed line](#what-counts-as-a-changed-line).
- **With a quick fix only**: those that come with a fix.
- **Message contains**: those whose message, as the results show it, contains the text, ignoring case.
  Leave it empty for any message.

With more than one, a problem has to pass them all.

#### What counts as a changed line

The lines are those that differ from the last commit, as the gutter of the editor marks them:

- A line added or changed counts, whether its change is staged or not.
- A line only deleted does not: it is no longer there for a problem to be on.
- A file added since the last commit counts as a whole.
- A file the VCS does not track has no changed line, and neither has a file outside any VCS.

A problem is on a changed line when the code it marks meets one.

The lines are found anew as each run starts, **Rerun** included, so they are those of the code the run inspects.

#### Caveat: only the reporting is narrowed, not what is inspected

The inspections still run over the whole scope, so that global ones, such as **Unused declaration**, still see every
use of what they report on.\
Local inspections, which check one file at a time, skip the files with no changed line.

#### Caveat: global inspections are filtered element by element

A global inspection, such as **Unused declaration**, runs over the whole scope at once rather than file by file.\
Its problems are filtered for each element it reports them in (a class, a method, a file), all of them at once:
they are all reported when any one of them passes.

Some of them, **Unused declaration** among them, also show elements they report no problem in: a class or a method
nothing uses is shown with what the results say of it, but with no problem the filters could look at.\
With any of these filters set, those elements are left out, even though **Safe delete** in the results would fix them.\
They are left out of an export of the results too.

### What is remembered

- The choices are remembered per project.
- **Rerun** in the results repeats the run with the filter it had, even when the choice has changed since.
  Only the changed lines are found anew.

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
