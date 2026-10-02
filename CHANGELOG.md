# Changelog

Notable changes to the plugin, in the format of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).\
A release closes `[Unreleased]` into a section of its own, and its release notes are that section.\
A released section is compared against the tag that released it, so it may not be edited again.

## [Unreleased]

### Added

- **Inspect Code with Filters…**, next to **Inspect Code…**: the same inspections over the same scope, reporting only
  the problems the filters in its dialog let through.
- Filters by severity, either one severity and every severity above it, or severities ticked one by one.
- Filters by group of inspections, and to the cleanup inspections, those modified in the profile, or the batch-mode
  ones only.
- Filters on the problems themselves: on lines changed since the last commit, with a quick fix, or with a message
  containing a text.
- The filter chosen is remembered per project, and **Rerun** in the results repeats a run with the filter it had.
