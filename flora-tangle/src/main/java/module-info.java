module com.flora.tangle {
    requires com.flora.root;
    requires static java.compiler; // 仅在测试编译时使用 javax.tools
}
