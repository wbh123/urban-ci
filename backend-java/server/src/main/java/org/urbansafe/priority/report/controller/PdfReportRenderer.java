package org.urbansafe.priority.report.controller;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** 不依赖浏览器和在线资源的第一版 PDF 渲染器。 */
public final class PdfReportRenderer {

    private static final int IMAGE_WIDTH = 1240;
    private static final int IMAGE_HEIGHT = 1754;
    private static final int MARGIN = 90;
    private static final int LINE_HEIGHT = 40;

    byte[] render(String reportCode, Map<String, Object> snapshot, String disclaimer) {
        List<String> lines = new ArrayList<>();
        lines.add("城安智序 · 楼栋风险筛查辅助报告");
        lines.add("报告编号：" + reportCode);
        lines.add("报告模板：" + ReportDashboardService.TEMPLATE_VERSION);
        lines.add("");
        flatten("", snapshot, lines, 0);
        lines.add("");
        lines.add("免责声明：" + disclaimer);
        return pdf(drawPages(lines));
    }

    private List<byte[]> drawPages(List<String> sourceLines) {
        List<String> lines = new ArrayList<>();
        for (String line : sourceLines) {
            lines.addAll(wrap(line, 46));
        }
        int linesPerPage = (IMAGE_HEIGHT - MARGIN * 2) / LINE_HEIGHT;
        List<byte[]> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += linesPerPage) {
            int end = Math.min(lines.size(), start + linesPerPage);
            BufferedImage image = new BufferedImage(
                    IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(31, 41, 55));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            int y = MARGIN;
            for (int i = start; i < end; i++) {
                String line = lines.get(i);
                if (i == 0) {
                    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
                    graphics.setColor(new Color(21, 84, 73));
                } else {
                    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
                    graphics.setColor(new Color(31, 41, 55));
                }
                graphics.drawString(line, MARGIN, y);
                y += LINE_HEIGHT;
            }
            graphics.dispose();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "jpg", out);
                pages.add(out.toByteArray());
            } catch (IOException ex) {
                throw new IllegalStateException("报告页面渲染失败", ex);
            }
        }
        return pages;
    }

    private List<String> wrap(String line, int maxChars) {
        if (line == null || line.isEmpty()) return List.of("");
        List<String> result = new ArrayList<>();
        for (int start = 0; start < line.length(); start += maxChars) {
            result.add(line.substring(start, Math.min(line.length(), start + maxChars)));
        }
        return result;
    }

    private void flatten(
            String prefix, Object value, List<String> lines, int depth) {
        if (depth > 5) return;
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> flatten(
                    prefix + label(key) + "：", child, lines, depth + 1));
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                lines.add(prefix + "无");
            } else {
                int index = 1;
                for (Object child : list) {
                    flatten(prefix + index++ + ". ", child, lines, depth + 1);
                }
            }
        } else {
            lines.add(prefix + readable(value));
        }
    }

    private String label(Object key) {
        String value = String.valueOf(key);
        return switch (value) {
            case "building" -> "楼栋档案";
            case "assessment" -> "评分结果";
            case "inspections" -> "巡检记录";
            case "evidence" -> "业务证据";
            case "aiEvidence" -> "人工智能辅助证据";
            case "riskScore" -> "风险分";
            case "confidenceScore" -> "判断置信度";
            case "completenessScore" -> "资料完整度";
            case "priorityScore" -> "更新优先级分";
            case "responsibilityBoundary" -> "责任边界";
            default -> value;
        };
    }

    private String readable(Object value) {
        if (value == null) return "未知";
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private byte[] pdf(List<byte[]> images) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("%PDF-1.4\n%âãÏÓ\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            int objectCount = 2 + images.size() * 3;
            long[] offsets = new long[objectCount + 1];
            writeObject(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < images.size(); i++) {
                kids.append(3 + i * 3).append(" 0 R ");
            }
            writeObject(out, offsets, 2,
                    "<< /Type /Pages /Count " + images.size()
                            + " /Kids [" + kids + "] >>");
            for (int i = 0; i < images.size(); i++) {
                int pageObject = 3 + i * 3;
                int imageObject = pageObject + 1;
                int contentObject = pageObject + 2;
                writeObject(out, offsets, pageObject,
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                                + "/Resources << /XObject << /Im0 "
                                + imageObject
                                + " 0 R >> >> /Contents "
                                + contentObject
                                + " 0 R >>");
                writeStream(out, offsets, imageObject,
                        "<< /Type /XObject /Subtype /Image /Width "
                                + IMAGE_WIDTH
                                + " /Height "
                                + IMAGE_HEIGHT
                                + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                                + " /Filter /DCTDecode",
                        images.get(i));
                byte[] content = "q 595 0 0 842 0 0 cm /Im0 Do Q\n"
                        .getBytes(StandardCharsets.US_ASCII);
                writeStream(out, offsets, contentObject, "<<", content);
            }
            long xref = out.size();
            out.write(("xref\n0 " + (objectCount + 1) + "\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write("0000000000 65535 f \n"
                    .getBytes(StandardCharsets.US_ASCII));
            for (int i = 1; i <= objectCount; i++) {
                out.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i])
                        .getBytes(StandardCharsets.US_ASCII));
            }
            out.write(("trailer\n<< /Size " + (objectCount + 1)
                    + " /Root 1 0 R >>\nstartxref\n"
                    + xref
                    + "\n%%EOF\n")
                    .getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("PDF 封装失败", ex);
        }
    }

    private void writeObject(
            ByteArrayOutputStream out, long[] offsets, int number, String body)
            throws IOException {
        offsets[number] = out.size();
        out.write((number + " 0 obj\n" + body + "\nendobj\n")
                .getBytes(StandardCharsets.US_ASCII));
    }

    private void writeStream(
            ByteArrayOutputStream out,
            long[] offsets,
            int number,
            String dictionary,
            byte[] data) throws IOException {
        offsets[number] = out.size();
        out.write((number + " 0 obj\n" + dictionary + " /Length "
                + data.length + " >>\nstream\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.write("\nendstream\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));
    }
}
