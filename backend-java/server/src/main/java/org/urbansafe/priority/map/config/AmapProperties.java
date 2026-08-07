package org.urbansafe.priority.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urban-safe.map.amap")
public class AmapProperties {

    private String jsApiKey = "";
    private String securityJsCode = "";
    private String serviceHost = "/_AMapService";
    private String webServiceKey = "";
    private String webServiceBaseUrl = "https://restapi.amap.com";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int cacheTtlSeconds = 86400;
    private boolean liveTestEnabled = false;

    public String getJsApiKey() {
        return jsApiKey;
    }

    public void setJsApiKey(String jsApiKey) {
        this.jsApiKey = jsApiKey;
    }

    public String getSecurityJsCode() {
        return securityJsCode;
    }

    public void setSecurityJsCode(String securityJsCode) {
        this.securityJsCode = securityJsCode;
    }

    public String getServiceHost() {
        return serviceHost;
    }

    public void setServiceHost(String serviceHost) {
        this.serviceHost = serviceHost;
    }

    public String getWebServiceKey() {
        return webServiceKey;
    }

    public void setWebServiceKey(String webServiceKey) {
        this.webServiceKey = webServiceKey;
    }

    public String getWebServiceBaseUrl() {
        return webServiceBaseUrl;
    }

    public void setWebServiceBaseUrl(String webServiceBaseUrl) {
        this.webServiceBaseUrl = webServiceBaseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public boolean isLiveTestEnabled() {
        return liveTestEnabled;
    }

    public void setLiveTestEnabled(boolean liveTestEnabled) {
        this.liveTestEnabled = liveTestEnabled;
    }
}
