package com.flora.root.mock.jsonschema.impl;

import java.util.Locale;

/**
 * {@code format} 关键字的取值源：产出能通过 {@code FormatValidators} 校验的字符串。
 * <p>同时供 {@link SemanticStringGenerator} 复用（日期/时间/邮箱/URL/域名/IP/UUID 等
 * 语义与 format 取值本应一致），两处共用同一实现避免漂移。</p>
 * <p>全部取值走注入的熵源，同一种子可复现。</p>
 */
public final class FormatGenerator {

    private final RandomSupport random;

    FormatGenerator(RandomSupport random) {
        this.random = random;
    }

    /** 按 format 关键字取值；未知 format 退化为随机字母串。 */
    String generate(String format) {
        return switch (format) {
            case "date-time" -> dateTime();
            case "date" -> date();
            case "time" -> time();
            case "email", "idn-email" -> email();
            case "hostname", "idn-hostname" -> hostname();
            case "ipv4" -> ipv4();
            case "ipv6" -> ipv6();
            case "uri", "iri" -> uri();
            case "uri-reference", "iri-reference" -> "/" + random.randomAlpha(6);
            case "uuid" -> uuid();
            case "regex" -> "[a-z]+";
            case "json-pointer" -> "/" + random.randomAlpha(4) + "/" + random.randomAlpha(4);
            case "relative-json-pointer" -> random.intBetween(0, 9) + "/" + random.randomAlpha(4);
            case "duration" -> "P" + random.intBetween(1, 9) + "DT" + random.intBetween(0, 23) + "H";
            default -> random.randomAlpha(8);
        };
    }

    String date() {
        int year = random.intBetween(1970, 2030);
        int month = random.intBetween(1, 12);
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                year, month, random.intBetween(1, daysInMonth(year, month)));
    }

    String time() {
        return String.format(Locale.ROOT, "%02d:%02d:%02dZ",
                random.intBetween(0, 23), random.intBetween(0, 59), random.intBetween(0, 59));
    }

    String dateTime() {
        return date() + "T" + time();
    }

    String email() {
        return random.randomAlpha(random.intBetween(4, 8)) + "@"
                + random.randomAlpha(random.intBetween(3, 6)) + ".com";
    }

    String hostname() {
        return random.randomAlpha(random.intBetween(3, 6)) + "."
                + random.randomAlpha(random.intBetween(2, 4)) + ".com";
    }

    String uri() {
        return "https://" + random.randomAlpha(random.intBetween(3, 6)) + ".com/"
                + random.randomAlpha(random.intBetween(2, 6));
    }

    String ipv4() {
        return random.intBetween(1, 254) + "."
                + random.intBetween(0, 255) + "."
                + random.intBetween(0, 255) + "."
                + random.intBetween(1, 254);
    }

    String ipv6() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%x", random.intBetween(0, 0xffff)));
        }
        return sb.toString();
    }

    /** 由注入熵源构造 uuid 文本（不用 {@code UUID.randomUUID()}，保证同种子可复现）。 */
    String uuid() {
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 36; i++) {
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                sb.append('-');
            } else {
                sb.append("0123456789abcdef".charAt(random.intBetween(0, 15)));
            }
        }
        return sb.toString();
    }

    private static int daysInMonth(int year, int month) {
        return switch (month) {
            case 2 -> (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }
}
