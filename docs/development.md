# Developing Inspect Code with Filters

| Command                  | Does                                                                |
|--------------------------|---------------------------------------------------------------------|
| `./gradlew runIde`       | starts a separate IDE with the plugin installed, in its own sandbox |
| `./gradlew test`         | runs the tests, which include real **Inspect Code** runs            |
| `./gradlew verifyPlugin` | checks binary compatibility with the recommended IDE builds         |

`verifyPlugin` downloads each IDE build it checks.
