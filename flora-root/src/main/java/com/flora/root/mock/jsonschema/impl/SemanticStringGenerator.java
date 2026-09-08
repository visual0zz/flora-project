package com.flora.root.mock.jsonschema.impl;

import java.util.List;
import java.util.Locale;

/**
 * 字段名语义推断与语义化随机字符串生成。
 * <p>按属性名猜测字段含义（camelCase / snake_case / 中文拼音均可分词），
 * 生成"像样"的值：{@code email} 出邮箱、{@code phone} 出手机号、{@code createdAt} 出日期时间等。
 * 无法识别含义时退化为随机字母数字串。</p>
 *
 * <p><b>本类只负责"猜 + 造"，不负责合规</b>：生成值是否满足 {@code pattern}/
 * {@code minLength}/{@code maxLength} 由调用方判定，不合规可重试，
 * 累计 {@link #MAX_REJECTIONS} 次拒绝后调用方应改为按正则直接生成。</p>
 */
public final class SemanticStringGenerator {

    /** 语义候选被拒绝的最大次数：超过则放弃语义生成（由调用方改为按正则生成）。 */
    public static final int MAX_REJECTIONS = 5;

    private static final String[] FIRST_NAMES = {
            "Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Sophia", "Mason",
            "Isabella", "Lucas", "Mia", "Henry", "Charlotte", "Aiden"};
    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Wilson", "Anderson", "Taylor", "Moore", "Clark", "Lewis"};
    private static final String[] CITIES = {
            "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou", "Chengdu", "Wuhan",
            "Xian", "Nanjing", "Suzhou", "Tokyo", "Singapore", "London", "New York"};
    private static final String[] COUNTRIES = {
            "China", "Japan", "Singapore", "Germany", "France", "United Kingdom",
            "United States", "Canada", "Australia", "India"};
    private static final String[] COMPANY_PREFIX = {
            "Bright", "Nova", "Summit", "Vertex", "Aurora", "Cedar", "Orbit", "Pioneer",
            "Quantum", "Silver", "Union", "Vanguard"};
    private static final String[] COMPANY_SUFFIX = {
            "Technologies", "Labs", "Group", "Holdings", "Systems", "Networks", "Digital"};
    private static final String[] WORDS = {
            "alpha", "beacon", "cipher", "delta", "ember", "flux", "granite", "harbor",
            "ivory", "jade", "kettle", "lantern", "meadow", "nimbus", "onyx", "pebble"};
    private static final String[] STATUSES = {
            "active", "pending", "disabled", "archived", "draft", "published", "expired"};
    private static final String[] CATEGORIES = {
            "general", "business", "technology", "sports", "health", "education", "finance"};
    private static final String[] COLORS = {
            "red", "orange", "yellow", "green", "blue", "purple", "black", "white", "gray"};
    private static final String[] GENDERS = {"male", "female", "other", "unknown"};
    private static final String[] LOCALES = {"zh-CN", "en-US", "en-GB", "ja-JP", "de-DE", "fr-FR"};
    private static final String[] CURRENCIES = {"CNY", "USD", "EUR", "JPY", "GBP", "HKD"};

    private final RandomSupport random;

    SemanticStringGenerator(RandomSupport random) {
        this.random = random;
    }

    /** 字段含义：推断的语义类别。 */
    public enum Kind {
        FIRST_NAME, LAST_NAME, FULL_NAME, USERNAME, NICKNAME, PASSWORD,
        EMAIL, PHONE, TEL, URL, HOSTNAME, IP, UUID, ID,
        DATE, DATETIME, TIME, ADDRESS, CITY, COUNTRY, COMPANY,
        TITLE, DESCRIPTION, STATUS, CATEGORY, TAG, CURRENCY, PRICE,
        AGE, CODE, POSTAL, GENDER, COLOR, LOCALE, VERSION, PATH, PORT
    }

    // ── 语义推断 ──

    private record Rule(Kind kind, List<String> keys) {
    }

