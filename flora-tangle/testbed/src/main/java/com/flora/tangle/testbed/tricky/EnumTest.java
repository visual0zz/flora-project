package com.flora.tangle.testbed.tricky;

/**
 * 枚举类型测试类，验证混淆器对枚举类型的处理。
 * <p>
 * 包含带字段和构造器的枚举、实现接口的枚举、
 * 带抽象方法的枚举、枚举集合操作等场景。
 * </p>
 */
public final class EnumTest {

    /**
     * 枚举实现的接口，每个方向提供描述。
     */
    public interface Describable {
        /** 获取方向描述。 */
        String describe();

        /** 获取反方向。 */
        Direction opposite();
    }

    /**
     * 方向枚举，每个常量携带角度值、实现 Describable 接口。
     */
    public enum Direction implements Describable {
        /** 北 - 0度 */
        NORTH(0) {
            @Override
            public String describe() {
                return "北方向，指向正北";
            }

            @Override
            public Direction opposite() {
                return SOUTH;
            }
        },
        /** 东 - 90度 */
        EAST(90) {
            @Override
            public String describe() {
                return "东方向，指向正东";
            }

            @Override
            public Direction opposite() {
                return WEST;
            }
        },
        /** 南 - 180度 */
        SOUTH(180) {
            @Override
            public String describe() {
                return "南方向，指向正南";
            }

            @Override
            public Direction opposite() {
                return NORTH;
            }
        },
        /** 西 - 270度 */
        WEST(270) {
            @Override
            public String describe() {
                return "西方向，指向正西";
            }

            @Override
            public Direction opposite() {
                return EAST;
            }
        };

        /** 角度值 */
        private final int angle;

        /**
         * 枚举构造器。
         *
         * @param angle 方向角度
         */
        Direction(int angle) {
            this.angle = angle;
        }

        /**
         * 获取角度值。
         *
         * @return 角度
         */
        public int getAngle() {
            return angle;
        }

        /**
         * 根据角度查找方向。
         *
         * @param angle 角度
         * @return 对应的方向，未找到返回 null
         */
        public static Direction fromAngle(int angle) {
            int normalized = ((angle % 360) + 360) % 360;
            for (Direction d : values()) {
                if (d.angle == normalized) {
                    return d;
                }
            }
            return null;
        }
    }

    /**
     * 操作状态枚举，带数据字段和抽象方法。
     */
    public enum Status {
        /** 待处理 */
        PENDING("待处理") {
            @Override
            public boolean canTransitionTo(Status target) {
                return target == PROCESSING || target == CANCELLED;
            }
        },
        /** 处理中 */
        PROCESSING("处理中") {
            @Override
            public boolean canTransitionTo(Status target) {
                return target == COMPLETED || target == FAILED || target == PENDING;
            }
        },
        /** 已完成 */
        COMPLETED("已完成") {
            @Override
            public boolean canTransitionTo(Status target) {
                return false; // 终态
            }
        },
        /** 已取消 */
        CANCELLED("已取消") {
            @Override
            public boolean canTransitionTo(Status target) {
                return false; // 终态
            }
        },
        /** 失败 */
        FAILED("失败") {
            @Override
            public boolean canTransitionTo(Status target) {
                return target == PENDING; // 允许重试
            }
        };

        /** 中文名称 */
        private final String displayName;

        /**
         * 构造状态。
         *
         * @param displayName 中文显示名
         */
        Status(String displayName) {
            this.displayName = displayName;
        }

        /**
         * 获取中文显示名。
         *
         * @return 显示名
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * 判断是否能转换到目标状态（抽象方法）。
         *
         * @param target 目标状态
         * @return 可转换返回 true
         */
        public abstract boolean canTransitionTo(Status target);
    }

    /**
     * 季度枚举，使用 values() 和 valueOf()。
     */
    public enum Quarter {
        Q1, Q2, Q3, Q4
    }

    /**
     * 执行所有枚举操作并返回结果摘要。
     *
     * @return 格式为 "EnumTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder result = new StringBuilder();

        // ---- 1. 枚举遍历 + 字段访问 ----
        int totalAngle = 0;
        for (Direction d : Direction.values()) {
            totalAngle += d.getAngle();
        }
        result.append("方向角度和=").append(totalAngle);

        // ---- 2. 枚举方法调用（接口方法 + 自定义方法） ----
        Direction east = Direction.EAST;
        result.append(" | ").append(east).append("=").append(east.describe())
                .append(" 反向=").append(east.opposite());

        // ---- 3. 枚举查找（fromAngle 静态方法） ----
        Direction found = Direction.fromAngle(180);
        result.append(" | 180度对应=").append(found);

        // ---- 4. 枚举 compareTo / ordinal / name ----
        Direction north = Direction.NORTH;
        Direction south = Direction.SOUTH;
        int cmp = north.compareTo(south);
        result.append(" | NORTH.compareTo(SOUTH)=").append(cmp);
        result.append(" | NORTH.ordinal=").append(north.ordinal());
        result.append(" | SOUTH.name=").append(south.name());

        // ---- 5. 带抽象方法的 Status 枚举 ----
        StringBuilder transitionResult = new StringBuilder();
        Status current = Status.PENDING;
        transitionResult.append("当前=").append(current.getDisplayName());

        for (Status target : Status.values()) {
            if (current.canTransitionTo(target)) {
                transitionResult.append(" ->").append(target);
            }
        }
        result.append(" | 状态转换:[").append(transitionResult).append("]");

        // ---- 6. 状态机路径模拟 ----
        Status[] path = {Status.PENDING, Status.PROCESSING, Status.COMPLETED};
        StringBuilder pathResult = new StringBuilder();
        for (int i = 0; i < path.length - 1; i++) {
            Status from = path[i];
            Status to = path[i + 1];
            pathResult.append(from).append("->").append(to)
                    .append("(").append(from.canTransitionTo(to)).append(") ");
        }
        result.append(" | 路径:").append(pathResult);

        // ---- 7. valueOf 测试 ----
        Quarter q = Quarter.valueOf("Q3");
        result.append(" | valueOf(Q3)=").append(q).append(" ordinal=").append(q.ordinal());

        // ---- 8. values() 完整遍历 ----
        StringBuilder quarterNames = new StringBuilder();
        for (Quarter qtr : Quarter.values()) {
            quarterNames.append(qtr).append(",");
        }
        if (quarterNames.length() > 0) {
            quarterNames.setLength(quarterNames.length() - 1);
        }
        result.append(" | 季度:").append(quarterNames);

        // ---- 9. switches on enum ----
        String switchResult = directionLabel(Direction.WEST);
        result.append(" | switch:").append(switchResult);

        // ---- 10. 枚举集合 ----
        java.util.EnumSet<Direction> cardinal = java.util.EnumSet.of(Direction.NORTH, Direction.SOUTH);
        java.util.EnumMap<Direction, String> dirMap = new java.util.EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            dirMap.put(d, d.describe());
        }
        result.append(" | EnumSet大小=").append(cardinal.size());
        result.append(" | EnumMap大小=").append(dirMap.size());

        return "EnumTest:OK:" + result.toString();
    }

    /**
     * switch 枚举辅助方法。
     *
     * @param d 方向
     * @return 标签
     */
    private static String directionLabel(Direction d) {
        return switch (d) {
            case NORTH -> " ↑ 北";
            case EAST -> " → 东";
            case SOUTH -> " ↓ 南";
            case WEST -> " ← 西";
        };
    }
}
