import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成应用图标: 深色圆角方块 + 白色几何 J + 青色圆点(欧美极简风)。
 * 输出 icon.ico(多尺寸, 供 jpackage 用) 和 resources/icon.png(256, 供窗口图标用)。
 * 用法: java MakeIcon <输出目录>
 */
public class MakeIcon {

    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 深蓝黑圆角底
        g.setColor(new Color(0x0F, 0x17, 0x2A));
        float r = size * 0.235f;
        g.fill(new RoundRectangle2D.Float(size * 0.015f, size * 0.015f,
                size * 0.97f, size * 0.97f, r, r));

        // 白色几何 J: 竖线 + 圆弧底钩
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(size * 0.155f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float j = new Path2D.Float();
        j.moveTo(size * 0.615f, size * 0.20f);
        j.lineTo(size * 0.615f, size * 0.53f);
        j.quadTo(size * 0.615f, size * 0.79f, size * 0.40f, size * 0.79f);
        j.quadTo(size * 0.205f, size * 0.79f, size * 0.205f, size * 0.60f);
        g.draw(j);

        // 青色圆点点缀在 J 顶端右侧(避开竖笔与圆角)
        g.setColor(new Color(0x22, 0xD3, 0xEE));
        float dot = size * 0.105f;
        g.fillOval(Math.round(size * 0.73f), Math.round(size * 0.17f),
                Math.round(dot), Math.round(dot));

        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : ".";
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};

        // 256 大图单独存 PNG(窗口图标用)
        ImageIO.write(draw(256), "png", new java.io.File(dir, "icon.png"));

        // 多尺寸 ICO(PNG-in-ICO, Vista+ 支持)
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) pngs.add(png(draw(s)));
        try (OutputStream out = new FileOutputStream(new java.io.File(dir, "icon.ico"))) {
            out.write(shortLE(0)); out.write(shortLE(1)); out.write(shortLE(sizes.length));
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                out.write(s >= 256 ? 0 : s);            // 宽
                out.write(s >= 256 ? 0 : s);            // 高
                out.write(0);                            // 调色板
                out.write(0);                            // 保留
                out.write(shortLE(1));                   // 平面
                out.write(shortLE(32));                  // 位深
                out.write(intLE(pngs.get(i).length));    // 数据长度
                out.write(intLE(offset));                // 数据偏移
                offset += pngs.get(i).length;
            }
            for (byte[] p : pngs) out.write(p);
        }
        System.out.println("icon.ico + icon.png 已生成于 " + dir);
    }

    static byte[] shortLE(int v) { return new byte[]{(byte) v, (byte) (v >> 8)}; }
    static byte[] intLE(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }
}
