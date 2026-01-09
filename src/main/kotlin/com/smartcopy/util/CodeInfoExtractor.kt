package com.smartcopy.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * 代码信息提取工具
 * 从编辑器中提取选中代码的文件路径和行号信息
 */
object CodeInfoExtractor {

    /**
     * 代码信息数据类
     */
    data class CodeInfo(
        val filePath: String,
        val startLine: Int,
        val endLine: Int
    ) {
        /**
         * 格式化输出：relative/path/to/File.java:startLine-endLine
         */
        fun format(): String {
            return if (startLine == endLine) {
                "$filePath:$startLine"
            } else {
                "$filePath:$startLine-$endLine"
            }
        }
    }

    /**
     * 从编辑器和 PSI 文件中提取代码信息
     * 
     * @param editor 当前编辑器
     * @param psiFile 当前 PSI 文件
     * @return 代码信息，如果没有选中内容则返回 null
     */
    fun extract(editor: Editor, psiFile: PsiFile): CodeInfo? {
        val selectionModel = editor.selectionModel
        
        // 检查是否有选中内容
        if (!selectionModel.hasSelection()) {
            return null
        }
        
        val document = editor.document
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd
        
        // 获取起始行号和结束行号（1-based）
        val startLine = document.getLineNumber(selectionStart) + 1
        val endLine = document.getLineNumber(selectionEnd) + 1
        
        // 获取文件相对于项目根目录的路径
        val filePath = getRelativePath(psiFile)
        
        return CodeInfo(filePath, startLine, endLine)
    }

    /**
     * 获取文件相对于项目根目录的路径
     */
    private fun getRelativePath(psiFile: PsiFile): String {
        val project = psiFile.project
        val virtualFile = psiFile.virtualFile ?: return psiFile.name
        
        // 获取项目根目录
        val projectBasePath = project.basePath ?: return virtualFile.name
        val filePath = virtualFile.path
        
        // 计算相对路径
        return if (filePath.startsWith(projectBasePath)) {
            filePath.removePrefix(projectBasePath).removePrefix("/")
        } else {
            // 如果文件不在项目目录下，返回文件名
            virtualFile.name
        }
    }
}
