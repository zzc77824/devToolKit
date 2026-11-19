package org.zzc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import java.awt.Container
import javax.swing.JButton

class QrCodePanelTest : BasePlatformTestCase() {

    fun testGeneratesPreviewWhenUserClicksGenerate() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = QrCodePanel()
            findComponents(panel, JBTextArea::class.java).single().text = "https://example.com"

            val generateButton = findComponents(panel, JButton::class.java)
                .single { it.text == MyMessageBundle.message("qr.generate.button") }
            generateButton.doClick()

            assertTrue(
                "Preview image should be set after generation",
                findComponents(panel, JBLabel::class.java).any { it.icon != null },
            )

            val splitter = findComponents(panel, JBSplitter::class.java).single()
            assertFalse("QR panel must use a left-to-right splitter", splitter.isVertical)
            assertNotNull(splitter.firstComponent)
            assertNotNull(splitter.secondComponent)
        }
    }

    private fun <T> findComponents(container: Container, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun visit(current: Container) {
            for (child in current.components) {
                if (type.isInstance(child)) {
                    found += type.cast(child)
                }
                if (child is Container) {
                    visit(child)
                }
            }
        }
        visit(container)
        return found
    }
}
