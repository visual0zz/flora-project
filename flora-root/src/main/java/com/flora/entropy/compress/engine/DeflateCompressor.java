package com.flora.entropy.compress.engine;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.entropy.compress.Compressor;
import com.flora.entropy.compress.CompressorAlgorithmFactoryRegister;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 把 JDK 自带的 {@link Deflater} / {@link Inflater} 接入 {@link Compressor} 接口。
 * <p>示例：{@code CompressorProvider.compressor("DEFLATE")} 或 {@code DeflateCompressor.of("DEFLATE")}。</p>
 */
@ThreadFragile
public final class DeflateCompressor implements Compressor {

    private final String algorithm;
    private final Deflater deflater;
    private final Inflater inflater;

    private DeflateCompressor(String algorithm) {
        this.algorithm = algorithm;
        this.deflater = new Deflater();
        this.inflater = new Inflater();
    }

    /**
     * 按算法名创建实例。
     *
     * @param algorithm 算法名（当前仅支持 {@code "DEFLATE"}）
     * @return 新实例
     */
    public static DeflateCompressor of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new DeflateCompressor(algorithm);
    }

    private static final Set<String> SUPPORTED = Set.of("DEFLATE");

    @Override
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    // ── 流式压缩 ──

    @Override
    public void update(byte[] in, int inOff, int len) {
        deflater.setInput(in, inOff, len);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        deflater.finish();
        int total = 0;
        while (!deflater.finished()) {
            int n = deflater.deflate(out, outOff + total, out.length - outOff - total);
            if (n == 0) break;
            total += n;
        }
        return total;
    }

    @Override
    public void reset() {
        deflater.reset();
    }

    // ── 流式解压 ──

    @Override
    public void decompressUpdate(byte[] in, int inOff, int len) {
        inflater.setInput(in, inOff, len);
    }

    @Override
    public int decompressDoFinal(byte[] out, int outOff) {
        int total = 0;
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(out, outOff + total, out.length - outOff - total);
                if (n == 0) break;
                total += n;
            }
        } catch (DataFormatException e) {
            throw new IllegalStateException("解压数据格式错误: " + algorithm, e);
        }
        return total;
    }

    @Override
    public void decompressReset() {
        inflater.reset();
    }

    // ── 一次性便捷入口 ──

    @Override
    public byte[] compress(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        Deflater d = new Deflater();
        try {
            d.setInput(data);
            d.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                int n = d.deflate(buf);
                if (n == 0) break;
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } finally {
            d.end();
        }
    }

    @Override
    public byte[] decompress(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length * 2);
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n;
                try {
                    n = inf.inflate(buf);
                } catch (DataFormatException e) {
                    throw new IllegalStateException("解压数据格式错误: " + algorithm, e);
                }
                if (n == 0) break;
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } finally {
            inf.end();
        }
    }

    @Override
    public AlgorithmFactory<? extends Compressor> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Compressor> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CompressorAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return SUPPORTED;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public Compressor construct(String algorithmName, AlgorithmComponent... components) {
            return DeflateCompressor.of(algorithmName);
        }
    };
}
