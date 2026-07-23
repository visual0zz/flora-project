package com.flora.tangle;

import com.flora.classfile.ClassModel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 代码混淆器：读入一个 jar，输出一个 jar，过程中把类名与成员名替换为无意义的短名字。
 *
 * <p>整体分两遍处理，以保证“一处改名、处处同步”：
 * <ol>
 *     <li>第一遍：扫描所有 class，建立“旧类名→新类名”与“(所属类, 成员名, 描述符)→新成员名”的映射；
 *         同时把已有名字登记到名字生成器，避免新名字撞车。</li>
 *     <li>第二遍：逐个 class 应用全部映射（类名重命名会改写常量池与描述符/签名里的文本，
 *         成员重命名会改写声明与 Fieldref/Methodref 引用），写回字节；
 *         非 class 资源原样拷贝，MANIFEST 中的 Main-Class 若被改名则同步更新。</li>
 * </ol>
 *
 * <p>本类只依赖 JDK 标准库与 flora-root 的 class 文件库，不引入任何第三方依赖。
 */
public final class Obfuscator {

    /** 不重命名的类前缀（如 JDK 类，即便被打包进来也不动）。 */
    private final List<String> keepClassPrefixes = new ArrayList<>();

    /** 是否开启字符串常量混淆（默认开启）。 */
    private boolean stringObfuscation = true;

    /** 是否开启控制流混淆（默认开启）：在每个方法末尾追加不可达的诱饵指令块。 */
    private boolean controlFlowObfuscation = true;

    /** 是否剥离调试信息（默认开启）。 */
    private boolean stripDebugInfo = true;

    /** 是否开启收缩优化（移除未使用的字段，默认关闭，可能会破坏反射调用的类）。 */
    private boolean shrinkingOptimization = false;

    public void keepClassPrefix(String prefix) {
        keepClassPrefixes.add(prefix);
    }

    /** 设置是否对类中的字符串字面量做混淆（加密后运行时还原）。 */
    public void setStringObfuscation(boolean on) {
        this.stringObfuscation = on;
    }

    /** 设置是否对方法做控制流混淆（在方法末尾追加不可达诱饵块）。 */
    public void setControlFlowObfuscation(boolean on) {
        this.controlFlowObfuscation = on;
    }

    /** 设置是否剥离调试信息（LineNumberTable / LocalVariableTable）。 */
    public void setStripDebugInfo(boolean on) {
        this.stripDebugInfo = on;
    }

    /** 设置是否开启收缩优化（移除未使用的私有字段）。 */
    public void setShrinkingOptimization(boolean on) {
        this.shrinkingOptimization = on;
    }

