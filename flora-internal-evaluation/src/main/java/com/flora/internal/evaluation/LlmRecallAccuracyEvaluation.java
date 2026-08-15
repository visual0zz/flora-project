package com.flora.internal.evaluation;

import com.flora.root.ai.AiApi;
import com.flora.root.ai.api.ChatClient;
import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ChatResponse;
import com.flora.root.ai.api.Message;
import com.flora.root.codec.HexUtil;
import com.flora.root.common.words.WordList;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 大模型复述准确率人工评测。
 * <p>用 {@link SecureRandom} 生成随机字节序列，分别翻译成三种表示形式——hex、
 * base64、英文单词列——交给大模型复述，然后把模型回复与输入逐字符核对，统计并打印
 * 每种表示形式的复述准确率（整串全对率与字符级准确率）。</p>
 * <p>运行：需设置环境变量 {@code DEEPSEEK_API_KEY}（与本仓库 AI 实时测试一致），
 * 直接运行 {@link #main}。模型为 DeepSeek {@code deepseek-chat}。</p>
 */
public final class LlmRecallAccuracyEvaluation {

    private static final String DEEPSEEK_BASE = "https://api.deepseek.com";
    private static final String MODEL = "deepseek-chat";
    private static final String ENDPOINT_ID = "recall-eval";

    /** 每种表示形式的样本数。 */
    private static final int SAMPLES_PER_FORM = 1;
    /** 随机字节序列长度（bit）。 */
    private static final int BITS_LENGTH = 10 * 1024 * 8;

    /** 内置英文词表（8192 = 2^13 词），词索引 = 13 bit，作为"英文单词列"表示的词典。 */
    private static final WordList ENGLISH_WORDS = WordList.english();

    /** 词表索引位数：log2(8192) = 13。 */
    private static final int WORD_BITS = 13;

    private LlmRecallAccuracyEvaluation() {
    }

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("缺少环境变量 DEEPSEEK_API_KEY，无法调用大模型。");
            return;
        }

        ChatClient client = registerClient(apiKey);
        SecureRandom random = new SecureRandom();

        System.out.println("======== 大模型复述准确率评测 ========");
        System.out.println("模型: " + MODEL + "，每种表示形式样本数: " + SAMPLES_PER_FORM
                + "，熵: " + BITS_LENGTH + " bit");

        evaluateForm(client, random, "HEX", HEX);
        evaluateForm(client, random, "BASE64", BASE64);
        evaluateForm(client, random, "WORDLIST", WORDLIST);

        AiApi.unregister(ENDPOINT_ID);
        System.out.println("======== 评测结束 ========");
    }

    /** 对某一种表示形式生成样本、让模型复述、核对并打印统计。 */
    private static void evaluateForm(ChatClient client, SecureRandom random, String label,
                                     Representation encoder) throws Exception {
        int exactMatches = 0;
        int charMatched = 0;
        int charTotal = 0;
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < SAMPLES_PER_FORM; i++) {
            byte[] bytes = new byte[BITS_LENGTH / 8];
            random.nextBytes(bytes);
            String input = encoder.encode(bytes);

            String reply = recall(client, input);

            boolean exact = reply.equals(input);
            if (exact) {
                exactMatches++;
            } else {
                failures.add(input + "  =>  " + reply);
            }
            int[] cmp = compare(input, reply);
            charMatched += cmp[0];
            charTotal += cmp[1];
            // 长度核对：输入 vs 回复长度，用于区分"截断"（回复远短于输入）与"编错"（等长但对不上）
            double lenRatio = input.isEmpty() ? 1.0 : (double) reply.length() / input.length();
            System.out.printf("  [样本%d] 输入长度=%d 回复长度=%d 长度比=%.2f%n",
                    i + 1, input.length(), reply.length(), lenRatio);
        }

        double totalCharAccuracy = charTotal == 0 ? 0.0 : (double) charMatched / charTotal;
        System.out.printf("【%s】整串全对率: %d/%d (%.1f%%)，字符级准确率: %.2f%%%n",
                label, exactMatches, SAMPLES_PER_FORM,
                100.0 * exactMatches / SAMPLES_PER_FORM, totalCharAccuracy * 100);
        if (!failures.isEmpty()) {
            System.out.println("  未全对的样本：");
            for (String f : failures) {
                System.out.println("    " + f);
            }
        }
        System.out.println();
    }

    /** 向模型发起一次复述请求并返回回复文本（做基本归一化）。 */
    private static String recall(ChatClient client, String data) throws Exception {
        String prompt = "请原样复述下面这一串数据，不要添加任何解释、前后缀或格式，"
                + "只输出这一串数据本身：\n\n" + data;
        ChatRequest request = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, prompt))
                .build();
        ChatResponse response = client.chat(request);
        String text = response.text();
        return normalize(text);
    }

    /** 去掉回复中的空白、换行与常见包裹引号，便于与纯数据串比对。 */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        // 去掉外层可能出现的反引号 / 引号包裹
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed.replaceAll("\\s+", "");
    }

    /** 逐字符核对两个串，返回 [匹配字符数, 总字符数]。 */
    private static int[] compare(String a, String b) {
        int n = Math.max(a.length(), b.length());
        if (n == 0) {
            return new int[]{0, 0};
        }
        int matched = 0;
        for (int i = 0; i < n; i++) {
            char ca = i < a.length() ? a.charAt(i) : '\0';
            char cb = i < b.length() ? b.charAt(i) : '\0';
            if (ca == cb) {
                matched++;
            }
        }
        return new int[]{matched, n};
    }

    private static ChatClient registerClient(String apiKey) {
        String json = "{\"id\":\"" + ENDPOINT_ID + "\",\"apiKind\":\"DEEPSEEK_OFFICIAL\","
                + "\"modelId\":\"" + MODEL + "\",\"baseUrl\":\"" + DEEPSEEK_BASE + "\","
                + "\"apiKey\":\"" + apiKey + "\",\"capabilities\":[\"CHAT\"]}";
        AiApi.register(json);
        return AiApi.getByName(ENDPOINT_ID + ":CHAT", ChatClient.class);
    }

    /** hex 表示。 */
    private static String toHex(byte[] bytes) {
        return HexUtil.encodeHex(bytes);
    }

    /** base64 表示。 */
    private static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 英文单词列表示：把字节序列按位流拼接，每 13 bit 取一个词索引（对应内置 8192 词词表），
     * 词间用连字符连接。词索引 = 词表下标，故该表示与 hex/base64 一样是字节序列的确定编码。
     */
    private static String toWordList(byte[] bytes) {
        int totalBits = bytes.length * 8;
        int wordCount = (totalBits + WORD_BITS - 1) / WORD_BITS;
        StringBuilder sb = new StringBuilder();
        for (int w = 0; w < wordCount; w++) {
            int idx = readBits(bytes, w * WORD_BITS, WORD_BITS);
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(ENGLISH_WORDS.wordAt(idx));
        }
        return sb.toString();
    }

    /** 从字节位流（MSB 优先）读取 length 位，返回无符号整数。 */
    private static int readBits(byte[] bytes, int bitOffset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            int bit = bitOffset + i;
            int byteIndex = bit >>> 3;
            int bitInByte = 7 - (bit & 7);
            int bitValue = (byteIndex < bytes.length)
                    ? ((bytes[byteIndex] >>> bitInByte) & 1)
                    : 0;
            value = (value << 1) | bitValue;
        }
        return value;
    }

    /** 表示形式编码器。 */
    private interface Representation {
        String encode(byte[] bytes);
    }

    /** 让 evaluateForm 能复用 this::toHex 等实例方法引用（成员内部使用）。 */
    private static final Representation HEX = LlmRecallAccuracyEvaluation::toHex;
    private static final Representation BASE64 = LlmRecallAccuracyEvaluation::toBase64;
    private static final Representation WORDLIST = LlmRecallAccuracyEvaluation::toWordList;
}
