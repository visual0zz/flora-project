package com.flora.tangle.testbed.tricky;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 内部类、匿名类、静态内部类测试类，验证混淆器对各种内部类类型的处理。
 * <p>
 * 包含静态内部类、成员内部类、方法内部类、匿名内部类等多种场景，
 * 确保混淆后的字节码中内部类与外部类的关系保持正确。
 * </p>
 */
public final class InnerClassTest {

    /** 外部类私有字段 */
    private final String outerField = "外部字段值";

    /** 外部类私有静态字段 */
    private static final String OUTER_STATIC = "外部静态字段";

    /**
     * 静态内部类 - 不持有对外部类的引用。
     */
    public static class StaticInner {
        private final String name;
        private static int instanceCount = 0;

        /**
         * 构造一个静态内部类实例。
         *
         * @param name 名称
         */
        public StaticInner(String name) {
            this.name = name;
            instanceCount++;
        }

        /**
         * 获取名称。
         *
         * @return 名称
         */
        public String getName() {
            return name;
        }

        /**
         * 访问外部类的静态成员。
         *
         * @return 外部类静态字段值
         */
        public String accessOuterStatic() {
            return OUTER_STATIC;
        }

        /**
         * 获取静态内部类的实例计数。
         *
         * @return 实例数
         */
        public static int getInstanceCount() {
            return instanceCount;
        }

        /**
         * 渲染结果。
         *
         * @return 结果字符串
         */
        public String render() {
            return "StaticInner{name=" + name + ", count=" + instanceCount + "}";
        }
    }

    /**
     * 成员内部类 - 持有对外部类的隐式引用。
     */
    public class MemberInner {
        private final int id;
        private final String label;

        /**
         * 构造一个成员内部类实例。
         *
         * @param id    标识
         * @param label 标签
         */
        public MemberInner(int id, String label) {
            this.id = id;
            this.label = label;
        }

        /**
         * 获取完整的标签（含外部类字段）。
         *
         * @return 完整标签
         */
        public String getFullLabel() {
            // 访问外部类的实例字段
            return label + "(" + outerField + ")";
        }

        /**
         * 获取 ID。
         *
         * @return 标识
         */
        public int getId() {
            return id;
        }

        /**
         * 渲染结果。
         *
         * @return 结果字符串
         */
        public String render() {
            return "MemberInner{id=" + id + ", label=" + label
                    + ", outer=" + outerField + "}";
        }
    }

    /**
     * 使用成员内部类。
     *
     * @return 渲染结果
     */
    public String useMemberInner() {
        MemberInner mi = new MemberInner(1, "测试成员内部类");
        return mi.getFullLabel();
    }

    /**
     * 执行所有内部类操作并返回结果摘要。
     *
     * @return 格式为 "InnerClassTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder result = new StringBuilder();

        // ---- 1. 静态内部类 ----
        StaticInner si1 = new StaticInner("实例A");
        StaticInner si2 = new StaticInner("实例B");
        StaticInner si3 = new StaticInner("实例C");
        result.append("静态内部类: ").append(si1.render())
                .append(", 访问外部静态: ").append(si1.accessOuterStatic())
                .append(", 总实例数=").append(StaticInner.getInstanceCount());

        // ---- 2. 成员内部类 ----
        InnerClassTest outer = new InnerClassTest();
        MemberInner mi1 = outer.new MemberInner(10, "项一");
        MemberInner mi2 = outer.new MemberInner(20, "项二");
        result.append(" | 成员内部类: ").append(mi1.render())
                .append(", ").append(mi2.render())
                .append(", getFullLabel: ").append(mi1.getFullLabel());

        // ---- 3. 方法内部类（方法中定义的类） ----
        class MethodInner {
            private final int x;
            private final int y;

            MethodInner(int x, int y) {
                this.x = x;
                this.y = y;
            }

            int sum() {
                return x + y;
            }

            int product() {
                return x * y;
            }

            @Override
            public String toString() {
                return "MethodInner{x=" + x + ", y=" + y
                        + ", sum=" + sum() + ", prod=" + product() + "}";
            }
        }

        MethodInner mInner1 = new MethodInner(3, 4);
        MethodInner mInner2 = new MethodInner(5, 7);
        result.append(" | 方法内部类: ").append(mInner1)
                .append(", ").append(mInner2);

        // ---- 4. 匿名内部类（Runnable） ----
        StringBuilder runResult = new StringBuilder();
        Runnable runner = new Runnable() {
            @Override
            public void run() {
                runResult.append("匿名Runnable执行");
            }
        };
        runner.run();
        result.append(" | 匿名Runnable: ").append(runResult);

        // ---- 5. 匿名内部类（Comparator） ----
        String[] names = {"张三", "李四", "王五", "赵六", "钱七"};
        Comparator<String> nameComparator = new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                // 按字符串长度降序，相同则按自然序
                int lenCmp = Integer.compare(b.length(), a.length());
                if (lenCmp != 0) {
                    return lenCmp;
                }
                return a.compareTo(b);
            }
        };
        Arrays.sort(names, nameComparator);
        result.append(" | 匿名Comparator: ").append(Arrays.toString(names));

        // ---- 6. 多重嵌套的匿名内部类 ----
        Runnable nested = new Runnable() {
            @Override
            public void run() {
                Comparable<String> comp = new Comparable<String>() {
                    @Override
                    public int compareTo(String o) {
                        // 仅用于证明嵌套匿名类不崩溃
                        return o.length() - 5;
                    }
                };
                @SuppressWarnings("unused")
                int ignored = comp.compareTo("测试");
            }
        };
        nested.run();
        result.append(" | 嵌套匿名类: 正常执行");

        // ---- 7. 继承自静态内部类的匿名类 ----
        StaticInner anonInner = new StaticInner("匿名子类") {
            @Override
            public String render() {
                return "匿名子类:" + getName();
            }
        };
        result.append(" | 匿名继承静态内部类: ").append(anonInner.render());

        return "InnerClassTest:OK:" + result.toString();
    }
}