    /** 执行混淆，返回输出 jar 的字节。 */
    public byte[] obfuscate(byte[] inputJar) throws IOException {
        List<JarEntryData> entries = readJar(inputJar);

        // ===== 第一遍：建立重命名映射 =====
        NameGenerator classGen = new NameGenerator();
        NameGenerator memberGen = new NameGenerator();
        Map<String, String> classNameMap = new LinkedHashMap<>();   // 旧内部名 -> 新内部名
        Map<MemberKey, String> memberMap = new HashMap<>();          // (所属类, 名, 描述符) -> 新名

        // 预留所有已有的类名与成员名，防止新名字冲突。
        for (JarEntryData e : entries) {
            if (!e.isClass) {
                continue;
            }
            ClassModel cm = ClassModel.read(e.bytes);
            classGen.reserve(cm.getThisClassName());
            for (ClassModel.MemberInfo m : cm.getFields()) {
                memberGen.reserve(cm.utf8(m.nameIndex));
            }
            for (ClassModel.MemberInfo m : cm.getMethods()) {
                memberGen.reserve(cm.utf8(m.nameIndex));
            }
        }
        for (JarEntryData e : entries) {
            if (!e.isClass) {
                continue;
            }
            ClassModel cm = ClassModel.read(e.bytes);
            String oldName = cm.getThisClassName();
            if (shouldRenameClass(oldName)) {
                classNameMap.put(oldName, "a/" + classGen.next());
            }
            for (ClassModel.MemberInfo m : cm.getFields()) {
                String name = cm.utf8(m.nameIndex);
                String desc = cm.utf8(m.descriptorIndex);
                memberMap.put(new MemberKey(oldName, name, desc), memberGen.next());
            }
            for (ClassModel.MemberInfo m : cm.getMethods()) {
                String name = cm.utf8(m.nameIndex);
                if (name.startsWith("<")) {
                    continue; // <init> / <clinit> 不能改名
                }
                // 被保留的类（--keep）的入口 main 方法不应改名，否则 java -jar 找不到入口。
                if (!shouldRenameClass(oldName)
                        && name.equals("main")
                        && cm.utf8(m.descriptorIndex).equals("([Ljava/lang/String;)V")
                        && (m.accessFlags & 0x0009) == 0x0009) { // ACC_PUBLIC | ACC_STATIC
                    continue;
                }
                String desc = cm.utf8(m.descriptorIndex);
                memberMap.put(new MemberKey(oldName, name, desc), memberGen.next());
            }
        }

        // ===== 第二遍：应用映射并写出 =====
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (JarEntryData e : entries) {
                if (e.isClass) {
                    writeObfuscatedClass(zos, e, classNameMap, memberMap);
                } else if (e.name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    writeManifest(zos, e, classNameMap);
                } else {
                    zos.putNextEntry(new ZipEntry(e.name));
                    zos.write(e.bytes);
                    zos.closeEntry();
                }
            }
        }
        return out.toByteArray();
    }

    private void writeObfuscatedClass(ZipOutputStream zos, JarEntryData e,
                                      Map<String, String> classNameMap,
                                      Map<MemberKey, String> memberMap) throws IOException {
        ClassModel cm = ClassModel.read(e.bytes);

        // 1. 调试信息剥离（全部类都执行）
        if (stripDebugInfo) {
            cm.stripDebugInfo();
        }

        // 2. 应用类名/成员名重命名（所有类都需要更新引用）
        for (Map.Entry<String, String> en : classNameMap.entrySet()) {
            cm.renameClass(en.getKey(), en.getValue());
        }
        for (Map.Entry<MemberKey, String> en : memberMap.entrySet()) {
            MemberKey k = en.getKey();
            cm.renameMember(k.owner, k.name, k.descriptor, en.getValue());
        }

        // 3. 控制流混淆（在类级别捕获异常，避免单个类阻塞整个流程）
        if (controlFlowObfuscation) {
            try {
                cm.obfuscateControlFlow();
            } catch (Exception ex) {
                System.err.println("警告: 控制流混淆失败 " + cm.getThisClassName() + " - " + ex.getMessage());
            }
        }
        // 4. 字符串常量混淆
        if (stringObfuscation) {
            try {
                cm.obfuscateStrings();
            } catch (Exception ex) {
                System.err.println("警告: 字符串混淆失败 " + cm.getThisClassName() + " - " + ex.getMessage());
            }
        }
        String newName = cm.getThisClassName();
        byte[] result = cm.toBytes();
        zos.putNextEntry(new ZipEntry(newName + ".class"));
        zos.write(result);
        zos.closeEntry();
    }

    private void writeManifest(ZipOutputStream zos, JarEntryData e,
                               Map<String, String> classNameMap) throws IOException {
        String text = new String(e.bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (Map.Entry<String, String> en : classNameMap.entrySet()) {
            // Main-Class 在清单里用 '.' 分隔，统一替换两种形式。
            text = text.replace(en.getKey().replace('/', '.'), en.getValue().replace('/', '.'));
        }
        zos.putNextEntry(new ZipEntry(e.name));
        zos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private boolean shouldRenameClass(String internalName) {
        if (internalName.equals("module-info")) {
            return false; // 模块描述文件不能改名
        }
        for (String prefix : keepClassPrefixes) {
            if (internalName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    // ---- jar 读取 ----

    private static List<JarEntryData> readJar(byte[] jar) throws IOException {
        List<JarEntryData> result = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = zis.readAllBytes();
                String name = entry.getName();
                boolean isClass = name.endsWith(".class");
                result.add(new JarEntryData(name, bytes, isClass));
            }
        }
        return result;
    }

    private static final class JarEntryData {
        String name;
        byte[] bytes;
        boolean isClass;

        JarEntryData(String name, byte[] bytes, boolean isClass) {
            this.name = name;
            this.bytes = bytes;
            this.isClass = isClass;
        }
    }

    /** 成员标识：所属类 + 名字 + 描述符，三者唯一确定一个可重命名的符号。 */
    private static final class MemberKey {
        String owner;
        String name;
        String descriptor;

        MemberKey(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MemberKey other)) {
                return false;
            }
            return owner.equals(other.owner) && name.equals(other.name)
                    && descriptor.equals(other.descriptor);
        }

        @Override
        public int hashCode() {
            return owner.hashCode() * 31 * 31 + name.hashCode() * 31 + descriptor.hashCode();
        }
    }
}
