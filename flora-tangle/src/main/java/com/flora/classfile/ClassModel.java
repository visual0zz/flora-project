package com.flora.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


/**
 * Java Class 文件模型，支持读取、修改和写入 {@code .class} 文件。
 * <p>提供类层次结构的解析（常量池、访问标志、接口、字段、方法、属性），
 * 以及类重命名、成员重命名、字符串混淆、控制流混淆等功能。</p>
 */
public final class ClassModel {

    /** 访问标志：public */
    public static final int ACC_PUBLIC = 0x0001;
    /** 访问标志：private */
    public static final int ACC_PRIVATE = 0x0002;
    /** 访问标志：static */
    public static final int ACC_STATIC = 0x0008;
    /** 访问标志：final */
    public static final int ACC_FINAL = 0x0010;

    private int minorVersion;
    private int majorVersion;
    private ConstantPool constantPool;
    private int accessFlags;
    private int thisClass;
    private int superClass;
    private int[] interfaces;
    private final List<MemberInfo> fields = new ArrayList<>();
    private final List<MemberInfo> methods = new ArrayList<>();
    private final List<Attribute> classAttributes = new ArrayList<>();

    /**
     * 类成员（字段或方法）的信息。
     */
    public static final class MemberInfo {
        public int accessFlags;
        public int nameIndex;
        public int descriptorIndex;
        /** 属性字节序列（已序列化格式） */
        public byte[] attributes;

        MemberInfo(int accessFlags, int nameIndex, int descriptorIndex, byte[] attributes) {
            this.accessFlags = accessFlags;
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.attributes = attributes;
        }
    }

    /**
     * 属性（Attribute），包含名称索引和信息字节。
     */
    public static final class Attribute {
        public int nameIndex;
        /** 属性信息字节 */
        public byte[] info;

        Attribute(int nameIndex, byte[] info) {
            this.nameIndex = nameIndex;
            this.info = info;
        }
    }

