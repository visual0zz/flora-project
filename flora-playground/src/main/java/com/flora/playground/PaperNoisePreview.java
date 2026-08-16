package com.flora.playground;

import com.flora.root.graphics.noise.PaperNoise;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

/**
 * 纸纤维噪声分量预览（Swing GUI）：把低/中/高频分量与整体分别渲染并同屏展示，
 * 便于目视调参（基色 #F8F4E9，幅度 ±25，与 SanctumGui.PaperPanel 一致）。
 *
 * <p>用法：{@code java -m com.flora.playground/com.flora.playground.PaperNoisePreview}
 */
public final class PaperNoisePreview {

    private static final int BASE_R = 0xF8;
    private static final int BASE_G = 0xF4;
    private static final int BASE_B = 0xE9;
    private static final int AMP = 25;

    private PaperNoisePreview() {
    }

    public static void main(String[] args) {
        int size = 512;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("PaperNoise 分量预览");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new GridLayout(2, 2, 4, 4));
            add(frame, "低频 · 厚薄", render(size, PaperNoise::low));
            add(frame, "中频 · 纤维", render(size, PaperNoise::mid));
            add(frame, "高频 · 颗粒", render(size, PaperNoise::high));
            add(frame, "整体", render(size, PaperNoise::paper));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static void add(JFrame frame, String title, BufferedImage img) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
        frame.add(panel);
    }

    private interface Fn {
        float apply(int x, int y);
    }

    private static BufferedImage render(int size, Fn fn) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int d = (int) Math.round(fn.apply(x, y) * AMP);
                int r = PaperNoise.clamp(BASE_R + d);
                int g = PaperNoise.clamp(BASE_G + d);
                int b = PaperNoise.clamp(BASE_B + d);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }
}
