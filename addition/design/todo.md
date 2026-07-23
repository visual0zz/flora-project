
### 问题
1. inheritanceHierarchyDistance 对接口源的潜在 bug（最重要）
调试时暴露的：inheritanceHierarchyDistance(Object.class, List.class) 返回 Integer.MAX_VALUE。原因——List 是接口，其 getSuperclass() 为 null，向上走父类链立刻终止、到不了
Object；而兜底分支只对"声明源是接口"生效（这里是 Object 类）。
后果：凡 find 的 sourceType 是接口类型时，所有"声明源为 Object.class"的转换器 sourceDistance 一律 -1、直接落选。门面里 sourceType = value.getClass() 永远是具体类，所以被掩盖了；但 find 是公开
API，直接传 List.class 就会解析失败（我那个测试就是这么踩中的）。
建议修 inheritanceHierarchyDistance：当 valueType 是接口或走完父类链仍未命中时，继续遍历 valueType.getInterfaces()（递归到 Object）。这样接口源也能正确算出距离。这是真正的健壮性缺陷，不是风格问题。
2. TupleN需要从object包里面移动出来
3. ramet的idea插件运行效率有问题，快速随意输入特殊字符会直接卡死或者卡崩编辑器

### 想法

1. 将ramet自己做成一个maven插件，而不是依赖别的插件引入，如何
2. 如何去仓库下载的问题应该配置在哪里，如何配置在仓库内部
3. 如果要让插件上架，需要什么
4. 插件能检查语法正确性吗
5. 目前胡乱输入文字会导致卡顿，需要看一下怎么回事，参考一下jar是怎么做的
6. ${}内部文字的默认色应该是白色，而不是背景的灰色
7. test test-slow的分解，目前对于gradle工程是不正常的
8. 测试时要包含代码覆盖率
9. 转换器的路由逻辑能哈希化吗
10. Array转换器对于其中的元素应该调用转换器门户来处理
11. Array是如何处理collection输入的，是否也要调用门户
12. 目标匹配度，按照谁先拒绝谁就优先的原则，遍历继承链条
9. mimicry包用来做源代码混淆

* 目标object
* 目标容器
* 目标整数容器

# idea
game  chainlink util 分布式  反向注入加载系统 redislite playground  虚拟编译容器


### flora 后续计划

1. 内存独立数据库
2. 分布式发现系统
3. 区块链基础工具
4. 面向叙事语言，写剧情专用DSL，兼容文字游戏，字符拆分
5. 可分裂数据库，数据库用类似git的方式进行分裂，可以任意增加字段
6. 纯粹无关键字编程语言



