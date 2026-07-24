package com.flora.entropy.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.flora.entropy.hash.UsageScenario.*;
import static com.flora.entropy.hash.UsageScenario.ADDRESSING;
import static com.flora.entropy.hash.UsageScenario.ENCRYPTING;

import com.flora.codec.HexUtil;

public final class StandardHash {
    /**
     * 计算字符串的 MD5 哈希（十六进制字符串）。
     *
     * @param s 输入字符串
     * @return 32 位小写十六进制哈希字符串
     */
    @SuitedFor(COMPATIBLE)
    public static String md5(String s) {
        return digestHex("MD5", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 哈希。
     *
     * @param data 输入数据
     * @return 16 字节哈希值
     */
    @SuitedFor(COMPATIBLE)
    public static byte[] md5(byte[] data) {
        return digest("MD5", data);
    }

    /**
     * 计算字符串的 SHA-1 哈希（十六进制字符串）。
     *
     * @param s 输入字符串
     * @return 40 位小写十六进制哈希字符串
     */
    @SuitedFor(COMPATIBLE)
    public static String sha1(String s) {
        return digestHex("SHA-1", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-1 哈希。
     *
     * @param data 输入数据
     * @return 20 字节哈希值
     */
    @SuitedFor(COMPATIBLE)
    public static byte[] sha1(byte[] data) {
        return digest("SHA-1", data);
    }

    /**
     * 计算字符串的 SHA-256 哈希（十六进制字符串）。
     *
     * @param s 输入字符串
     * @return 64 位小写十六进制哈希字符串
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha256(String s) {
        return digestHex("SHA-256", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-256 哈希。
     *
     * @param data 输入数据
     * @return 32 字节哈希值
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha256(byte[] data) {
        return digest("SHA-256", data);
    }

    /**
     * 计算字符串的 SHA-512 哈希（十六进制字符串）。
     *
     * @param s 输入字符串
     * @return 128 位小写十六进制哈希字符串
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha512(String s) {
        return digestHex("SHA-512", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-512 哈希。
     *
     * @param data 输入数据
     * @return 64 字节哈希值
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha512(byte[] data) {
        return digest("SHA-512", data);
    }

    /**
     * 计算字符串的 SHA3-256 哈希（十六进制字符串）。
     *
     * @param s 输入字符串
     * @return 64 位小写十六进制哈希字符串
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha3(String s) {
        return digestHex("SHA-3-256", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA3-256 哈希。
     *
     * @param data 输入数据
     * @return 32 字节哈希值
     */
    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha3(byte[] data) {
        return digest("SHA-3-256", data);
    }

    /**
     * 通用消息摘要计算。
     *
     * @param algorithm 算法名称（如 "MD5"、"SHA-256"）
     * @param data      输入字节数组
     * @return 摘要字节数组
     */
    private static byte[] digest(String algorithm, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("Digest calculation failed: " + algorithm, e);
        }
    }

    /**
     * 计算消息摘要并返回十六进制小写字符串。
     *
     * @param algorithm 算法名称
     * @param data      输入字节数组
     * @return 十六进制字符串
     */
    private static String digestHex(String algorithm, byte[] data) {
        return bytesToHex(digest(algorithm, data));
    }

    /**
     * 将字节数组转换为十六进制小写字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        return HexUtil.encodeHex(bytes);
    }
}