    /** 规则按优先级排列：更具体的语义在前（username 先于 name）。 */
    private static final List<Rule> RULES = List.of(
            new Rule(Kind.UUID, List.of("uuid", "guid")),
            new Rule(Kind.EMAIL, List.of("email", "mail")),
            new Rule(Kind.PASSWORD, List.of("password", "passwd", "pwd", "secret")),
            new Rule(Kind.USERNAME, List.of("username", "user", "account", "login", "accountname")),
            new Rule(Kind.NICKNAME, List.of("nickname", "nick", "displayname", "alias")),
            new Rule(Kind.FIRST_NAME, List.of("firstname", "first", "given", "givenname")),
            new Rule(Kind.LAST_NAME, List.of("lastname", "last", "family", "surname", "familyname")),
            new Rule(Kind.FULL_NAME, List.of("fullname", "realname", "name", "author", "owner", "contact")),
            new Rule(Kind.PHONE, List.of("phone", "mobile", "telephone", "cellphone", "msisdn")),
            new Rule(Kind.TEL, List.of("tel", "fax", "landline")),
            new Rule(Kind.URL, List.of("url", "uri", "link", "href", "website", "site", "homepage")),
            new Rule(Kind.HOSTNAME, List.of("hostname", "host", "domain")),
            new Rule(Kind.IP, List.of("ip", "ipaddress", "ipaddr")),
            new Rule(Kind.DATETIME, List.of("datetime", "timestamp", "createdat", "updatedat",
                    "createtime", "updatetime", "lastmodified")),
            new Rule(Kind.DATE, List.of("date", "birthday", "birthdate", "day")),
            new Rule(Kind.TIME, List.of("time", "clock")),
            new Rule(Kind.ADDRESS, List.of("address", "addr", "street", "location")),
            new Rule(Kind.CITY, List.of("city", "town")),
            new Rule(Kind.COUNTRY, List.of("country", "nation", "region")),
            new Rule(Kind.COMPANY, List.of("company", "org", "organization", "vendor", "brand", "merchant")),
            new Rule(Kind.TITLE, List.of("title", "subject", "headline", "caption")),
            new Rule(Kind.DESCRIPTION, List.of("description", "desc", "remark", "comment", "content",
                    "message", "detail", "intro", "introduction", "bio", "note", "summary")),
            new Rule(Kind.STATUS, List.of("status", "state")),
            new Rule(Kind.CATEGORY, List.of("category", "type", "kind", "class")),
            new Rule(Kind.TAG, List.of("tag", "label", "keyword")),
            new Rule(Kind.CURRENCY, List.of("currency")),
            new Rule(Kind.PRICE, List.of("price", "amount", "money", "cost", "fee", "salary",
                    "total", "balance", "revenue")),
            new Rule(Kind.AGE, List.of("age")),
            new Rule(Kind.POSTAL, List.of("postal", "zip", "zipcode", "postcode")),
            new Rule(Kind.CODE, List.of("code", "token", "serial", "sku", "no", "number")),
            new Rule(Kind.GENDER, List.of("gender", "sex")),
            new Rule(Kind.COLOR, List.of("color", "colour")),
            new Rule(Kind.LOCALE, List.of("locale", "language", "lang")),
            new Rule(Kind.VERSION, List.of("version", "ver", "revision")),
            new Rule(Kind.PATH, List.of("path", "file", "filename", "filepath", "directory", "folder")),
            new Rule(Kind.PORT, List.of("port")),
            new Rule(Kind.ID, List.of("id", "identifier"))
    );

    /**
     * 推断字段含义；无法识别返回 null。
     * <p>先按分词精确命中（{@code userName} → [user,name] → USERNAME），
     * 再按整名包含兜底（{@code userEmailList} 含 email）。</p>
     */
    public static Kind infer(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        List<String> tokens = tokenize(fieldName);
        for (String token : tokens) {
            for (Rule r : RULES) {
                if (r.keys().contains(token)) {
                    return r.kind();
                }
            }
        }
        String joined = String.join("", tokens);
        for (Rule r : RULES) {
            for (String key : r.keys()) {
                if (joined.contains(key)) {
                    return r.kind();
                }
            }
        }
        return null;
    }

