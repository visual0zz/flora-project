package com.flora.root.codec.jsonschema.format;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 常用格式校验器注册表（JSON Schema {@code format} 关键字）。
 * <p>只作用于字符串实例。未知格式在 schema 编译期抛错（严格模式）。</p>
 *
 * <p><b>支持的 format</b>：date/date-time/time/email/idn-email/hostname/idn-hostname/
 * ipv4/ipv6/uri/uri-reference/iri/iri-reference/uuid/regex/json-pointer/
 * relative-json-pointer/duration。</p>
 *
 * <p><b>正则校验（{@code format: "regex"} 与 {@code pattern} 关键字）</b>：
 * 使用 JDK {@code java.util.regex} 全特性（含环视/反向引用/命名组），
 * {@code pattern} 为 ECMA-262 搜索语义（{@code find()}），字符串任意位置命中即通过；
 * {@code format: "regex"} 则要求整个字符串匹配。</p>
 *
 * <p><b>不支持的语法</b>：未列出的 format 视为未知，编译期抛错。</p>
 */
public final class FormatValidators {

    private static final Map<String, Predicate<String>> FORMATS = new LinkedHashMap<>();

    private static final Pattern RFC3339_DATE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern RFC3339_TIME = Pattern.compile(
            "\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private static final Pattern JSON_POINTER = Pattern.compile(
            "^(/([^/~]|~[01])*)*$");
    private static final Pattern DURATION = Pattern.compile(
            "^P(?=\\d|T\\d)(?:\\d+Y)?(?:\\d+M)?(?:\\d+D)?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+(?:\\.\\d+)?S)?)?$");

    static {
        FORMATS.put("date-time", s -> validDateTime(s));
        FORMATS.put("date", s -> validDate(s));
        FORMATS.put("time", s -> validTime(s));
        FORMATS.put("email", s -> EMAIL.matcher(s).matches());
        FORMATS.put("idn-email", s -> EMAIL.matcher(s).matches());
        FORMATS.put("hostname", s -> HOSTNAME.matcher(s).matches());
        FORMATS.put("idn-hostname", s -> HOSTNAME.matcher(s).matches());
        FORMATS.put("ipv4", FormatValidators::validIpv4);
        FORMATS.put("ipv6", FormatValidators::validIpv6);
        FORMATS.put("uri", s -> validUri(s, false));
        FORMATS.put("uri-reference", s -> validUri(s, true));
        FORMATS.put("iri", s -> validUri(s, false));
        FORMATS.put("iri-reference", s -> validUri(s, true));
        FORMATS.put("uuid", s -> UUID.matcher(s).matches());
        FORMATS.put("regex", s -> validRegex(s));
        FORMATS.put("json-pointer", s -> s.isEmpty() || JSON_POINTER.matcher(s).matches());
        FORMATS.put("relative-json-pointer", FormatValidators::validRelativePointer);
        FORMATS.put("duration", s -> DURATION.matcher(s).matches());
    }

    private FormatValidators() {
    }

    public static boolean isKnown(String format) {
        return FORMATS.containsKey(format);
    }

    public static Predicate<String> get(String format) {
        return FORMATS.get(format);
    }

    // ── 实现 ──

    private static boolean validDate(String s) {
        return RFC3339_DATE.matcher(s).matches() && validCalendar(s);
    }

    private static boolean validDateTime(String s) {
        int t = s.indexOf('T');
        if (t < 0) {
            return false;
        }
        String date = s.substring(0, t);
        String time = s.substring(t + 1);
        return validDate(date) && validTime(time);
    }

    private static boolean validTime(String s) {
        return RFC3339_TIME.matcher(s).matches();
    }

    private static boolean validCalendar(String date) {
        try {
            String[] parts = date.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12 || day < 1) {
                return false;
            }
            int maxDay = switch (month) {
                case 2 -> isLeap(year) ? 29 : 28;
                case 4, 6, 9, 11 -> 30;
                default -> 31;
            };
            return day <= maxDay;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLeap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    private static boolean validIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || (part.length() > 1 && part.charAt(0) == '0')) {
                return false;
            }
            try {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean validIpv6(String s) {
        if (s.contains("%")) {
            return false; // 不含 zone index
        }
        int doubleColon = s.indexOf("::");
        if (s.indexOf("::", doubleColon + 1) >= 0) {
            return false; // 至多一个 ::
        }
        int groups = 0;
        String[] halves = s.split("::", -1);
        String left = halves[0];
        String right = halves.length > 1 ? halves[1] : "";
        if (doubleColon >= 0) {
            groups = countGroups(left) + countGroups(right);
            if (groups >= 8) {
                return false;
            }
        }
        return isValidHexGroupSequence(left) && isValidHexGroupSequence(right);
    }

    private static int countGroups(String s) {
        return s.isEmpty() ? 0 : s.split(":").length;
    }

    private static boolean isValidHexGroupSequence(String s) {
        if (s.isEmpty()) {
            return true;
        }
        for (String group : s.split(":")) {
            if (group.isEmpty() || group.length() > 4) {
                return false;
            }
            for (int i = 0; i < group.length(); i++) {
                char c = group.charAt(i);
                if (Character.digit(c, 16) < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validUri(String s, boolean reference) {
        try {
            URI uri = new URI(s);
            if (!reference) {
                return uri.getScheme() != null;
            }
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean validRegex(String s) {
        try {
            Pattern.compile(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validRelativePointer(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return false;
        }
        String rest = s.substring(i);
        return rest.isEmpty() || JSON_POINTER.matcher(rest).matches();
    }
}
