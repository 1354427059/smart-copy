# Smart Copy

一个 IntelliJ IDEA 插件，用于快速将选中代码的文件路径和行号信息，或选中的代码内容发送到 Terminal 控制台。

**老吕制作**

## 功能特性

### 模式 1：发送路径和行号
- 📋 选中代码后，右键菜单点击「Send Path and Line to Terminal」
- 📝 另起一行发送文件相对路径、起始行号、结束行号
- ⏱️ 支持快捷键 `Ctrl+Alt+S`（Mac 上为 `Cmd+Option+S`）

### 模式 2：发送选中内容 ⭐️
- 📝 选中代码后，右键菜单点击「Send Selection to Terminal」
- 🖥️ 另起一行发送选中的代码文本内容到 Terminal
- 📁 自动添加文件路径和行号注释（# From: path/to/file:10-25）
- ⏱️ 支持快捷键 `Ctrl+Alt+Shift+S`（Mac 上为 `Cmd+Option+Shift+S`）

### 通用特性
- 🎯 自动检测终端类型（经典终端 / 2024/2025 Block Terminal 重置版）
- 🎯 兼容 IDEA 2023.3+ 至 2025.x 所有版本
- 🌐 支持 macOS、Windows、Linux

## 输出格式

### 模式 1：路径和行号
```
relative/path/to/File.java:startLine-endLine
```

例如：
```
src/main/java/com/example/MyService.java:10-25
```

### 模式 2：选中内容
另起一行发送，自动添加文件路径和行号注释和代码内容：
```bash

# From: src/main/java/com/example/MyService.java:10-12
public void myMethod() {
    System.out.println("Hello World");
}
```

## 安装方式

### 方式一：直接下载

1. 下载插件包：[smart-copy-1.0.4.zip](build/distributions/smart-copy-1.0.4.zip)
2. 在 IDEA 中：`File` → `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. 选择下载的 `.zip` 文件，重启 IDEA

### 方式二：手动构建

```bash
# 克隆仓库
git clone https://github.com/your-username/smart-copy.git
cd smart-copy

# 使用 Gradle 构建
./gradlew buildPlugin

# 生成的插件位于 build/distributions/smart-copy-x.x.x.zip
```

## 使用方法

### 模式 1：发送路径和行号
1. 在代码编辑器中选中一段代码
2. 右键点击，选择「Send Path and Line to Terminal」
3. 或使用快捷键 `Ctrl+Alt+S`（Mac: `Cmd+Option+S`）
4. 终端将另起一行，显示文件路径和行号信息

### 模式 2：发送选中内容
1. 在代码编辑器中选中一段代码
2. 右键点击，选择「Send Selection to Terminal」
3. 或使用快捷键 `Ctrl+Alt+Shift+S`（Mac: `Cmd+Option+Shift+S`）
4. 终端将另起一行，显示文件路径和行号注释和选中的代码内容

## 开发环境

- **Kotlin**: 1.9.25
- **IntelliJ Platform**: 2023.3+
- **Gradle**: 8.10
- **JDK**: 17

## 构建命令

```bash
# 构建插件
./gradlew buildPlugin

# 验证插件配置
./gradlew verifyPluginConfiguration

# 运行测试
./gradlew test
```

## 项目结构

```
smart-copy/
├── src/
│   └── main/
│       ├── kotlin/com/smartcopy/
│       │   ├── action/          # Action 定义
│       │   ├── service/         # Terminal 服务
│       │   └── util/            # 工具类
│       └── resources/
│           └── META-INF/
│               └── plugin.xml   # 插件配置
├── build.gradle.kts             # Gradle 构建配置
├── gradle.properties            # Gradle 属性配置
└── settings.gradle.kts          # Gradle 设置
```

## 版本历史

### 1.0.4
- 优化模式2：发送内容时另起一行，避免与终端当前内容混淆
- 优化模式2：自动添加文件路径和行号注释（# From: path/to/file:10-25）

### 1.0.3
- 新增发送选中内容功能（Ctrl+Alt+Shift+S）
- 两种模式：发送路径+行号 或 发送选中代码内容

### 1.0.2
- 输出格式改为文件相对路径（相对于项目根目录）
- 支持 IDEA 2024/2025 Block Terminal（重置版终端）

### 1.0.1
- 兼容 IDEA 2020.3 至 2025.x 所有版本
- 支持 macOS、Windows、Linux
- 改进 Terminal 发送兼容性

### 1.0.0
- 初始版本发布
- 支持选中代码发送到 Terminal

## 作者

老吕制作

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 [GitHub Issues](https://github.com/your-username/smart-copy/issues) 联系。