    /** 分词：camelCase/PascalCase 拆词，非字母数字作分隔符，统一小写。 */
    private static List<String> tokenize(String s) {
        String spaced = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("[^a-zA-Z0-9]+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
        if (spaced.isEmpty()) {
            return List.of();
        }
        return List.of(spaced.split("\\s+"));
    }

    // ── 语义化取值 ──

    /** 按字段名生成语义化字符串；无法识别含义时返回随机字母数字串。 */
    public String generate(String fieldName) {
        Kind kind = infer(fieldName);
        if (kind == null) {
            return random.randomAlnum(random.intBetween(4, 12));
        }
        return value(kind);
    }

    /** 按语义类别生成一个值。 */
    public String value(Kind kind) {
        return switch (kind) {
            case FIRST_NAME -> pick(FIRST_NAMES);
            case LAST_NAME -> pick(LAST_NAMES);
            case FULL_NAME -> pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
            case USERNAME -> random.randomAlpha(random.intBetween(5, 8))
                    + random.intBetween(10, 99);
            case NICKNAME -> pick(WORDS) + "_" + pick(WORDS);
            case PASSWORD -> random.randomAscii(random.intBetween(10, 14));
            case EMAIL -> random.randomAlpha(random.intBetween(5, 8)) + "@"
                    + random.randomAlpha(random.intBetween(4, 6)) + ".com";
            case PHONE -> "1" + pick(new String[]{"3", "5", "7", "8", "9"})
                    + digits(9);
            case TEL -> "0" + random.intBetween(10, 89) + "-" + digits(8);
            case URL -> "https://" + random.randomAlpha(random.intBetween(4, 7)) + ".com/"
                    + random.randomAlpha(random.intBetween(3, 6));
            case HOSTNAME -> random.randomAlpha(random.intBetween(4, 6)) + "."
                    + random.randomAlpha(3) + ".com";
            case IP -> random.intBetween(1, 223) + "." + random.intBetween(0, 255)
                    + "." + random.intBetween(0, 255) + "." + random.intBetween(1, 254);
            case UUID -> uuid();
            case ID -> random.randomAlnum(random.intBetween(6, 10));
            case DATE -> date();
            case DATETIME -> date() + "T" + time();
            case TIME -> time();
            case ADDRESS -> random.intBetween(1, 999) + " " + pick(WORDS)
                    + " Street, " + pick(CITIES);
            case CITY -> pick(CITIES);
            case COUNTRY -> pick(COUNTRIES);
            case COMPANY -> pick(COMPANY_PREFIX) + " " + pick(COMPANY_SUFFIX);
            case TITLE -> capitalize(pick(WORDS)) + " " + pick(WORDS);
            case DESCRIPTION -> capitalize(pick(WORDS)) + " " + pick(WORDS)
                    + " " + pick(WORDS) + ".";
            case STATUS -> pick(STATUSES);
            case CATEGORY -> pick(CATEGORIES);
            case TAG -> pick(WORDS);
            case CURRENCY -> pick(CURRENCIES);
            case PRICE -> random.intBetween(1, 9999) + "."
                    + String.format(Locale.ROOT, "%02d", random.intBetween(0, 99));
            case AGE -> String.valueOf(random.intBetween(18, 70));
            case CODE -> random.randomAlnum(random.intBetween(6, 8)).toUpperCase(Locale.ROOT);
            case POSTAL -> digits(6);
            case GENDER -> pick(GENDERS);
            case COLOR -> pick(COLORS);
            case LOCALE -> pick(LOCALES);
            case VERSION -> random.intBetween(0, 9) + "." + random.intBetween(0, 20)
                    + "." + random.intBetween(0, 30);
            case PATH -> "/" + random.randomAlpha(random.intBetween(3, 6)) + "/"
                    + random.randomAlpha(random.intBetween(3, 6)) + "."
                    + pick(new String[]{"txt", "json", "png", "log"});
            case PORT -> String.valueOf(random.intBetween(1024, 65535));
        };
    }

    private String pick(String[] options) {
        return options[random.random().nextInt(options.length)];
    }

    private String digits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(random.intBetween(0, 9));
        }
        return sb.toString();
    }

    private String date() {
        int year = random.intBetween(1990, 2030);
        int month = random.intBetween(1, 12);
        int day = random.intBetween(1, 28);
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
    }

    private String time() {
        return String.format(Locale.ROOT, "%02d:%02d:%02dZ",
                random.intBetween(0, 23), random.intBetween(0, 59), random.intBetween(0, 59));
    }

    /** 由注入熵源构造 uuid 文本（不引入独立随机源，保证同种子可复现）。 */
    private String uuid() {
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

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
