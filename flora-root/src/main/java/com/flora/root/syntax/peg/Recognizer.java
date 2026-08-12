package com.flora.root.syntax.peg;

import com.flora.root.syntax.common.exceptions.ParseException;
import com.flora.root.tag.ThreadFragile;

/**
 * 有状态的识别器，类比 {@link java.util.regex.Matcher}：在给定输入上做一次或多次匹配。
 *
 * <p>由 {@link Grammar#recognizer(CharSequence)} 创建。匹配前可用 {@link #region(int, int)}
 * 限定子区间，匹配后可取 {@link #tree()} / {@link #end()} / {@link #failure()}。</p>
 *
 * <p><b>非线程安全</b>：实例保存最近一次匹配的结果（{@link #tree()} / {@link #failure()} 等），
 * 同一实例并发调用匹配方法会互相覆盖状态；与 {@code Matcher} 一样，每个线程应持有自己的实例。</p>
 */
@ThreadFragile("同一实例跨线程并发匹配会互相覆盖最近结果；与 Matcher 一样每个线程应持有自己的实例")
public interface Recognizer {
    /** 从当前位置匹配到末尾（全量匹配）。 */
    boolean matches();

    /** 从当前位置匹配一个前缀（不要求到末尾）。 */
    boolean lookingAt();

    /** 最近一次成功匹配的语法树；未匹配成功返回 {@code null}。 */
    ParseTree tree();

    /** 最近一次匹配结束的字符偏移。 */
    int end();

    /** 最近一次失败的原因；未失败返回 {@code null}。 */
    ParseException failure();

    Recognizer reset(CharSequence input);

    Recognizer region(int start, int end);
}
