package com.flora.root.syntax.peg;

import com.flora.root.tag.ModuleEntry;
import com.flora.root.syntax.common.exceptions.ParseException;
import com.flora.root.syntax.common.exceptions.SyntaxException;
import com.flora.root.syntax.peg.impl.Compiler;
import com.flora.root.syntax.peg.impl.MetaParser;
import com.flora.root.syntax.peg.impl.RecognizerImpl;
import com.flora.root.syntax.peg.impl.Validator;

import java.util.List;

/**
 * 通用语法解析器门面，类比 {@link java.util.regex.Pattern}：输入一份 g4 记法语法定义字符串，编译为
 * 内存中的高效识别器，再去识别其它字符串得到 token 列表与语法树。
 *
 * <p>典型链式用法：{@code Grammar.compile(def).parse(input).tokens()} / {@code ...tree()}。</p>
 *
 * <p>线程安全：实例（编译产物）不可变、可被多线程共享，{@link #parse(CharSequence)} /
 * {@link #tryParse(CharSequence)} 每次匹配都使用独立的状态。但 {@link #recognizer(CharSequence)}
 * 返回的有状态识别器不是线程安全的（见 {@link Recognizer}），每个线程应持有自己的实例。</p>
 */
@ModuleEntry
public final class Grammar {
    private final Compiler.CompiledGrammar cg;

    private Grammar(Compiler.CompiledGrammar cg) {
        this.cg = cg;
    }

    /** 编译语法定义字符串；非法 g4 子集（未定义引用、词法规则可空串、左递归等）抛 {@link SyntaxException}。 */
    public static Grammar compile(String definition) {
        return compile(definition, new GrammarOptions());
    }

    public static Grammar compile(String definition, GrammarOptions options) {
        MetaParser.GrammarDef def = MetaParser.parse(definition);
        Validator.Validation v = Validator.validate(def);
        return new Grammar(new Compiler(options).compile(def, v));
    }

    /** 全量匹配；成功返回 token 列表 + 语法树，失败抛 {@link ParseException}。 */
    public ParseOutput parse(CharSequence input) {
        RecognizerImpl r = new RecognizerImpl(cg, input);
        if (r.matches()) {
            return new ParseOutput(true, r.tokens(), r.tree(), null);
        }
        throw r.failure();
    }

    /** 不抛异常版本：借 {@link ParseOutput#success()} / {@link ParseOutput#error()} 判断。 */
    public ParseOutput tryParse(CharSequence input) {
        RecognizerImpl r = new RecognizerImpl(cg, input);
        try {
            if (r.matches()) {
                return new ParseOutput(true, r.tokens(), r.tree(), null);
            }
            return new ParseOutput(false, r.tokens(), null, r.failure());
        } catch (ParseException e) {
            // 词法 / 文法错误一律转为失败结果，不抛
            return new ParseOutput(false, List.of(), null, e);
        }
    }

    /** 输入字符串是否构成一个完整可解析的结构（全量匹配成功）；等价于 {@code tryParse(input).success()}。 */
    public boolean matches(String input) {
        return tryParse(input).success();
    }

    /** 有状态识别器（类比 {@link java.util.regex.Matcher}）。 */
    public Recognizer recognizer(CharSequence input) {
        return new RecognizerImpl(cg, input);
    }

    /** 入口规则名。 */
    public String entry() {
        return cg.entry();
    }
}
