package com.flora.root.runtime.log.impl;

import com.flora.root.runtime.log.Level;
import com.flora.root.runtime.log.spi.Appender;
import com.flora.root.runtime.log.spi.Layout;
import com.flora.root.runtime.log.spi.LogEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 滚动文件日志附加器，支持基于时间和基于大小的日志滚动策略融合（复合触发）。
 * <p>
 * 触发条件由配置自然组合，而非在二者中二选一：
 * <ul>
 *   <li>时间触发生效 ⇔ {@code filePattern} 含 {@code %d}（需要按日期分区）；</li>
 *   <li>尺寸触发生效 ⇔ {@code maxSize > 0}（默认 10 MiB，故默认即带尺寸上限）。</li>
 * </ul>
 * 活动文件始终是 {@code basePath}（无日期），跨天或达尺寸上限时归档，归档名按 {@code filePattern} 渲染。
 * 带日期模式时，{@code %i} 序号在每个时间周期（跨天）归零，文件名中的日期即该归档内容所属的周期；
 * 保留策略 {@code maxHistory} 为跨所有日期的全局上限，超出部分按时间最旧者裁剪。
 */
public class RollingFileAppender implements Appender {

    private String name;
    private Level threshold = Level.TRACE;
    private Layout layout = new Layout("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");


    private Path basePath;

    private Path currentPath;

    private String filePattern;

    private String datePattern = "yyyy-MM-dd";

    /** 当前打开（活动）文件所属的时间周期；首次写入时惰性初始化为当天。 */
    private String openPeriod;

    private long maxSize = 10 * 1024 * 1024;

    private int maxHistory = 7;

    private long currentSize;

    private FileAppender delegate;

    public RollingFileAppender() {
    }

    public RollingFileAppender(String file) {
        this.basePath = Paths.get(file);
    }

    /**
     * 设置基础文件路径（文件系统无关）。
     *
     * @param file 基础文件路径
     */
    public RollingFileAppender(Path file) {
        this.basePath = file;
    }

    /**
     * 设置基础文件路径（文件系统无关，流式 API）。
     *
     * @param file 基础文件路径
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender file(Path file) {
        this.basePath = file;
        return this;
    }


    /**
     * 设置基础文件路径（流式 API）。
     *
     * @param file 基础文件路径
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender file(String file) {
        this.basePath = Paths.get(file);
        return this;
    }

    /**
     * 设置日志输出格式（流式 API）。
     *
     * @param pattern 布局模式字符串
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender pattern(String pattern) {
        this.layout = new Layout(pattern);
        return this;
    }

    /**
     * 设置归档文件命名模式（log4j 风格，流式 API）。
     * <p>
     * 模式中的 {@code %d{日期格式}}（或 {@code %d}）替换为归档所属周期，
     * {@code %i} 替换为周期内序号（1 为最新归档）。例如 {@code app-%d{yyyy-MM-dd}.%i.log} 或 {@code app-%i.log}。
     * 带 {@code %d} 即启用日期分区（时间触发），带 {@code %i} 即按序号归档；二者可同时出现（日期+尺寸复合）。
     * 未设置时回退到默认命名：含日期分区为 {@code base.log.日期}，否则为 {@code base.log.N}。
     * 注意：本实现仅使用模式决定文件名，不执行 {@code .gz} 等压缩。
     *
     * @param filePattern 归档文件命名模式
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender filePattern(String filePattern) {
        this.filePattern = filePattern;
        return this;
    }

    /**
     * 设置基于时间滚动/命名时的日期格式（流式 API）。
     *
     * @param datePattern 日期格式，如 "yyyy-MM-dd"
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender datePattern(String datePattern) {
        this.datePattern = datePattern;
        return this;
    }

    /**
     * 设置基于大小滚动时的最大文件字节数（流式 API）。
     *
     * @param maxSizeBytes 最大字节数；0 表示禁用尺寸触发（仅按时间滚动）。
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender maxSize(long maxSizeBytes) {
        this.maxSize = maxSizeBytes;
        return this;
    }

    /**
     * 设置最大历史归档文件数（流式 API，跨所有日期的全局上限）。
     *
     * @param maxHistory 最大历史文件数；≤0 表示不限制保留。
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender maxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
        return this;
    }

    @Override
    public Level getThreshold() {
        return threshold;
    }

    @Override
    public void setThreshold(Level threshold) {
        this.threshold = threshold;
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    @Override
    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    @Override
    public Path getTargetPath() {
        return basePath;
    }

    /**
     * 追加日志事件，在写入前检查是否需要进行文件滚动。
     *
     * @param event 日志事件
     */
    @Override
    public synchronized void append(LogEvent event) {
        if (!threshold.isEnabled(event.getLevel())) {
            return;
        }
        if (openPeriod == null) {
            openPeriod = today();
        }

        if (checkRoll()) {
            roll();
        }

        if (delegate == null) {
            delegate = createDelegate();
        }

        delegate.append(event);
        // 自行累计已写入字节数：缓冲写入（含内存文件系统）下 Files.size 不能可靠反映
        // 当前文件大小，故由附加器跟踪，而非依赖文件系统查询。
        currentSize += layout.format(event).getBytes(StandardCharsets.UTF_8).length;
    }


