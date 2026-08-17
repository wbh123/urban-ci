package org.urbansafe.priority.ai.orchestration;

import org.urbansafe.priority.ai.provider.AiProviderException;

/**
 * 归一化检测框的共享校验与修正组件。
 *
 * <p>FastAPI 客户端校验与持久层入库必须复用同一语义，避免出现“客户端接受、持久层
 * 又因更严格条件静默丢弃”的不一致。统一约定：归一化坐标只允许 {@link #EPSILON} 级别的
 * 浮点误差；超出该容差或非有限数值视为真正非法并抛出明确异常，绝不静默丢弃或大幅裁剪。
 */
public final class AiBoundingBoxNormalizer {

    /** 归一化坐标浮点误差容差；例如 x+width=1.0000000001 视为 1。 */
    public static final double EPSILON = 1e-6;

    private AiBoundingBoxNormalizer() {
    }

    /** 归一化后的检测框数值。 */
    public record Box(double x, double y, double width, double height) {
    }

    /** 归一化并夹取极小浮点越界；真正非法坐标抛出明确异常。 */
    public static Box normalize(Double x, Double y, Double width, Double height) {
        requireFinite(x, y, width, height);
        if (x < -EPSILON || y < -EPSILON || x > 1.0 + EPSILON || y > 1.0 + EPSILON) {
            throw invalid("检测框左上角坐标越界");
        }
        if (width <= 0.0 || height <= 0.0) {
            throw invalid("检测框宽高必须为正");
        }
        if (width > 1.0 + EPSILON || height > 1.0 + EPSILON
                || x + width > 1.0 + EPSILON || y + height > 1.0 + EPSILON) {
            throw invalid("检测框范围越界");
        }

        double normalizedX = clampUnit(x);
        double normalizedY = clampUnit(y);
        double normalizedWidth = Math.min(width, 1.0 - normalizedX);
        double normalizedHeight = Math.min(height, 1.0 - normalizedY);
        if (normalizedWidth <= 0.0 || normalizedHeight <= 0.0) {
            throw invalid("检测框宽高经归一化后无效");
        }
        return new Box(normalizedX, normalizedY, normalizedWidth, normalizedHeight);
    }

    /** 仅校验是否落在归一化容差内，不修正数值，供客户端响应校验复用。 */
    public static boolean isValid(Double x, Double y, Double width, Double height) {
        if (!finite(x) || !finite(y) || !finite(width) || !finite(height)) {
            return false;
        }
        return x >= -EPSILON && y >= -EPSILON
                && x <= 1.0 + EPSILON && y <= 1.0 + EPSILON
                && width > 0.0 && height > 0.0
                && width <= 1.0 + EPSILON && height <= 1.0 + EPSILON
                && x + width <= 1.0 + EPSILON && y + height <= 1.0 + EPSILON;
    }

    private static void requireFinite(Double x, Double y, Double width, Double height) {
        if (x == null || y == null || width == null || height == null) {
            throw invalid("检测框缺少坐标或宽高字段");
        }
        if (!finite(x) || !finite(y) || !finite(width) || !finite(height)) {
            throw invalid("检测框必须使用有限数值");
        }
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double clampUnit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static AiProviderException invalid(String message) {
        return new AiProviderException(AiErrorCodes.AI_INVALID_RESPONSE, message);
    }
}
