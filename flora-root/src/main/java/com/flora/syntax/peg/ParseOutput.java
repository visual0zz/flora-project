package com.flora.syntax.peg;

import com.flora.syntax.definition.Token;
import com.flora.syntax.definition.TokenKind;
import com.flora.syntax.exceptions.ParseException;

import java.util.List;

/**
 * 一次 parse 的结果：同时持有词法层输出（token 列表）与解析层输出（语法树），供链式分别取出。
 *
 * <p>{@code parse()} 失败即在取对象前抛 {@link ParseException}；{@code tryParse()} 不抛，借
 * {@code success()} / {@code error()} 判断。{@code tokens()} 返回词法器产出的全部 token（含
 * {@code kind=SKIP} 的），parser 默认自动跳过 trivia 组别（{@link TokenKind.Category#TRIVIA}）与
 * {@link TokenKind#SKIP}，可用 {@code GrammarOptions.autoSkip(false)} 关闭该跳过。
 */
public final class ParseOutput {
    private final boolean success;
    private final List<Token> tokens;
    private final ParseTree tree;
    private final ParseException error;

    public ParseOutput(boolean success, List<Token> tokens, ParseTree tree, ParseException error) {
        this.success = success;
        this.tokens = tokens;
        this.tree = tree;
        this.error = error;
    }

    public boolean success() { return success; }
    public List<Token> tokens() { return tokens; }
    public ParseTree tree() { return tree; }
    public ParseException error() { return error; }
}
