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
 * 右键菜单项：将选中的代码文本内容（带文件路径）发送到 Terminal
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

        // 获取选中的文本
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            showNotification(project, "请先选中代码", NotificationType.WARNING)
            return
        }

        // 获取文件相对路径
        val filePath = getRelativePath(psiFile, project)
        
        // 获取选中内容的起始行号和结束行号
        val document = editor.document
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val startLine = document.getLineNumber(selectionStart) + 1
        val endLine = document.getLineNumber(selectionEnd) + 1
        
        // 构建文件路径和行号信息
        val fileInfo = if (startLine == endLine) {
            "$filePath:$startLine"
        } else {
            "$filePath:$startLine-$endLine"
        }
        
        // 构建发送内容：换行符 + 文件路径和行号注释 + 选中内容
        val contentToSend = buildString {
            append("\n")  // 另起一行
            append("# From: $fileInfo\n")  // 文件路径和行号注释
            append(selectedText)
        }

        // 发送到 Terminal
        when (val result = TerminalService.sendToTerminal(project, contentToSend)) {
            is TerminalService.SendResult.Success -> {
                showNotification(project, "已发送选中内容到 Terminal", NotificationType.INFORMATION)
            }
            is TerminalService.SendResult.Error -> {
                copyToClipboard(contentToSend)
                showNotification(
                    project,
                    "${result.message}，已复制到剪贴板，请手动粘贴到外部终端",
                    NotificationType.WARNING
                )
            }
        }
    }

    /**
     * 获取文件相对于项目根目录的路径
     */
    private fun getRelativePath(psiFile: com.intellij.psi.PsiFile, project: Project): String {
        val virtualFile = psiFile.virtualFile ?: return psiFile.name
        val projectBasePath = project.basePath ?: return virtualFile.name
        val filePath = virtualFile.path
        
        return if (filePath.startsWith(projectBasePath)) {
            filePath.removePrefix(projectBasePath).removePrefix("/")
        } else {
            virtualFile.name
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
