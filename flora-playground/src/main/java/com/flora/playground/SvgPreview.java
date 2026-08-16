package com.flora.playground;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 渲染单个 SVG 图标为 PNG，用于目视检查图标绘制效果。
 *
 * <p>用法：{@code java -m com.flora.playground/com.flora.playground.SvgPreview <svg路径> <尺寸> [输出png]}
 */
public final class SvgPreview {

    private SvgPreview() {
    }

    public static void main(String[] args) throws Exception {
        Path svg = Path.of(args.length > 0 ? args[0]
                : "flora-sanctum/flora-sanctum-app/src/main/resources/icons/sync.svg");
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 192;
        Path out = args.length > 2 ? Path.of(args[2]) : Path.of("target", "svg-preview.png");
        Files.createDirectories(out.getParent());

        try (InputStream in = Files.newInputStream(svg)) {
            LoaderContext ctx = LoaderContext.createDefault();
            SVGDocument doc = new SVGLoader().load(in, svg.toUri(), ctx);
            if (doc == null) {
                System.err.println("SVG parse failed: " + svg);
                System.exit(1);
            }
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(0xF8, 0xF4, 0xE9));
            g.fillRect(0, 0, size, size);
            g.scale((double) size / doc.size().width, (double) size / doc.size().height);
            doc.render(null, g, new ViewBox(doc.size()));
            g.dispose();
            ImageIO.write(img, "png", out.toFile());
            System.out.println("written " + out.toAbsolutePath());
        }
    }
}
