package cz.loplex.inspectionfilter

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class FilterPopupUiTest {

    @Test
    fun escInThePopupOfGroupsGoesBackToTheGroupsItOpenedWith() {
        ideWithPlugin("escInThePopupOfGroups").runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            invokeAction("cz.loplex.inspectionfilter.InspectCodeWithFilters", now = false)
            ui.dialog(title = "Specify Inspection Scope") {
                val groups = x { byVisibleText("All groups") }
                groups.shouldBe { present() }
                // The Choose… of the row of groups: the second of the four.
                xx { byVisibleText("Choose…") }.list()[1].click()
                x { byVisibleText("Uncheck All") }.click()
                x { byVisibleText("No group: nothing will be reported") }.shouldBe { present() }
                keyboard { escape() }
                groups.shouldBe { present() }
                cancelButton.click()
            }
        }
    }
}
