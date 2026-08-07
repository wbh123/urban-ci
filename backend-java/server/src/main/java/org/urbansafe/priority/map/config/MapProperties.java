package org.urbansafe.priority.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "urban-safe.map")
public class MapProperties {

    private boolean enabled = false;
    private String provider = "AMAP";
    private double defaultCenterLongitude = 113.13396;
    private double defaultCenterLatitude = 27.82767;
    private int defaultZoom = 12;

    @NestedConfigurationProperty
    private AmapProperties amap = new AmapProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public double getDefaultCenterLongitude() {
        return defaultCenterLongitude;
    }

    public void setDefaultCenterLongitude(double defaultCenterLongitude) {
        this.defaultCenterLongitude = defaultCenterLongitude;
    }

    public double getDefaultCenterLatitude() {
        return defaultCenterLatitude;
    }

    public void setDefaultCenterLatitude(double defaultCenterLatitude) {
        this.defaultCenterLatitude = defaultCenterLatitude;
    }

    public int getDefaultZoom() {
        return defaultZoom;
    }

    public void setDefaultZoom(int defaultZoom) {
        this.defaultZoom = defaultZoom;
    }

    public AmapProperties getAmap() {
        return amap;
    }

    public void setAmap(AmapProperties amap) {
        this.amap = amap;
    }
}
