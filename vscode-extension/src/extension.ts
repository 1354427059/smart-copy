import * as vscode from 'vscode';
import * as path from 'path';

export function activate(context: vscode.ExtensionContext) {
    const sendPathCommand = vscode.commands.registerCommand(
        'smartCopy.sendPathToTerminal',
        () => sendPathToTerminal()
    );

    const sendSelectionCommand = vscode.commands.registerCommand(
        'smartCopy.sendSelectionToTerminal',
        () => sendSelectionToTerminal()
    );

    context.subscriptions.push(sendPathCommand, sendSelectionCommand);
}

async function sendPathToTerminal(): Promise<void> {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showWarningMessage('请先打开一个文件');
        return;
    }

    const selection = editor.selection;
    if (selection.isEmpty) {
        vscode.window.showWarningMessage('请先选中代码');
        return;
    }

    const relativePath = getRelativePath(editor.document.uri);
    const startLine = selection.start.line + 1;
    const endLine = selection.end.line + 1;

    const pathInfo = startLine === endLine
        ? `${relativePath}:${startLine}`
        : `${relativePath}:${startLine}-${endLine}`;

    await sendToTerminal('\n' + pathInfo);
    vscode.window.showInformationMessage('已发送路径到 Terminal');
}

async function sendSelectionToTerminal(): Promise<void> {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showWarningMessage('请先打开一个文件');
        return;
    }

    const selection = editor.selection;
    if (selection.isEmpty) {
        vscode.window.showWarningMessage('请先选中代码');
        return;
    }

    const selectedText = editor.document.getText(selection);
    if (!selectedText.trim()) {
        vscode.window.showWarningMessage('选中内容为空');
        return;
    }

    const relativePath = getRelativePath(editor.document.uri);
    const startLine = selection.start.line + 1;
    const endLine = selection.end.line + 1;

    const fileInfo = startLine === endLine
        ? `${relativePath}:${startLine}`
        : `${relativePath}:${startLine}-${endLine}`;

    const contentToSend = `\n# From: ${fileInfo}\n${selectedText}`;

    await sendToTerminal(contentToSend);
    vscode.window.showInformationMessage('已发送选中内容到 Terminal');
}

function getRelativePath(fileUri: vscode.Uri): string {
    const workspaceFolder = vscode.workspace.getWorkspaceFolder(fileUri);
    if (workspaceFolder) {
        return path.relative(workspaceFolder.uri.fsPath, fileUri.fsPath);
    }
    return path.basename(fileUri.fsPath);
}

async function sendToTerminal(text: string): Promise<void> {
    let terminal = vscode.window.activeTerminal;
    
    if (!terminal) {
        terminal = vscode.window.createTerminal('Smart Copy');
    }
    
    terminal.show(true);
    terminal.sendText(text, false); // false: 不自动按回车
}

export function deactivate() {}
