package org.urbansafe.priority.common.request;

/** 保存当前线程的请求编号与客户端地址，供统一响应和审计链路复用。 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        String requestId = REQUEST_ID.get();
        return requestId == null ? "UNKNOWN" : requestId;
    }

    /**
     * 保存当前请求的客户端地址。
     *
     * @param clientIp Servlet 容器解析得到的远端地址
     */
    public static void setClientIp(String clientIp) {
        CLIENT_IP.set(clientIp);
    }

    /**
     * 获取当前请求客户端地址。
     *
     * @return 客户端地址；非 Web 线程返回空字符串
     */
    public static String getClientIp() {
        String clientIp = CLIENT_IP.get();
        return clientIp == null ? "" : clientIp;
    }

    public static void clear() {
        REQUEST_ID.remove();
        CLIENT_IP.remove();
    }
}
