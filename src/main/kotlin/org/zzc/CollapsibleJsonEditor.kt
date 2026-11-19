package org.zzc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import org.zzc.json.JsonFoldRanges
import javax.swing.Timer

/**
 * An [EditorTextField] that behaves like a small code editor: JSON highlighting comes from the
 * file type, and JSON objects/arrays get fold regions in the gutter so long documents can be
 * collapsed. Folding is recomputed shortly after edits.
 */
class CollapsibleJsonEditor(
    project: Project?,
    fileType: FileType,
    viewer: Boolean,
) : EditorTextField(EditorFactory.getInstance().createDocument(""), project, fileType, viewer, false) {

    private val foldingTimer = Timer(FOLDING_DELAY_MS) { rebuildFolds() }.apply {
        isRepeats = false
    }

    init {
        addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                foldingTimer.restart()
            }
        })
    }

    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
        editor.settings.isLineNumbersShown = true
        editor.settings.isFoldingOutlineShown = true
        // Folds are managed by this class, not by JSON auto-folding.
        editor.settings.isAutoCodeFoldingEnabled = false
        ApplicationManager.getApplication().invokeLater { rebuildFolds() }
    }

    private fun rebuildFolds() {
        val editor = getEditor() ?: return
        if (editor.isDisposed) return

        val ranges = JsonFoldRanges.compute(editor.document.immutableCharSequence)
        editor.foldingModel.runBatchFoldingOperation {
            val foldingModel = editor.foldingModel
            for (region in foldingModel.allFoldRegions) {
                foldingModel.removeFoldRegion(region)
            }
            // Add outer regions first so nested regions stay properly contained.
            for (range in ranges.sortedWith(compareBy({ it.startOffset }, { -it.endOffset }))) {
                runCatching {
                    foldingModel.addFoldRegion(range.startOffset, range.endOffset, range.placeholder)
                        ?.isExpanded = true
                }
            }
        }
    }

    override fun removeNotify() {
        foldingTimer.stop()
        super.removeNotify()
    }

    private companion object {
        const val FOLDING_DELAY_MS = 250
    }
}
