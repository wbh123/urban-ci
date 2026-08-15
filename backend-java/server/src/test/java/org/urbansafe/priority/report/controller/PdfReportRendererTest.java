package org.urbansafe.priority.report.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PdfReportRendererTest {

    @Test
    void prefersKnownChineseFontFamiliesInsteadOfLogicalSansSerif() {
        assertThat(PdfReportRenderer.selectPreferredChineseFontFamily(List.of(
                "Arial", "Microsoft YaHei", "Noto Sans CJK SC")))
                .isEqualTo("Noto Sans CJK SC");
        assertThat(PdfReportRenderer.selectPreferredChineseFontFamily(List.of(
                "Arial", "Microsoft YaHei")))
                .isEqualTo("Microsoft YaHei");
    }

    @Test
    void returnsNullWhenNoPreferredChineseFontIsInstalled() {
        assertThat(PdfReportRenderer.selectPreferredChineseFontFamily(List.of(
                "Arial", "DejaVu Sans", "Liberation Sans")))
                .isNull();
    }
}
