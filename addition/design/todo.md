
### 问题
1. flora-root/src/main/java/com/flora/entropy/probds 里面的算法缺少人工核对
2. flora-root/src/main/java/com/flora/fast/container/tuple 缺少人工核对
3. flora-root/src/main/java/com/flora/os/virtual/file 缺少人工核对


### 想法

1. 将ramet自己做成一个maven插件，而不是依赖别的插件引入，如何
2. 如何去仓库下载的问题应该配置在哪里，如何配置在仓库内部
3. 如果要让插件上架，需要什么
4. Array转换器对于其中的元素应该调用转换器门户来处理
5. Array是如何处理collection输入的，是否也要调用门户
6. 目标匹配度，按照谁先拒绝谁就优先的原则，遍历继承链条
7. mimicry包用来做源代码混淆

* 目标object
* 目标容器
* 目标整数容器


### flora 后续计划
1. 内存独立数据库
2. 分布式发现系统
3. 区块链基础工具
4. 面向叙事语言，写剧情专用DSL，兼容文字游戏，字符拆分
5. 可分裂数据库，数据库用类似git的方式进行分裂，可以任意增加字段
6. 纯粹无关键字编程语言
7. game  chainlink util 分布式  反向注入加载系统 redislite playground  虚拟编译容器
8. 自定义语言 around函数
9. 搞一个“OpenBug”项目，项目主体是一个agent软件，可以配置api，然后和agent对话，会给每个agent分配一个自身代码的子工作区，然后让agent修复自身bug，然后通过测试用例来判断agent的工作能力。然后让一个区域的bug被另一个区域当作特性来使用，使得多个bug互相串扰，整个管理软件本身运行在bug上，所以任何修复都很容易导致软件自身崩溃。


根 树干 树枝 树叶 

### 如何提供基础能力 ？
1. ssh
2. git



### 决策
1. flora-sanctum里的实际代码不要了，这个名字可以留着空壳挺好听的，后面可能有别的用处
