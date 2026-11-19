package org.zzc

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.zzc.qr.QrCodeError
import org.zzc.qr.QrCodeException
import org.zzc.qr.QrCodeGenerator
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Tool window content: turns arbitrary text into a QR code with adjustable error correction and size.
 */
class QrCodePanel : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val generator = QrCodeGenerator()

    private val errorCorrectionLevels = listOf(
        ErrorCorrectionLevel.L,
        ErrorCorrectionLevel.M,
        ErrorCorrectionLevel.Q,
        ErrorCorrectionLevel.H,
    )

    private val inputArea = JBTextArea().apply {
        rows = 5
        lineWrap = true
        wrapStyleWord = true
    }

    private val errorCorrectionCombo = JComboBox(
        arrayOf(
            message("qr.level.l"),
            message("qr.level.m"),
            message("qr.level.q"),
            message("qr.level.h"),
        ),
    ).apply {
        selectedIndex = 1 // M is the best default trade-off between capacity and robustness.
        toolTipText = message("qr.errorCorrection.tooltip")
    }

    private val sizeSpinner = JBIntSpinner(
        QrCodeGenerator.DEFAULT_SIZE_PX,
        QrCodeGenerator.MIN_SIZE_PX,
        QrCodeGenerator.MAX_SIZE_PX,
        32,
    ).apply {
        toolTipText = message("qr.size.tooltip")
    }

    private val generateButton = JButton(message("qr.generate.button")).apply {
        addActionListener { generate() }
    }

    private val resultLabel = JBLabel("", SwingConstants.CENTER).apply {
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
        border = JBUI.Borders.empty(8)
    }

    private val resultScroll = JBScrollPane(resultLabel).apply {
        border = JBUI.Borders.empty()
    }

    private val statusLabel = JBLabel(" ").apply {
        font = JBUI.Fonts.smallFont()
    }

    /** Debounces regeneration while the user is typing. */
    private val regenerateTimer = Timer(350) { generate() }.apply {
        isRepeats = false
    }

    init {
        border = JBUI.Borders.empty(8)

        add(createSplitter(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        inputArea.emptyText.text = message("qr.input.placeholder")
        inputArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleGeneration()
            override fun removeUpdate(e: DocumentEvent) = scheduleGeneration()
            override fun changedUpdate(e: DocumentEvent) = scheduleGeneration()
        })
        errorCorrectionCombo.addActionListener { generate() }
        sizeSpinner.addChangeListener { scheduleGeneration() }
    }

    /** Input and controls on the left, live QR preview on the right. */
    private fun createSplitter(): JBSplitter {
        val left = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(6))).apply {
            add(JBLabel(message("qr.input.label")), BorderLayout.NORTH)
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
            add(createControls(), BorderLayout.SOUTH)
        }

        val right = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(6))).apply {
            add(JBLabel(message("qr.preview.label")), BorderLayout.NORTH)
            add(resultScroll, BorderLayout.CENTER)
        }

        return JBSplitter(false, 0.45f).apply {
            firstComponent = left
            secondComponent = right
            dividerWidth = JBUI.scale(1)
        }
    }

    private fun createControls(): JBPanel<JBPanel<*>> =
        JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(JBLabel(message("qr.errorCorrection.label")))
            add(errorCorrectionCombo)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(JBLabel(message("qr.size.label")))
            add(sizeSpinner)
            add(JBLabel(message("qr.size.unit")))
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(generateButton)
        }

    private fun scheduleGeneration() {
        regenerateTimer.restart()
    }

    private fun generate() {
        val text = inputArea.text
        if (text.isEmpty()) {
            showEmptyState()
            return
        }
        try {
            val code = generator.generate(
                text = text,
                errorCorrection = errorCorrectionLevels[errorCorrectionCombo.selectedIndex],
                preferredSizePx = sizeSpinner.number,
            )
            resultLabel.icon = ImageIcon(code.image)
            resultLabel.text = null
            showStatus(
                message("qr.status.info", code.version, code.moduleCount, code.image.width),
                isError = false,
            )
        } catch (e: QrCodeException) {
            resultLabel.icon = null
            resultLabel.text = ""
            val level = errorCorrectionLevels[errorCorrectionCombo.selectedIndex]
            showStatus(
                when (e.error) {
                    QrCodeError.EMPTY_CONTENT -> message("qr.error.empty")
                    QrCodeError.CONTENT_TOO_LONG -> message(
                        "qr.error.tooLong",
                        text.toByteArray(Charsets.UTF_8).size,
                        level.name,
                        QrCodeGenerator.maxByteCapacity(level),
                    )
                },
                isError = true,
            )
        }
    }

    private fun showEmptyState() {
        resultLabel.icon = null
        resultLabel.text = ""
        showStatus("", isError = false)
    }

    private fun showStatus(text: String, isError: Boolean) {
        statusLabel.text = text
        statusLabel.foreground = if (isError) JBColor.RED else JBColor.GRAY
    }

    override fun removeNotify() {
        regenerateTimer.stop()
        super.removeNotify()
    }

    private fun message(key: String, vararg params: Any?): String = MyMessageBundle.message(key, *params)
}
