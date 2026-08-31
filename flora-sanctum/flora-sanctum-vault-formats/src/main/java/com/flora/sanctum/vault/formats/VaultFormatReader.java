package com.flora.sanctum.vault.formats;

import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.bitwarden.BitwardenReader;
import com.flora.sanctum.vault.formats.kp1.KeePass1Reader;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 第三方保险库读取入口：自动识别格式并分派到对应读取器。
 * <p>识别规则（均只基于非敏感的文件头/结构特征）：
 * <ul>
 *   <li>KeePass 1.x：魔数 {@code 0x9AA2D903 0xB54BFB65}（第二个签名与 KDBX 的 {@code 0xB54BFB67} 不同）；</li>
 *   <li>1Password OPVault：ZIP 中含 {@code profile.js}（通常在 {@code default/} 下）；</li>
 *   <li>1Password 1PUX：ZIP 中含 {@code export.data}；</li>
 *   <li>Bitwarden：JSON 文本，含 {@code items}/{@code $type:"Bitwarden"} 等特征键。</li>
 * </ul>
 * <p>KDBX（主版本 2/3/4）不在此处处理，交由 {@code flora-sanctum-kdbx} 的
 * {@code KdbxReader} 负责，识别为 null 时上层应回退到 KDBX 读取。</p>
 */
public final class VaultFormatReader {

    private static final int SIG1 = 0x9AA2D903;
    /** KeePass1 的第二签名；KDBX（KeePass2）为 {@code 0xB54BFB67}，据此区分同一族格式。 */
    private static final int SIG2_KEEPASS1 = 0xB54BFB65;
    private static final int SIG2_KDBX = 0xB54BFB67;

    private VaultFormatReader() {
    }

    /** 识别格式；无法识别（含 KDBX 2/3/4）时返回 null。 */
    public static VaultFormat detect(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        int sig1 = readLe32(data, 0);
        int sig2 = readLe32(data, 4);
        if (sig1 == SIG1) {
            if (sig2 == SIG2_KEEPASS1) {
                return VaultFormat.KEEPASS1;
            }
            if (sig2 == SIG2_KDBX) {
                return null; // KDBX 交给 kdbx 模块
            }
        }
        if (isZip(data)) {
            Set<String> names = zipEntryNames(data);
            boolean hasProfile = names.stream().anyMatch(n -> n.endsWith("profile.js"));
            boolean hasExport = names.stream().anyMatch(n -> n.endsWith("export.data"));
            if (hasExport) {
                return VaultFormat.ONEPUX;
            }
            if (hasProfile) {
                return VaultFormat.OPVAULT;
            }
            return null;
        }
        if (looksLikeJson(data)) {
            return VaultFormat.BITWARDEN;
        }
        return null;
    }

    /** 识别并读取；无法识别时抛出 {@link VaultReadException.Stage#UNSUPPORTED}。 */
    public static KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException {
        VaultFormat f = detect(data);
        if (f == null) {
            throw VaultReadException.of(VaultReadException.Stage.UNSUPPORTED, null,
                    "无法识别的保险库格式（非 KeePass1 / Bitwarden / OPVault / 1PUX）");
        }
        // 各格式读取器按实现进度接入；未实现的格式给出明确的不支持提示（仍为可编译、可工作模块）。
        return switch (f) {
            case BITWARDEN -> new BitwardenReader().read(data, password, keyFile);
            case KEEPASS1 -> new KeePass1Reader().read(data, password, keyFile);
            default -> throw VaultReadException.of(VaultReadException.Stage.UNSUPPORTED, f,
                    "该保险库格式的只读读取器尚未实现");
        };
    }

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    private static boolean looksLikeJson(byte[] data) {
        int i = 0;
        while (i < data.length && (data[i] == ' ' || data[i] == '\t' || data[i] == '\n' || data[i] == '\r')) {
            i++;
        }
        return i < data.length && (data[i] == '{' || data[i] == '[');
    }

    private static Set<String> zipEntryNames(byte[] data) {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
        } catch (Exception ignored) {
            // 不是合法 ZIP
        }
        return names;
    }

    private static int readLe32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8
                | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }
}
