import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    // Said here rather than left to whichever JDK runs Gradle; 21 is what the platform this compiles against
    // requires.
    jvmToolchain(21)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")

        ideaVersion {
            sinceBuild = "253"

            // Left unbounded: an upper bound would make every IDE update a release the plugin needs in order to
            // keep working. `verifyPlugin` is what finds an IDE that breaks it.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
