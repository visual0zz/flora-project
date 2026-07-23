package com.flora.classfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.Collections;


/**
 * 字节码操作工具，支持解析和汇编 JVM 方法的 Code 属性。
 * <p>提供从字节码字节序列解析为指令列表（{@link CodeModel}）的能力，
 * 并在修改后支持重新汇编（含偏移量和分支目标的重计算）。</p>
 */
public final class Bytecode {

    /**
     * 单条 JVM 指令，包含偏移量、操作码、长度和原始字节。
     */
    public static final class Insn {
        public final int offset;
        public final int opcode;
        public final int length;
        public final byte[] raw;

        Insn(int offset, int opcode, int length, byte[] raw) {
            this.offset = offset;
            this.opcode = opcode;
            this.length = length;
            this.raw = raw;
        }

        /**
         * @return 操作数部分的字节（不含操作码字节）
         */
        public byte[] operands() {
            byte[] op = new byte[raw.length - 1];
            System.arraycopy(raw, 1, op, 0, op.length);
            return op;
        }
    }

    /**
     * Code 属性的模型表示，包含操作栈/局部变量信息、指令列表、异常表和属性。
     */
    public static final class CodeModel {
        public int maxStack;
        public int maxLocals;
        public final List<Insn> instructions = new ArrayList<>();
        /** 异常表条目：[startPc, endPc, handlerPc, catchType] */
        public final List<int[]> exceptionTable = new ArrayList<>();
        public final List<ClassModel.Attribute> attributes = new ArrayList<>();

        Function<Integer, String> nameResolver;

