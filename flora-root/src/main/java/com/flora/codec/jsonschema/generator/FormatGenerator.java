package com.flora.codec.jsonschema.generator;

import java.util.Locale;
import java.util.UUID;

/**
 * {@code format} 关键字逆向生成：产出能通过 {@code FormatValidators} 校验的字符串。
 */
final class FormatGenerator {

    private final RandomSupport random;

    FormatGenerator(RandomSupport random) {
        this.random = random;
    }

    String generate(String format) {
        return switch (format) {
            case "date-time" -> randomDateTime();
            case "date" -> randomDate();
            case "time" -> randomTime();
            case "email", "idn-email" -> randomEmail();
            case "hostname", "idn-hostname" -> randomHostname();
            case "ipv4" -> randomIpv4();
            case "ipv6" -> randomIpv6();
            case "uri", "iri" -> "https://example.com/" + random.randomAlpha(6);
            case "uri-reference", "iri-reference" -> "/" + random.randomAlpha(6);
            case "uuid" -> UUID.randomUUID().toString();
            case "regex" -> "[a-z]+";
            case "json-pointer" -> "/" + random.randomAlpha(4) + "/" + random.randomAlpha(4);
            case "relative-json-pointer" -> random.intBetween(0, 9) + "/" + random.randomAlpha(4);
            case "duration" -> "P" + random.intBetween(1, 9) + "DT" + random.intBetween(0, 23) + "H";
            default -> random.randomAlpha(8);
        };
    }

    private String randomDate() {
        int year = random.intBetween(1970, 2030);
        int month = random.intBetween(1, 12);
        int day = random.intBetween(1, daysInMonth(year, month));
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
    }

    private String randomTime() {
        return String.format(Locale.ROOT, "%02d:%02d:%02dZ",
                random.intBetween(0, 23), random.intBetween(0, 59), random.intBetween(0, 59));
    }

    private String randomDateTime() {
        return randomDate() + "T" + randomTime();
    }

    private String randomEmail() {
        return random.randomAlpha(6) + "@" + random.randomAlpha(5) + ".com";
    }

    private String randomHostname() {
        return random.randomAlpha(4) + "." + random.randomAlpha(3) + ".com";
    }

    private String randomIpv4() {
        return random.intBetween(1, 254) + "."
                + random.intBetween(0, 255) + "."
                + random.intBetween(0, 255) + "."
                + random.intBetween(1, 254);
    }

    private String randomIpv6() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%x", random.intBetween(0, 0xffff)));
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
