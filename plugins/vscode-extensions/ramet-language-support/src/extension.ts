import * as vscode from 'vscode';

/**
 * Ramet 模板语言 VS Code 扩展入口。
 *
 * 注册两个 DefinitionProvider：
 * - Include 路径跳转：`<#include "path.ramet">` → 打开目标文件
 * - 表达式跳转：`${varName}` → `@Param{ varName }`、`<@macroName>` → `<#macro macroName>`
 */
export function activate(context: vscode.ExtensionContext) {
    const rametDocSelector: vscode.DocumentSelector = {
        language: 'ramet',
        scheme: 'file'
    };

    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(
            rametDocSelector,
            new RametIncludeProvider()
        )
    );

    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(
            rametDocSelector,
            new RametExpressionProvider()
        )
    );
}

export function deactivate() {}

// ============================================================
// Include 跳转：<#include "path.ramet"> → 目标文件
// ============================================================

class RametIncludeProvider implements vscode.DefinitionProvider {

    provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.Definition | vscode.LocationLink[]> {
        const lineText = document.lineAt(position.line).text;
        const includeMatch = this.matchIncludeAtPos(lineText, position.character);
        if (!includeMatch) {
            return null;
        }

        const path = includeMatch;
        // 提取文件名部分
        const fileName = path.includes('/') ? path.substring(path.lastIndexOf('/') + 1) : path;

        // 在工作区中查找匹配的 .ramet 文件
        return vscode.workspace.findFiles(`**/${fileName}`, '**/node_modules/**', 10)
            .then(files => {
                if (files.length === 0) return null;

                // 优先匹配完整路径，否则用第一个
                const exact = files.find(f => f.fsPath.replace(/\\/g, '/').endsWith(path));
                const target = exact || files[0];
                return new vscode.Location(target, new vscode.Position(0, 0));
            });
    }

    /** 检查 position 是否在 <#include "..." > 的引号内，返回引号内的路径。 */
    private matchIncludeAtPos(line: string, col: number): string | null {
        const re = /<#include\s+"([^"]+)"/g;
        let match: RegExpExecArray | null;
        while ((match = re.exec(line)) !== null) {
            const qStart = match.index + match[0].indexOf('"') + 1;
            const qEnd = qStart + match[1].length;
            if (col >= qStart && col <= qEnd) {
                return match[1];
            }
        }
        return null;
    }
}

// ============================================================
// 表达式跳转：${varName} → @Param / <@macroName> → <#macro>
// ============================================================

class RametExpressionProvider implements vscode.DefinitionProvider {

    provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.Definition | vscode.LocationLink[]> {
        const text = document.getText();

        // 1. 检查是否是宏调用 <@name>
        const macroCallName = this.matchMacroCallAtPos(text, position);
        if (macroCallName) {
            return this.findMacroDefinition(document, macroCallName);
        }

        // 2. 检查是否是变量引用 ${var} 或指令中的 var
        const varName = this.matchVarRefAtPos(text, position);
        if (varName) {
            return this.findParamDefinition(document, varName);
        }

        return null;
    }

    /** 检查 position 是否在 <@name 的宏名上。 */
    private matchMacroCallAtPos(text: string, pos: vscode.Position): string | null {
        const lines = text.split('\n');
        const line = lines[pos.line] || '';
        const offset = pos.character;

        const re = /<@([a-zA-Z_]\w*)/g;
        let match: RegExpExecArray | null;
        while ((match = re.exec(line)) !== null) {
            const start = match.index + 2; // 跳过 <@
            const end = start + match[1].length;
            if (offset >= start && offset <= end) {
                return match[1];
            }
        }
        return null;
    }

    /** 检查 position 是否在 ${var} 或指令中的 var 上。 */
    private matchVarRefAtPos(text: string, pos: vscode.Position): string | null {
        const lines = text.split('\n');
        const line = lines[pos.line] || '';

        // 排除关键字
        const isKeyword = (w: string) =>
            /^(true|false|null|greaterThan|lessThan|greaterThanOrEquals|lessThanOrEquals|equals|notEquals|and|or|not|if|list|include|macro|else|capital|lower|upper|concat|contains|replace|startsWith|repeat|join|default|notNull|isNull|isEmpty|isBlank|range|sequenceJoin|length|javaPackageToPath)$/i.test(w);

        // 匹配 ${identifier} / ${identifier.property}
        const interpRe = /\$\{([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\}/g;
        let match: RegExpExecArray | null;
        while ((match = interpRe.exec(line)) !== null) {
            const dStart = match.index + 2; // 跳过 ${
            const dEnd = dStart + match[1].length;
            if (pos.character >= dStart && pos.character <= dEnd) {
                const name = match[1].split('.')[0];
                return isKeyword(name) ? null : name;
            }
        }

        // 匹配指令中的裸标识符（不在注释和字符串中）
        const idRe = /\b([a-zA-Z_]\w*)\b/g;
        while ((match = idRe.exec(line)) !== null) {
            // 检查是否在 <#...> 或 <@...> 内部
            const lineBefore = line.substring(0, match.index);
            if (!lineBefore.includes('<#')) continue;

            if (pos.character >= match.index && pos.character <= match.index + match[1].length) {
                const name = match[1];
                if (isKeyword(name)) continue;
                return name;
            }
        }

        return null;
    }

    /** 在当前文件或其他 .ramet 文件中查找 <#macro macroName> 定义。 */
    private findMacroDefinition(
        document: vscode.TextDocument,
        name: string
    ): Thenable<vscode.Location | null> {
        // 先搜当前文件
        const localMatch = this.findMacroInText(document.getText(), name);
        if (localMatch) {
            const pos = document.positionAt(localMatch);
            return Promise.resolve(new vscode.Location(document.uri, pos));
        }
        // 再搜工作区中所有 .ramet 文件
        return vscode.workspace.findFiles('**/*.ramet', '**/node_modules/**', 100)
            .then(files => {
                for (const uri of files) {
                    if (uri.toString() === document.uri.toString()) continue;
                    return vscode.workspace.openTextDocument(uri).then(doc => {
                        const offset = this.findMacroInText(doc.getText(), name);
                        if (offset !== null) {
                            return new vscode.Location(uri, doc.positionAt(offset));
                        }
                        return null;
                    });
                }
                return null;
            });
    }

    private findMacroInText(text: string, name: string): number | null {
        const re = new RegExp(`<#macro\\s+${escapeRegex(name)}(?:\\s|>)`, 'g');
        const match = re.exec(text);
        return match ? match.index : null;
    }

    /** 在文件的注释块中查找 @Param{ name: } 定义。 */
    private findParamDefinition(
        document: vscode.TextDocument,
        name: string
    ): vscode.Location | null {
        const text = document.getText();

        // 先找到 @Param 块，然后在里面找 name:
        const paramBlockRe = /@Param\{([^}]*)\}/g;
        let blockMatch: RegExpExecArray | null;
        while ((blockMatch = paramBlockRe.exec(text)) !== null) {
            const blockBody = blockMatch[1];
            const keyRe = new RegExp(`\\b${escapeRegex(name)}\\s*:`, 'g');
            if (keyRe.test(blockBody)) {
                // 找到 name: 在 block 中的位置，转化为文档位置
                const nameIdx = blockBody.indexOf(name);
                const absoluteIdx = blockMatch.index + blockMatch[0].indexOf('{') + 1 + nameIdx;
                const pos = document.positionAt(absoluteIdx);
                return new vscode.Location(document.uri, pos);
            }
        }

        return null;
    }
}

function escapeRegex(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
