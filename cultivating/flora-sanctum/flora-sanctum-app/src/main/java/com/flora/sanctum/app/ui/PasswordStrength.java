package com.flora.sanctum.app.ui;

import com.nulabinc.zxcvbn.Zxcvbn;
import com.nulabinc.zxcvbn.Strength;

import java.util.List;

/**
 * 主密码强度评估。算法源自 Dropbox zxcvbn（KeePassXC 内置同款 C 移植），
 * 此处直接复用其 Java 移植 {@code com.nulab-inc:zxcvbn}，避免自行搬运 4.1MB 词典。
 * <p>质量分级沿用 KeePassXC 的熵阈值（{@code PasswordHealth::quality}）：
 * 熵（bits）≤0 极差、<40 弱、<75 中、<100 良、≥100 优。
 * zxcvbn4j 不直接暴露熵，以 {@code guessesLog10 * log2(10)} 还原为等价 bits 熵。</p>
 */
final class PasswordStrength {

    /** 质量等级，与 KeePassXC 的 {@code PasswordHealth::Quality} 对齐。 */
    enum Quality {
        /** 熵 ≤ 0：空密码或无法评估。 */
        BAD,
        /** 熵 < 40。 */
        POOR,
        /** 熵 < 75。 */
        WEAK,
        /** 熵 < 100。 */
        GOOD,
        /** 熵 ≥ 100。 */
        EXCELLENT
    }

    private static final double LOG2_10 = Math.log(10.0) / Math.log(2.0);

    private static final Zxcvbn ZXCVBN = new Zxcvbn();

    private final double entropy;
    private final Quality quality;
    private final String warning;
    private final List<String> suggestions;

    private PasswordStrength(double entropy, Quality quality, String warning, List<String> suggestions) {
        this.entropy = entropy;
        this.quality = quality;
        this.warning = warning;
        this.suggestions = suggestions;
    }

    /**
     * 评估主密码强度。
     *
     * @param password 明文主密码
     * @param userWords 用户相关弱词（如仓库名、用户名），会作为额外词典参与评估；可为 null
     * @return 强度评估结果（含熵、等级、提示）
     */
    static PasswordStrength evaluate(String password, List<String> userWords) {
        Strength s = (userWords == null || userWords.isEmpty())
                ? ZXCVBN.measure(password)
                : ZXCVBN.measure(password, userWords);
        // guessesLog10 即 log10(猜测次数)，乘 log2(10) 还原为等价熵 bits，与 KeePassXC 熵同尺度。
        double entropy = s.getGuessesLog10() * LOG2_10;
        String warning = s.getFeedback() == null ? "" : s.getFeedback().getWarning();
        List<String> suggestions = s.getFeedback() == null ? List.of() : s.getFeedback().getSuggestions();
        return new PasswordStrength(entropy, toQuality(entropy), warning, suggestions);
    }

    private static Quality toQuality(double entropy) {
        if (entropy <= 0) {
            return Quality.BAD;
        } else if (entropy < 40) {
            return Quality.POOR;
        } else if (entropy < 75) {
            return Quality.WEAK;
        } else if (entropy < 100) {
            return Quality.GOOD;
        }
        return Quality.EXCELLENT;
    }

    /** 等价熵（bits），与 KeePassXC 同一刻度。 */
    double entropy() {
        return entropy;
    }

    Quality quality() {
        return quality;
    }

    /** 一句话警告（如"这是最常见的密码之一"），可能为空。 */
    String warning() {
        return warning == null ? "" : warning;
    }

    /** 改进建议列表。 */
    List<String> suggestions() {
        return suggestions;
    }
}
