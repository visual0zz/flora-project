package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.ParseException;
import com.flora.syntax.peg.ParseTree;
import com.flora.syntax.peg.Recognizer;
import com.flora.syntax.peg.Token;
import com.flora.syntax.peg.TokenKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别器运行时：先 lex 得全部 token（含 SKIP），过滤出显著 token（跳过 Trivia / SKIP），再在显著流上
 * 跑 token 级 PEG（packrat），建 {@link ParseTree} 并回填字符偏移。失败收集最远失败位置 + 期望项。
 */
public final class RecognizerImpl implements Recognizer {

    private final Compiler.CompiledGrammar cg;
    private CharSequence base;
    private int regionStart;
    private int regionEnd;

    private List<Token> allTokens;
    private List<Token> sig;
    private ParseTree lastTree;
    private ParseException lastFailure;
    private boolean matched;

    public RecognizerImpl(Compiler.CompiledGrammar cg, CharSequence base) {
        this.cg = cg;
        this.base = base;
        this.regionStart = 0;
        this.regionEnd = base.length();
    }    @Override
    public boolean matches() {
        return run(true);
    }

    @Override
    public boolean lookingAt() {
        return run(false);
    }

    @Override
    public ParseTree tree() {
        return lastTree;
    }

    @Override
    public int end() {
        return lastTree != null ? lastTree.end() : 0;
    }

    @Override
    public ParseException failure() {
        return lastFailure;
    }

    /** 词法器产出的全部 token（含 {@code kind=SKIP} 的，含 EOF 哨兵）。 */
    public List<Token> tokens() {
        return allTokens;
    }

    @Override
    public Recognizer reset(CharSequence input) {
        this.base = new StringBuilder(input);
        this.regionStart = 0;
        this.regionEnd = base.length();
        this.allTokens = null;
        this.sig = null;
        this.lastTree = null;
        this.lastFailure = null;
        this.matched = false;
        return this;
    }

    @Override
    public Recognizer region(int start, int end) {
        this.regionStart = Math.max(0, start);
        this.regionEnd = Math.min(base.length(), end);
        return this;
    }

    private boolean run(boolean fullMatch) {
        CharSequence slice = base.subSequence(regionStart, regionEnd);
        allTokens = cg.lexer().lex(slice);
        sig = new ArrayList<>();
        for (Token t : allTokens) {
            if (!TokenKind.autoSkipped(t.kind())) sig.add(t);
        }
        Matchers.Run r = new Matchers.Run(sig);
        Matchers.Matched m = cg.entryMatcher().run(r, 0);
        if (m == null) {
            matched = false;
            lastTree = null;
            lastFailure = buildError(r);
            return false;
        }
        if (fullMatch && m.consumed() != sig.size() - 1) {
            matched = false;
            lastTree = null;
            r.fail(m.consumed(), "<EOF>");
            lastFailure = buildError(r);
            return false;
        }
        matched = true;
        lastTree = m.children().get(0);
        lastFailure = null;
        return true;
    }

    private ParseException buildError(Matchers.Run r) {
        int fi = Math.max(0, Math.min(r.furthest, sig.size() - 1));
        Token t = sig.get(fi);
        String expected = r.expected.isEmpty() ? "<输入结束>" : r.expectedText();
        String got = t.kind() instanceof TokenKind.Eof ? "<EOF>" : "'" + t.text() + "'";
        return new ParseException("期望 " + expected + " 但遇到 " + got, t.line(), t.column(), t.start(), expected);
    }
}
