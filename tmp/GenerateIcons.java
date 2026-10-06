import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

public class GenerateIcons {
    public static void main(String[] args) throws Exception {
        int[] sizes = {48, 72, 96, 144, 192};
        String[] dirs = {
            "app/src/main/res/mipmap-mdpi",
            "app/src/main/res/mipmap-hdpi",
            "app/src/main/res/mipmap-xhdpi",
            "app/src/main/res/mipmap-xxhdpi",
            "app/src/main/res/mipmap-xxxhdpi"
        };

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            File dir = new File(dirs[i]);
            dir.mkdirs();

            // 1. Square ic_launcher.png (White background, centered emblem with safe margins)
            BufferedImage squareImg = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = squareImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Fill white background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);

            // Draw emblem scaled to safe diameter (~62% of icon size)
            drawEmblem(g, size, false);
            g.dispose();
            ImageIO.write(squareImg, "png", new File(dir, "ic_launcher.png"));

            // 2. Round ic_launcher_round.png (Circular mask with transparent corners)
            BufferedImage roundImg = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gr = roundImg.createGraphics();
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Clip to circle
            Ellipse2D.Double circle = new Ellipse2D.Double(0.5, 0.5, size - 1, size - 1);
            gr.setClip(circle);
            gr.setColor(Color.WHITE);
            gr.fill(circle);

            drawEmblem(gr, size, false);
            gr.dispose();
            ImageIO.write(roundImg, "png", new File(dir, "ic_launcher_round.png"));

            System.out.println("Generated icons for size " + size + " in " + dirs[i]);
        }
    }

    private static void drawEmblem(Graphics2D g, int size, boolean round) {
        // Original SVG bounding box: W = 282.18, H = 263.63, center = (141.09, 131.815)
        // Target emblem size: 0.58 * size to guarantee zero corner clipping
        double targetW = size * 0.58;
        double scale = targetW / 282.18;
        double targetH = 263.63 * scale;

        double tx = (size - targetW) / 2.0;
        double ty = (size - targetH) / 2.0;

        AffineTransform baseTx = g.getTransform();
        g.translate(tx, ty);
        g.scale(scale, scale);

        // Path 1: #B65729
        Path2D.Double p1 = new Path2D.Double();
        double[][] pts1 = {
            {273.32, -23.78}, {342.39, -23.78}, {385.62, -23.78}, {454.69, -23.78},
            {496.11, -23.78}, {512.04, -23.78}, {385.62, -150.2}, {385.62, -92.85},
            {342.39, -92.85}, {342.39, -193.44}, {273.32, -262.5}, {273.32, -23.78}
        };
        for (int i = 0; i < pts1.length; i++) {
            double x = -pts1[i][0] + 555.5029;
            double y = -pts1[i][1] + 1.1246;
            if (i == 0) p1.moveTo(x, y); else p1.lineTo(x, y);
        }
        p1.closePath();
        g.setColor(new Color(0xB6, 0x57, 0x29));
        g.fill(p1);

        // Path 2: #F37A40
        Path2D.Double p2 = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        double x0 = -288.84 + 571.0248; double y0 = -56.4 + 81.3093;
        double x1 = -357.91 + 571.0248; double y1 = -(-12.66) + 81.3093;
        double x2 = -401.14 + 571.0248; double y2 = -(-12.66) + 81.3093;
        double x3 = -401.14 + 571.0248; double y3 = -(-70.02) + 81.3093;
        double x4 = -527.56 + 571.0248; double y4 = -56.4 + 81.3093;
        double cx1 = -506.32 + 571.0248; double cy1 = -56.4 + 81.3093;
        double cx2 = -326.27 + 571.0248; double cy2 = -56.4 + 81.3093;

        p2.moveTo(x0, y0);
        p2.lineTo(x1, y1);
        p2.lineTo(x2, y2);
        p2.lineTo(x3, y3);
        p2.lineTo(x4, y4);
        p2.curveTo(cx1, cy1, cx2, cy2, x0, y0);
        p2.closePath();
        g.setColor(new Color(0xF3, 0x7A, 0x40));
        g.fill(p2);

        // Path 3: #03045E
        Path2D.Double p3 = new Path2D.Double();
        double[][] pts3 = {
            {0, 238.72}, {69.07, 238.72}, {112.3, 238.72}, {181.37, 238.72},
            {222.79, 238.72}, {238.72, 238.72}, {112.3, 112.3}, {112.3, 169.65},
            {69.07, 169.65}, {69.07, 69.07}, {0, 0}, {0, 238.72}
        };
        for (int i = 0; i < pts3.length; i++) {
            if (i == 0) p3.moveTo(pts3[i][0], pts3[i][1]); else p3.lineTo(pts3[i][0], pts3[i][1]);
        }
        p3.closePath();
        g.setColor(new Color(0x03, 0x04, 0x5E));
        g.fill(p3);

        // Path 4: #003958
        Path2D.Double p4 = new Path2D.Double();
        double[][] pts4 = {
            {69.07, 69.07}, {0, 0}, {0, 238.72}, {69.07, 238.72}, {112.3, 238.72},
            {181.37, 238.72}, {222.79, 238.72}, {238.72, 238.72}, {112.3, 112.3},
            {112.3, 169.65}, {69.07, 169.65}, {69.07, 69.07}
        };
        for (int i = 0; i < pts4.length; i++) {
            if (i == 0) p4.moveTo(pts4[i][0], pts4[i][1]); else p4.lineTo(pts4[i][0], pts4[i][1]);
        }
        p4.closePath();
        g.setColor(new Color(0x00, 0x39, 0x58));
        g.fill(p4);

        // Path 5: #055079
        Path2D.Double p5 = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        double[][] pts5 = {
            {0, 238.72}, {69.07, 169.65}, {112.3, 169.65}, {112.3, 112.3},
            {238.72, 238.72}, {222.79, 238.72}, {181.37, 238.72}, {112.3, 238.72}, {0, 238.72}
        };
        for (int i = 0; i < pts5.length; i++) {
            if (i == 0) p5.moveTo(pts5[i][0], pts5[i][1]); else p5.lineTo(pts5[i][0], pts5[i][1]);
        }
        p5.closePath();
        g.setColor(new Color(0x05, 0x50, 0x79));
        g.fill(p5);

        g.setTransform(baseTx);
    }
}
