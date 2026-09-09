package com.endstep.ms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;

/**
 * Compoe a "carta customizada": imagem real da carta como base, a arte do
 * usuario encaixada na janela de arte, e (opcional) faixas cobrindo o nome
 * e o quadro de regras com texto novo. Retangulos calibrados para o frame
 * moderno normal — o front usa as mesmas fracoes no preview.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Component
public class ImageCompositor {
    private static final Logger log = LoggerFactory.getLogger(ImageCompositor.class);

    public static final double[] ART_RECT = {0.066, 0.108, 0.868, 0.418};
    public static final double[] NAME_RECT = {0.060, 0.052, 0.880, 0.055};
    public static final double[] TEXT_RECT = {0.062, 0.600, 0.876, 0.300};

    private final Font baseFont;

    public ImageCompositor() {
        Font f;
        try (InputStream in = ImageCompositor.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            f = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            log.warn("Sem DejaVuSans, usando Serif do sistema: {}", e.toString());
            f = new Font("Serif", Font.PLAIN, 12);
        }
        this.baseFont = f;
    }

    public record Params(byte[] baseImage, byte[] userArt,
                         double zoom, double offsetX, double offsetY,
                         String displayName, String overlayText,
                         boolean nameBar, boolean textBar) {
    }

    public record Result(byte[] fullPng, byte[] thumbPng) {
    }

    public Result compose(Params p) {
        try {
            BufferedImage base = ImageIO.read(new ByteArrayInputStream(p.baseImage()));
            if (base == null) {
                throw new IllegalArgumentException("imagem base ilegivel");
            }
            int w = base.getWidth();
            int h = base.getHeight();

            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.drawImage(base, 0, 0, null);

            if (p.userArt() != null && p.userArt().length > 0) {
                BufferedImage art = ImageIO.read(new ByteArrayInputStream(p.userArt()));
                if (art != null) {
                    Rectangle2D.Double r = rect(ART_RECT, w, h);

                    double cover = Math.max(r.width / art.getWidth(), r.height / art.getHeight());
                    double scale = cover * clamp(p.zoom(), 1.0, 6);
                    double dw = art.getWidth() * scale;
                    double dh = art.getHeight() * scale;

                    double dx = r.x + (r.width - dw) / 2 + clamp(p.offsetX(), -1, 1) * r.width;
                    double dy = r.y + (r.height - dh) / 2 + clamp(p.offsetY(), -1, 1) * r.height;

                    dx = clamp(dx, r.x + r.width - dw, r.x);
                    dy = clamp(dy, r.y + r.height - dh, r.y);
                    java.awt.Shape oldClip = g.getClip();
                    g.setClip(r);
                    g.drawImage(art, (int) Math.round(dx), (int) Math.round(dy),
                            (int) Math.round(dw), (int) Math.round(dh), null);
                    g.setClip(oldClip);
                }
            }

            if (p.nameBar() && p.displayName() != null && !p.displayName().isBlank()) {
                Rectangle2D.Double r = rect(NAME_RECT, w, h);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f));
                g.setColor(new Color(232, 224, 208));
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, r.height * 0.5, r.height * 0.5));
                g.setComposite(AlphaComposite.SrcOver);
                drawCentered(g, p.displayName().trim(), r, baseFont.deriveFont(Font.BOLD, (float) (r.height * 0.62)),
                        new Color(20, 18, 16));
            }

            if (p.textBar() && p.overlayText() != null && !p.overlayText().isBlank()) {
                Rectangle2D.Double r = rect(TEXT_RECT, w, h);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f));
                g.setColor(new Color(240, 236, 228));
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
                g.setComposite(AlphaComposite.SrcOver);
                drawWrapped(g, p.overlayText().trim(), r, baseFont.deriveFont(Font.PLAIN, (float) (h * 0.026)),
                        new Color(20, 18, 16));
            }

            g.dispose();

            byte[] full = toPng(out);
            byte[] thumb = toPng(scaleToWidth(out, 320));
            return new Result(full, thumb);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao compor a carta: " + e.getMessage(), e);
        }
    }

    private static Rectangle2D.Double rect(double[] frac, int w, int h) {
        return new Rectangle2D.Double(frac[0] * w, frac[1] * h, frac[2] * w, frac[3] * h);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void drawCentered(Graphics2D g, String text, Rectangle2D r, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout tl = new TextLayout(text, font, frc);
        Rectangle2D b = tl.getBounds();
        float x = (float) (r.getX() + (r.getWidth() - b.getWidth()) / 2 - b.getX());
        float y = (float) (r.getY() + (r.getHeight() - b.getHeight()) / 2 - b.getY());
        tl.draw(g, x, y);
    }

    private static void drawWrapped(Graphics2D g, String text, Rectangle2D r, Font font, Color color) {
        g.setColor(color);
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.FONT, font);
        AttributedString as = new AttributedString(text, attrs);
        FontRenderContext frc = g.getFontRenderContext();
        LineBreakMeasurer m = new LineBreakMeasurer(as.getIterator(), frc);
        float wrap = (float) (r.getWidth() - 10);
        float y = (float) r.getY() + 4;
        while (m.getPosition() < text.length() && y < r.getY() + r.getHeight()) {
            TextLayout layout = m.nextLayout(wrap);
            y += layout.getAscent();
            layout.draw(g, (float) r.getX() + 5, y);
            y += layout.getDescent() + layout.getLeading();
        }
    }

    private static BufferedImage scaleToWidth(BufferedImage src, int targetW) {
        if (src.getWidth() <= targetW) {
            return src;
        }
        int targetH = Math.round(src.getHeight() * (targetW / (float) src.getWidth()));
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dst;
    }

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(img, "png", os);
        return os.toByteArray();
    }
}
