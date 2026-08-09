package org.urbansafe.priority.map.repository;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 小区中心点独立写仓储。坐标系由服务层显式判定，仓储层不再根据 provider 猜测。 */
@Repository
public class CommunityLocationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public CommunityLocationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> save(UUID communityId, double longitude, double latitude,
            String address, String provider, String coordinateSystem, String level, String metadata) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("communityId", communityId)
                .addValue("longitude", longitude)
                .addValue("latitude", latitude)
                .addValue("address", address)
                .addValue("provider", provider)
                .addValue("coordinateSystem", coordinateSystem)
                .addValue("level", level)
                .addValue("metadata", metadata);

        int updated = jdbc.update("""
                UPDATE geo.community_location
                SET centroid=ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326),
                    formatted_address=:address,
                    source_provider=:provider,
                    source_coordinate_system=:coordinateSystem,
                    match_level=:level,
                    metadata=CAST(:metadata AS jsonb),
                    collected_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE community_id=:communityId AND deleted_at IS NULL
                """, parameters);

        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO geo.community_location
                      (community_id,centroid,formatted_address,source_provider,
                       source_coordinate_system,match_level,metadata)
                    VALUES (:communityId,
                      ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326),
                      :address,:provider,:coordinateSystem,:level,CAST(:metadata AS jsonb))
                    """, parameters);
        }

        return jdbc.queryForObject("""
                SELECT community_id AS "communityId",
                       ST_X(centroid) AS "longitude",
                       ST_Y(centroid) AS "latitude",
                       formatted_address AS "formattedAddress",
                       source_provider AS "provider",
                       source_coordinate_system AS "coordinateSystem",
                       match_level AS "matchLevel",
                       metadata AS "metadata",
                       updated_at AS "updatedAt"
                FROM geo.community_location
                WHERE community_id=:communityId AND deleted_at IS NULL
                """, Map.of("communityId", communityId), rowMapper);
    }
}
