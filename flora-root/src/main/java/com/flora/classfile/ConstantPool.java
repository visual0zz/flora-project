package com.flora.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 常量池（Constant Pool）的模型表示。
 * <p>支持读取、写入和修改 class 文件常量池，提供按类型添加常量项的便捷方法。
 * 自动处理 Long/Double 占两个槽位的规则。</p>
 */
final class ConstantPool {

    /** UTF8 字符串常量标签 */
    static final int TAG_UTF8 = 1;
    /** Integer 常量标签 */
    static final int TAG_INTEGER = 3;
    /** Float 常量标签 */
    static final int TAG_FLOAT = 4;
    /** Long 常量标签 */
    static final int TAG_LONG = 5;
    /** Double 常量标签 */
    static final int TAG_DOUBLE = 6;
    /** Class 引用标签 */
    static final int TAG_CLASS = 7;
    /** String 常量标签 */
    static final int TAG_STRING = 8;
    /** Fieldref 引用标签 */
    static final int TAG_FIELDREF = 9;
    /** Methodref 引用标签 */
    static final int TAG_METHODREF = 10;
    /** InterfaceMethodref 引用标签 */
    static final int TAG_INTERFACE_METHODREF = 11;
    /** NameAndType 描述标签 */
    static final int TAG_NAME_AND_TYPE = 12;
    /** MethodHandle 标签 */
    static final int TAG_METHOD_HANDLE = 15;
    /** MethodType 标签 */
    static final int TAG_METHOD_TYPE = 16;
    /** Dynamic 标签 */
    static final int TAG_DYNAMIC = 17;
    /** InvokeDynamic 标签 */
    static final int TAG_INVOKE_DYNAMIC = 18;
    /** Module 标签 */
    static final int TAG_MODULE = 19;
    /** Package 标签 */
    static final int TAG_PACKAGE = 20;

    
    /**
     * 常量池条目，根据 tag 类型使用不同的字段存储数据。
     */
    static final class Entry {
        int tag;
        String utf8;          // TAG_UTF8
        int intBits;          // TAG_INTEGER, TAG_FLOAT
        long longBits;        // TAG_LONG, TAG_DOUBLE
        int nameIndex;        // TAG_CLASS, TAG_STRING, TAG_MODULE, TAG_PACKAGE, TAG_NAME_AND_TYPE
        int classIndex;       // TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF
        int nameAndTypeIndex; // TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC
        int descriptorIndex;  // TAG_NAME_AND_TYPE, TAG_METHOD_TYPE
        int referenceKind;    // TAG_METHOD_HANDLE
        int referenceIndex;   // TAG_METHOD_HANDLE
        int bootstrapIndex;   // TAG_DYNAMIC, TAG_INVOKE_DYNAMIC
        byte[] raw;           // 未知类型回退

        Entry(int tag) {
            this.tag = tag;
        }
    }

    
    private final List<Entry> entries = new ArrayList<>();
    
    private final Map<String, Integer> utf8Cache = new HashMap<>();

