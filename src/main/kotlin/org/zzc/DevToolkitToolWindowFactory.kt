package org.zzc

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory

/** Single DevToolkit tool window hosting the QR and JSON tools as tabs. */
class DevToolkitToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val tabs = JBTabbedPane().apply {
            addTab(
                MyMessageBundle.message("devtoolkit.tab.qr"),
                AllIcons.Toolwindows.ToolWindowPalette,
                QrCodePanel(),
            )
            addTab(
                MyMessageBundle.message("devtoolkit.tab.json"),
                AllIcons.FileTypes.Json,
                JsonPanel(project),
            )
        }

        val content = ContentFactory.getInstance().createContent(tabs, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
