# Smart Copy

一个高效的开发工具插件，支持 **IntelliJ IDEA** 和 **VS Code**。
用于快速将选中代码的文件路径和行号信息，或选中的代码内容直接发送到内置 Terminal 控制台。

**老吕制作**

---

## 核心功能

### 模式 1：发送路径和行号
- 📋 选中代码后，右键菜单点击「Send Path and Line to Terminal」
- 📝 另起一行发送文件相对路径、起始行号、结束行号
- ⏱️ 快捷键：`Ctrl+Alt+S`（Mac: `Cmd+Option+S`）
- 📄 格式：`relative/path/to/File.extension:start-end`

### 模式 2：发送选中内容 ⭐️
- 📝 选中代码后，右键菜单点击「Send Selection to Terminal」
- 🖥️ 另起一行发送选中的代码文本内容到 Terminal
- 📁 自动添加文件路径和行号注释（`# From: path/to/file:10-25`）
- ⏱️ 快捷键：`Ctrl+Alt+Shift+S`（Mac: `Cmd+Option+Shift+S`）

---

## 🧩 IntelliJ IDEA 插件

### 兼容性
- **IDE**: IDEA 2023.3+ 至 2025.x 所有版本
- **系统**: macOS, Windows, Linux
- **终端**: 支持经典终端及 2024/2025 Block Terminal 重置版

### 安装方式
1. **下载**: [smart-copy-1.0.4.zip](build/distributions/smart-copy-1.0.4.zip)
2. **安装**: `File` → `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. **重启**: 重启 IDEA 生效

### 构建命令
```bash
./gradlew buildPlugin
# 产物位置: build/distributions/smart-copy-x.x.x.zip
```

---

## 🧩 VS Code 扩展

### 兼容性
- **VS Code**: 1.74.0+
- **系统**: macOS, Windows, Linux

### 安装方式

#### 方式一：本地打包安装
1. 进入扩展目录并打包：
   ```bash
   cd vscode-extension
   npm install
   # 需要先安装 vsce: npm install -g @vscode/vsce
   npm run package
   ```
2. 在 VS Code 中安装生成的 `.vsix` 文件：
   - 打开扩展面板 (`Ctrl+Shift+X`)
   - 点击右上角 `...` 菜单
   - 选择 `Install from VSIX...`

#### 方式二：源码调试
1. 用 VS Code 打开 `vscode-extension` 文件夹
2. 按 `F5` 启动调试窗口

### 开发命令
```bash
cd vscode-extension
npm run compile   # 编译
npm run watch     # 监听变更
npm run package   # 打包 VSIX
```

---

## 项目结构

```
smart-copy/
├── src/                 # IntelliJ IDEA 插件源码
│   └── main/kotlin/
├── vscode-extension/    # VS Code 扩展源码
│   ├── src/
│   ├── package.json
│   └── tsconfig.json
├── build.gradle.kts     # IDEA 插件构建配置
└── README.md
```

## 版本历史

### VS Code Extension v1.0.0
- 首次发布
- 完整支持模式1（路径+行号）和模式2（代码内容）
- 快捷键与 IDEA 版保持一致

### IDEA Plugin v1.0.4
- 优化模式2：发送内容时另起一行，避免与终端当前内容混淆
- 优化模式2：自动添加文件路径和行号注释

## 许可证

MIT License

## 贡献与反馈

如有问题或建议，请通过 [GitHub Issues](https://github.com/your-username/smart-copy/issues) 联系。
