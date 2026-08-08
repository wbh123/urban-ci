package org.urbansafe.priority.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证楼栋中心点定位迁移具备独立表、外键和必要索引。 */
class BuildingLocationMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void buildingLocationMigrationShouldCreateRequiredSchema() {
        String relation = jdbcTemplate.queryForObject(
                "SELECT to_regclass('geo.building_location')::text",
                String.class);
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname='geo' AND tablename='building_location'
                """, String.class);
        Integer buildingForeignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname='geo'
                  AND t.relname='building_location'
                  AND c.contype='f'
                  AND c.confrelid='core.building'::regclass
                """, Integer.class);
        String uniqueIndexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname='geo'
                  AND tablename='building_location'
                  AND indexname='uk_building_location_active'
                """, String.class);
        String spatialIndexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname='geo'
                  AND tablename='building_location'
                  AND indexname='idx_building_location_centroid_gist'
                """, String.class);

        assertThat(relation).isEqualTo("geo.building_location");
        assertThat(indexes).contains(
                "uk_building_location_active",
                "idx_building_location_centroid_gist");
        assertThat(buildingForeignKeys).isEqualTo(1);
        assertThat(uniqueIndexDefinition)
                .contains("UNIQUE")
                .contains("building_id")
                .contains("deleted_at IS NULL");
        assertThat(spatialIndexDefinition)
                .containsIgnoringCase("USING gist")
                .contains("centroid")
                .contains("deleted_at IS NULL");
    }
}