    /**
     * 关闭委托的文件附加器，释放资源。
     */
    @Override
    public synchronized void close() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
    }


    /**
     * 检查是否需要执行文件滚动：复合触发 = 跨天（带日期分区时）或达到尺寸上限。
     *
     * @return 如果需要滚动则返回 true
     */
    private boolean checkRoll() {
        if (basePath == null) {
            return false;
        }
        String t = today();
        boolean dayChanged = hasDatePattern() && openPeriod != null && !t.equals(openPeriod);
        boolean sizeExceeded = maxSize > 0 && delegate != null && currentSize >= maxSize;
        return dayChanged || sizeExceeded;
    }


    /**
     * 执行文件滚动操作：将当前文件归档为按模式命名的归档文件，并裁剪超出保留上限的旧归档。
     * <p>
     * 带日期分区时归档到当前打开周期（{@code openPeriod}）名下的 {@code .1}，周期内旧归档整体后移；
     * 滚动后 {@code openPeriod} 更新为当天，使下一周期的 {@code %i} 序号归零。
     */
    private void roll() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }

        String archiveDate = (hasDatePattern() && openPeriod != null) ? openPeriod : today();

        // 序号式归档（含 filePattern 的任意情形，以及无 filePattern 的默认尺寸命名 base.log.N）：
        // 旧归档整体后移（.1 → .2, .2 → .3 ...），当前文件归档为 .1。
        for (int i = maxHistory - 1; i >= 1; i--) {
            Path old = archivePath(archiveDate, i);
            Path newer = archivePath(archiveDate, i + 1);
            if (Files.exists(old)) {
                if (i == maxHistory - 1) {
                    tryDelete(newer);
                }
                tryMove(old, newer);
            }
        }
        tryMove(currentPath, archivePath(archiveDate, 1));

        if (hasDatePattern()) {
            openPeriod = today();
        }

        pruneArchives();

        currentPath = resolveCurrentPath();
        delegate = createDelegate();
        currentSize = 0;
    }

    /**
     * 全局保留裁剪：扫描归档目录，删除超出 {@code maxHistory} 的最旧归档（跨所有日期）。
     * <p>
     * 仅删除与归档命名模式匹配的文件，不会误删活动文件或其它无关文件。
     */
    private void pruneArchives() {
        if (maxHistory <= 0) {
            return;
        }
        Path dir = archiveDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        Pattern p = archiveNamePattern();
        List<Path> matched = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds) {
                if (p.matcher(f.getFileName().toString()).matches()) {
                    matched.add(f);
                }
            }
        } catch (IOException ignore) {
            return;
        }
        // 最新在前：日期降序、序号降序
        matched.sort((a, b) -> compareArchive(a.getFileName().toString(), b.getFileName().toString(), p));
        for (int i = maxHistory; i < matched.size(); i++) {
            tryDelete(matched.get(i));
        }
    }

    /**
     * 按归档文件名推断其（日期, 序号）用于排序；无对应部分记为 null/0。
     */
    private int compareArchive(String na, String nb, Pattern p) {
        ArchiveKey ka = keyOf(na, p);
        ArchiveKey kb = keyOf(nb, p);
        int c;
        if (ka.date != null && kb.date != null) {
            c = kb.date.compareTo(ka.date); // 日期降序（新在前）
        } else if (ka.date == null && kb.date == null) {
            c = 0;
        } else {
            c = ka.date != null ? -1 : 1; // 有日期者视为更新
        }
        if (c != 0) {
            return c;
        }
        int ia = ka.index != null ? ka.index : 0;
        int ib = kb.index != null ? kb.index : 0;
        return Integer.compare(ib, ia); // 序号降序（新在前）
    }

    private ArchiveKey keyOf(String name, Pattern p) {
        Matcher m = p.matcher(name);
        if (m.matches()) {
            String date = m.group("date");
            String idx = m.group("idx");
            Integer index = idx != null ? Integer.parseInt(idx) : null;
            return new ArchiveKey(date, index);
        }
        return new ArchiveKey(null, null);
    }

    /**
     * 由 filePattern 末段（或默认命名）构造归档文件名正则：
     * 字面量转义，{@code %d{...}}/{@code %d} → 日期捕获组，{@code %i} → 序号捕获组。
     */
    private Pattern archiveNamePattern() {
        String template;
        if (filePattern != null) {
            template = Path.of(filePattern).getFileName().toString();
        } else {
            String base = basePath.getFileName().toString();
            template = hasDatePattern() ? base + ".%d" : base + ".%i";
        }
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < template.length()) {
            if (template.startsWith("%d{", i)) {
                int end = template.indexOf('}', i);
                sb.append("(?<date>[0-9-]+)");
                i = end + 1;
            } else if (template.startsWith("%d", i)) {
                sb.append("(?<date>[0-9-]+)");
                i += 2;
            } else if (template.startsWith("%i", i)) {
                sb.append("(?<idx>[0-9]+)");
                i += 2;
            } else {
                sb.append(Pattern.quote(String.valueOf(template.charAt(i))));
                i++;
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    /**
     * 归档文件所在目录：取解析后的归档路径父目录，回退到 basePath 的父目录。
     */
    private Path archiveDir() {
        if (filePattern != null) {
            Path resolved = resolveArchivePath(today(), 1).getParent();
            if (resolved != null) {
                return resolved;
            }
        }
        return basePath.getParent() != null ? basePath.getParent() : basePath;
    }

    /**
     * 解析与 {@code basePath} 同目录、以 {@code suffix} 为扩展名的归档文件路径，
     * 使用 basePath 所在文件系统的路径构造，避免硬编码默认文件系统。
     *
     * @param suffix 追加在基础文件名之后的后缀（含点号）
     * @return 归档文件路径
     */
    private Path siblingWithSuffix(String suffix) {
        return basePath.resolveSibling(basePath.getFileName() + suffix);
    }

    /**
     * 根据 filePattern 解析归档文件路径，替换 {@code %d{...}}/{@code %d} 为日期、{@code %i} 为序号。
     * 无日期模式时 date 参数被忽略，仅 {@code %i} 生效。
     *
     * @param date  归档所属周期（替换 %d）
     * @param index 周期内序号（替换 %i，1 为最新归档）
     * @return 解析后的归档文件路径
     */
    private Path resolveArchivePath(String date, int index) {
        String name = filePattern
                .replace("%d{" + datePattern + "}", date)
                .replace("%d", date)
                .replace("%i", Integer.toString(index));
        // 使用 basePath 所在文件系统构造归档路径，避免硬编码默认文件系统，
        // 使 filePattern 在任意文件系统（含内存虚拟文件系统）下都能正确解析。
        return basePath.getFileSystem().getPath(name);
    }

    /**
     * 解析当前（活动）输出文件路径：恒为 basePath（无日期），日期分区体现在归档名中。
     *
     * @return 当前文件路径
     */
    private Path resolveCurrentPath() {
        return basePath;
    }

    private Path archivePath(String date, int index) {
        if (filePattern != null) {
            return resolveArchivePath(date, index);
        }
        return siblingWithSuffix("." + index);
    }

    private void tryMove(Path src, Path dst) {
        try {
            if (src != null && Files.exists(src)) {
                Files.move(src, dst);
            }
        } catch (IOException e) {
            System.err.println("Log roll error: " + e.getMessage());
        }
    }

    private void tryDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            System.err.println("Log prune error: " + e.getMessage());
        }
    }

    private boolean hasDatePattern() {
        return filePattern != null && filePattern.contains("%d");
    }

    private String today() {
        return new SimpleDateFormat(datePattern).format(new Date());
    }

    /**
     * 创建一个委托的 FileAppender 实例，用于实际写入日志。
     *
     * @return FileAppender 实例
     */
    private FileAppender createDelegate() {
        if (currentPath == null) {
            currentPath = resolveCurrentPath();
        }
        FileAppender fa = new FileAppender();
        fa.file(currentPath);
        fa.setLayout(layout);
        fa.setThreshold(Level.TRACE);
        return fa;
    }

    /** 归档文件名解析结果：（日期, 序号）。 */
    private static final class ArchiveKey {
        private final String date;
        private final Integer index;

        private ArchiveKey(String date, Integer index) {
            this.date = date;
            this.index = index;
        }
    }
}
