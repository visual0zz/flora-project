package com.flora.root.container;

import com.flora.root.java.CheckUtil;
import com.flora.root.tag.ReadOnly;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Variant：任意多个类型任取其一的值容器（N 元 sum type，对应 C++ {@code std::variant}）。
 * <p>声明阶段给出替代类型表（{@link #of(Class[])}，须非空且不重复）；之后恰好持有一个值，
 * 该值的运行时类型必须是替代类型表之一，否则抛 {@link IllegalArgumentException}。
 * 无值状态（对应 {@code std::variant} 的 {@code valueless_by_exception}）用
 * {@link #isValueless()} 判断。</p>
 * <p>不可变：{@code set} / {@code clear} 返回新实例，原实例不受影响。
 * 值允许为 {@code null}，但 null 无法自动匹配类型，须用 {@link #set(int, Object)} 或
 * {@link #set(Class, Object)} 显式指定。替代类型用包装类（如 {@code Integer.class}），
 * 自动匹配按声明顺序取第一个 {@code isInstance} 命中者。</p>
 * <p>与 JDK 生态兼容：{@link #get(Class)} / {@link #getOrElse(Class, Object)} 与
 * {@link Optional} 无缝互转，{@link #stream()} 产出当前值单元素流，
 * {@link #visit} 接受 {@code java.util.function.Function} 做模式匹配，实例可序列化。</p>
 */
@ReadOnly
public final class Variant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 无值状态的索引。 */
    private static final int VALUELESS = -1;

    private final Class<?>[] types;
    private final int index;
    private final Object value;

    private Variant(Class<?>[] types, int index, Object value) {
        this.types = types;
        this.index = index;
        this.value = value;
    }

    /**
     * 声明替代类型表并创建无值 Variant。
     *
     * @param types 替代类型（须非空、非 null、不重复），建议使用包装类
     * @return 无值 Variant
     * @throws IllegalArgumentException 类型表为空、含 null 或含重复类型时
     */
    public static Variant of(Class<?>... types) {
        CheckUtil.notNull(types, "替代类型表不能为空");
        CheckUtil.mustTrue(types.length > 0, "替代类型表至少需要一个类型");
        Class<?>[] copy = types.clone();
        for (int i = 0; i < copy.length; i++) {
            CheckUtil.notNull(copy[i], "替代类型不能为 null");
            for (int j = 0; j < i; j++) {
                CheckUtil.mustTrue(copy[j] != copy[i], "替代类型不能重复: " + copy[i].getName());
            }
        }
        return new Variant(copy, VALUELESS, null);
    }

    /**
     * 声明替代类型表并持有指定值（自动匹配类型）。
     *
     * @param value 初始值；null 时无法自动匹配，请改用 {@code of(types).set(Class, null)}
     * @param types 替代类型
     * @return 持有值的 Variant
     * @throws IllegalArgumentException 类型表非法或值不属于任一替代类型
     */
    public static Variant of(Object value, Class<?>... types) {
        return of(types).set(value);
    }

    /**
     * @return 替代类型数量
     */
    public int size() {
        return types.length;
    }

    /**
     * @return 是否无值（valueless）
     */
    public boolean isValueless() {
        return index < 0;
    }

    /**
     * @return 当前持有值在类型表中的索引；无值时返回 -1
     */
    public int index() {
        return index;
    }

    /**
     * @return 当前持有值的声明类型；无值时返回 null
     */
    public Class<?> currentType() {
        return index < 0 ? null : types[index];
    }

    /**
     * @param type 替代类型
     * @return 当前是否持有该类型（按类型表相等比较，与值无关）
     */
    public boolean holds(Class<?> type) {
        return index >= 0 && types[index] == type;
    }

    /**
     * @param i 类型表索引
     * @return 当前是否持有索引为 i 的类型
     */
    public boolean holdsIndex(int i) {
        return index == i;
    }

    /**
     * 设置值（自动匹配类型），返回新实例。
     *
     * @param value 值；null 无法自动匹配，请用 {@link #set(Class, Object)} 或 {@link #set(int, Object)}
     * @return 持有新值的 Variant
     * @throws IllegalArgumentException 值不属于任一替代类型
     */
    public Variant set(Object value) {
        Objects.requireNonNull(value, "null 值请用 set(Class, Object) 或 set(int, Object) 显式指定类型");
        for (int i = 0; i < types.length; i++) {
            if (types[i].isInstance(value)) {
                return new Variant(types, i, value);
            }
        }
        throw new IllegalArgumentException("值类型 " + value.getClass().getName()
                + " 不在替代类型表中: " + Arrays.toString(types));
    }

    /**
     * 按类型表索引设置值，返回新实例。
     *
     * @param i     类型表索引
     * @param value 值（可为 null）
     * @return 持有新值的 Variant
     * @throws IllegalArgumentException 索引越界或值类型与该索引的声明类型不符
     */
    public Variant set(int i, Object value) {
        CheckUtil.mustTrue(i >= 0 && i < types.length, "类型表索引越界: " + i);
        checkAssignable(i, value);
        return new Variant(types, i, value);
    }

    /**
     * 按声明类型设置值，返回新实例。
     *
     * @param type  替代类型
     * @param value 值（可为 null）
     * @return 持有新值的 Variant
     * @throws IllegalArgumentException 类型不在表中或值类型与该类型不符
     */
    public Variant set(Class<?> type, Object value) {
        int i = indexOf(type);
        if (i < 0) {
            throw new IllegalArgumentException("类型不在替代类型表中: " + type.getName());
        }
        checkAssignable(i, value);
        return new Variant(types, i, value);
    }

    /**
     * 清空值，返回无值新实例。
     *
     * @return 无值 Variant
     */
    public Variant clear() {
        return new Variant(types, VALUELESS, null);
    }

    /**
     * 当前值（可为 null）。
     *
     * @return 当前值
     * @throws NoSuchElementException 无值时
     */
    public Object value() {
        if (index < 0) {
            throw new NoSuchElementException("Variant 无值");
        }
        return value;
    }

    /**
     * 按声明类型取当前值。
     *
     * @param type 替代类型
     * @param <T>  值类型
     * @return 当前持有该类型时的值（可为 null）；否则为 {@link Optional#empty()}
     */
    public <T> Optional<T> get(Class<T> type) {
        if (!holds(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(type.cast(value));
    }

    /**
     * 按声明类型取当前值；类型不匹配或为 null 时返回默认值。
     *
     * @param type         替代类型
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 当前值或默认值
     */
    public <T> T getOrElse(Class<T> type, T defaultValue) {
        return get(type).orElse(defaultValue);
    }

    /**
     * 按声明类型强取当前值。
     *
     * @param type 替代类型
     * @param <T>  值类型
     * @return 当前值（可为 null）
     * @throws IllegalArgumentException 当前不持有该类型时
     */
    public <T> T getValue(Class<T> type) {
        if (!holds(type)) {
            throw new IllegalArgumentException("当前不持有类型 " + type.getName()
                    + "（实际: " + (index < 0 ? "无值" : currentType().getName()) + "）");
        }
        return type.cast(value);
    }

    /**
     * 模式匹配：单一访问器作用于当前值。
     * <p>调用方配合 {@link #index()} / {@link #currentType()} 自行分派与类型转换。</p>
     *
     * @param visitor 值访问器（接收当前值，可为 null）
     * @param <R>     结果类型
     * @return 访问结果
     * @throws NoSuchElementException 无值时
     */
    public <R> R visit(Function<Object, R> visitor) {
        Objects.requireNonNull(visitor, "visitor 不能为空");
        return visitor.apply(value());
    }

    /**
     * 模式匹配：按类型表索引分派到对应访问器（类似 {@code std::visit} 的分支集）。
     *
     * @param visitors 访问器数组，长度必须等于 {@link #size()}；valueless 时须提供兜底
     *                 （可把某一元素设为忽略参数的兜底访问器）
     * @param <R>      结果类型
     * @return 对应访问器的结果
     * @throws IllegalArgumentException 访问器数量与类型表不符，或 valueless 时无兜底
     */
    public <R> R visit(Function<Object, R>... visitors) {
        CheckUtil.notNull(visitors, "visitors 不能为空");
        CheckUtil.mustTrue(visitors.length == types.length,
                "访问器数量 " + visitors.length + " 须等于替代类型数量 " + types.length);
        if (index < 0) {
            throw new NoSuchElementException("Variant 无值，无法按索引分派");
        }
        return visitors[index].apply(value);
    }

    /**
     * 当前值单元素流；无值时为空流。可直接与 JDK Stream 管线衔接。
     *
     * @return 当前值流
     */
    public Stream<Object> stream() {
        return index < 0 ? Stream.empty() : Stream.of(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Variant that)) return false;
        return index == that.index && Arrays.equals(types, that.types) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(types), index, value);
    }

    @Override
    public String toString() {
        return index < 0
                ? "Variant{valueless, types=" + Arrays.toString(types) + '}'
                : "Variant{index=" + index + "/" + types.length + ", type=" + types[index].getSimpleName()
                        + ", value=" + value + '}';
    }

    private int indexOf(Class<?> type) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                return i;
            }
        }
        return -1;
    }

    private void checkAssignable(int i, Object v) {
        CheckUtil.mustTrue(v == null || types[i].isInstance(v),
                "值类型 " + (v == null ? "null" : v.getClass().getName())
                        + " 与声明类型 " + types[i].getName() + " 不符");
    }
}
