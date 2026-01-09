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
import java.awt.datatransfer.StringSelection

/**
 * 发送选中内容到 Terminal 的 Action
 * 右键菜单项：将选中的代码文本内容发送到 Terminal
 */
class SendSelectionToTerminalAction : AnAction() {

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
        
        // 只有在有项目、编辑器且有选中内容时才显示
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        
        presentation.isEnabledAndVisible = project != null 
            && editor != null 
            && hasSelection
    }

    /**
     * 执行发送操作
     */
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        // 获取选中的文本
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            showNotification(project, "请先选中代码", NotificationType.WARNING)
            return
        }

        // 发送到 Terminal
        when (val result = TerminalService.sendToTerminal(project, selectedText)) {
            is TerminalService.SendResult.Success -> {
                showNotification(project, "已发送选中内容到 Terminal", NotificationType.INFORMATION)
            }
            is TerminalService.SendResult.Error -> {
                copyToClipboard(selectedText)
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
