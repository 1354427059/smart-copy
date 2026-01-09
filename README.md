# Smart Copy

一个 IntelliJ IDEA 插件，用于快速将选中代码的文件路径和行号信息发送到 Terminal 控制台。

## 功能特性

- 📋 选中代码后，右键菜单点击「Send to SmartCopy」
- 📝 自动提取文件相对路径、起始行号、结束行号
- 🖥️ 发送到当前激活的 Terminal 输入框
- ⌨️ 支持快捷键 `Ctrl+Alt+S`（Mac 上为 `Cmd+Option+S`）
- 🎯 兼容 IDEA 2023.3+ 至 2025.x 所有版本
- 🌐 支持 macOS、Windows、Linux

## 输出格式

```
relative/path/to/File.java:startLine-endLine
```

例如：
```
src/main/java/com/example/MyService.java:10-25
```

## 安装方式

### 方式一：直接下载

1. 下载插件包：[smart-copy-1.0.2.zip](target/smart-copy-1.0.2.zip)
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

1. 在代码编辑器中选中一段代码
2. 右键点击，选择「Send to SmartCopy」
3. 或使用快捷键 `Ctrl+Alt+S`（Mac: `Cmd+Option+S`）
4. 文件路径和行号信息将自动发送到 Terminal

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