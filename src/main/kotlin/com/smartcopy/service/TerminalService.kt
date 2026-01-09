package com.smartcopy.service

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.nio.charset.StandardCharsets

/**
 * Terminal 服务
 * 用于向当前激活的 Terminal 发送文本
 * 兼容 IDEA 2023.3+ 所有版本
 * 支持经典终端和 2024/2025 Block Terminal（重置版终端）
 * 支持 macOS、Windows、Linux
 */
object TerminalService {

    private val LOG = Logger.getInstance(TerminalService::class.java)
    private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"

    // 终端引擎类型
    enum class TerminalEngineType {
        CLASSIC,      // 经典终端 (JediTerm)
        BLOCK_2024,   // 2024/2025 Block Terminal（重置版）
        UNKNOWN
    }

    sealed class SendResult {
        data class Success(val message: String) : SendResult()
        data class Error(val message: String) : SendResult()
    }

    /**
     * 获取 IDEA 版本信息
     */
    private fun getIdeaVersion(): String {
        return try {
            val appInfo = ApplicationInfo.getInstance()
            "${appInfo.majorVersion}.${appInfo.minorVersion}"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 检测终端引擎类型
     */
    private fun detectTerminalEngineType(content: Content): TerminalEngineType {
        val component = content.component
        val componentTree = buildComponentTree(component)
        
        LOG.info("检测终端引擎类型，组件树: $componentTree")
        
        // 检查是否是 Block Terminal（重置版）
        if (componentTree.any { 
            it.contains("BlockTerminal") || 
            it.contains("TerminalBlocksComponent") ||
            it.contains("org.jetbrains.plugins.terminal.block") ||
            it.contains("org.jetbrains.plugins.terminal.exp")
        }) {
            LOG.info("检测到 Block Terminal（重置版终端）")
            return TerminalEngineType.BLOCK_2024
        }
        
        // 检查是否是经典终端
        if (componentTree.any { 
            it.contains("JediTerm") || 
            it.contains("ShellTerminalWidget") ||
            it.contains("TerminalPanel")
        }) {
            LOG.info("检测到经典终端 (JediTerm)")
            return TerminalEngineType.CLASSIC
        }
        
        LOG.info("未能确定终端类型")
        return TerminalEngineType.UNKNOWN
    }

    /**
     * 构建组件树（用于检测终端类型）
     */
    private fun buildComponentTree(component: java.awt.Component?): List<String> {
        val result = mutableListOf<String>()
        collectComponentNames(component, result)
        return result
    }

    private fun collectComponentNames(component: java.awt.Component?, result: MutableList<String>) {
        if (component == null) return
        result.add(component.javaClass.name)
        if (component is java.awt.Container) {
            for (child in component.components) {
                collectComponentNames(child, result)
            }
        }
    }

    fun sendToTerminal(project: Project, text: String): SendResult {
        val ideaVersion = getIdeaVersion()
        LOG.info("IDEA 版本: $ideaVersion, 尝试发送文本到 Terminal: $text")

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return SendResult.Error("Terminal 工具窗口未找到，请先打开 Terminal")

        if (!toolWindow.isVisible) {
            return SendResult.Error("Terminal 未打开，请先打开 Terminal（View → Tool Windows → Terminal）")
        }

        val content = toolWindow.contentManager.selectedContent
            ?: return SendResult.Error("没有活动的 Terminal 标签页")

        // 检测终端引擎类型
        val engineType = detectTerminalEngineType(content)
        LOG.info("终端引擎类型: $engineType")

        return try {
            when (engineType) {
                TerminalEngineType.BLOCK_2024 -> {
                    // 优先使用 Block Terminal 专用方法
                    val blockResult = tryBlockTerminal(project, content, text)
                    if (blockResult != null) {
                        LOG.info("通过 Block Terminal API 成功发送")
                        return blockResult
                    }
                    // 回退到 Robot 粘贴
                    val robotResult = tryRobotPaste(project, toolWindow, text)
                    if (robotResult != null) {
                        LOG.info("通过 Robot 粘贴成功发送")
                        return robotResult
                    }
                }
                TerminalEngineType.CLASSIC -> {
                    // 使用经典终端方法
                    val classicResult = tryClassicTerminalArchitecture(content, text)
                    if (classicResult != null) {
                        LOG.info("通过经典终端架构成功发送")
                        return classicResult
                    }
                }
                TerminalEngineType.UNKNOWN -> {
                    // 尝试所有方法
                    LOG.info("终端类型未知，尝试所有方法")
                }
            }

            // 通用回退策略
            // 策略1: 尝试通过焦点组件发送
            val focusResult = tryFocusedComponent(project, text)
            if (focusResult != null) {
                LOG.info("通过焦点组件成功发送")
                return focusResult
            }

            // 策略2: 尝试新终端架构 API
            val newResult = tryNewTerminalArchitecture(project, content, text)
            if (newResult != null) {
                LOG.info("通过新终端架构成功发送")
                return newResult
            }

            // 策略3: 回退到经典终端架构
            val classicResult = tryClassicTerminalArchitecture(content, text)
            if (classicResult != null) {
                LOG.info("通过经典终端架构成功发送")
                return classicResult
            }

            // 策略4: Robot 模拟粘贴（最后手段）
            val robotResult = tryRobotPaste(project, toolWindow, text)
            if (robotResult != null) {
                LOG.info("通过 Robot 粘贴成功发送")
                return robotResult
            }

            SendResult.Error("无法发送到 Terminal，请确保 Terminal 已连接并处于活动状态")
        } catch (e: Exception) {
            LOG.error("发送到 Terminal 失败", e)
            SendResult.Error("发送失败: ${e.message}")
        }
    }

    // ==================== Block Terminal (2024/2025 重置版) ====================

    /**
     * 专门处理 Block Terminal（重置版终端）
     */
    private fun tryBlockTerminal(project: Project, content: Content, text: String): SendResult? {
        LOG.info("尝试 Block Terminal 专用方法")

        // 方式1: 通过 TerminalToolWindowManager 获取 BlockTerminalSession
        try {
            val result = tryBlockTerminalViaManager(project, text)
            if (result != null) return result
        } catch (e: Exception) {
            LOG.debug("通过 Manager 发送失败: ${e.message}")
        }

        // 方式2: 通过 DataContext 获取 BlockTerminalSession
        try {
            val result = tryBlockTerminalViaDataContext(content.component, text)
            if (result != null) return result
        } catch (e: Exception) {
            LOG.debug("通过 DataContext 发送失败: ${e.message}")
        }

        // 方式3: 递归查找 Block Terminal 组件
        try {
            val result = findAndSendToBlockTerminal(content.component, text)
            if (result != null) return result
        } catch (e: Exception) {
            LOG.debug("递归查找发送失败: ${e.message}")
        }

        // 方式4: 通过 ShellCommandExecutorKt 发送
        try {
            val result = tryShellCommandExecutor(project, text)
            if (result != null) return result
        } catch (e: Exception) {
            LOG.debug("通过 ShellCommandExecutor 发送失败: ${e.message}")
        }

        return null
    }

    private fun tryBlockTerminalViaManager(project: Project, text: String): SendResult? {
        try {
            // 尝试 TerminalToolWindowManager
            val managerClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
            val getInstance = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstance.invoke(null, project) ?: return null

            // 尝试获取当前终端 widget
            val widgetMethods = listOf(
                "getActiveTerminalWidget",
                "getCurrentTerminalWidget", 
                "getFocusedTerminalWidget"
            )
            
            for (methodName in widgetMethods) {
                try {
                    val widget = invokeMethod(manager, methodName) ?: continue
                    LOG.info("获取到 Widget ($methodName): ${widget.javaClass.name}")
                    
                    val result = sendToBlockTerminalWidget(widget, text)
                    if (result != null) return result
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("TerminalToolWindowManager 方式失败: ${e.message}")
        }
        return null
    }

    private fun tryBlockTerminalViaDataContext(component: java.awt.Component, text: String): SendResult? {
        try {
            val dataContext = DataManager.getInstance().getDataContext(component)
            
            // Block Terminal 的 DataKey
            val blockDataKeys = listOf(
                "org.jetbrains.plugins.terminal.block.TerminalDataContextUtils.BLOCK_TERMINAL_SESSION",
                "org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.BLOCK_TERMINAL",
                "BlockTerminalSession",
                "blockTerminalSession",
                "BLOCK_TERMINAL_SESSION",
                "TerminalSession",
                "terminalSession"
            )
            
            for (key in blockDataKeys) {
                try {
                    val session = dataContext.getData(key)
                    if (session != null) {
                        LOG.info("从 DataContext 获取到: $key -> ${session.javaClass.name}")
                        val result = sendToBlockTerminalSession(session, text)
                        if (result != null) return result
                    }
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("DataContext 方式失败: ${e.message}")
        }
        return null
    }

    /**
     * 发送文本到 Block Terminal Widget
     */
    private fun sendToBlockTerminalWidget(widget: Any, text: String): SendResult? {
        LOG.info("尝试发送到 Block Terminal Widget: ${widget.javaClass.name}")

        // 方式1: 获取 session 并发送
        try {
            val session = invokeMethod(widget, "getSession")
                ?: invokeMethod(widget, "getBlockTerminalSession")
                ?: invokeMethod(widget, "getTerminalSession")
                ?: getFieldRecursive(widget, "session")
                ?: getFieldRecursive(widget, "blockSession")
            
            if (session != null) {
                LOG.info("获取到 Session: ${session.javaClass.name}")
                val result = sendToBlockTerminalSession(session, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 session 失败: ${e.message}")
        }

        // 方式2: 获取 controller 并发送
        try {
            val controller = invokeMethod(widget, "getController")
                ?: invokeMethod(widget, "getTerminalController")
                ?: getFieldRecursive(widget, "controller")
            
            if (controller != null) {
                LOG.info("获取到 Controller: ${controller.javaClass.name}")
                val result = sendToBlockTerminalController(controller, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 controller 失败: ${e.message}")
        }

        // 方式3: 获取 model 并发送
        try {
            val model = invokeMethod(widget, "getModel")
                ?: invokeMethod(widget, "getTerminalModel")
                ?: getFieldRecursive(widget, "model")
            
            if (model != null) {
                LOG.info("获取到 Model: ${model.javaClass.name}")
                val result = sendToBlockTerminalModel(model, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 model 失败: ${e.message}")
        }

        // 方式4: 直接在 widget 上尝试发送方法
        val sendMethods = listOf(
            "sendText", "sendString", "typeText", "inputText",
            "executeCommand", "sendToTerminal", "write"
        )
        for (methodName in sendMethods) {
            try {
                invokeMethodWithArg(widget, methodName, text)
                if (hasMethod(widget, methodName, String::class.java)) {
                    LOG.info("通过 $methodName 发送成功")
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            } catch (e: Exception) { /* 继续 */ }
        }

        return null
    }

    /**
     * 发送文本到 Block Terminal Session
     */
    private fun sendToBlockTerminalSession(session: Any, text: String): SendResult? {
        LOG.info("尝试发送到 Block Terminal Session: ${session.javaClass.name}")
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        // 方式1: sendText / sendString
        val textMethods = listOf("sendText", "sendString", "typeText", "write", "input")
        for (methodName in textMethods) {
            try {
                invokeMethodWithArg(session, methodName, text)
                if (hasMethod(session, methodName, String::class.java)) {
                    LOG.info("通过 Session.$methodName 发送成功")
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            } catch (e: Exception) { /* 继续 */ }
        }

        // 方式2: 通过 terminalStarter
        try {
            val starter = invokeMethod(session, "getTerminalStarter")
                ?: getFieldRecursive(session, "terminalStarter")
            if (starter != null) {
                try {
                    val method = starter.javaClass.getMethod("sendString", String::class.java, Boolean::class.javaPrimitiveType)
                    method.invoke(starter, text, false)
                    return SendResult.Success("已发送到 Terminal: $text")
                } catch (e: Exception) {
                    try {
                        val method = starter.javaClass.getMethod("sendString", String::class.java)
                        method.invoke(starter, text)
                        return SendResult.Success("已发送到 Terminal: $text")
                    } catch (e2: Exception) { /* 继续 */ }
                }
            }
        } catch (e: Exception) {
            LOG.debug("通过 terminalStarter 发送失败: ${e.message}")
        }

        // 方式3: 通过 ttyConnector
        try {
            val connector = invokeMethod(session, "getTtyConnector")
                ?: getFieldRecursive(session, "ttyConnector")
                ?: getFieldRecursive(session, "connector")
            if (connector != null) {
                val isConnected = invokeMethod(connector, "isConnected") as? Boolean ?: true
                if (isConnected) {
                    try {
                        val method = connector.javaClass.getMethod("write", ByteArray::class.java)
                        method.invoke(connector, bytes)
                        return SendResult.Success("已发送到 Terminal: $text")
                    } catch (e: Exception) {
                        try {
                            val method = connector.javaClass.getMethod("write", String::class.java)
                            method.invoke(connector, text)
                            return SendResult.Success("已发送到 Terminal: $text")
                        } catch (e2: Exception) { /* 继续 */ }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("通过 ttyConnector 发送失败: ${e.message}")
        }

        // 方式4: 通过 outputChannel
        try {
            val channel = invokeMethod(session, "getOutputChannel")
                ?: invokeMethod(session, "getChannel")
                ?: getFieldRecursive(session, "outputChannel")
            if (channel != null) {
                try {
                    val method = channel.javaClass.getMethod("write", ByteArray::class.java)
                    method.invoke(channel, bytes)
                    return SendResult.Success("已发送到 Terminal: $text")
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("通过 outputChannel 发送失败: ${e.message}")
        }

        return null
    }

    /**
     * 发送文本到 Block Terminal Controller
     */
    private fun sendToBlockTerminalController(controller: Any, text: String): SendResult? {
        LOG.info("尝试发送到 Controller: ${controller.javaClass.name}")

        val methods = listOf(
            "sendText", "sendString", "typeText", "inputText",
            "handleInput", "processInput", "write"
        )
        for (methodName in methods) {
            try {
                invokeMethodWithArg(controller, methodName, text)
                if (hasMethod(controller, methodName, String::class.java)) {
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            } catch (e: Exception) { /* 继续 */ }
        }

        // 尝试获取 session
        try {
            val session = invokeMethod(controller, "getSession")
                ?: getFieldRecursive(controller, "session")
            if (session != null) {
                return sendToBlockTerminalSession(session, text)
            }
        } catch (e: Exception) { /* 继续 */ }

        return null
    }

    /**
     * 发送文本到 Block Terminal Model
     */
    private fun sendToBlockTerminalModel(model: Any, text: String): SendResult? {
        LOG.info("尝试发送到 Model: ${model.javaClass.name}")

        val methods = listOf("sendText", "sendString", "write", "input")
        for (methodName in methods) {
            try {
                invokeMethodWithArg(model, methodName, text)
                if (hasMethod(model, methodName, String::class.java)) {
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            } catch (e: Exception) { /* 继续 */ }
        }

        return null
    }

    /**
     * 递归查找并发送到 Block Terminal 组件
     */
    private fun findAndSendToBlockTerminal(component: java.awt.Component?, text: String): SendResult? {
        if (component == null) return null

        val className = component.javaClass.name

        // Block Terminal 相关组件
        if (className.contains("BlockTerminal") || 
            className.contains("TerminalBlocksComponent") ||
            className.contains("org.jetbrains.plugins.terminal.block") ||
            className.contains("org.jetbrains.plugins.terminal.exp")) {
            
            LOG.info("找到 Block Terminal 组件: $className")
            
            // 尝试作为 Widget 发送
            val widgetResult = sendToBlockTerminalWidget(component, text)
            if (widgetResult != null) return widgetResult

            // 尝试从 DataContext 获取 session
            val dataResult = tryBlockTerminalViaDataContext(component, text)
            if (dataResult != null) return dataResult
        }

        // 递归查找子组件
        if (component is java.awt.Container) {
            for (child in component.components) {
                val result = findAndSendToBlockTerminal(child, text)
                if (result != null) return result
            }
        }

        return null
    }

    /**
     * 通过 ShellCommandExecutor 发送
     */
    private fun tryShellCommandExecutor(project: Project, text: String): SendResult? {
        try {
            // 尝试找到 ShellCommandExecutor 或类似的执行器
            val executorClasses = listOf(
                "org.jetbrains.plugins.terminal.block.ShellCommandExecutor",
                "org.jetbrains.plugins.terminal.exp.ShellCommandExecutor",
                "org.jetbrains.plugins.terminal.ShellTerminalRunner"
            )
            
            for (className in executorClasses) {
                try {
                    val clazz = Class.forName(className)
                    val getInstance = clazz.getMethod("getInstance", Project::class.java)
                    val executor = getInstance.invoke(null, project) ?: continue
                    
                    // 尝试发送
                    val methods = listOf("sendText", "executeText", "sendToTerminal")
                    for (methodName in methods) {
                        try {
                            invokeMethodWithArg(executor, methodName, text)
                            if (hasMethod(executor, methodName, String::class.java)) {
                                return SendResult.Success("已发送到 Terminal: $text")
                            }
                        } catch (e: Exception) { /* 继续 */ }
                    }
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("ShellCommandExecutor 方式失败: ${e.message}")
        }
        return null
    }

    // ==================== 策略1: 通过焦点组件发送 ====================

    private fun tryFocusedComponent(project: Project, text: String): SendResult? {
        try {
            val focusManager = IdeFocusManager.getInstance(project)
            val focusOwner = focusManager.focusOwner ?: return null

            // 从焦点组件查找 Terminal 相关组件
            var component: java.awt.Component? = focusOwner
            while (component != null) {
                val className = component.javaClass.name.lowercase()
                
                // 新终端架构组件
                if (className.contains("terminalview") || 
                    className.contains("blockterminal") ||
                    className.contains("terminalwidgetimpl")) {
                    val result = sendToTerminalView(component, text)
                    if (result != null) return result
                }
                
                // 经典终端架构组件
                if (className.contains("jediterm") || 
                    className.contains("shellterminal") ||
                    (className.contains("terminal") && className.contains("widget"))) {
                    val result = sendToClassicWidget(component, text)
                    if (result != null) return result
                }

                // 尝试从 DataContext 获取
                val dataResult = tryDataContext(component, text)
                if (dataResult != null) return dataResult

                component = component.parent
            }
        } catch (e: Exception) {
            LOG.debug("通过焦点组件发送失败: ${e.message}")
        }
        return null
    }

    private fun tryDataContext(component: java.awt.Component, text: String): SendResult? {
        try {
            val dataContext = DataManager.getInstance().getDataContext(component)
            
            // 尝试各种 DataKey
            val dataKeys = listOf(
                "TerminalView",
                "TerminalWidget", 
                "terminalWidget",
                "TERMINAL_VIEW",
                "TERMINAL_WIDGET",
                "org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.TERMINAL_VIEW"
            )
            
            for (key in dataKeys) {
                try {
                    val obj = dataContext.getData(key)
                    if (obj != null) {
                        LOG.info("从 DataContext 找到: $key -> ${obj.javaClass.name}")
                        
                        // 尝试作为 TerminalView 发送
                        val viewResult = sendToTerminalView(obj, text)
                        if (viewResult != null) return viewResult
                        
                        // 尝试作为 Widget 发送
                        val widgetResult = sendToNewWidget(obj, text)
                        if (widgetResult != null) return widgetResult
                    }
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("DataContext 查找失败: ${e.message}")
        }
        return null
    }

    // ==================== 策略2: 新终端架构 (IDEA 2024.1+) ====================

    private fun tryNewTerminalArchitecture(project: Project, content: Content, text: String): SendResult? {
        // 方式1: 通过 TerminalToolWindowManager 获取 Widget
        try {
            val managerClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
            val getInstance = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstance.invoke(null, project)
            if (manager != null) {
                // 尝试 findWidgetByContent
                try {
                    val findMethod = managerClass.getMethod("findWidgetByContent", Content::class.java)
                    val widget = findMethod.invoke(manager, content)
                    if (widget != null) {
                        LOG.info("找到 Widget (findWidgetByContent): ${widget.javaClass.name}")
                        val result = sendToNewWidget(widget, text)
                        if (result != null) return result
                    }
                } catch (e: Exception) { /* 继续 */ }

                // 尝试 getActiveTerminalWidget
                try {
                    val activeWidget = invokeMethod(manager, "getActiveTerminalWidget")
                    if (activeWidget != null) {
                        LOG.info("找到 Widget (getActiveTerminalWidget): ${activeWidget.javaClass.name}")
                        val result = sendToNewWidget(activeWidget, text)
                        if (result != null) return result
                    }
                } catch (e: Exception) { /* 继续 */ }

                // 尝试 getTerminalWidgets
                try {
                    val widgets = invokeMethod(manager, "getTerminalWidgets") as? Collection<*>
                    if (!widgets.isNullOrEmpty()) {
                        for (widget in widgets) {
                            if (widget == null) continue
                            LOG.info("尝试 Widget: ${widget.javaClass.name}")
                            val result = sendToNewWidget(widget, text)
                            if (result != null) return result
                        }
                    }
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) {
            LOG.debug("TerminalToolWindowManager 不可用: ${e.message}")
        }

        // 方式2: 通过 Key 从 Content userData 获取
        try {
            val keyNames = listOf("TerminalWidget", "TERMINAL_WIDGET", "terminalWidget")
            for (keyName in keyNames) {
                val widgetKey = Key.findKeyByName(keyName)
                if (widgetKey != null) {
                    @Suppress("UNCHECKED_CAST")
                    val widget = content.getUserData(widgetKey as Key<Any>)
                    if (widget != null) {
                        LOG.info("从 userData 找到 Widget ($keyName): ${widget.javaClass.name}")
                        val result = sendToNewWidget(widget, text)
                        if (result != null) return result
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("从 userData 获取 Widget 失败: ${e.message}")
        }

        // 方式3: 从 DataContext 获取
        val dataResult = tryDataContext(content.component, text)
        if (dataResult != null) return dataResult

        // 方式4: 递归查找新架构组件
        return findAndSendToNewTerminal(content.component, text)
    }

    private fun sendToNewWidget(widget: Any, text: String): SendResult? {
        LOG.info("尝试发送到新 Widget: ${widget.javaClass.name}")
        
        // 尝试 TerminalView
        try {
            val view = getFieldRecursive(widget, "terminalView")
                ?: getFieldRecursive(widget, "view")
                ?: invokeMethod(widget, "getTerminalView")
                ?: invokeMethod(widget, "getView")
            if (view != null) {
                LOG.info("找到 TerminalView: ${view.javaClass.name}")
                val result = sendToTerminalView(view, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 TerminalView 失败: ${e.message}")
        }

        // 尝试 TerminalInput
        try {
            val input = getFieldRecursive(widget, "terminalInput")
                ?: getFieldRecursive(widget, "input")
                ?: invokeMethod(widget, "getTerminalInput")
                ?: invokeMethod(widget, "getInput")
            if (input != null) {
                LOG.info("找到 TerminalInput: ${input.javaClass.name}")
                val result = sendToTerminalInput(input, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 TerminalInput 失败: ${e.message}")
        }

        // 直接在 widget 上尝试各种发送方法
        val sendMethods = listOf(
            "sendText", "sendString", "typeText", "insertText",
            "writeText", "inputText", "typeString"
        )
        for (methodName in sendMethods) {
            try {
                val result = invokeMethodWithArg(widget, methodName, text)
                if (result != null || hasMethod(widget, methodName, String::class.java)) {
                    LOG.info("通过 $methodName 成功发送")
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            } catch (e: Exception) { /* 继续 */ }
        }

        // 尝试 TerminalSession
        try {
            val session = invokeMethod(widget, "getTermSession")
                ?: invokeMethod(widget, "getSession")
                ?: invokeMethod(widget, "getTerminalSession")
                ?: getFieldRecursive(widget, "session")
            if (session != null) {
                LOG.info("找到 Session: ${session.javaClass.name}")
                val result = sendToSession(session, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 Session 失败: ${e.message}")
        }

        // 回退到经典方式
        return sendToClassicWidget(widget, text)
    }

    private fun sendToTerminalView(view: Any, text: String): SendResult? {
        LOG.info("尝试发送到 TerminalView: ${view.javaClass.name}")
        
        // 方式1: sendText(String)
        try {
            val method = view.javaClass.getMethod("sendText", String::class.java)
            method.invoke(view, text)
            return SendResult.Success("已发送到 Terminal: $text")
        } catch (e: Exception) {
            LOG.debug("sendText 失败: ${e.message}")
        }

        // 方式2: createSendTextBuilder().text(text).send()
        try {
            val builderMethod = view.javaClass.getMethod("createSendTextBuilder")
            val builder = builderMethod.invoke(view)
            if (builder != null) {
                // 尝试新的 Builder API
                try {
                    val textMethod = builder.javaClass.getMethod("text", String::class.java)
                    textMethod.invoke(builder, text)
                    val sendMethod = builder.javaClass.getMethod("send")
                    sendMethod.invoke(builder)
                    return SendResult.Success("已发送到 Terminal: $text")
                } catch (e: Exception) {
                    // 尝试旧的 Builder API
                    try {
                        val sendMethod = builder.javaClass.getMethod("send", String::class.java)
                        sendMethod.invoke(builder, text)
                        return SendResult.Success("已发送到 Terminal: $text")
                    } catch (e2: Exception) {
                        LOG.debug("Builder.send 失败: ${e2.message}")
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("createSendTextBuilder 失败: ${e.message}")
        }

        // 方式3: 获取 terminalInput 并发送
        try {
            val input = invokeMethod(view, "getTerminalInput")
                ?: getFieldRecursive(view, "terminalInput")
            if (input != null) {
                val result = sendToTerminalInput(input, text)
                if (result != null) return result
            }
        } catch (e: Exception) {
            LOG.debug("获取 terminalInput 失败: ${e.message}")
        }

        // 方式4: 获取 model 并发送
        try {
            val model = invokeMethod(view, "getModel")
                ?: getFieldRecursive(view, "model")
            if (model != null) {
                val sendMethod = model.javaClass.getMethod("sendText", String::class.java)
                sendMethod.invoke(model, text)
                return SendResult.Success("已发送到 Terminal: $text")
            }
        } catch (e: Exception) {
            LOG.debug("获取 model 失败: ${e.message}")
        }

        return null
    }

    private fun sendToTerminalInput(input: Any, text: String): SendResult? {
        LOG.info("尝试发送到 TerminalInput: ${input.javaClass.name}")
        
        val methods = listOf("sendString", "sendText", "type", "insert", "write")
        for (methodName in methods) {
            try {
                val method = input.javaClass.getMethod(methodName, String::class.java)
                method.invoke(input, text)
                return SendResult.Success("已发送到 Terminal: $text")
            } catch (e: Exception) { /* 继续 */ }
        }
        return null
    }

    private fun sendToSession(session: Any, text: String): SendResult? {
        LOG.info("尝试发送到 Session: ${session.javaClass.name}")
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        // 方式1: sendText
        try {
            val method = session.javaClass.getMethod("sendText", String::class.java)
            method.invoke(session, text)
            return SendResult.Success("已发送到 Terminal: $text")
        } catch (e: Exception) { /* 继续 */ }

        // 方式2: 通过 channel
        try {
            val channel = invokeMethod(session, "getChannel")
                ?: invokeMethod(session, "getOutputChannel")
                ?: getFieldRecursive(session, "channel")
            if (channel != null) {
                try {
                    val method = channel.javaClass.getMethod("write", ByteArray::class.java)
                    method.invoke(channel, bytes)
                    return SendResult.Success("已发送到 Terminal: $text")
                } catch (e: Exception) { /* 继续 */ }
            }
        } catch (e: Exception) { /* 继续 */ }

        // 方式3: 通过 ttyConnector
        try {
            val connector = invokeMethod(session, "getTtyConnector")
                ?: getFieldRecursive(session, "ttyConnector")
            if (connector != null) {
                val isConnected = invokeMethod(connector, "isConnected") as? Boolean ?: false
                if (isConnected) {
                    val method = connector.javaClass.getMethod("write", ByteArray::class.java)
                    method.invoke(connector, bytes)
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            }
        } catch (e: Exception) { /* 继续 */ }

        return null
    }

    private fun findAndSendToNewTerminal(component: java.awt.Component?, text: String): SendResult? {
        if (component == null) return null

        val className = component.javaClass.name.lowercase()

        if (className.contains("terminalview") || 
            className.contains("terminalwidgetimpl") ||
            className.contains("blockterminal")) {
            val result = sendToTerminalView(component, text)
            if (result != null) return result
        }

        if (className.contains("jediterm") || className.contains("shellterminal")) {
            val result = sendToClassicWidget(component, text)
            if (result != null) return result
        }

        if (component is java.awt.Container) {
            for (child in component.components) {
                val result = findAndSendToNewTerminal(child, text)
                if (result != null) return result
            }
        }

        return null
    }

    // ==================== 策略3: 经典终端架构 (IDEA 2023.x) ====================

    private fun tryClassicTerminalArchitecture(content: Content, text: String): SendResult? {
        try {
            val key = Key.findKeyByName("TERMINAL_WIDGET")
            if (key != null) {
                @Suppress("UNCHECKED_CAST")
                val widget = content.getUserData(key as Key<Any>)
                if (widget != null) {
                    val result = sendToClassicWidget(widget, text)
                    if (result != null) return result
                }
            }
        } catch (e: Exception) { /* 继续 */ }

        return findAndSendToClassicTerminal(content.component, text)
    }

    private fun sendToClassicWidget(widget: Any, text: String): SendResult? {
        LOG.info("尝试发送到经典 Widget: ${widget.javaClass.name}")
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        // 方式1: getTerminalStarter().sendString()
        try {
            val starter = invokeMethod(widget, "getTerminalStarter")
            if (starter != null) {
                try {
                    val method = starter.javaClass.getMethod("sendString", String::class.java, Boolean::class.javaPrimitiveType)
                    method.invoke(starter, text, false)
                    return SendResult.Success("已发送到 Terminal: $text")
                } catch (e: Exception) {
                    try {
                        val method = starter.javaClass.getMethod("sendString", String::class.java)
                        method.invoke(starter, text)
                        return SendResult.Success("已发送到 Terminal: $text")
                    } catch (e2: Exception) { /* 继续 */ }
                }
            }
        } catch (e: Exception) { /* 继续 */ }

        // 方式2: getTtyConnector().write()
        try {
            val connector = invokeMethod(widget, "getTtyConnector")
            if (connector != null) {
                val isConnected = invokeMethod(connector, "isConnected") as? Boolean ?: false
                if (isConnected) {
                    val method = connector.javaClass.getMethod("write", ByteArray::class.java)
                    method.invoke(connector, bytes)
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            }
        } catch (e: Exception) { /* 继续 */ }

        // 方式3: 通过内部字段获取 connector
        try {
            val connector = getFieldRecursive(widget, "myTtyConnector")
                ?: getFieldRecursive(widget, "ttyConnector")
            if (connector != null) {
                val isConnected = invokeMethod(connector, "isConnected") as? Boolean ?: false
                if (isConnected) {
                    val method = connector.javaClass.getMethod("write", ByteArray::class.java)
                    method.invoke(connector, bytes)
                    return SendResult.Success("已发送到 Terminal: $text")
                }
            }
        } catch (e: Exception) { /* 继续 */ }

        // 方式4: getTerminal().writeString()
        try {
            val terminal = invokeMethod(widget, "getTerminal")
            if (terminal != null) {
                val method = terminal.javaClass.getMethod("writeString", String::class.java)
                method.invoke(terminal, text)
                return SendResult.Success("已发送到 Terminal: $text")
            }
        } catch (e: Exception) { /* 继续 */ }

        return null
    }

    private fun findAndSendToClassicTerminal(component: java.awt.Component?, text: String): SendResult? {
        if (component == null) return null

        val className = component.javaClass.name

        if (className.contains("Terminal") && className.contains("Widget")) {
            val result = sendToClassicWidget(component, text)
            if (result != null) return result
        }

        if (className.contains("JediTerm")) {
            val result = sendToClassicWidget(component, text)
            if (result != null) return result
        }

        if (component is java.awt.Container) {
            for (child in component.components) {
                val result = findAndSendToClassicTerminal(child, text)
                if (result != null) return result
            }
        }

        return null
    }

    // ==================== 策略4: Robot 模拟粘贴 (macOS 26.2+) ====================

    private fun tryRobotPaste(project: Project, toolWindow: ToolWindow, text: String): SendResult? {
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        if (!isMac) {
            LOG.info("非 macOS 系统，跳过 Robot 粘贴")
            return null
        }

        LOG.info("尝试 macOS Robot 粘贴方案")
        
        try {
            // 设置剪贴板内容
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            
            // 激活终端窗口并模拟粘贴
            toolWindow.activate {
                try {
                    // 等待窗口激活
                    Thread.sleep(200)
                    
                    // 使用 Robot 模拟 Cmd+V
                    val robot = Robot()
                    robot.setAutoDelay(50)
                    
                    // macOS 使用 META (Command) 键
                    robot.keyPress(KeyEvent.VK_META)
                    robot.keyPress(KeyEvent.VK_V)
                    robot.keyRelease(KeyEvent.VK_V)
                    robot.keyRelease(KeyEvent.VK_META)
                    
                    LOG.info("Robot 粘贴命令已发送")
                } catch (e: Exception) {
                    LOG.warn("Robot 粘贴执行失败: ${e.message}", e)
                }
            }
            
            return SendResult.Success("已发送到 Terminal: $text")
        } catch (e: Exception) {
            LOG.warn("Robot 粘贴方案失败: ${e.message}", e)
            return null
        }
    }

    // ==================== 工具方法 ====================

    private fun invokeMethod(obj: Any, methodName: String): Any? {
        try {
            val method = obj.javaClass.getMethod(methodName)
            return method.invoke(obj)
        } catch (e: Exception) { /* 继续 */ }

        try {
            val method = obj.javaClass.getDeclaredMethod(methodName)
            method.isAccessible = true
            return method.invoke(obj)
        } catch (e: Exception) { /* 继续 */ }

        var clazz: Class<*>? = obj.javaClass.superclass
        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod(methodName)
                method.isAccessible = true
                return method.invoke(obj)
            } catch (e: Exception) {
                clazz = clazz.superclass
            }
        }

        return null
    }

    private fun invokeMethodWithArg(obj: Any, methodName: String, arg: String): Any? {
        try {
            val method = obj.javaClass.getMethod(methodName, String::class.java)
            return method.invoke(obj, arg)
        } catch (e: Exception) { /* 继续 */ }

        try {
            val method = obj.javaClass.getDeclaredMethod(methodName, String::class.java)
            method.isAccessible = true
            return method.invoke(obj, arg)
        } catch (e: Exception) { /* 继续 */ }

        return null
    }

    private fun hasMethod(obj: Any, methodName: String, paramType: Class<*>): Boolean {
        return try {
            obj.javaClass.getMethod(methodName, paramType)
            true
        } catch (e: Exception) {
            try {
                obj.javaClass.getDeclaredMethod(methodName, paramType)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun getFieldRecursive(obj: Any, fieldName: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(obj)
            } catch (e: Exception) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    fun isTerminalAvailable(project: Project): Boolean {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID)
        return toolWindow != null && toolWindow.isVisible
    }
}