        /**
         * 将当前 CodeModel 重新汇编为字节序列（不做指令替换）。
         *
         * @return 重新汇编后的 Code 属性字节
         */
        public byte[] toBytes() {
            try {
                return assemble(Collections.emptyMap());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * 将当前 CodeModel 重新汇编为字节序列，并应用指令替换。
         * <p>替换后自动重算所有分支指令的目标偏移量，以及异常表、
         * LineNumberTable、LocalVariableTable、StackMapTable 中的偏移量。</p>
         *
         * @param replacements 指令偏移量到替换后字节的映射
         * @return 重新汇编后的 Code 属性字节
         * @throws IOException 如果汇编过程中发生 I/O 错误
         */
        public byte[] assemble(Map<Integer, byte[]> replacements) throws IOException {
            int n = instructions.size();
            if (n == 0) {
                ByteArrayBuilder out = new ByteArrayBuilder();
                out.writeShort(maxStack);
                out.writeShort(maxLocals);
                out.writeInt(0);
                out.writeShort(exceptionTable.size());
                for (int[] e : exceptionTable) {
                    out.writeShort(e[0]);
                    out.writeShort(e[1]);
                    out.writeShort(e[2]);
                    out.writeShort(e[3]);
                }
                out.write(ClassModel.serializeAttributes(attributes));
                return out.toByteArray();
            }
            int[] newOff = new int[n];
            int off = 0;
            for (int i = 0; i < n; i++) {
                Insn in = instructions.get(i);
                byte[] raw = replacements.getOrDefault(in.offset, in.raw);
                newOff[i] = off;
                off += raw.length;
            }
            int oldCodeLen = instructions.get(n - 1).offset + instructions.get(n - 1).raw.length;
            int[] map = new int[Math.max(oldCodeLen, 1)];
            for (int i = 0; i < n; i++) {
                Insn in = instructions.get(i);
                int o = in.offset;
                int ol = in.raw.length;
                for (int j = 0; j < ol; j++) {
                    map[o + j] = newOff[i] + j;
                }
            }
            int totalNew = off;
            IntUnaryOperator trans = pc -> {
                if (pc < 0) {
                    return 0;
                }
                if (pc >= oldCodeLen) {
                    return totalNew;
                }
                return map[pc];
            };
            
            ByteArrayBuilder codeOut = new ByteArrayBuilder();
            for (int i = 0; i < n; i++) {
                Insn in = instructions.get(i);
                byte[] raw = replacements.getOrDefault(in.offset, in.raw);
                if (isBranch(in.opcode)) {
                    emitBranch(codeOut, in, newOff[i], trans);
                } else {
                    codeOut.write(raw);
                }
            }
            
            ByteArrayBuilder exOut = new ByteArrayBuilder();
            exOut.writeShort(exceptionTable.size());
            for (int[] e : exceptionTable) {
                exOut.writeShort(trans.applyAsInt(e[0]));
                exOut.writeShort(trans.applyAsInt(e[1]));
                exOut.writeShort(trans.applyAsInt(e[2]));
                exOut.writeShort(e[3]);
            }
            
            List<ClassModel.Attribute> newAttrs = new ArrayList<>();
            for (ClassModel.Attribute a : attributes) {
                byte[] info = a.info;
                if (nameResolver != null) {
                    String name = nameResolver.apply(a.nameIndex);
                    if ("LineNumberTable".equals(name)) {
                        info = translateLineNumberTable(info, trans);
                    } else if ("LocalVariableTable".equals(name)
                            || "LocalVariableTypeTable".equals(name)) {
                        info = translateLocalVarTable(info, trans);
                    } else if ("StackMapTable".equals(name)) {
                        info = translateStackMapTable(info, trans);
                    }
                }
                newAttrs.add(new ClassModel.Attribute(a.nameIndex, info));
            }
            ByteArrayBuilder out = new ByteArrayBuilder();
            out.writeShort(maxStack);
            out.writeShort(maxLocals);
            out.writeInt(codeOut.size());
            out.write(codeOut);
            out.write(exOut);
            out.write(ClassModel.serializeAttributes(newAttrs));
            return out.toByteArray();
        }
    }

    
    private static final int[] BASE_LEN = new int[256];

    static {
        
        for (int i = 0; i < 256; i++) {
            BASE_LEN[i] = -1;
        }
        
        
        int[] fixed = {
                0x00, 1, 0x01, 1, 0x02, 1, 0x03, 1, 0x04, 1, 0x05, 1, 0x06, 1, 0x07, 1, 0x08, 1,
                0x09, 1, 0x0a, 1, 0x0b, 1, 0x0c, 1, 0x0d, 1, 0x0e, 1,
                0x0f, 1, 0x10, 2, 0x11, 3, 0x12, 2, 0x13, 3, 0x14, 3,
                0x15, 1, 0x16, 1, 0x17, 1, 0x18, 1, 0x19, 1,
                0x1a, 1, 0x1b, 1, 0x1c, 1, 0x1d, 1, 0x1e, 1, 0x1f, 1, 0x20, 1, 0x21, 1,
                0x22, 1, 0x23, 1, 0x24, 1, 0x25, 1, 0x26, 1, 0x27, 1, 0x28, 1, 0x29, 1,
                0x2a, 1, 0x2b, 1, 0x2c, 1, 0x2d, 1,
                0x2e, 1, 0x2f, 1, 0x30, 1, 0x31, 1, 0x32, 1, 0x33, 1, 0x34, 1, 0x35, 1,
                0x36, 1, 0x37, 1, 0x38, 1, 0x39, 1, 0x3a, 1,
                0x3b, 1, 0x3c, 1, 0x3d, 1, 0x3e, 1, 0x3f, 1, 0x40, 1, 0x41, 1, 0x42, 1, 0x43, 1,
                0x44, 1, 0x45, 1, 0x46, 1, 0x47, 1, 0x48, 1, 0x49, 1, 0x4a, 1,
                0x4b, 1, 0x4c, 1, 0x4d, 1, 0x4e, 1,
                0x4f, 1, 0x50, 1, 0x51, 1, 0x52, 1, 0x53, 1, 0x54, 1, 0x55, 1, 0x56, 1,
                0x57, 1, 0x58, 1, 0x59, 1, 0x5a, 1, 0x5b, 1, 0x5c, 1, 0x5d, 1, 0x5e, 1, 0x5f, 1,
                0x60, 1, 0x61, 1, 0x62, 1, 0x63, 1, 0x64, 1, 0x65, 1, 0x66, 1, 0x67, 1, 0x68, 1,
                0x69, 1, 0x6a, 1, 0x6b, 1, 0x6c, 1, 0x6d, 1, 0x6e, 1, 0x6f, 1, 0x70, 1, 0x71, 1,
                0x72, 1, 0x73, 1, 0x74, 1, 0x75, 1, 0x76, 1, 0x77, 1, 0x78, 1, 0x79, 1, 0x7a, 1,
                0x7b, 1, 0x7c, 1, 0x7d, 1, 0x7e, 1, 0x7f, 1, 0x80, 1, 0x81, 1,
                0x82, 1, 0x83, 1,             
                0x84, 3,                      
                0x85, 1, 0x86, 1,             
                0x87, 1, 0x88, 1, 0x89, 1, 0x8a, 1, 0x8b, 1, 0x8c, 1, 0x8d, 1, 0x8e, 1, 0x8f, 1,
                0x90, 1, 0x91, 1, 0x92, 1, 0x93, 1, 
                0x94, 1, 0x95, 1, 0x96, 1, 0x97, 1, 0x98, 1, 
                0x99, 3, 0x9a, 3, 0x9b, 3, 0x9c, 3, 0x9d, 3, 0x9e, 3, 0x9f, 3, 0xa0, 3, 0xa1, 3,
                0xa2, 3, 0xa3, 3, 0xa4, 3, 0xa5, 3, 0xa6, 3, 0xa7, 3, 0xa8, 3, 
                0xa9, 2,                      
                0xaa, 1, 0xab, 1,             
                0xac, 1, 0xad, 1, 0xae, 1, 0xaf, 1, 0xb0, 1, 0xb1, 1, 
                0xb2, 3, 0xb3, 3, 0xb4, 3, 0xb5, 3, 0xb6, 3, 0xb7, 3, 0xb8, 3, 0xb9, 5, 0xba, 5,
                0xbb, 3, 0xbc, 2, 0xbd, 3, 0xbe, 1, 0xbf, 1, 
                0xc0, 3, 0xc1, 3, 0xc2, 1, 0xc3, 1, 
                0xc4, 3,                      
                0xc5, 4, 0xc6, 3, 0xc7, 3, 0xc8, 5, 0xc9, 5, 
        };
        for (int i = 0; i < fixed.length; i += 2) {
            BASE_LEN[fixed[i]] = fixed[i + 1];
        }
    }

    private Bytecode() {
    }

    
    private static int lengthOf(byte[] code, int pos) throws IOException {
        int op = code[pos] & 0xff;
        switch (op) {
            case 0xaa: { 
                int p = pos + 1;
                p += (4 - (p % 4)) % 4; 
                int low = readInt(code, p + 4);
                int high = readInt(code, p + 8);
                return (p + 12 + (high - low + 1) * 4) - pos;
            }
            case 0xab: { 
                int p = pos + 1;
                p += (4 - (p % 4)) % 4;
                int npairs = readInt(code, p + 4);
                return (p + 8 + npairs * 8) - pos;
            }
            case 0xc4: { 
                int sub = code[pos + 1] & 0xff;
                return sub == 0x84 ? 6 : 4; 
            }
            default: {
                int len = BASE_LEN[op];
                if (len < 0) {
                    throw new IOException("不支持/未知的操作码: 0x" + Integer.toHexString(op)
                            + " @ " + pos);
                }
                return len;
            }
        }
    }

    private static int readInt(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16)
                | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    
    /**
     * 解析 Code 属性的字节数据为 {@link CodeModel}。
     * <p>自动识别每条指令的长度，包括 tableswitch ({@code 0xaa})、
     * lookupswitch ({@code 0xab}) 和 wide ({@code 0xc4}) 等变长指令。</p>
     *
     * @param codeInfo     Code 属性的完整字节（包括 maxStack、maxLocals）
     * @param nameResolver 常量池名称解析器（用于后续属性翻译）
     * @return 解析后的 CodeModel
     * @throws IOException 如果字节码格式不合法或不支持的指令
     */
    public static CodeModel parse(byte[] codeInfo, Function<Integer, String> nameResolver) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeInfo));
        CodeModel cm = new CodeModel();
        cm.nameResolver = nameResolver;
        cm.maxStack = in.readUnsignedShort();
        cm.maxLocals = in.readUnsignedShort();
        int codeLength = in.readInt();
        byte[] code = new byte[codeLength];
        int read = in.readNBytes(code, 0, codeLength);
        if (read != codeLength) {
            throw new IOException("Code 字节不足");
        }
        
