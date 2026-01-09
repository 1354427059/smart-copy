package com.smartcopy.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ide.CopyPasteManager
import com.smartcopy.service.TerminalService
import com.smartcopy.util.CodeInfoExtractor
import java.awt.datatransfer.StringSelection

/**
 * 发送到 Terminal 的 Action
 * 右键菜单项：将选中代码的类名和行号发送到 Terminal
 */
class SendToTerminalAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * 控制菜单项的可见性和可用性
     */
    override fun update(event: AnActionEvent) {
        val presentation = event.presentation
        
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)
        
        // 只有在有项目、编辑器、文件且有选中内容时才显示
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        
        presentation.isEnabledAndVisible = project != null 
            && editor != null 
            && psiFile != null 
            && hasSelection
    }

    /**
     * 执行发送操作
     */
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return

        // 提取代码信息
        val codeInfo = CodeInfoExtractor.extract(editor, psiFile)
        if (codeInfo == null) {
            showNotification(project, "请先选中代码", NotificationType.WARNING)
            return
        }

        // 格式化信息，另起一行发送
        val formattedText = "\n" + codeInfo.format()

        // 发送到 Terminal
        when (val result = TerminalService.sendToTerminal(project, formattedText)) {
            is TerminalService.SendResult.Success -> {
                showNotification(project, result.message, NotificationType.INFORMATION)
            }
            is TerminalService.SendResult.Error -> {
                copyToClipboard(formattedText)
                showNotification(
                    project,
                    "${result.message}，已复制到剪贴板，请手动粘贴到外部终端",
                    NotificationType.WARNING
                )
            }
        }
    }

    /**
     * 显示通知
     */
    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SmartCopy.Notification")
            .createNotification("Smart Copy", content, type)
            .notify(project)
    }

    private fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        CopyPasteManager.getInstance().setContents(selection)
    }
}
