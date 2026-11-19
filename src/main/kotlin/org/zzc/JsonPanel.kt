package org.zzc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.zzc.json.JsonError
import org.zzc.json.JsonException
import org.zzc.json.JsonFormatter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * Tool window content: formats, minifies and queries JSON with JSONPath.
 *
 * Input and output are foldable JSON editors (syntax highlighting, line numbers, brace folding).
 * Parsing and querying run on a background thread; only the latest operation updates the UI.
 */
class JsonPanel(project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val formatter = JsonFormatter()

    private val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")

    private val inputEditor = CollapsibleJsonEditor(project, jsonFileType, viewer = false).apply {
        setPlaceholder(message("json.input.placeholder"))
    }

    private val outputEditor = CollapsibleJsonEditor(project, jsonFileType, viewer = true).apply {
        setPlaceholder(message("json.output.placeholder"))
    }

    private val indentCombo = JComboBox(
        arrayOf(
            message("json.indent.two"),
            message("json.indent.four"),
        ),
    ).apply {
        selectedIndex = 0
    }

    private val pathField = JBTextField().apply {
        emptyText.text = message("json.path.placeholder")
        toolTipText = message("json.path.tooltip")
        preferredSize = Dimension(JBUI.scale(260), preferredSize.height)
    }

    private val formatButton = JButton(message("json.format.button"))
    private val minifyButton = JButton(message("json.minify.button"))
    private val queryButton = JButton(message("json.query.button"))

    private val statusLabel = JBLabel(" ").apply {
        font = JBUI.Fonts.smallFont()
    }

    /** Identifies the latest launched operation so stale background results are ignored. */
    private var operationId = 0

    init {
        border = JBUI.Borders.empty(8)

        add(createToolbar(), BorderLayout.NORTH)
        add(createEditors(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        formatButton.addActionListener { formatJson() }
        minifyButton.addActionListener { minifyJson() }
        queryButton.addActionListener { queryJson() }
        pathField.addActionListener { queryJson() }
    }

    private fun createToolbar(): JBPanel<JBPanel<*>> {
        val actions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(JBLabel(message("json.indent.label")))
            add(indentCombo)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(formatButton)
            add(minifyButton)
        }

        val pathRow = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(6), 0)).apply {
            add(JBLabel(message("json.path.label")), BorderLayout.WEST)
            add(pathField, BorderLayout.CENTER)
            add(queryButton, BorderLayout.EAST)
        }

        return JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(4))).apply {
            add(actions, BorderLayout.NORTH)
            add(pathRow, BorderLayout.SOUTH)
        }
    }

    private fun createEditors(): JBSplitter {
        val input = section(message("json.input.label"), inputEditor)
        val output = section(message("json.output.label"), outputEditor)
        return JBSplitter(false, 0.5f).apply {
            firstComponent = input
            secondComponent = output
            dividerWidth = JBUI.scale(1)
        }
    }

    private fun section(title: String, content: JComponent): JBPanel<JBPanel<*>> =
        JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(title), BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

    private fun formatJson() {
        val input = inputEditor.text
        if (input.isBlank()) {
            showEmptyInput()
            return
        }
        val indent = indentSize()
        runOperation {
            val formatted = formatter.format(input, indent)
            Outcome(
                output = formatted,
                status = message("json.status.formatted", formatted.count { it == '\n' } + 1),
            )
        }
    }

    private fun minifyJson() {
        val input = inputEditor.text
        if (input.isBlank()) {
            showEmptyInput()
            return
        }
        runOperation {
            val minified = formatter.minify(input)
            Outcome(
                output = minified,
                status = message("json.status.minified", minified.length),
            )
        }
    }

    private fun queryJson() {
        val input = inputEditor.text
        if (input.isBlank()) {
            showEmptyInput()
            return
        }
        val path = pathField.text.trim()
        if (path.isEmpty()) {
            showStatus(message("json.error.emptyPath"), isError = true)
            return
        }
        val indent = indentSize()
        runOperation {
            val result = formatter.query(input, path, indent)
            Outcome(
                output = result.json,
                status = message("json.status.queried", result.matchCount),
            )
        }
    }

    private fun indentSize(): Int = if (indentCombo.selectedIndex == 1) 4 else 2

    private fun runOperation(operation: () -> Outcome) {
        val application = ApplicationManager.getApplication()
        val id = ++operationId
        application.executeOnPooledThread {
            val result = runCatching(operation)
            application.invokeLater {
                if (id != operationId) {
                    return@invokeLater
                }
                result.onSuccess(::applyOutcome).onFailure(::applyFailure)
            }
        }
    }

    private fun applyOutcome(outcome: Outcome) {
        outputEditor.text = outcome.output
        showStatus(outcome.status, isError = false)
    }

    private fun applyFailure(throwable: Throwable) {
        outputEditor.text = ""
        showStatus(describe(throwable), isError = true)
    }

    private fun describe(throwable: Throwable): String = when (throwable) {
        is JsonException -> when (throwable.error) {
            JsonError.INVALID_JSON ->
                if (throwable.line != null && throwable.column != null) {
                    message(
                        "json.error.invalidJsonAt",
                        throwable.line,
                        throwable.column,
                        throwable.message ?: "",
                    )
                } else {
                    message("json.error.invalidJson", throwable.message ?: "")
                }

            JsonError.INVALID_PATH -> message("json.error.invalidPath", throwable.message ?: "")
            JsonError.PATH_NOT_FOUND -> message("json.error.pathNotFound", throwable.message ?: "")
        }

        else -> message("json.error.generic", throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun showEmptyInput() {
        outputEditor.text = ""
        showStatus(message("json.error.emptyInput"), isError = true)
    }

    private fun showStatus(text: String, isError: Boolean) {
        statusLabel.text = text
        statusLabel.foreground = if (isError) JBColor.RED else JBColor.GRAY
    }

    private fun message(key: String, vararg params: Any?): String = MyMessageBundle.message(key, *params)

    private data class Outcome(val output: String, val status: String)
}
