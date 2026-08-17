package org.urbansafe.priority.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.provider.AiProviderException;

class AiBoundingBoxNormalizerTest {

    @Test
    void normalizeClampsTinyFloatingPointOverflow() {
        AiBoundingBoxNormalizer.Box box = AiBoundingBoxNormalizer.normalize(
                0.5, 0.5, 0.5000000001, 0.4999999999);

        assertThat(box.width()).isEqualTo(0.5);
        assertThat(box.x() + box.width()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void normalizeRejectsTrulyInvalidCoordinates() {
        assertThatThrownBy(() -> AiBoundingBoxNormalizer.normalize(1.5, 0.0, 0.2, 0.2))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("越界");
    }

    @Test
    void normalizeRejectsSevereExtentOverflowInsteadOfSilentlyClamping() {
        assertThatThrownBy(() -> AiBoundingBoxNormalizer.normalize(0.5, 0.2, 0.6, 0.3))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("越界");
    }

    @Test
    void normalizeRejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> AiBoundingBoxNormalizer.normalize(Double.NaN, 0.0, 0.2, 0.2))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("有限数值");
    }

    @Test
    void isValidAcceptsTinyOverflowWithinEpsilon() {
        assertThat(AiBoundingBoxNormalizer.isValid(0.5, 0.5, 0.5000000001, 0.5)).isTrue();
        assertThat(AiBoundingBoxNormalizer.isValid(0.5, 0.5, 0.6, 0.5)).isFalse();
        assertThat(AiBoundingBoxNormalizer.isValid(Double.NaN, 0.0, 0.2, 0.2)).isFalse();
    }
}
