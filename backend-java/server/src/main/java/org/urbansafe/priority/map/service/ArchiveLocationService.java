package org.urbansafe.priority.map.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

/** 楼栋地图中心点的对象范围权限、输入校验和持久化入口。 */
@Service
public class ArchiveLocationService {

    private static final Set<String> LOCATION_PROVIDERS =
            Set.of("AMAP", "MANUAL", "IMPORT", "MOCK");

    private final BusinessAccessService access;
    private final Phase2Repository repository;

    public ArchiveLocationService(BusinessAccessService access, Phase2Repository repository) {
        this.access = access;
        this.repository = repository;
    }

    public Map<String, Object> getBuildingLocation(UUID buildingId) {
        access.assertCanReadBuilding(buildingId);
        return repository.findBuildingLocation(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BUILDING_LOCATION_NOT_FOUND", "楼栋尚未保存地图位置"));
    }

    public Map<String, Object> saveBuildingLocation(UUID buildingId, Map<String, Object> request) {
        access.assertCanManageBuilding(buildingId);
        double longitude = number(request.get("longitude"), "longitude");
        double latitude = number(request.get("latitude"), "latitude");
        if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new InvalidRequestException("MAP_COORDINATE_INVALID", "经纬度超出有效范围");
        }
        String provider = locationProvider(request.get("provider"));
        return repository.saveBuildingLocation(
                buildingId,
                longitude,
                latitude,
                text(request.get("formattedAddress")),
                provider,
                text(request.get("matchLevel")),
                repository.json(locationMetadata(request)));
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
