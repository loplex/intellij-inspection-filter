# Developing Inspect Code with Filters

| Command                  | Does                                                                |
|--------------------------|---------------------------------------------------------------------|
| `./gradlew runIde`       | starts a separate IDE with the plugin installed, in its own sandbox |
| `./gradlew test`         | runs the tests, which include real **Inspect Code** runs            |
| `scripts/ui-tests.sh`    | runs the [UI tests](#ui-tests-in-a-real-ide) in a real IDE          |
| `./gradlew verifyPlugin` | checks binary compatibility with the recommended IDE builds         |

`verifyPlugin` downloads each IDE build it checks.

## UI tests in a real IDE

`scripts/ui-tests.sh` runs `./gradlew integrationTest`: the tests in `src/integrationTest`, which start the IDE with the
plugin installed and drive it with JetBrains'
[Starter and Driver](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html).\
They check what the tests of `./gradlew test` cannot reach, such as a key pressed in a popup of the dialog.\
[Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot), the older way to do this, is deprecated in their
favor.

The IDE needs an X display, and `UI_TESTS_DISPLAY` picks which:

| `UI_TESTS_DISPLAY` | The IDE opens on                       | Meant for                         |
|--------------------|----------------------------------------|-----------------------------------|
| `xvfb`, by default | a virtual display, which shows nothing | a run, on a desktop or in CI      |
| `xephyr`           | a window of its own on the desktop     | watching what the tests do        |
| `desktop`          | the desktop itself, `$DISPLAY`         | a run with nothing else on screen |

```sh
scripts/ui-tests.sh
UI_TESTS_DISPLAY=xephyr scripts/ui-tests.sh --tests '*FilterPopupUiTest*'
```

- In CI too, the script is all it takes: it starts the virtual display itself.
- It needs Xvfb or Xephyr for the display it starts, which is the first one free.
- Openbox, when installed, is started on that display as the window manager.
- Xephyr opens its window on the desktop's `$DISPLAY`.
- Arguments go on to Gradle.
- `UI_TESTS_SCREEN` picks the size of the display started, 1920x1080 by default.
- Where `$DISPLAY` is already the one to use, `./gradlew integrationTest` runs the tests just as well.
- Each test starts an IDE of its own, so it takes a minute or so.
- The IDE is the one the plugin is built against, run from where Gradle unpacked it: Starter downloads none.
- The IDE's configuration, logs and reports go to `build/ui-tests`, and so do the display's logs.

## Checking it by hand on a virtual display

`scripts/sandbox-on-xvfb.sh` runs the sandbox IDE of `runIde` on a virtual X display.\
There it can be driven with `xdotool` and looked at in screenshots without taking over the desktop - by an agent, for
instance.

It needs Xvfb, `xdpyinfo`, `xdotool`, `wmctrl` and ImageMagick.\
Openbox, when installed, is started as the window manager: without one, nothing places or focuses the windows.

```sh
scripts/sandbox-on-xvfb.sh start build/manual-test/some-project
export DISPLAY=:99
scripts/sandbox-on-xvfb.sh shot results 1880x340+36+735
scripts/sandbox-on-xvfb.sh stop
```

- **start** opens the project and maximizes the IDE's window.
  It refuses while another sandbox IDE runs, as the two would share one configuration directory.
- **shot** saves a screenshot, cropped when given a geometry, to `build/manual-test/shots`.
- **stop** closes the IDE as **File | Exit** does, and then the display.

Logs go to `build/manual-test` too; `XVFB_DISPLAY` picks a display other than 99.

### What gets in the way when driving it

- **The project to open**: a copy under `build/manual-test`, such as `git clone --depth 1 file://…`, can be changed
  freely - to give **On lines changed since the last commit** something to report, for instance.
  One under `/tmp/user/<uid>` did not import: Maven reported invalid virtual files and found no modules.
- **Opening the action**: from the **Code** menu. **Find Action** answers slowly while the project is indexed.
- **Clicking**: a dialog does not always open at the same place, so take a screenshot before clicking into it.
- **Popup menus**: an item, such as **XML** under **Export** in the results, takes a click only after the pointer has
  rested on it. Clicked straight away, the menu just closes.
- **Typing**: keys go wherever the focus is when the expected dialog has not opened. In the project tree, **Enter**
  opens the files selected.
- **Reading the results**: export them to XML from the results' toolbar, and read the file of each inspection, such as
  `unused.xml`. That is exact, where reading the tree off a screenshot is not.
- **The error after an export**: in IntelliJ IDEA 2025.3.5, every XML export logs "Descriptions are missed for tools"
  for database inspections, and the IDE reports an error. Plain **Inspect Code** does the same, so it is not the
  plugin's.

For what a test can check on its own, see [UI tests in a real IDE](#ui-tests-in-a-real-ide).