    private ClassModel() {
    }

    

    
    /**
     * 从字节数据读取 Class 文件并构建模型。
     *
     * @param data class 文件的完整字节
     * @return ClassModel 实例
     * @throws IOException 如果不是合法的 class 文件或格式错误
     */
    public static ClassModel read(byte[] data) throws IOException {
        ClassModel m = new ClassModel();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("不是合法的 class 文件：magic=0x" + Integer.toHexString(magic));
        }
        m.minorVersion = in.readUnsignedShort();
        m.majorVersion = in.readUnsignedShort();
        int cpCount = in.readUnsignedShort();
        m.constantPool = ConstantPool.read(in, cpCount);
        m.accessFlags = in.readUnsignedShort();
        m.thisClass = in.readUnsignedShort();
        m.superClass = in.readUnsignedShort();
        int interfaceCount = in.readUnsignedShort();
        m.interfaces = new int[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            m.interfaces[i] = in.readUnsignedShort();
        }
        int fieldCount = in.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            m.fields.add(readMember(in));
        }
        int methodCount = in.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            m.methods.add(readMember(in));
        }
        m.classAttributes.addAll(readAttributes(in));
        if (in.available() != 0) {
            throw new IOException("class 文件存在多余的尾部字节: " + in.available());
        }
        return m;
    }

    private static MemberInfo readMember(DataInputStream in) throws IOException {
        int access = in.readUnsignedShort();
        int nameIndex = in.readUnsignedShort();
        int descriptorIndex = in.readUnsignedShort();
        byte[] attributes = readAttributesBytes(in);
        return new MemberInfo(access, nameIndex, descriptorIndex, attributes);
    }

    private static List<Attribute> readAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        List<Attribute> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int nameIndex = in.readUnsignedShort();
            int length = in.readInt();
            byte[] info = new byte[length];
            int read = in.readNBytes(info, 0, length);
            if (read != length) {
                throw new IOException("属性字节不足：期望 " + length + " 实际 " + read);
            }
            result.add(new Attribute(nameIndex, info));
        }
        return result;
    }

    
    private static byte[] readAttributesBytes(DataInputStream in) throws IOException {
        ByteArrayBuilder out = new ByteArrayBuilder();
        int count = in.readUnsignedShort();
        out.writeShort(count);
        for (int i = 0; i < count; i++) {
            int nameIndex = in.readUnsignedShort();
            int length = in.readInt();
            byte[] info = new byte[length];
            int read = in.readNBytes(info, 0, length);
            if (read != length) {
                throw new IOException("属性字节不足：期望 " + length + " 实际 " + read);
            }
            out.writeShort(nameIndex);
            out.writeInt(length);
            out.write(info);
        }
        return out.toByteArray();
    }

    

    /**
     * @return 常量池
     */
    public ConstantPool constantPool() {
        return constantPool;
    }

    /**
     * @return 当前类的内部名称（如 {@code java/lang/String}）
     */
    public String getThisClassName() {
        return constantPool.className(thisClass);
    }

    /**
     * 读取常量池中指定索引的 UTF8 字符串。
     *
     * @param index 常量池索引
     * @return UTF8 字符串
     */
    public String utf8(int index) {
        return constantPool.utf8(index);
    }

    /**
     * @return 父类的内部名称，没有父类（java/lang/Object）时返回 null
     */
    public String getSuperClassName() {
        return superClass == 0 ? null : constantPool.className(superClass);
    }

    /**
     * @return 字段列表
     */
    public List<MemberInfo> getFields() {
        return fields;
    }

    /**
     * @return 方法列表
     */
    public List<MemberInfo> getMethods() {
        return methods;
    }

    /**
     * @return 当前类在常量池中的索引
     */
    public int getThisClassIndex() {
        return thisClass;
    }

    

    
    /**
     * 重命名类引用（包括常量池中的 Class、UTF8 引用等）。
     *
     * @param oldName 旧类名
     * @param newName 新类名
     */
    public void renameClass(String oldName, String newName) {
        ConstantPool cp = this.constantPool;
        
        for (int i = 1; i < cpSlotCount(); i++) {
            ConstantPool.Entry e = cp.entry(i);
            if (e == null) {
                continue;
            }
            if (e.tag == ConstantPool.TAG_UTF8) {
                e.utf8 = replaceClassNameInUtf8(e.utf8, oldName, newName);
            } else if (e.tag == ConstantPool.TAG_CLASS || e.tag == ConstantPool.TAG_MODULE
                    || e.tag == ConstantPool.TAG_PACKAGE) {
                String name = cp.utf8(e.nameIndex);
                String replaced = replaceClassNameInUtf8(name, oldName, newName);
                if (!replaced.equals(name)) {
                    e.nameIndex = cp.addUtf8(replaced);
                }
            }
        }
    }

    
    /**
     * 重命名指定类中的成员（字段、方法及所有引用点）。
     *
     * @param ownerClass 成员所在的类名
     * @param oldName    旧成员名
     * @param descriptor 成员描述符
     * @param newName    新成员名
     */
    public void renameMember(String ownerClass, String oldName, String descriptor, String newName) {
        if (!ownerClass.equals(getThisClassName())) {
            return;
        }
        ConstantPool cp = this.constantPool;
        boolean found = false;
        for (MemberInfo m : fields) {
            if (cp.utf8(m.nameIndex).equals(oldName) && cp.utf8(m.descriptorIndex).equals(descriptor)) {
                m.nameIndex = cp.addUtf8(newName);
                found = true;
            }
        }
        for (MemberInfo m : methods) {
            if (cp.utf8(m.nameIndex).equals(oldName) && cp.utf8(m.descriptorIndex).equals(descriptor)) {
                m.nameIndex = cp.addUtf8(newName);
                found = true;
            }
        }
        if (!found) {
            return; 
        }
        
        int newNat = cp.addNameAndType(newName, descriptor);
        for (int i = 1; i < cpSlotCount(); i++) {
            ConstantPool.Entry e = cp.entry(i);
            if (e == null) {
                continue;
            }
            if (e.tag == ConstantPool.TAG_FIELDREF || e.tag == ConstantPool.TAG_METHODREF
                    || e.tag == ConstantPool.TAG_INTERFACE_METHODREF) {
                if (cp.className(e.classIndex).equals(ownerClass)
                        && cp.natName(e.nameAndTypeIndex).equals(oldName)
                        && cp.natDescriptor(e.nameAndTypeIndex).equals(descriptor)) {
                    e.nameAndTypeIndex = newNat;
                }
            }
        }
    }

    private int cpSlotCount() {
        
        return constantPool.size();
    }

    
    private static String replaceClassNameInUtf8(String text, String oldName, String newName) {
        if (text == null || text.isEmpty() || !text.contains(oldName)) {
            return text;
        }
        int n = oldName.length();
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i <= text.length() - n) {
            if (text.regionMatches(i, oldName, 0, n)) {
                boolean boundary;
                if (i + n == text.length()) {
                    boundary = true;
                } else {
                    char after = text.charAt(i + n);
                    boundary = !isNameChar(after);
                }
                if (boundary) {
                    sb.append(newName);
                    i += n;
                    continue;
                }
            }
            sb.append(text.charAt(i));
            i++;
        }
        
        sb.append(text, i, text.length());
        return sb.toString();
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '$' || c == '/';
    }

    

    
    /**
     * 从字节数据解析属性列表。
     *
     * @param bytes 属性段字节
     * @return 属性列表
     * @throws IOException 如果格式错误
     */
    public static List<Attribute> parseAttributes(byte[] bytes) throws IOException {
        return readAttributes(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    /**
     * 将属性列表序列化为字节。
     *
     * @param attrs 属性列表
     * @return 序列化后的字节
     */
    public static byte[] serializeAttributes(List<Attribute> attrs) {
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(attrs.size());
        for (Attribute a : attrs) {
            out.writeShort(a.nameIndex);
            out.writeInt(a.info.length);
            out.write(a.info);
        }
        return out.toByteArray();
    }

    /**
     * 获取指定成员的 Code 属性字节。
     *
     * @param m 成员信息
     * @return Code 属性字节，没有 Code 属性时返回 null
     * @throws IOException 如果属性解析失败
     */
    public byte[] getCodeInfo(MemberInfo m) throws IOException {
        for (Attribute a : parseAttributes(m.attributes)) {
            if (utf8(a.nameIndex).equals("Code")) {
                return a.info;
            }
        }
        return null;
    }

    /**
     * 设置指定成员的 Code 属性字节。
     *
     * @param m    成员信息
     * @param info 新的 Code 属性字节
     * @throws IOException 如果成员不包含 Code 属性
     */
    public void setCodeInfo(MemberInfo m, byte[] info) throws IOException {
        List<Attribute> attrs = parseAttributes(m.attributes);
        boolean replaced = false;
        for (Attribute a : attrs) {
            if (utf8(a.nameIndex).equals("Code")) {
                a.info = info;
                replaced = true;
            }
        }
        if (!replaced) {
            throw new IOException("成员不包含 Code 属性，无法写回");
        }
        m.attributes = serializeAttributes(attrs);
    }

    /**
     * 对指定成员的方法体应用指令替换（重写 Code 属性）。
     *
     * @param m            成员信息
     * @param replacements 指令偏移量到替换后字节的映射
     * @throws IOException 如果解析或汇编失败
     */
    public void rewriteCode(MemberInfo m, Map<Integer, byte[]> replacements) throws IOException {
        byte[] info = getCodeInfo(m);
        if (info == null) {
            return;
        }
        Bytecode.CodeModel cm = Bytecode.parse(info, this::utf8);
        byte[] newInfo = cm.assemble(replacements);
        setCodeInfo(m, newInfo);
    }

    

    
    private static final int STRING_KEY = 0x37;

    
    /**
     * 混淆当前类中的所有字符串常量。
     * <p>对 String 常量进行 Base64 编码（加简单的 XOR 加密），并在引用处插入解密方法调用。</p>
     *
     * @return 如果存在任何被混淆的字符串返回 true
     * @throws IOException 如果字节码解析或汇编失败
     */
    public boolean obfuscateStrings() throws IOException {
        ConstantPool cp = this.constantPool;
        
        
        java.util.Set<Integer> obfuscated = new java.util.HashSet<>();
        boolean hasAny = false;
        for (MemberInfo m : methods) {
            byte[] info = getCodeInfo(m);
            if (info == null) {
                continue;
            }
            Bytecode.CodeModel cm = Bytecode.parse(info, this::utf8);
            for (Bytecode.Insn in : cm.instructions) {
                int op = in.opcode;
                if (op == 0x12 || op == 0x13) { 
                    int idx = readCpIndex(in);
                    // 跳过无效的常量池索引（可能由字节码解析误差导致）
                    if (idx <= 0 || idx >= constantPool.size()) continue;
                    ConstantPool.Entry e = cp.entry(idx);
                    if (e != null && e.tag == ConstantPool.TAG_STRING) {
                        hasAny = true;
                        if (!obfuscated.contains(idx)) {
                            String plain = cp.utf8(e.nameIndex);
                            if (!plain.isEmpty()) {
                                // 修改 Utf8 条目本身的值为密文；同时保留旧明文到末尾以便恢复
                                cp.entry(e.nameIndex).utf8 = base64(plain);
                            }
                            obfuscated.add(idx);
                        }
                    }
                }
            }
        }
        if (!hasAny) {
            return false;
        }
        
        String synthName = uniqueMethodName("tD");
        int decRef = cp.addMethodref(getThisClassName(), synthName,
                "(Ljava/lang/String;)Ljava/lang/String;");
        
        for (MemberInfo m : methods) {
            if (utf8(m.nameIndex).equals(synthName)) {
                continue; 
            }
            byte[] info = getCodeInfo(m);
            if (info == null) {
                continue;
            }
            Bytecode.CodeModel cm = Bytecode.parse(info, this::utf8);
            java.util.Map<Integer, byte[]> reps = new java.util.HashMap<>();
            for (Bytecode.Insn in : cm.instructions) {
                int op = in.opcode;
                if (op == 0x12 || op == 0x13) {
                    int idx = readCpIndex(in);
                    if (idx <= 0 || idx >= constantPool.size()) continue;
                    if (obfuscated.contains(idx)) {
                        ByteArrayBuilder b = new ByteArrayBuilder();
                        b.writeByte(0x13);     
                        b.writeShort(idx);
                        b.writeByte(0xb8);     
                        b.writeShort(decRef);
                        reps.put(in.offset, b.toByteArray());
                    }
                }
            }
            if (!reps.isEmpty()) {
                setCodeInfo(m, cm.assemble(reps));
            }
        }
        
        methods.add(buildDecryptMethod(cp, synthName, decRef));
        return true;
    }

    private static int readCpIndex(Bytecode.Insn in) {
        byte[] o = in.operands();
        if (in.opcode == 0x12) {
            return o[0] & 0xff; 
        }
        return ((o[0] & 0xff) << 8) | (o[1] & 0xff); 
    }

    
    private String uniqueMethodName(String base) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (MemberInfo m : methods) {
            existing.add(utf8(m.nameIndex));
        }
        String name = base;
        int n = 0;
        while (existing.contains(name)) {
            name = base + (n++);
        }
        return name;
    }

    
    private static String base64(String plain) {
        byte[] bytes = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ STRING_KEY);
        }
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    
    private MemberInfo buildDecryptMethod(ConstantPool cp, String name, int decRef) {
        int nameIndex = cp.addUtf8(name);
        int descIndex = cp.addUtf8("(Ljava/lang/String;)Ljava/lang/String;");

        int getDecoderRef = cp.addMethodref("java/util/Base64", "getDecoder",
                "()Ljava/util/Base64$Decoder;");
        int decodeRef = cp.addMethodref("java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B");
        int utf8Ref = cp.addFieldref("java/nio/charset/StandardCharsets", "UTF_8",
                "Ljava/nio/charset/Charset;");
        int stringInitRef = cp.addMethodref("java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V");
        int stringClass = cp.addClass("java/lang/String");
        int byteArrayClass = cp.addClass("[B");     

        
        ByteArrayBuilder code = new ByteArrayBuilder();
        code.writeByte(0xb8); code.writeShort(getDecoderRef);   
        code.writeByte(0x2a);                                    
        code.writeByte(0xb6); code.writeShort(decodeRef);        
        code.writeByte(0x4c);                                    
        code.writeByte(0x03);                                    
        code.writeByte(0x3d);                                    
        code.writeByte(0xa7); code.writeShort(0x10);             
        code.writeByte(0x2b);                                    
        code.writeByte(0x1c);                                    
        code.writeByte(0x2b);                                    
        code.writeByte(0x1c);                                    
        code.writeByte(0x33);                                    
        code.writeByte(0x10); code.writeByte(STRING_KEY);        
        code.writeByte(0x82);                                    
        code.writeByte(0x92);                                    
        code.writeByte(0x54);                                    
        code.writeByte(0x84); code.writeByte(0x02); code.writeByte(0x01); 
        code.writeByte(0x1c);                                    
        code.writeByte(0x2b);                                    
        code.writeByte(0xbe);                                    
        code.writeByte(0xa1); code.writeShort(-16);             
        code.writeByte(0xbb); code.writeShort(stringClass);     
        code.writeByte(0x59);                                    
        code.writeByte(0x2b);                                    
        code.writeByte(0xb2); code.writeShort(utf8Ref);          
        code.writeByte(0xb7); code.writeShort(stringInitRef);    
        code.writeByte(0xb0);                                    

        
        ByteArrayBuilder codeAttr = new ByteArrayBuilder();
        codeAttr.writeShort(4);          
        codeAttr.writeShort(3);          
        codeAttr.writeInt(code.size());
        codeAttr.write(code);
        codeAttr.writeShort(0);          
        if (majorVersion >= 50) {
            
            
            ByteArrayBuilder smt = new ByteArrayBuilder();
            smt.writeShort(2);               
            smt.writeByte(0xff);             
            smt.writeShort(0x0d);            
            smt.writeShort(3);               
            smt.writeByte(0x07); smt.writeShort(stringClass); 
            smt.writeByte(0x07); smt.writeShort(byteArrayClass); 
            smt.writeByte(0x01);             
            smt.writeShort(0);               
            smt.writeByte(0x0c);             
            int smtName = cp.addUtf8("StackMapTable");
            codeAttr.writeShort(1);          
            codeAttr.writeShort(smtName);
            codeAttr.writeInt(smt.size());
            codeAttr.write(smt);
        } else {
            codeAttr.writeShort(0);          
        }

        int codeName = cp.addUtf8("Code");
        ByteArrayBuilder attrs = new ByteArrayBuilder();
        attrs.writeShort(1);                 
        attrs.writeShort(codeName);
        attrs.writeInt(codeAttr.size());
        attrs.write(codeAttr);

        return new MemberInfo(0x0002 | 0x0008, nameIndex, descIndex, attrs.toByteArray());
    }

    

    
    private static final int ACC_ABSTRACT = 0x0400;
    private static final int ACC_NATIVE = 0x0100;

    
    /**
     * 混淆当前类中所有方法的控制流。
     * <p>在每个方法末尾插入死代码（弹出+pop + aconst_null + athrow），
     * 并更新 StackMapTable 以维持验证器兼容性。</p>
     *
     * @return 如果至少有一个方法被混淆返回 true
     * @throws IOException 如果字节码解析或汇编失败
     */
    public boolean obfuscateControlFlow() throws IOException {
        boolean any = false;
        for (MemberInfo m : methods) {
            try {
                if (obfuscateMethodControlFlow(m)) {
                    any = true;
                }
            } catch (Exception e) {
                // 单个方法混淆失败不影响其他方法
                System.err.println("警告: 方法 " + utf8(m.nameIndex) + " 控制流混淆失败 - " + e.getMessage());
            }
        }
        return any;
    }

    private boolean obfuscateMethodControlFlow(MemberInfo m) throws IOException {
        if ((m.accessFlags & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return false; 
        }
        byte[] info = getCodeInfo(m);
        if (info == null) {
            return false;
        }
        Bytecode.CodeModel cm = Bytecode.parse(info, this::utf8);
        int codeLen = ((info[4] & 0xff) << 24) | ((info[5] & 0xff) << 16)
                | ((info[6] & 0xff) << 8) | (info[7] & 0xff);
        byte[] origCode = Arrays.copyOfRange(info, 8, 8 + codeLen);
        
        
        
        ByteArrayBuilder code = new ByteArrayBuilder();
        code.write(origCode);
        for (int k = 0; k < 5; k++) {                    
            code.writeByte(0x03);
            code.writeByte(0x57);
        }
        code.writeByte(0x01);                            
        code.writeByte(0xbf);                            
        int newCodeLen = code.size();
        int decoyStart = codeLen; 

        
        ByteArrayBuilder ex = new ByteArrayBuilder();
        ex.writeShort(cm.exceptionTable.size());
        for (int[] e : cm.exceptionTable) {
            ex.writeShort(e[0]);
            ex.writeShort(e[1]);
            ex.writeShort(e[2]);
            ex.writeShort(e[3]);
        }

        
        
        
        List<Attribute> newAttrs = new ArrayList<>();
        boolean patched = false;
        for (Attribute a : cm.attributes) {
            if (cm.nameResolver != null && "StackMapTable".equals(cm.nameResolver.apply(a.nameIndex))) {
                newAttrs.add(new Attribute(a.nameIndex, appendSameFrame(a.info, decoyStart)));
                patched = true;
            } else {
                newAttrs.add(a);
            }
        }
        if (!patched && majorVersion >= 50) {
            
            int smtName = constantPool.addUtf8("StackMapTable");
            newAttrs.add(new Attribute(smtName, appendSameFrame(null, decoyStart)));
        }

        ByteArrayBuilder codeAttr = new ByteArrayBuilder();
        codeAttr.writeShort(Math.max(cm.maxStack, 1));   
        codeAttr.writeShort(cm.maxLocals);
        codeAttr.writeInt(newCodeLen);
        codeAttr.write(code);
        codeAttr.write(ex);
        codeAttr.write(serializeAttributes(newAttrs));
        setCodeInfo(m, codeAttr.toByteArray());
        return true;
    }

    
    private static int lastStackMapOffset(byte[] smtInfo) throws IOException {
        if (smtInfo == null) {
            return -1;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(smtInfo));
        int entries = in.readUnsignedShort();
        int prev = -1;
        for (int e = 0; e < entries; e++) {
            int ft = in.readUnsignedByte();
            int delta;
            if (ft <= 63) {
                delta = ft;
            } else if (ft <= 127) {
                delta = ft - 64;
                skipVType(in);
            } else if (ft == 247) {
                delta = in.readUnsignedShort();
                skipVType(in);
            } else if (ft >= 248 && ft <= 250) {
                delta = in.readUnsignedShort();    
            } else if (ft >= 251 && ft <= 254) {
                delta = in.readUnsignedShort();
                for (int x = 0; x < ft - 251; x++) {
                    skipVType(in);
                }
            } else if (ft == 255) {
                delta = in.readUnsignedShort();
                int lc = in.readUnsignedShort();
                for (int x = 0; x < lc; x++) {
                    skipVType(in);
                }
                int sc = in.readUnsignedShort();
                for (int x = 0; x < sc; x++) {
                    skipVType(in);
                }
            } else {
                throw new IOException("不支持的 StackMapFrame 类型: " + ft);
            }
            prev = prev + delta + 1;
        }
        return prev;
    }

    
    private static void skipVType(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        if (tag == 7 || tag == 8) {       
            in.readUnsignedShort();
        }
        
    }

    
    private static byte[] appendSameFrame(byte[] smtInfo, int decoyStart) throws IOException {
        int last = (smtInfo == null) ? -1 : lastStackMapOffset(smtInfo);
        int newDelta = decoyStart - last - 1; 
        int entries = (smtInfo == null) ? 0 : ((smtInfo[0] & 0xff) << 8) | (smtInfo[1] & 0xff);
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(entries + 1);
        if (smtInfo != null) {
            byte[] frames = Arrays.copyOfRange(smtInfo, 2, smtInfo.length); 
            out.write(frames);
        }
        if (newDelta <= 63) {
            out.writeByte(newDelta);                    
        } else {
            out.writeByte(247);                         
            out.writeShort(newDelta);
        }
        return out.toByteArray();
    }

    

    
    /**
     * 剥离调试信息：移除 SourceFile、SourceDebugExtension、Deprecated、Synthetic 等类级属性。
     * <p>Code 属性内的 LineNumberTable 等调试属性在 {@link #toBytes()} 阶段由 Bytecode 处理。</p>
     */
    public void stripDebugInfo() {
        classAttributes.removeIf(a -> {
            String name = utf8(a.nameIndex);
            return "SourceFile".equals(name)
                || "SourceDebugExtension".equals(name)
                || "Deprecated".equals(name)
                || "Synthetic".equals(name)
                || "EnclosingMethod".equals(name);
        });
    }

    /**
     * 将当前 ClassModel 写回为 class 文件的字节。
     *
     * @return class 文件的字节数组
     */
    public byte[] toBytes() {
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeInt(0xCAFEBABE);
        out.writeShort(minorVersion);
        out.writeShort(majorVersion);
        out.write(constantPool.toBytes());
        out.writeShort(accessFlags);
        out.writeShort(thisClass);
        out.writeShort(superClass);
        out.writeShort(interfaces.length);
        for (int itf : interfaces) {
            out.writeShort(itf);
        }
        out.writeShort(fields.size());
        for (MemberInfo f : fields) {
            writeMember(out, f);
        }
        out.writeShort(methods.size());
        for (MemberInfo m : methods) {
            writeMember(out, m);
        }
        out.writeShort(classAttributes.size());
        for (Attribute a : classAttributes) {
            out.writeShort(a.nameIndex);
            out.writeInt(a.info.length);
            out.write(a.info);
        }
        return out.toByteArray();
    }

    private void writeMember(ByteArrayBuilder out, MemberInfo m) {
        out.writeShort(m.accessFlags);
        out.writeShort(m.nameIndex);
        out.writeShort(m.descriptorIndex);
        out.write(m.attributes); 
    }
}