    ConstantPool() {
        entries.add(null); 
    }

    
    /**
     * @return 常量池大小（含索引 0 的空占位）
     */
    int size() {
        return entries.size();
    }

    
    /**
     * 从 DataInputStream 读取常量池。
     *
     * @param in    输入流
     * @param count 常量池条目数（含索引 0）
     * @return 常量池实例
     * @throws IOException 如果是非法标签
     */
    static ConstantPool read(DataInputStream in, int count) throws IOException {
        ConstantPool cp = new ConstantPool();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            Entry e = new Entry(tag);
            switch (tag) {
                case TAG_UTF8 -> {
                    
                    e.utf8 = in.readUTF();
                }
                case TAG_INTEGER, TAG_FLOAT -> e.intBits = in.readInt();
                case TAG_LONG, TAG_DOUBLE -> {
                    e.longBits = in.readLong();
                    cp.entries.add(e);
                    cp.entries.add(null); 
                    i++; 
                    continue;
                }
                case TAG_CLASS, TAG_STRING, TAG_MODULE, TAG_PACKAGE ->
                        e.nameIndex = in.readUnsignedShort();
                case TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF -> {
                    e.classIndex = in.readUnsignedShort();
                    e.nameAndTypeIndex = in.readUnsignedShort();
                }
                case TAG_NAME_AND_TYPE -> {
                    e.nameIndex = in.readUnsignedShort();
                    e.descriptorIndex = in.readUnsignedShort();
                }
                case TAG_METHOD_TYPE -> e.descriptorIndex = in.readUnsignedShort();
                case TAG_METHOD_HANDLE -> {
                    e.referenceKind = in.readUnsignedByte();
                    e.referenceIndex = in.readUnsignedShort();
                }
                case TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> {
                    e.bootstrapIndex = in.readUnsignedShort();
                    e.nameAndTypeIndex = in.readUnsignedShort();
                }
                default -> {
                    
                    throw new IOException("不支持的常量池标签: " + tag);
                }
            }
            cp.entries.add(e);
        }
        return cp;
    }

    
    byte[] toBytes() {
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(entries.size()); 
        for (int i = 1; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e == null) {
                continue; 
            }
            out.writeByte(e.tag);
            switch (e.tag) {
                case TAG_UTF8 -> {
                    byte[] bytes = toModifiedUtf8(e.utf8);
                    out.writeShort(bytes.length);
                    out.write(bytes);
                }
                case TAG_INTEGER, TAG_FLOAT -> out.writeInt(e.intBits);
                case TAG_LONG, TAG_DOUBLE -> out.writeLong(e.longBits);
                case TAG_CLASS, TAG_STRING, TAG_MODULE, TAG_PACKAGE ->
                        out.writeShort(e.nameIndex);
                case TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF -> {
                    out.writeShort(e.classIndex);
                    out.writeShort(e.nameAndTypeIndex);
                }
                case TAG_NAME_AND_TYPE -> {
                    out.writeShort(e.nameIndex);
                    out.writeShort(e.descriptorIndex);
                }
                case TAG_METHOD_TYPE -> out.writeShort(e.descriptorIndex);
                case TAG_METHOD_HANDLE -> {
                    out.writeByte(e.referenceKind);
                    out.writeShort(e.referenceIndex);
                }
                case TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> {
                    out.writeShort(e.bootstrapIndex);
                    out.writeShort(e.nameAndTypeIndex);
                }
                default -> out.write(e.raw);
            }
        }
        return out.toByteArray();
    }

    
    private static byte[] toModifiedUtf8(String s) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(bos)) {
            dos.writeUTF(s);
        } catch (IOException impossible) {
            throw new AssertionError(impossible); 
        }
        byte[] all = bos.toByteArray();
        
        byte[] body = new byte[all.length - 2];
        System.arraycopy(all, 2, body, 0, body.length);
        return body;
    }

    

    /**
     * @param index 常量池索引
     * @return 对应条目
     */
    Entry entry(int index) {
        return entries.get(index);
    }

    /**
     * @param index 常量池索引
     * @return 对应索引的 UTF8 字符串
     */
    String utf8(int index) {
        return entries.get(index).utf8;
    }

    /**
     * 获取 Class 条目的类名。
     *
     * @param classIndex Class 条目索引
     * @return 类内部名称
     */
    String className(int classIndex) {
        return utf8(entries.get(classIndex).nameIndex);
    }

    /**
     * 获取 NameAndType 条目的名称。
     *
     * @param natIndex NameAndType 条目索引
     * @return 名称
     */
    String natName(int natIndex) {
        return utf8(entries.get(natIndex).nameIndex);
    }

    /**
     * 获取 NameAndType 条目的描述符。
     *
     * @param natIndex NameAndType 条目索引
     * @return 描述符
     */
    String natDescriptor(int natIndex) {
        return utf8(entries.get(natIndex).descriptorIndex);
    }

    

    
    /**
     * 添加一个 UTF8 字符串常量，有缓存，重复值返回相同索引。
     *
     * @param value 字符串值
     * @return 常量池索引
     */
    int addUtf8(String value) {
        Integer existing = utf8Cache.get(value);
        if (existing != null) {
            return existing;
        }
        Entry e = new Entry(TAG_UTF8);
        e.utf8 = value;
        entries.add(e);
        int index = entries.size() - 1;
        utf8Cache.put(value, index);
        return index;
    }

    /**
     * 添加一个 Class 引用常量（有去重）。
     *
     * @param internalName 类内部名称（如 {@code java/lang/String}）
     * @return 常量池索引
     */
    int addClass(String internalName) {
        for (int i = 1; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e != null && e.tag == TAG_CLASS && utf8(e.nameIndex).equals(internalName)) {
                return i;
            }
        }
        Entry e = new Entry(TAG_CLASS);
        e.nameIndex = addUtf8(internalName);
        entries.add(e);
        return entries.size() - 1;
    }

    /**
     * 添加一个 String 常量。
     *
     * @param value 字符串值
     * @return 常量池索引
     */
    int addString(String value) {
        int utf8 = addUtf8(value);
        Entry e = new Entry(TAG_STRING);
        e.nameIndex = utf8;
        entries.add(e);
        return entries.size() - 1;
    }

    /**
     * 添加一个 NameAndType 条目。
     *
     * @param name       名称
     * @param descriptor 描述符
     * @return 常量池索引
     */
    int addNameAndType(String name, String descriptor) {
        Entry e = new Entry(TAG_NAME_AND_TYPE);
        e.nameIndex = addUtf8(name);
        e.descriptorIndex = addUtf8(descriptor);
        entries.add(e);
        return entries.size() - 1;
    }

    /**
     * 添加一个 Fieldref 引用。
     *
     * @param classInternal 字段所在类的内部名称
     * @param name          字段名
     * @param descriptor    字段描述符
     * @return 常量池索引
     */
    int addFieldref(String classInternal, String name, String descriptor) {
        int c = addClass(classInternal);
        int nat = addNameAndType(name, descriptor);
        Entry e = new Entry(TAG_FIELDREF);
        e.classIndex = c;
        e.nameAndTypeIndex = nat;
        entries.add(e);
        return entries.size() - 1;
    }

    /**
     * 添加一个 Methodref 引用。
     *
     * @param classInternal 方法所在类的内部名称
     * @param name          方法名
     * @param descriptor    方法描述符
     * @return 常量池索引
     */
    int addMethodref(String classInternal, String name, String descriptor) {
        int c = addClass(classInternal);
        int nat = addNameAndType(name, descriptor);
        Entry e = new Entry(TAG_METHODREF);
        e.classIndex = c;
        e.nameAndTypeIndex = nat;
        entries.add(e);
        return entries.size() - 1;
    }

    /**
     * 在常量池中查找指定值的 String 常量。
     *
     * @param value 字符串值
     * @return 常量池索引，未找到时返回 -1
     */
    int findString(String value) {
        for (int i = 1; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e != null && e.tag == TAG_STRING && utf8(e.nameIndex).equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
