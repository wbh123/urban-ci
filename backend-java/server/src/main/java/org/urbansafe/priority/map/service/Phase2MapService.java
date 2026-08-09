package org.urbansafe.priority.map.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;
import org.urbansafe.priority.map.repository.CommunityLocationRepository;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

@Service
public class Phase2MapService {

    private static final Set<String> LOCATION_PROVIDERS =
            Set.of("AMAP", "MANUAL", "IMPORT", "MOCK");
    private static final Set<String> LOCATION_COORDINATE_SYSTEMS =
            Set.of("GCJ02", "WGS84", "BD09", "UNKNOWN");

    private final MapProperties map;
    private final AmapProperties amap;
    private final Phase2Repository repository;
    private final CommunityLocationRepository locationRepository;

    public Phase2MapService(MapProperties map, AmapProperties amap, Phase2Repository repository,
            CommunityLocationRepository locationRepository) {
        this.map = map;
        this.amap = amap;
        this.repository = repository;
        this.locationRepository = locationRepository;
    }

    public Map<String, Object> runtimeConfig() {
        boolean live = map.isEnabled() && text(amap.getJsApiKey()) != null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", map.isEnabled());
        result.put("mode", live ? "LIVE" : "MOCK");
        result.put("provider", map.getProvider());
        result.put("jsApiKey", live ? amap.getJsApiKey() : "");
        result.put("serviceHost", amap.getServiceHost());
        result.put("securityJsCodeExposed", false);
        result.put("defaultCenter", Map.of("longitude", map.getDefaultCenterLongitude(),
                "latitude", map.getDefaultCenterLatitude()));
        result.put("defaultZoom", map.getDefaultZoom());
        return result;
    }

    public List<Map<String, Object>> communityPoints() {
        return repository.listCommunityPoints();
    }

    public Map<String, Object> saveLocation(UUID communityId, Map<String, Object> request) {
        if (!repository.communityExists(communityId)) {
            throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在");
        }
        double longitude = number(request.get("longitude"), "longitude");
        double latitude = number(request.get("latitude"), "latitude");
        if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new InvalidRequestException("MAP_COORDINATE_INVALID", "经纬度超出有效范围");
        }
        String provider = locationProvider(request.get("provider"));
        String coordinateSystem = locationCoordinateSystem(request.get("coordinateSystem"), provider);
        return locationRepository.save(communityId, longitude, latitude,
                text(request.get("formattedAddress")), provider, coordinateSystem,
                text(request.get("matchLevel")), repository.json(locationMetadata(request)));
    }

    public Map<String, Object> getLocation(UUID communityId) {
        if (!repository.communityExists(communityId)) {
            throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在");
        }
        return repository.findCommunityLocation(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMMUNITY_LOCATION_NOT_FOUND", "小区尚未保存地图位置"));
    }

    private String locationProvider(Object value) {
        String provider = text(value);
        if (provider == null) {
            return "MANUAL";
        }
        provider = provider.toUpperCase(Locale.ROOT);
        if (!LOCATION_PROVIDERS.contains(provider)) {
            throw new InvalidRequestException(
                    "MAP_PROVIDER_INVALID", "provider 仅支持 AMAP、MANUAL、IMPORT 或 MOCK");
        }
        return provider;
    }

    private String locationCoordinateSystem(Object value, String provider) {
        String coordinateSystem = text(value);
        if (coordinateSystem == null) {
            return "AMAP".equals(provider) ? "GCJ02" : "UNKNOWN";
        }
        coordinateSystem = coordinateSystem.toUpperCase(Locale.ROOT);
        if (!LOCATION_COORDINATE_SYSTEMS.contains(coordinateSystem)) {
            throw new InvalidRequestException(
                    "MAP_COORDINATE_SYSTEM_INVALID",
                    "coordinateSystem 仅支持 GCJ02、WGS84、BD09 或 UNKNOWN");
        }
        return coordinateSystem;
    }

    private Object locationMetadata(Map<String, Object> request) {
        Object rawMetadata = request.get("metadata");
        if (!Boolean.TRUE.equals(request.get("mock"))) {
            return rawMetadata;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (rawMetadata instanceof Map<?, ?> source) {
            source.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        metadata.put("mock", true);
        return metadata;
    }

    private double number(Object value, String field) {
        try {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw new InvalidRequestException("MAP_FIELD_INVALID", field + " 必须为数字");
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }
}