        int pos = 0;
        while (pos < codeLength) {
            int len = lengthOf(code, pos);
            if (pos + len > codeLength) {
                throw new IOException("指令长度越界: opcode=0x"
                        + Integer.toHexString(code[pos] & 0xff) + " @ " + pos
                        + " len=" + len + " 剩余=" + (codeLength - pos));
            }
            byte[] raw = new byte[len];
            System.arraycopy(code, pos, raw, 0, len);
            cm.instructions.add(new Insn(pos, code[pos] & 0xff, len, raw));
            pos += len;
        }
        int exLen = in.readUnsignedShort();
        for (int i = 0; i < exLen; i++) {
            cm.exceptionTable.add(new int[]{
                    in.readUnsignedShort(), in.readUnsignedShort(),
                    in.readUnsignedShort(), in.readUnsignedShort()});
        }
        cm.attributes.addAll(ClassModel.parseAttributes(restOf(in)));
        return cm;
    }

    
    private static byte[] restOf(DataInputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    

    private static boolean isBranch(int op) {
        return (op >= 0x99 && op <= 0xa8) || op == 0xaa || op == 0xab
                || op == 0xc6 || op == 0xc7 || op == 0xc8 || op == 0xc9;
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    
    private static void emitBranch(ByteArrayBuilder out, Insn in, int newInstrOff,
                                   IntUnaryOperator trans) {
        int op = in.opcode;
        byte[] o = in.operands();
        out.writeByte(op);
        switch (op) {
            case 0x99: case 0x9a: case 0x9b: case 0x9c: case 0x9d: case 0x9e:
            case 0x9f: case 0xa0: case 0xa1: case 0xa2: case 0xa3: case 0xa4:
            case 0xa5: case 0xa6: case 0xa7: case 0xa8: case 0xc6: case 0xc7: {
                int oldTarget = in.offset + (short) u2(o, 0);
                int rel = trans.applyAsInt(oldTarget) - newInstrOff;
                out.writeShort(rel);
                break;
            }
            case 0xc8: case 0xc9: {
                int oldTarget = in.offset + readInt(o, 0);
                out.writeInt(trans.applyAsInt(oldTarget) - newInstrOff);
                break;
            }
            case 0xaa: { 
                int pad = (4 - ((in.offset + 1) % 4)) % 4;
                int low = readInt(o, pad + 4);
                int high = readInt(o, pad + 8);
                for (int k = 0; k < pad; k++) {
                    out.writeByte(0);
                }
                int defOld = in.offset + readInt(o, pad);
                out.writeInt(trans.applyAsInt(defOld) - newInstrOff);
                out.writeInt(low);
                out.writeInt(high);
                int nn = high - low + 1;
                for (int i = 0; i < nn; i++) {
                    int tgt = in.offset + readInt(o, pad + 12 + i * 4);
                    out.writeInt(trans.applyAsInt(tgt) - newInstrOff);
                }
                break;
            }
            case 0xab: { 
                int pad = (4 - ((in.offset + 1) % 4)) % 4;
                int npairs = readInt(o, pad + 4);
                for (int k = 0; k < pad; k++) {
                    out.writeByte(0);
                }
                int defOld = in.offset + readInt(o, pad);
                out.writeInt(trans.applyAsInt(defOld) - newInstrOff);
                out.writeInt(npairs);
                for (int i = 0; i < npairs; i++) {
                    int match = readInt(o, pad + 8 + i * 8);
                    int tgt = in.offset + readInt(o, pad + 8 + i * 8 + 4);
                    out.writeInt(match);
                    out.writeInt(trans.applyAsInt(tgt) - newInstrOff);
                }
                break;
            }
            default:
                out.write(o); 
        }
    }

    private static byte[] translateLineNumberTable(byte[] info, IntUnaryOperator trans)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        int count = in.readUnsignedShort();
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(count);
        for (int i = 0; i < count; i++) {
            out.writeShort(trans.applyAsInt(in.readUnsignedShort()));
            out.writeShort(in.readUnsignedShort());
        }
        return out.toByteArray();
    }

    private static byte[] translateLocalVarTable(byte[] info, IntUnaryOperator trans)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        int count = in.readUnsignedShort();
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(count);
        for (int i = 0; i < count; i++) {
            int start = in.readUnsignedShort();
            int len = in.readUnsignedShort();
            int name = in.readUnsignedShort();
            int desc = in.readUnsignedShort();
            int index = in.readUnsignedShort();
            int newEnd = trans.applyAsInt(start + len);
            out.writeShort(trans.applyAsInt(start));
            out.writeShort(newEnd - trans.applyAsInt(start));
            out.writeShort(name);
            out.writeShort(desc);
            out.writeShort(index);
        }
        return out.toByteArray();
    }

    
    private static byte[] readVType(DataInputStream in, IntUnaryOperator trans, boolean[] changed)
            throws IOException {
        int tag = in.readUnsignedByte();
        if (tag == 7) {
            int cp = in.readUnsignedShort();
            return new byte[]{(byte) 7, (byte) (cp >> 8), (byte) cp};
        }
        if (tag == 8) {
            int off = in.readUnsignedShort();
            int noff = trans.applyAsInt(off);
            if (noff != off) {
                changed[0] = true;
            }
            return new byte[]{(byte) 8, (byte) (noff >> 8), (byte) noff};
        }
        return new byte[]{(byte) tag};
    }

    
    private static byte[] translateStackMapTable(byte[] info, IntUnaryOperator trans)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        int entries;
        try {
            entries = in.readUnsignedShort();
        } catch (EOFException e) {
            return info;
        }
        // 快速校验：entries 数量不合理时直接跳过
        if (entries > 2000) {
            return info;
        }
        ByteArrayBuilder out = new ByteArrayBuilder();
        out.writeShort(entries);
        List<byte[]> curLocals = new ArrayList<>();
        boolean[] changed = {false};
        int prevOffset = -1;
        int prevNew = -1;
        try {
            for (int e = 0; e < entries; e++) {
            int ft = in.readUnsignedByte();
            int delta;
            List<byte[]> stack = new ArrayList<>();
            if (ft <= 63) {
                delta = ft;
            } else if (ft <= 127) {
                delta = ft - 64;
                stack.add(readVType(in, trans, changed));
            } else if (ft == 247) {
                delta = in.readUnsignedShort();
                stack.add(readVType(in, trans, changed));
            } else if (ft >= 248 && ft <= 250) {
                int k = 251 - ft;
                for (int x = 0; x < k; x++) {
                    curLocals.remove(curLocals.size() - 1);
                }
                delta = in.readUnsignedShort();
            } else if (ft >= 251 && ft <= 254) {
                
                delta = in.readUnsignedShort();
                int k = ft - 251;
                for (int x = 0; x < k; x++) {
                    curLocals.add(readVType(in, trans, changed));
                }
            } else if (ft == 255) {
                delta = in.readUnsignedShort();
                int lc = in.readUnsignedShort();
                curLocals.clear();
                for (int x = 0; x < lc; x++) {
                    curLocals.add(readVType(in, trans, changed));
                }
                int sc = in.readUnsignedShort();
                for (int x = 0; x < sc; x++) {
                    stack.add(readVType(in, trans, changed));
                }
            } else {
                throw new IOException("不支持的 StackMapFrame 类型: " + ft);
            }
            int actual = prevOffset + delta + 1;
            int newActual = trans.applyAsInt(actual);
            if (newActual != actual) {
                changed[0] = true;
            }
            int newDelta = newActual - prevNew - 1;
            
            out.writeByte(255);
            out.writeShort(newDelta);
            out.writeShort(curLocals.size());
            for (byte[] v : curLocals) {
                out.write(v);
            }
            out.writeShort(stack.size());
            for (byte[] v : stack) {
                out.write(v);
            }
            prevOffset = actual;
            prevNew = newActual;
            }
        } catch (java.io.EOFException e) {
            // StackMapTable 数据不完整，跳过翻译
            return info;
        }
        if (!changed[0]) {
            return info; 
        }
        return out.toByteArray();
    }
}
