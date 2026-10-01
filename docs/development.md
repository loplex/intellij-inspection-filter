# Developing Inspect Code with Filters

| Command                  | Does                                                                |
|--------------------------|---------------------------------------------------------------------|
| `./gradlew runIde`       | starts a separate IDE with the plugin installed, in its own sandbox |
| `./gradlew test`         | runs the tests, which include real **Inspect Code** runs            |
| `./gradlew verifyPlugin` | checks binary compatibility with the recommended IDE builds         |

`verifyPlugin` downloads each IDE build it checks.

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

For tests that drive the UI on their own, the IntelliJ Platform has
[Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot); the plugin does not use it.
