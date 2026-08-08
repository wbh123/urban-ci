package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** R2 空间边界结构契约：验证 V33 将旧空间表升级为可确认、可版本化结构。 */
class SpatialBoundaryMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationProvidesVersionedCommunityAndBuildingBoundaries() {
        assertBoundaryColumns("community_boundary", "community_id");
        assertBoundaryColumns("building_boundary", "building_id");

        assertThat(tableExists("geo", "spatial_boundary_revision")).isTrue();
        assertThat(columns("geo", "spatial_boundary_revision"))
                .contains(
                        "id",
                        "entity_type",
                        "entity_id",
                        "boundary_id",
                        "version",
                        "source_type",
                        "source_coordinate_system",
                        "display_coordinate_system",
                        "source_geometry_json",
                        "display_geometry",
                        "status",
                        "changed_by",
                        "changed_at");
    }

    @Test
    void migrationProvidesSpatialIndexesAndStableConstraints() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname='geo'
                """, String.class);

        assertThat(indexes)
                .contains(
                        "uk_community_boundary_active",
                        "uk_building_boundary_active",
                        "idx_community_boundary_display_gist",
                        "idx_building_boundary_display_gist",
                        "idx_spatial_boundary_revision_entity_version");

        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='geo'
                  AND t.relname IN ('community_boundary','building_boundary')
                """, String.class);
        assertThat(constraints)
                .contains("ck_community_boundary_status", "ck_building_boundary_status");
    }

    private void assertBoundaryColumns(String table, String entityColumn) {
        assertThat(tableExists("geo", table)).isTrue();
        assertThat(columns("geo", table))
                .contains(
                        "id",
                        entityColumn,
                        "source_type",
                        "source_provider",
                        "source_object_id",
                        "source_coordinate_system",
                        "source_geometry_json",
                        "display_coordinate_system",
                        "display_geometry",
                        "status",
                        "version",
                        "verified_by",
                        "verified_at",
                        "remark",
                        "created_at",
                        "updated_at",
                        "deleted_at");
    }

    private boolean tableExists(String schema, String table) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema=? AND table_name=?
                """, Integer.class, schema, table);
        return count != null && count > 0;
    }

    private List<String> columns(String schema, String table) {
        return jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema=? AND table_name=?
                ORDER BY ordinal_position
                """, String.class, schema, table);
    }
}
