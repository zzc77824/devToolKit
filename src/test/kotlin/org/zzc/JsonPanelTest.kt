package org.zzc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.UIUtil
import java.awt.Container
import javax.swing.JButton

class JsonPanelTest : BasePlatformTestCase() {

    fun testPanelContainsFoldableJsonEditors() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = JsonPanel(project)
            val editors = findComponents(panel, EditorTextField::class.java)

            assertEquals(2, editors.size)
            assertFalse("Input editor must be editable", editors.single { !it.isViewer }.isViewer)
            assertTrue("Output editor must be read-only", editors.single { it.isViewer }.isViewer)

            val buttonTexts = findComponents(panel, JButton::class.java).mapNotNull { it.text }
            assertTrue(buttonTexts.contains(MyMessageBundle.message("json.format.button")))
            assertTrue(buttonTexts.contains(MyMessageBundle.message("json.minify.button")))
            assertTrue(buttonTexts.contains(MyMessageBundle.message("json.query.button")))

            val splitter = findComponents(panel, JBSplitter::class.java).single()
            assertFalse("JSON panel must use a left-to-right splitter", splitter.isVertical)
            assertNotNull(splitter.firstComponent)
            assertNotNull(splitter.secondComponent)
        }
    }

    fun testFoldingRegionsAppearForMultilineJson() {
        var editor: Editor? = null
        ApplicationManager.getApplication().invokeAndWait {
            val panel = JsonPanel(project)
            val input = findComponents(panel, EditorTextField::class.java).first { !it.isViewer }
            input.setDisposedWith(testRootDisposable)
            input.text = "{\n  \"a\": {\n    \"b\": 1\n  }\n}"
            editor = input.getEditor(true)
        }

        val deadline = System.currentTimeMillis() + 5_000
        while ((editor == null || editor!!.foldingModel.allFoldRegions.isEmpty()) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(25)
            UIUtil.dispatchAllInvocationEvents()
        }

        assertNotNull("JSON editor should be created", editor)
        assertTrue("Folding gutter must be visible", editor!!.settings.isFoldingOutlineShown)
        assertTrue("Line numbers provide the folding gutter", editor!!.settings.isLineNumbersShown)
        assertTrue(
            "JSON objects should produce fold regions",
            editor!!.foldingModel.allFoldRegions.isNotEmpty(),
        )
    }

    fun testFormatButtonFormatsJson() {
        var panel: JsonPanel? = null
        ApplicationManager.getApplication().invokeAndWait {
            val created = JsonPanel(project)
            panel = created
            findComponents(created, EditorTextField::class.java).first { !it.isViewer }.text = """{"a":1}"""
            findComponents(created, JButton::class.java)
                .first { it.text == MyMessageBundle.message("json.format.button") }
                .doClick()
        }

        val output = findComponents(panel!!, EditorTextField::class.java).first { it.isViewer }
        val deadline = System.currentTimeMillis() + 10_000
        while (output.text.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            // Tests run on the EDT, so pump the queued invokeLater callback from the background thread.
            UIUtil.dispatchAllInvocationEvents()
        }

        assertEquals("{\n  \"a\": 1\n}", output.text)
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
