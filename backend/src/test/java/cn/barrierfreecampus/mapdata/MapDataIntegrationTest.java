package cn.barrierfreecampus.mapdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapDataIntegrationTest {
    private static final UUID DEMO_DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("barrierfreecampus_test")
            .withUsername("barrierfree_test")
            .withPassword("barrierfree_test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
        registry.add("app.security.jwt-secret", () -> "stage2-integration-test-secret-with-more-than-32-bytes");
    }

    @Autowired
    private MapDataService mapDataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldMigrateSeedAndQueryGcj02SpatialData() {
        MapDtos.MapSnapshot snapshot = mapDataService.snapshot(DEMO_DATASET_ID, null, true);

        assertThat(snapshot.dataset().coordinateSystem()).isEqualTo("GCJ02");
        assertThat(snapshot.buildings()).hasSize(5);
        assertThat(snapshot.nodes()).hasSize(20);
        assertThat(snapshot.edges()).hasSize(31);
        assertThat(snapshot.facilities()).hasSize(15);
        assertThat(snapshot.barriers()).hasSize(5);
        assertThat(jdbcTemplate.queryForObject("SELECT postgis_version() IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM route_node WHERE dataset_id = ? "
                                + "AND geom && ST_MakeEnvelope(112.934, 28.175, 112.940, 28.180, 0)",
                        Integer.class,
                        DEMO_DATASET_ID))
                .isEqualTo(20);
    }

    @Test
    void shouldExportAndIdempotentlyImportDemoGeoJson() {
        JsonNode geoJson = mapDataService.exportGeoJson(DEMO_DATASET_ID);
        MapDtos.ImportResult imported = mapDataService.importGeoJson(DEMO_DATASET_ID, geoJson, "integration-test");

        assertThat(geoJson.path("type").asText()).isEqualTo("FeatureCollection");
        assertThat(geoJson.path("coordinateSystem").asText()).isEqualTo("GCJ02");
        assertThat(geoJson.path("features")).hasSize(66);
        assertThat(imported).isEqualTo(new MapDtos.ImportResult(20, 31, 15));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM route_edge WHERE dataset_id = ?", Integer.class, DEMO_DATASET_ID))
                .isEqualTo(31);
    }

    @Test
    void shouldEnforceAdminMapPermissions() throws Exception {
        mockMvc.perform(get("/api/admin/map/datasets").with(user("demo-user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/map/datasets").with(user("demo-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].coordinateSystem").value("GCJ02"));
    }

    @Test
    void shouldDisableAndReenableDataset() {
        try {
            assertThat(mapDataService.setDatasetEnabled(DEMO_DATASET_ID, false, "integration-test").enabled())
                    .isFalse();
            assertThat(mapDataService.listDatasets(false)).isEmpty();
        } finally {
            mapDataService.setDatasetEnabled(DEMO_DATASET_ID, true, "integration-test");
        }

        assertThat(mapDataService.listDatasets(false)).hasSize(1);
    }

    @Test
    void shouldPersistAdminNodeEdgeAndFacilityChanges() {
        UUID firstNode = mapDataService.saveNode(
                DEMO_DATASET_ID,
                null,
                new MapDtos.NodeRequest(
                        "N-INTEGRATION-A", "集成测试起点", "WAYPOINT", true,
                        new MapDtos.Coordinate(112.9388, 28.1768)),
                "integration-test");
        UUID secondNode = mapDataService.saveNode(
                DEMO_DATASET_ID,
                null,
                new MapDtos.NodeRequest(
                        "N-INTEGRATION-B", "集成测试终点", "WAYPOINT", true,
                        new MapDtos.Coordinate(112.9390, 28.1770)),
                "integration-test");
        UUID edge = mapDataService.saveEdge(
                DEMO_DATASET_ID,
                null,
                new MapDtos.EdgeRequest(
                        "E-INTEGRATION", "集成测试道路", firstNode, secondNode, BigDecimal.valueOf(32),
                        "GENTLE", false, 0, "STANDARD", "CONCRETE", "MEDIUM", true,
                        "ACTIVE", "LOW", List.of(new MapDtos.Coordinate(112.9389, 28.1769))),
                "integration-test");
        UUID facility = mapDataService.createFacility(
                DEMO_DATASET_ID,
                new MapDtos.FacilityRequest(
                        null, "FAC-INTEGRATION", "集成测试休息点", "REST_AREA", null, "OPEN",
                        "用于验证管理端写入持久化", true, new MapDtos.Coordinate(112.9389, 28.1769)),
                "integration-test");

        mapDataService.saveNode(
                DEMO_DATASET_ID,
                firstNode,
                new MapDtos.NodeRequest(
                        "N-INTEGRATION-A", "集成测试起点", "WAYPOINT", false,
                        new MapDtos.Coordinate(112.9388, 28.1768)),
                "integration-test");
        MapDtos.MapSnapshot refreshed = mapDataService.snapshot(DEMO_DATASET_ID, null, true);

        assertThat(refreshed.nodes()).anySatisfy(node -> {
            assertThat(node.id()).isEqualTo(firstNode);
            assertThat(node.active()).isFalse();
        });
        assertThat(refreshed.edges()).extracting(MapDtos.EdgeView::id).contains(edge);
        assertThat(refreshed.facilities()).extracting(MapDtos.FacilityView::id).contains(facility);
    }
}
