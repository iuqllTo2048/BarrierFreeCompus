package cn.barrierfreecampus.mapdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import cn.barrierfreecampus.routing.RoutingDtos;
import cn.barrierfreecampus.routing.RoutingService;
import cn.barrierfreecampus.business.BusinessDtos;
import cn.barrierfreecampus.business.BusinessService;
import cn.barrierfreecampus.auth.JwtService;
import cn.barrierfreecampus.agent.AgentDtos;
import cn.barrierfreecampus.agent.AgentRepository;
import cn.barrierfreecampus.agent.AgentTools;
import cn.barrierfreecampus.agent.AiProperties;
import cn.barrierfreecampus.agent.AgentExecutionContext;
import cn.barrierfreecampus.agent.ControlledAgentTools;
import cn.barrierfreecampus.analytics.AnalyticsDtos;
import cn.barrierfreecampus.analytics.AnalyticsFilter;
import cn.barrierfreecampus.analytics.AnalyticsService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
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
    private static final UUID SCHOOL_DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID NODE_2 = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID NODE_3 = UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID NODE_7 = UUID.fromString("40000000-0000-0000-0000-000000000007");
    private static final UUID NODE_8 = UUID.fromString("40000000-0000-0000-0000-000000000008");
    private static final UUID EDGE_2 = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID EDGE_6 = UUID.fromString("50000000-0000-0000-0000-000000000006");
    private static final UUID FACILITY_1 = UUID.fromString("60000000-0000-0000-0000-000000000001");

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

    @Autowired
    private RoutingService routingService;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AgentTools agentTools;

    @Autowired
    private ControlledAgentTools controlledAgentTools;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private AnalyticsService analyticsService;

    @BeforeEach
    void enableLegacyDemoFixture() {
        jdbcTemplate.update("UPDATE dataset SET enabled=TRUE WHERE id=?", DEMO_DATASET_ID);
    }

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
    void shouldExposeBlankSchoolDatasetAndKeepLegacyDemoDisabled() {
        jdbcTemplate.update("UPDATE dataset SET enabled=FALSE WHERE id=?", DEMO_DATASET_ID);
        List<MapDtos.DatasetView> publicDatasets = mapDataService.listDatasets(false);
        MapDtos.MapSnapshot school = mapDataService.snapshot(SCHOOL_DATASET_ID, null, false);

        assertThat(publicDatasets).extracting(MapDtos.DatasetView::code)
                .containsExactly("SCHOOL_EXAMPLE_V1");
        assertThat(school.dataset().name()).isEqualTo("学校示例校园数据集");
        assertThat(school.dataset().centerLng()).isEqualTo(104.695359);
        assertThat(school.dataset().centerLat()).isEqualTo(31.534827);
        assertThat(school.buildings()).isEmpty();
        assertThat(school.nodes()).isEmpty();
        assertThat(school.edges()).isEmpty();
        assertThat(school.facilities()).isEmpty();
        assertThat(school.barriers()).isEmpty();
        assertThat(mapDataService.listDatasets(true).stream()
                .filter(dataset -> dataset.code().equals("YUNLU_DEMO_V1"))
                .findFirst().orElseThrow().enabled()).isFalse();
    }

    @Test
    void shouldPersistPolylineAndCalculateAuthoritativeDistance() {
        UUID start = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("TEST-START", "测试起点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        UUID end = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("TEST-END", "测试终点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.696359, 31.535827)), "demo_admin");
        UUID edge = mapDataService.saveEdge(SCHOOL_DATASET_ID, null,
                new MapDtos.EdgeRequest("TEST-EDGE", "测试折线", start, end, BigDecimal.ONE,
                        "FLAT", false, 0, "STANDARD", "ASPHALT", "HIGH", true,
                        "ACTIVE", "LOW", List.of(new MapDtos.Coordinate(104.696359, 31.534827))),
                "demo_admin");

        MapDtos.EdgeView saved = mapDataService.snapshot(SCHOOL_DATASET_ID, null, true).edges().stream()
                .filter(item -> item.id().equals(edge)).findFirst().orElseThrow();
        assertThat(saved.geometry().path("coordinates")).hasSize(3);
        assertThat(saved.distanceM()).isGreaterThan(new BigDecimal("190"));
        assertThat(saved.distanceM()).isLessThan(new BigDecimal("220"));
        assertThat(saved.distanceM()).isNotEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void shouldExportAndIdempotentlyImportDemoGeoJson() {
        JsonNode geoJson = mapDataService.exportGeoJson(DEMO_DATASET_ID);
        MapDtos.ImportResult imported = mapDataService.importGeoJson(DEMO_DATASET_ID, geoJson, "integration-test");

        assertThat(geoJson.path("type").asText()).isEqualTo("FeatureCollection");
        assertThat(geoJson.path("coordinateSystem").asText()).isEqualTo("GCJ02");
        assertThat(geoJson.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(geoJson.path("features")).hasSize(81);
        assertThat(imported).isEqualTo(new MapDtos.ImportResult(20, 31, 15));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM route_edge WHERE dataset_id = ?", Integer.class, DEMO_DATASET_ID))
                .isEqualTo(31);
    }

    @Test
    void shouldPreviewAndApplyFormalGeoJsonWithBackup() {
        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("SYNC-N-01", "同步节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        JsonNode payload = mapDataService.exportGeoJson(SCHOOL_DATASET_ID);
        jdbcTemplate.update("DELETE FROM route_node WHERE dataset_id=? AND external_id='SYNC-N-01'", SCHOOL_DATASET_ID);

        MapDtos.ImportPreview preview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        assertThat(preview.errors()).isEmpty();
        assertThat(preview.summaries().get("NODE").creates()).isEqualTo(1);

        MapDtos.ImportApplyResult result = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "KEEP_TARGET",
                        preview.payloadFingerprint(), preview.targetFingerprint()),
                "demo_admin");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_node WHERE dataset_id=? AND external_id='SYNC-N-01'",
                Integer.class, SCHOOL_DATASET_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM geojson_import_backup WHERE id=?", Integer.class, result.backupId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='GEOJSON_FORMAL_IMPORT'", Integer.class)).isPositive();
    }

    @Test
    void shouldApplyAllShareableGeoJsonEntityTypesToFormalDataset() {
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                (com.fasterxml.jackson.databind.node.ObjectNode) mapDataService.exportGeoJson(DEMO_DATASET_ID);
        payload.put("datasetId", SCHOOL_DATASET_ID.toString());
        payload.put("datasetCode", "SCHOOL_EXAMPLE_V1");

        MapDtos.ImportPreview preview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        assertThat(preview.errors()).isEmpty();
        assertThat(preview.summaries().get("BUILDING").creates()).isEqualTo(5);
        assertThat(preview.summaries().get("ENTRANCE").creates()).isEqualTo(5);
        assertThat(preview.summaries().get("BARRIER").creates()).isEqualTo(5);

        MapDtos.ImportApplyResult result = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "OVERWRITE",
                        preview.payloadFingerprint(), preview.targetFingerprint()),
                "demo_admin");
        assertThat(result.created()).isEqualTo(81);
        assertDatasetEntityCount("building", 5);
        assertDatasetEntityCount("building_entrance", 5);
        assertDatasetEntityCount("route_node", 20);
        assertDatasetEntityCount("route_edge", 31);
        assertDatasetEntityCount("accessible_facility", 15);
        assertDatasetEntityCount("barrier_report", 5);
    }

    @Test
    void shouldKeepOrOverwriteFormalGeoJsonConflictExplicitly() {
        UUID nodeId = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("SYNC-CONFLICT", "文件版本", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        JsonNode payload = mapDataService.exportGeoJson(SCHOOL_DATASET_ID);
        mapDataService.saveNode(SCHOOL_DATASET_ID, nodeId,
                new MapDtos.NodeRequest("SYNC-CONFLICT", "本地版本", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.696000, 31.535000)), "demo_admin");

        MapDtos.ImportPreview keepPreview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        assertThat(keepPreview.summaries().get("NODE").conflicts()).isEqualTo(1);
        MapDtos.ImportApplyResult kept = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "KEEP_TARGET",
                        keepPreview.payloadFingerprint(), keepPreview.targetFingerprint()),
                "demo_admin");
        assertThat(kept.keptLocal()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM route_node WHERE id=?", String.class, nodeId)).isEqualTo("本地版本");

        MapDtos.ImportPreview overwritePreview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        MapDtos.ImportApplyResult overwritten = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "OVERWRITE",
                        overwritePreview.payloadFingerprint(), overwritePreview.targetFingerprint()),
                "demo_admin");
        assertThat(overwritten.updated()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM route_node WHERE id=?", String.class, nodeId)).isEqualTo("文件版本");
    }

    @Test
    void shouldRejectStaleFormalGeoJsonPreviewAndInvalidDatasetCode() {
        JsonNode payload = mapDataService.exportGeoJson(SCHOOL_DATASET_ID);
        MapDtos.ImportPreview preview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("SYNC-STALE", "并发修改", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapDataService.applyGeoJson(
                        SCHOOL_DATASET_ID,
                        new MapDtos.ImportApplyRequest(payload, "KEEP_TARGET",
                                preview.payloadFingerprint(), preview.targetFingerprint()),
                        "demo_admin"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("目标数据集已发生变化");

        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("datasetCode", "OTHER_DATASET");
        assertThat(mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload).errors())
                .contains("datasetCode 与目标数据集不一致");
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
    void loginRefreshRotationAndLogoutMustRevokeRefreshTokens() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo_user\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("demo_user"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String loginCookieHeader = login.getResponse().getHeader("Set-Cookie");
        assertThat(loginCookieHeader).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth");
        assertThat(login.getResponse().getContentAsString()).doesNotContain("password", "password_hash");
        String firstRefreshToken = cookieValue(loginCookieHeader, "refresh_token");

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotatedRefreshToken = cookieValue(refresh.getResponse().getHeader("Set-Cookie"), "refresh_token");
        assertThat(rotatedRefreshToken).isNotEqualTo(firstRefreshToken);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", rotatedRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("refresh_token=", "Max-Age=0"));
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationErrorsMustBeGenericAndValidationSafe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录或刷新登录状态"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo_user\",\"password\":\"wrong-secret\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("wrong-secret"))));
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void userCannotResetDemoDataset() throws Exception {
        mockMvc.perform(post("/api/admin/business/datasets/" + DEMO_DATASET_ID + "/reset-demo")
                        .with(user("demo_user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldDisableAndReenableDataset() {
        try {
            assertThat(mapDataService.setDatasetEnabled(DEMO_DATASET_ID, false, "integration-test").enabled())
                    .isFalse();
            assertThat(mapDataService.listDatasets(false)).extracting(MapDtos.DatasetView::code)
                    .containsExactly("SCHOOL_EXAMPLE_V1");
        } finally {
            mapDataService.setDatasetEnabled(DEMO_DATASET_ID, true, "integration-test");
        }

        assertThat(mapDataService.listDatasets(false)).hasSize(2);
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

    @Test
    void shouldProduceDifferentWalkingProfilesAndWheelchairDetour() {
        RoutingDtos.RoutePlanResponse walking = routingService.plan(routeRequest(
                NODE_2, NODE_3, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        RoutingDtos.RoutePlanResponse wheelchair = routingService.plan(routeRequest(
                NODE_2, NODE_3, RoutingDtos.MobilityMode.WHEELCHAIR, RoutingDtos.RoutePreferences.defaults()));

        assertThat(walking.routes()).isNotEmpty();
        RoutingDtos.RouteResult shortest = walking.routes().stream()
                .filter(route -> route.equivalentProfiles().contains(RoutingDtos.RouteProfile.SHORTEST))
                .findFirst()
                .orElseThrow();
        RoutingDtos.RouteResult accessible = walking.routes().stream()
                .filter(route -> route.equivalentProfiles().contains(RoutingDtos.RouteProfile.ACCESSIBLE))
                .findFirst()
                .orElseThrow();
        assertThat(shortest.edgeIds()).contains(EDGE_2);
        assertThat(shortest.stairsCount()).isEqualTo(12);
        assertThat(accessible.edgeIds()).doesNotContain(EDGE_2);
        assertThat(wheelchair.routes()).isNotEmpty().allSatisfy(route -> {
            assertThat(route.edgeIds()).doesNotContain(EDGE_2);
            assertThat(route.stairsCount()).isZero();
        });
    }

    @Test
    void shouldApplyBlockedRoadImmediatelyWithoutRestart() {
        RoutingDtos.RoutePlanResponse before = routingService.plan(routeRequest(
                NODE_7, NODE_8, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        assertThat(before.routes()).anySatisfy(route -> assertThat(route.edgeIds()).contains(EDGE_6));

        jdbcTemplate.update("UPDATE route_edge SET status='BLOCKED' WHERE id=?", EDGE_6);
        RoutingDtos.RoutePlanResponse after = routingService.plan(routeRequest(
                NODE_7, NODE_8, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));

        assertThat(after.routes()).isNotEmpty().allSatisfy(route -> assertThat(route.edgeIds()).doesNotContain(EDGE_6));
        assertThat(after.routes()).allSatisfy(route -> assertThat(route.distanceM()).isGreaterThan(98));
    }

    @Test
    void shouldApplyApprovedActiveBarrierImmediatelyAndIgnoreExpiredBarrier() {
        jdbcTemplate.update(
                """
                INSERT INTO barrier_report(
                  id,dataset_id,external_id,title,barrier_type,review_status,active,
                  starts_at,ends_at,data_source,confidence_level,geom)
                VALUES (?::uuid,?::uuid,'BAR-INTEGRATION-ACTIVE','集成测试动态封路',
                  'VEHICLE_BLOCKING','APPROVED',TRUE,CURRENT_TIMESTAMP - INTERVAL '1 minute',
                  CURRENT_TIMESTAMP + INTERVAL '1 hour','USER_REPORT','LOW',
                  ST_SetSRID(ST_MakePoint(112.9360,28.1780),0))
                """,
                "71000000-0000-0000-0000-000000000001",
                DEMO_DATASET_ID.toString());

        RoutingDtos.RoutePlanResponse active = routingService.plan(routeRequest(
                NODE_7, NODE_8, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        assertThat(active.routes()).isNotEmpty().allSatisfy(route -> assertThat(route.edgeIds()).doesNotContain(EDGE_6));

        jdbcTemplate.update(
                "UPDATE barrier_report SET ends_at=CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE external_id='BAR-INTEGRATION-ACTIVE'");
        RoutingDtos.RoutePlanResponse expired = routingService.plan(routeRequest(
                NODE_7, NODE_8, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        assertThat(expired.routes()).anySatisfy(route -> assertThat(route.edgeIds()).contains(EDGE_6));
    }

    @Test
    void shouldReturnNoRouteAndDeduplicateSameNodeProfiles() {
        RoutingDtos.RoutePlanResponse sameNode = routingService.plan(routeRequest(
                NODE_2, NODE_2, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        assertThat(sameNode.routes()).hasSize(1);
        assertThat(sameNode.routes().getFirst().equivalentProfiles()).containsExactlyInAnyOrder(
                RoutingDtos.RouteProfile.SHORTEST,
                RoutingDtos.RouteProfile.ACCESSIBLE,
                RoutingDtos.RouteProfile.BALANCED);
        assertThat(sameNode.routes().getFirst().geometry().path("coordinates")).hasSize(2);
        assertThat(sameNode.routes().getFirst().distanceM()).isZero();

        jdbcTemplate.update(
                "UPDATE route_edge SET status='BLOCKED' WHERE from_node_id=? OR to_node_id=?", NODE_2, NODE_2);
        RoutingDtos.RoutePlanResponse noRoute = routingService.plan(routeRequest(
                NODE_2, NODE_3, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults()));
        assertThat(noRoute.routes()).isEmpty();
        assertThat(noRoute.notices()).anyMatch(notice -> notice.contains("不存在可达路线"));
    }

    @Test
    void shouldRejectOutOfRangePreferenceAtApiBoundary() throws Exception {
        String body = """
                {
                  "datasetId":"20000000-0000-0000-0000-000000000001",
                  "startNodeId":"40000000-0000-0000-0000-000000000002",
                  "endNodeId":"40000000-0000-0000-0000-000000000003",
                  "mobilityMode":"WALKING",
                  "travelPeriod":"DAY",
                  "preferences":{"distanceWeight":9.0}
                }
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/routes/plan")
                        .with(user("demo-user").roles("USER"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldCloseBarrierReportTrustReviewAndExpiryLoop() {
        jdbcTemplate.update(
                """
                INSERT INTO app_user(username,password_hash,role)
                SELECT 'reporter_two',password_hash,'USER' FROM app_user WHERE username='demo_user'
                """);
        BusinessDtos.BarrierSubmitRequest firstRequest = new BusinessDtos.BarrierSubmitRequest(
                DEMO_DATASET_ID, "北侧通道积水", "WATERLOGGING", "雨后出现积水，轮椅通行困难", 12,
                112.9348, 28.1762);
        BusinessDtos.BarrierReportView first = businessService.submitBarrier("demo_user", firstRequest);

        assertThat(first.reviewStatus()).isEqualTo("PENDING");
        assertThat(first.confidenceLevel()).isEqualTo("LOW");
        assertThat(first.active()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        businessService.submitBarrier("demo_user", firstRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("请勿重复提交");

        BusinessDtos.BarrierReportView second = businessService.submitBarrier(
                "reporter_two",
                new BusinessDtos.BarrierSubmitRequest(
                        DEMO_DATASET_ID, "北侧通道同类积水", "WATERLOGGING", "同一位置仍有积水", 12,
                        112.9349, 28.1762));
        assertThat(second.reviewStatus()).isEqualTo("NEEDS_VERIFICATION");
        assertThat(second.confidenceLevel()).isEqualTo("MEDIUM");
        assertThat(second.matchedReportId()).isEqualTo(first.id());
        assertThat(businessService.myBarriers("demo_user").getFirst().confidenceLevel()).isEqualTo("MEDIUM");

        BusinessDtos.BarrierReportView approved = businessService.reviewBarrier(
                second.id(), "demo_admin", new BusinessDtos.BarrierReviewRequest("APPROVED", true, "现场核验"));
        assertThat(approved.active()).isTrue();
        assertThat(approved.confidenceLevel()).isEqualTo("HIGH");

        jdbcTemplate.update("UPDATE barrier_report SET ends_at=CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE id=?", second.id());
        assertThat(businessService.expireBarriers()).isGreaterThanOrEqualTo(1);
        assertThat(businessService.adminBarriers("ALL").stream()
                .filter(item -> item.id().equals(second.id())).findFirst().orElseThrow().active()).isFalse();
    }

    @Test
    void shouldSupportFacilityInteractionHistoryFavoritesAndProfile() {
        businessService.rateFacility(FACILITY_1, "demo_user", new BusinessDtos.RatingRequest(5));
        businessService.rateFacility(FACILITY_1, "demo_user", new BusinessDtos.RatingRequest(4));
        businessService.commentFacility(
                FACILITY_1, "demo_user", new BusinessDtos.CommentRequest("坡道入口容易找到"));
        UUID suggestion = businessService.suggestFacility(
                FACILITY_1, "demo_user", new BusinessDtos.SuggestionRequest("INFORMATION_CORRECTION", "建议补充开放时间"));

        BusinessDtos.FacilityDetail detail = businessService.facility(FACILITY_1, "demo_user");
        assertThat(detail.myRating()).isEqualTo(4);
        assertThat(detail.ratingCount()).isEqualTo(1);
        assertThat(detail.comments()).extracting(BusinessDtos.FacilityCommentView::content)
                .contains("坡道入口容易找到");
        assertThat(businessService.suggestions()).extracting(BusinessDtos.FacilitySuggestionView::id)
                .contains(suggestion);
        businessService.reviewSuggestion(suggestion, "ACCEPTED", "demo_admin");

        RoutingDtos.RoutePlanRequest request = routeRequest(
                NODE_2, NODE_3, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults());
        RoutingDtos.RoutePlanResponse result = routingService.plan(request);
        UUID historyId = businessService.recordHistory("demo_user", request, result);
        UUID favoriteId = businessService.favorite(
                historyId, "demo_user", new BusinessDtos.FavoriteRequest("ACCESSIBLE", "去教学楼"));
        assertThat(businessService.history("demo_user")).extracting(BusinessDtos.RouteHistoryView::id)
                .contains(historyId);
        assertThat(businessService.favorites("demo_user")).extracting(BusinessDtos.FavoriteView::id)
                .contains(favoriteId);

        BusinessDtos.ProfileView updated = businessService.updateProfile(
                "demo_user",
                new BusinessDtos.ProfileUpdateRequest(
                        "演示用户", "WHEELCHAIR", true, 1.2, 1.5, 1.1, true, true));
        assertThat(updated.defaultMobilityMode()).isEqualTo("WHEELCHAIR");
        assertThat(updated.avoidStairs()).isTrue();

        businessService.deleteHistory(historyId, "demo_user");
        assertThat(businessService.favorites("demo_user")).isEmpty();
    }

    @Test
    void userMustNotAccessAdminBusinessApi() throws Exception {
        mockMvc.perform(get("/api/admin/business/overview").with(user("demo_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserAccessTokenMustStopWorkingImmediately() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO app_user(username,password_hash,role,enabled)
                SELECT 'disabled_access_user',password_hash,'USER',FALSE FROM app_user WHERE username='demo_user'
                """);
        String token = jwtService.issue("disabled_access_user", "USER");

        mockMvc.perform(get("/api/map/datasets").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demoResetMustRejectFormalDataset() {
        UUID formalId = UUID.fromString("20000000-0000-0000-0000-000000000099");
        jdbcTemplate.update(
                """
                INSERT INTO dataset(id,campus_id,code,name,dataset_type,coordinate_system,enabled,is_demo,description)
                VALUES (?,'10000000-0000-0000-0000-000000000001','FORMAL-RESET-TEST','正式数据保护测试',
                  'FORMAL','GCJ02',TRUE,FALSE,'不得被 Demo 重置影响')
                """,
                formalId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> businessService.resetDemo(formalId, "demo_admin"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("只允许重置 Demo");
        assertThat(jdbcTemplate.queryForObject("SELECT enabled FROM dataset WHERE id=?", Boolean.class, formalId)).isTrue();
    }

    @Test
    void demoResetMustClearBusinessDataKeepSeedAndAudit() {
        BusinessDtos.BarrierReportView report = businessService.submitBarrier(
                "demo_user",
                new BusinessDtos.BarrierSubmitRequest(
                        DEMO_DATASET_ID, "重置测试障碍", "NARROW_PATH", "仅用于验证安全重置", 6,
                        112.9390, 28.1760));
        businessService.rateFacility(FACILITY_1, "demo_user", new BusinessDtos.RatingRequest(3));
        jdbcTemplate.update("UPDATE dataset SET enabled=FALSE WHERE id=?", DEMO_DATASET_ID);
        jdbcTemplate.update(
                """
                UPDATE route_edge SET status='ACTIVE',slope_level='FLAT',has_stairs=FALSE,stairs_count=0,
                  width_level='WIDE',surface_type='ASPHALT',lighting_level='HIGH',risk_level='LOW'
                WHERE dataset_id=? AND external_id IN ('E-02','E-04','E-12','E-16','E-22','E-31')
                """,
                DEMO_DATASET_ID);
        jdbcTemplate.update(
                "UPDATE accessible_facility SET active=FALSE WHERE dataset_id=? AND external_id IN ('FAC-03','FAC-08')",
                DEMO_DATASET_ID);
        jdbcTemplate.update(
                "UPDATE barrier_report SET active=FALSE,review_status='REJECTED' WHERE dataset_id=? AND data_source='DEMO_GENERATED'",
                DEMO_DATASET_ID);

        businessService.resetDemo(DEMO_DATASET_ID, "demo_admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM barrier_report WHERE id=?", Integer.class, report.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM facility_rating WHERE dataset_id=?", Integer.class, DEMO_DATASET_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building WHERE dataset_id=? AND active=TRUE", Integer.class, DEMO_DATASET_ID))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT enabled FROM dataset WHERE id=?", Boolean.class, DEMO_DATASET_ID)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT slope_level,has_stairs,stairs_count,width_level,risk_level FROM route_edge WHERE dataset_id=? AND external_id='E-02'",
                DEMO_DATASET_ID)).containsEntry("slope_level", "MODERATE")
                .containsEntry("has_stairs", true).containsEntry("stairs_count", 12)
                .containsEntry("width_level", "NARROW").containsEntry("risk_level", "HIGH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT slope_level FROM route_edge WHERE dataset_id=? AND external_id='E-12'",
                String.class, DEMO_DATASET_ID)).isEqualTo("STEEP");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_edge WHERE dataset_id=? AND external_id IN ('E-04','E-16','E-22') AND slope_level='UNKNOWN'",
                Integer.class, DEMO_DATASET_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM route_edge WHERE dataset_id=? AND external_id='E-31'",
                String.class, DEMO_DATASET_ID)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accessible_facility WHERE dataset_id=? AND external_id IN ('FAC-03','FAC-08') AND active=TRUE",
                Integer.class, DEMO_DATASET_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM barrier_report WHERE dataset_id=? AND external_id IN ('BAR-01','BAR-02','BAR-03') AND active=TRUE AND review_status='APPROVED'",
                Integer.class, DEMO_DATASET_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='DEMO_RESET'", Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void agentToolsMustUseRealCampusDataAndDeterministicRouting() {
        List<AgentDtos.PlaceResult> places = agentTools.searchCampusPlace(
                DEMO_DATASET_ID, "从图书馆到体育与健康中心，轮椅怎么走", 8);
        assertThat(places).extracting(AgentDtos.PlaceResult::name)
                .contains("图书馆", "体育与健康中心");
        assertThat(agentTools.searchCampusPlace(DEMO_DATASET_ID, "体健中心", 3))
                .extracting(AgentDtos.PlaceResult::name)
                .contains("体育与健康中心");
        assertThat(agentTools.searchCampusPlace(DEMO_DATASET_ID, "图叔馆", 3))
                .extracting(AgentDtos.PlaceResult::name)
                .contains("图书馆");

        UUID start = places.stream().filter(item -> item.name().equals("图书馆"))
                .findFirst().orElseThrow().nearestNodeId();
        UUID end = places.stream().filter(item -> item.name().equals("体育与健康中心"))
                .findFirst().orElseThrow().nearestNodeId();
        RoutingDtos.RoutePlanResponse routes = agentTools.calculateAccessibleRoutes(new RoutingDtos.RoutePlanRequest(
                DEMO_DATASET_ID, start, end, RoutingDtos.MobilityMode.WHEELCHAIR,
                RoutingDtos.TravelPeriod.DAY, RoutingDtos.RoutePreferences.defaults()));

        assertThat(routes.routes()).isNotEmpty().allSatisfy(route -> assertThat(route.stairsCount()).isZero());
        assertThat(agentTools.compareRoutes(routes).recommendedProfile()).isNotBlank();
        assertThat(agentTools.searchFacilitiesNearRoute(routes)).isNotNull();
        assertThat(agentTools.searchActiveBarriers(DEMO_DATASET_ID)).allSatisfy(barrier ->
                assertThat(barrier.title()).isNotBlank());
        assertThat(agentTools.searchNearestAccessibleFacilities(
                DEMO_DATASET_ID, 112.9365, 28.1788, "ACCESSIBLE_TOILET", 3))
                .isNotEmpty()
                .allSatisfy(facility -> assertThat(facility.facilityType()).isEqualTo("ACCESSIBLE_TOILET"));
    }

    @Test
    void controlledAgentToolsMustUseTrustedContextAndBackendRouting() {
        AgentDtos.ConversationView conversation = agentRepository.createConversation(
                "demo_user", "受控工具测试", aiProperties);
        UUID requestId = UUID.randomUUID();
        UUID messageId = agentRepository.addMessage(
                conversation.id(), "USER", "从图书馆到体育与健康中心，轮椅怎么走？", requestId);
        UUID invocationId = agentRepository.startInvocation(
                "demo_user", conversation.id(), messageId, requestId, aiProperties);
        AgentExecutionContext.TurnContext context = new AgentExecutionContext.TurnContext(
                "demo_user", DEMO_DATASET_ID, conversation.id(), invocationId, (event, data) -> { });

        try {
            AgentExecutionContext.set(context);
            AgentDtos.RouteToolResult result = controlledAgentTools.calculateAccessibleRoutes(
                    "图书馆", "体育与健康中心", List.of(), "WHEELCHAIR", false, true);

            assertThat(result.status()).isEqualTo("ROUTES_READY");
            assertThat(result.routes()).hasSize(3);
            assertThat(result.comparison()).isNotNull();
            assertThat(context.routeResult()).isNotNull();
            assertThat(context.routeResult().routes()).allSatisfy(route -> assertThat(route.stairsCount()).isZero());
            assertThat(context.comparison().recommendedProfile()).isNotBlank();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_tool_call_log WHERE invocation_id=?", Integer.class, invocationId))
                    .isEqualTo(1);
        } finally {
            AgentExecutionContext.clear();
        }
    }

    @Test
    void controlledAgentToolMustPlanWaypointsInOneCallAndReturnMergedGeometry() {
        AgentDtos.ConversationView conversation = agentRepository.createConversation(
                "demo_user", "途经点路线测试", aiProperties);
        UUID requestId = UUID.randomUUID();
        UUID messageId = agentRepository.addMessage(
                conversation.id(), "USER", "从图书馆经过中央广场北到体育与健康中心", requestId);
        UUID invocationId = agentRepository.startInvocation(
                "demo_user", conversation.id(), messageId, requestId, aiProperties);
        AgentExecutionContext.TurnContext context = new AgentExecutionContext.TurnContext(
                "demo_user", DEMO_DATASET_ID, conversation.id(), invocationId, (event, data) -> { });

        try {
            AgentExecutionContext.set(context);
            AgentDtos.RouteToolResult result = controlledAgentTools.calculateAccessibleRoutes(
                    "图书馆", "体育与健康中心", List.of("中央广场北"),
                    "WALKING", false, true);

            assertThat(result.status()).isEqualTo("ROUTES_READY");
            assertThat(result.waypointNames()).containsExactly("中央广场北");
            assertThat(result.segments()).hasSize(2);
            assertThat(result.routes()).hasSize(3);
            assertThat(context.routeResult().routes()).isNotEmpty().allSatisfy(route ->
                    assertThat(route.geometry().path("coordinates").size()).isGreaterThan(2));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_tool_call_log WHERE invocation_id=?", Integer.class, invocationId))
                    .isEqualTo(1);
        } finally {
            AgentExecutionContext.clear();
        }
    }

    @Test
    void controlledAgentToolMustRejectNineWaypoints() {
        assertThatThrownBy(() -> controlledAgentTools.calculateAccessibleRoutes(
                "图书馆", "体育与健康中心", java.util.Collections.nCopies(9, "中央广场北"),
                "WALKING", false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("途经点最多支持 8 个");
    }

    @Test
    void agentBarrierToolMustCreateDraftWithoutWritingFormalReport() {
        AgentDtos.ConversationView conversation = agentRepository.createConversation(
                "demo_user", "障碍草稿测试", aiProperties);
        int reportsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM barrier_report", Integer.class);
        BusinessDtos.BarrierSubmitRequest payload = new BusinessDtos.BarrierSubmitRequest(
                DEMO_DATASET_ID, "图书馆附近积水", "WATERLOGGING", "轮椅通行困难", 12,
                112.9365, 28.1785);

        AgentDtos.BarrierDraftView draft;
        try {
            AgentExecutionContext.set(new AgentExecutionContext.TurnContext(
                    "demo_user", DEMO_DATASET_ID, conversation.id(), UUID.randomUUID(), (event, data) -> { }));
            draft = agentTools.createBarrierReportDraft(conversation.id(), payload);
        } finally {
            AgentExecutionContext.clear();
        }

        assertThat(draft.status()).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM barrier_report", Integer.class))
                .isEqualTo(reportsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_action_draft WHERE id=? AND status='PENDING'", Integer.class, draft.id()))
                .isEqualTo(1);
    }

    @Test
    void userMustNotReadAgentInvocationLogs() throws Exception {
        mockMvc.perform(get("/api/admin/agent/invocations").with(user("demo_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void agentSseMustCompleteAfterAuthenticatedAsyncRedispatch() throws Exception {
        AgentDtos.ConversationView conversation = agentRepository.createConversation(
                "demo_user", "SSE 集成测试", aiProperties);
        String body = """
                {
                  "datasetId":"20000000-0000-0000-0000-000000000001",
                  "content":"从图书馆到体育与健康中心，轮椅怎么走？",
                  "mobilityMode":"WHEELCHAIR"
                }
                """;

        MvcResult started = mockMvc.perform(post("/api/agent/conversations/{id}/messages/stream", conversation.id())
                        .with(user("demo_user").roles("USER"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:route_result")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void shouldCalculateSixAnalyticsViewsWithDocumentedBuildingWeights() {
        AnalyticsDtos.AnalyticsOverview overview = analyticsService.overview(analyticsFilter());

        assertThat(overview.summary().buildings()).isEqualTo(5);
        assertThat(overview.summary().facilities()).isEqualTo(15);
        assertThat(overview.buildingScores()).hasSize(5).allSatisfy(score -> {
            assertThat(score.score()).isBetween(0.0, 100.0);
            assertThat(score.entranceScore() + score.elevatorScore() + score.toiletScore()
                    + score.roadScore() + score.barrierScore() + score.completenessScore())
                    .isCloseTo(score.score(), org.assertj.core.data.Offset.offset(0.3));
        });
        assertThat(overview.facilityDistribution()).isNotEmpty();
        assertThat(overview.barrierTrend()).hasSize(30);
        assertThat(overview.confidenceDistribution()).extracting(AnalyticsDtos.ConfidenceDistribution::entityType)
                .containsExactly("BUILDING", "ENTRANCE", "EDGE", "FACILITY", "BARRIER");
    }

    @Test
    void shouldAggregateRealRouteHistoryAndApplyAnalyticsFilters() {
        RoutingDtos.RoutePlanRequest request = routeRequest(
                NODE_2, NODE_3, RoutingDtos.MobilityMode.WALKING, RoutingDtos.RoutePreferences.defaults());
        RoutingDtos.RoutePlanResponse result = routingService.plan(request);
        businessService.recordHistory("demo_user", request, result);

        AnalyticsDtos.AnalyticsOverview overview = analyticsService.overview(new AnalyticsFilter(
                DEMO_DATASET_ID, null, LocalDate.now().minusDays(1), LocalDate.now(),
                "RAMP", null, "UNKNOWN"));

        assertThat(overview.facilityDistribution()).allSatisfy(item -> assertThat(item.key()).isEqualTo("RAMP"));
        assertThat(overview.routeRisks()).isNotEmpty().allSatisfy(item -> {
            assertThat(item.sampleCount()).isPositive();
            assertThat(item.averageDistanceM()).isNotNegative();
        });
        assertThat(overview.confidenceDistribution()).allSatisfy(item -> {
            assertThat(item.high()).isZero();
            assertThat(item.medium()).isZero();
            assertThat(item.low()).isZero();
        });
    }

    @Test
    void shouldExportSafeUtf8CsvAndUseRuleSummaryWhenAiDisabled() {
        UUID buildingId = UUID.fromString("31000000-0000-0000-0000-000000000099");
        jdbcTemplate.update(
                """
                INSERT INTO building(id,dataset_id,external_id,name,category,data_source,confidence_level,geom)
                VALUES (?,?,'BLD-CSV','=1+1','OTHER','UNVERIFIED','UNKNOWN',
                  ST_GeomFromText('POLYGON((112.939 28.179,112.9391 28.179,112.9391 28.1791,112.939 28.1791,112.939 28.179))',0))
                """, buildingId, DEMO_DATASET_ID);
        AnalyticsFilter filter = new AnalyticsFilter(DEMO_DATASET_ID, buildingId,
                LocalDate.now().minusDays(29), LocalDate.now(), null, null, null);

        String csv = new String(analyticsService.csv(filter), StandardCharsets.UTF_8);
        AnalyticsDtos.GovernanceSummary summary = analyticsService.governanceSummary(filter);

        assertThat(csv).startsWith("\uFEFF").contains("'=1+1").contains("建筑评分");
        assertThat(summary.aiEnabled()).isFalse();
        assertThat(summary.generatedBy()).isEqualTo("RULES");
        assertThat(summary.text()).isNotBlank();
    }

    @Test
    void analyticsApiMustRequireAdminAndRejectInvalidDateRange() throws Exception {
        String url = "/api/admin/analytics/overview?datasetId=" + DEMO_DATASET_ID;
        mockMvc.perform(get(url).with(user("demo_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(user("demo_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildingScores.length()").value(5));
        mockMvc.perform(get(url + "&from=2026-08-20&to=2026-08-01")
                        .with(user("demo_admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("开始日期不能晚于结束日期"));
    }

    private AnalyticsFilter analyticsFilter() {
        return new AnalyticsFilter(DEMO_DATASET_ID, null, LocalDate.now().minusDays(29),
                LocalDate.now(), null, null, null);
    }

    private void assertDatasetEntityCount(String table, int expected) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE dataset_id=?",
                Integer.class, SCHOOL_DATASET_ID)).isEqualTo(expected);
    }

    @Test
    void shouldListPreviewAndRestoreFormalGeoJsonBackup() {
        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("RST-N-01", "恢复节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        JsonNode payload = mapDataService.exportGeoJson(SCHOOL_DATASET_ID);
        jdbcTemplate.update(
                "DELETE FROM route_node WHERE dataset_id=? AND external_id='RST-N-01'", SCHOOL_DATASET_ID);

        MapDtos.ImportPreview importPreview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        MapDtos.ImportApplyResult applied = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "KEEP_TARGET",
                        importPreview.payloadFingerprint(), importPreview.targetFingerprint()),
                "demo_admin");

        List<MapDtos.GeoJsonBackupView> backups = mapDataService.listGeoJsonBackups(SCHOOL_DATASET_ID);
        assertThat(backups).extracting(MapDtos.GeoJsonBackupView::id).contains(applied.backupId());
        // 备份保存的是导入前快照：节点在导入前已被删除，因此快照不含节点
        assertThat(backups.getFirst().objectCounts().get("NODE")).isZero();

        MapDtos.RestorePreview restorePreview =
                mapDataService.previewGeoJsonRestore(SCHOOL_DATASET_ID, applied.backupId());
        assertThat(restorePreview.snapshotCounts().get("NODE")).isZero();
        assertThat(restorePreview.toDeleteCounts().get("NODE")).isEqualTo(1);

        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("RST-EXTRA", "多余节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695600, 31.535000)), "demo_admin");
        MapDtos.RestorePreview changed =
                mapDataService.previewGeoJsonRestore(SCHOOL_DATASET_ID, applied.backupId());
        assertThat(changed.currentFingerprint()).isNotEqualTo(restorePreview.currentFingerprint());
        assertThat(changed.toDeleteCounts().get("NODE")).isEqualTo(2);

        MapDtos.RestoreResult result = mapDataService.restoreGeoJsonBackup(
                SCHOOL_DATASET_ID, applied.backupId(),
                new MapDtos.RestoreApplyRequest(changed.currentFingerprint()), "demo_admin");

        assertThat(result.deleted().get("NODE")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_node WHERE dataset_id=?", Integer.class, SCHOOL_DATASET_ID))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='GEOJSON_BACKUP_RESTORE'", Integer.class))
                .isPositive();
    }

    @Test
    void shouldRejectRestoreWhenDatasetChangedAfterPreview() {
        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("RST-N-02", "恢复节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        JsonNode payload = mapDataService.exportGeoJson(SCHOOL_DATASET_ID);
        jdbcTemplate.update(
                "DELETE FROM route_node WHERE dataset_id=? AND external_id='RST-N-02'", SCHOOL_DATASET_ID);
        MapDtos.ImportPreview importPreview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        MapDtos.ImportApplyResult applied = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "KEEP_TARGET",
                        importPreview.payloadFingerprint(), importPreview.targetFingerprint()),
                "demo_admin");

        MapDtos.RestorePreview restorePreview =
                mapDataService.previewGeoJsonRestore(SCHOOL_DATASET_ID, applied.backupId());
        mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("RST-EXTRA-2", "多余节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695700, 31.535000)), "demo_admin");

        assertThatThrownBy(() -> mapDataService.restoreGeoJsonBackup(
                SCHOOL_DATASET_ID, applied.backupId(),
                new MapDtos.RestoreApplyRequest(restorePreview.currentFingerprint()), "demo_admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void shouldKeepFacilityReferencedByRatingDuringRestore() {
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                (com.fasterxml.jackson.databind.node.ObjectNode) mapDataService.exportGeoJson(DEMO_DATASET_ID);
        payload.put("datasetId", SCHOOL_DATASET_ID.toString());
        payload.put("datasetCode", "SCHOOL_EXAMPLE_V1");
        MapDtos.ImportPreview importPreview = mapDataService.previewGeoJson(SCHOOL_DATASET_ID, payload);
        MapDtos.ImportApplyResult applied = mapDataService.applyGeoJson(
                SCHOOL_DATASET_ID,
                new MapDtos.ImportApplyRequest(payload, "OVERWRITE",
                        importPreview.payloadFingerprint(), importPreview.targetFingerprint()),
                "demo_admin");

        UUID facilityId = jdbcTemplate.queryForObject(
                "SELECT id FROM accessible_facility WHERE dataset_id=? AND external_id='FAC-01'",
                UUID.class, SCHOOL_DATASET_ID);
        jdbcTemplate.update(
                """
                INSERT INTO facility_rating(dataset_id, facility_id, user_id, rating)
                SELECT ?, ?, id, 5 FROM app_user WHERE username='demo_user'
                """,
                SCHOOL_DATASET_ID, facilityId);

        MapDtos.RestorePreview restorePreview =
                mapDataService.previewGeoJsonRestore(SCHOOL_DATASET_ID, applied.backupId());
        assertThat(restorePreview.keptBusinessCounts().get("FACILITY")).isEqualTo(1);
        assertThat(restorePreview.warnings()).anyMatch(message -> message.contains("保留"));

        MapDtos.RestoreResult result = mapDataService.restoreGeoJsonBackup(
                SCHOOL_DATASET_ID, applied.backupId(),
                new MapDtos.RestoreApplyRequest(restorePreview.currentFingerprint()), "demo_admin");

        assertThat(result.keptBusiness().get("FACILITY")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accessible_facility WHERE dataset_id=?",
                Integer.class, SCHOOL_DATASET_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building WHERE dataset_id=?",
                Integer.class, SCHOOL_DATASET_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_node WHERE dataset_id=?",
                Integer.class, SCHOOL_DATASET_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM facility_rating WHERE dataset_id=?",
                Integer.class, SCHOOL_DATASET_ID)).isEqualTo(1);
    }

    @Test
    void shouldDeleteNodeAndConnectedEdges() {
        UUID first = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("DEL-N-A", "删除测试A", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        UUID second = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("DEL-N-B", "删除测试B", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.696359, 31.535827)), "demo_admin");
        UUID edge = mapDataService.saveEdge(SCHOOL_DATASET_ID, null,
                new MapDtos.EdgeRequest("DEL-E-1", "删除测试路", first, second, BigDecimal.ONE,
                        "FLAT", false, 0, "STANDARD", "ASPHALT", "HIGH", true,
                        "ACTIVE", "LOW", List.of()), "demo_admin");

        mapDataService.deleteMapObject("nodes", SCHOOL_DATASET_ID, first, "demo_admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_node WHERE id=?", Integer.class, first)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_edge WHERE id=?", Integer.class, edge)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action='NODE_DELETE'", Integer.class)).isPositive();
    }

    @Test
    void shouldDeleteNodeAndRemoveReferencedRouteHistory() {
        UUID node = mapDataService.saveNode(SCHOOL_DATASET_ID, null,
                new MapDtos.NodeRequest("DEL-N-H", "历史引用节点", "INTERSECTION", true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        UUID historyId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO route_history(
                    id,user_id,dataset_id,start_node_id,end_node_id,mobility_mode,travel_period,request_json,result_json)
                SELECT ?, u.id, ?, ?, ?, 'WALKING','DAY','{}','{}' FROM app_user u WHERE username='demo_user'
                """,
                historyId, SCHOOL_DATASET_ID, node, node);

        mapDataService.deleteMapObject("nodes", SCHOOL_DATASET_ID, node, "demo_admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_history WHERE id=?", Integer.class, historyId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_node WHERE id=?", Integer.class, node)).isZero();
    }

    @Test
    void shouldDeleteBuildingAndCascadeEntrancesFacilitiesAndRatings() {
        UUID building = mapDataService.createBuilding(SCHOOL_DATASET_ID,
                new MapDtos.BuildingRequest("DEL-B-01", "删除测试建筑", "TEACHING", null, true,
                        new MapDtos.Coordinate(104.695359, 31.534827)), "demo_admin");
        UUID entrance = mapDataService.createEntrance(SCHOOL_DATASET_ID,
                new MapDtos.EntranceRequest(building, "DEL-EN-01", "删除测试入口", true,
                        "ACCESSIBLE", "OPEN", true,
                        new MapDtos.Coordinate(104.695459, 31.534827)), "demo_admin");
        UUID facility = mapDataService.createFacility(SCHOOL_DATASET_ID,
                new MapDtos.FacilityRequest(building, "DEL-F-01", "删除测试设施", "ELEVATOR",
                        "1F", "OPEN", null, true,
                        new MapDtos.Coordinate(104.695559, 31.534827)), "demo_admin");
        jdbcTemplate.update(
                """
                INSERT INTO facility_rating(dataset_id, facility_id, user_id, rating)
                SELECT ?, ?, id, 5 FROM app_user WHERE username='demo_user'
                """,
                SCHOOL_DATASET_ID, facility);

        mapDataService.deleteMapObject("buildings", SCHOOL_DATASET_ID, building, "demo_admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building WHERE id=?", Integer.class, building)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building_entrance WHERE id=?", Integer.class, entrance)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accessible_facility WHERE id=?", Integer.class, facility)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM facility_rating WHERE facility_id=?", Integer.class, facility)).isZero();
    }

    private String cookieValue(String setCookie, String name) {
        assertThat(setCookie).isNotBlank();
        String prefix = name + "=";
        return java.util.Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
    }

    private RoutingDtos.RoutePlanRequest routeRequest(
            UUID start,
            UUID end,
            RoutingDtos.MobilityMode mode,
            RoutingDtos.RoutePreferences preferences) {
        return new RoutingDtos.RoutePlanRequest(
                DEMO_DATASET_ID,
                start,
                end,
                mode,
                RoutingDtos.TravelPeriod.DAY,
                preferences);
    }
}
