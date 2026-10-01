package com.guyi.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guyi.access.service.MaintenanceService;
import com.guyi.access.util.AesUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end API test: boots the real application context (filters, security chain, JPA, scheduler)
 * against an in-memory database and drives the actual HTTP endpoints.
 *
 * <p>It exists because the previous verification stopped at "compiles + unit tests": nothing proved
 * that the Spring context starts, that the JPQL is valid, or that the security chain behaves. Each
 * step below pins down a defect found during review, so a regression fails the build.
 *
 * <p>Rate limits are raised here so they do not mask the lockout behaviour under test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=0123456789abcdef0123456789abcdef",
        "app.api-token=test-api-token",
        "app.rate-limit.login-max-requests=100",
        "app.rate-limit.max-requests=1000",
        "app.login.max-failed-attempts=5",
        "app.login.lock-minutes=15",
        // NON_KEYWORDS=VALUE: H2 reserves VALUE, but "value" is a legitimate MySQL column name (and
        // part of the schema inherited from the PHP version), so the test database relaxes it instead
        // of the production code being renamed.
        "spring.datasource.url=jdbc:h2:mem:guyi;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndApiTest {

    @LocalServerPort
    private int port;

    /** Called directly to exercise the scheduled housekeeping without waiting for its cron. */
    @Autowired
    private MaintenanceService maintenanceService;

    private RestTemplate rest;
    private String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    private static String token;
    private static String appKey;
    private static Integer appId;
    private static String cardCode;

    @BeforeEach
    void setUp() {
        // Streaming must be off: the JDK client cannot read the body of a 401 response to a POST with
        // a streamed body ("cannot retry due to server authentication, in streaming mode"), and this
        // suite deliberately asserts on 401/403 bodies.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        this.rest = new RestTemplate(factory);
        // 4xx/5xx are part of what this suite asserts on, so they must come back as responses rather
        // than exceptions
        this.rest.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // never called: hasError always returns false
            }
        });
        this.baseUrl = "http://localhost:" + port;
    }

    // ---------------------------------------------------------------- helpers

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode post(String path, String body, HttpHeaders headers) throws Exception {
        ResponseEntity<String> response = rest.exchange(baseUrl + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "POST " + path + " returned " + response.getStatusCode() + ": " + response.getBody());
        return mapper.readTree(response.getBody());
    }

    private JsonNode get(String path, HttpHeaders headers) throws Exception {
        ResponseEntity<String> response = rest.exchange(baseUrl + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "GET " + path + " returned " + response.getStatusCode() + ": " + response.getBody());
        return mapper.readTree(response.getBody());
    }

    // ---------------------------------------------------------------- steps

    @Test
    @Order(1)
    void healthReportsUpAndNotInstalled() throws Exception {
        JsonNode body = get("/api/health", jsonHeaders());

        assertEquals("UP", body.path("data").path("status").asText(), "database must be reachable");
        assertFalse(body.path("data").path("installed").asBoolean(),
                "a fresh database must report installed=false so the UI can route to the wizard");
    }

    @Test
    @Order(2)
    void installCreatesAdministrator() throws Exception {
        JsonNode body = post("/api/admin/install",
                "{\"admin_password\":\"secret123\"}", jsonHeaders());

        assertEquals(200, body.path("code").asInt(), body.toString());
        token = body.path("data").path("token").asText();
        assertFalse(token.isEmpty(), "install must return a JWT");
    }

    @Test
    @Order(3)
    void protectedEndpointAnswers401WithoutToken() {
        ResponseEntity<String> response = rest.exchange(baseUrl + "/api/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);

        // Was 403 before, which the admin frontend never mapped to a logout
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(4)
    void createsAppAndGeneratesStandardCardType() throws Exception {
        JsonNode app = post("/api/admin/apps",
                "{\"app_name\":\"E2E App\",\"app_version\":\"1.0\",\"app_notes\":\"\"}", authHeaders());
        appKey = app.path("data").path("app_key").asText();
        assertEquals(64, appKey.length(), "app_key must be 64 hex chars (it is the AES key)");

        JsonNode apps = get("/api/admin/apps", authHeaders());
        appId = apps.path("data").get(0).path("id").asInt();
        assertTrue(appId > 0);

        // type=day without custom_hours: this used to fail with 400 on every standard card type
        JsonNode generated = post("/api/admin/cards/generate",
                "{\"app_id\":" + appId + ",\"type\":\"day\",\"num\":3,\"pre\":\"E2E\",\"note\":\"e2e\"}",
                authHeaders());
        assertEquals(200, generated.path("code").asInt(), generated.toString());
        assertEquals(3, generated.path("data").path("cards").size());
        cardCode = generated.path("data").path("cards").get(0).asText();
        assertTrue(cardCode.startsWith("E2E"));
    }

    @Test
    @Order(5)
    void verifyActivatesCardAndAnswersPhpCompatiblePayload() throws Exception {
        JsonNode response = post("/api/verify",
                "{\"app_key\":\"" + appKey + "\",\"card_code\":\"" + cardCode + "\",\"device_hash\":\"device-abc\"}",
                jsonHeaders());

        // api_encrypt defaults to on and app_key is 64 chars, so the body must be encrypted
        String encrypted = response.path("encrypted_data").asText();
        assertFalse(encrypted.isEmpty(), "encrypted response expected, got: " + response);

        String plaintext = AesUtil.decrypt(encrypted, appKey);
        JsonNode payload = mapper.readTree(plaintext);
        assertEquals(200, payload.path("code").asInt(), plaintext);
        assertFalse(payload.path("data").path("expire_time").asText().isEmpty(),
                "activation must return an expiry time: " + plaintext);
    }

    @Test
    @Order(6)
    void dashboardReportsConsistentMetrics() throws Exception {
        JsonNode stats = get("/api/admin/dashboard", authHeaders()).path("data").path("stats");

        assertEquals(3, stats.path("total").asInt());
        assertEquals(2, stats.path("unused").asInt(), "one of the three cards is now activated");
        assertEquals(1, stats.path("used").asInt());
        assertEquals(0, stats.path("banned").asInt());
        assertEquals(0, stats.path("expired").asInt());
        assertEquals(1, stats.path("apps").asInt());
        assertTrue(stats.path("active").asInt() >= 1, "the activated device session must be counted");
    }

    @Test
    @Order(7)
    void cardSearchAppliesFiltersAndSort() throws Exception {
        // Exercises the sentinel-based JPQL (status/appId/type + keyword) that is only validated at runtime
        JsonNode result = get("/api/admin/cards?q=E2E&status=1&type=day&sort=expire_asc&page=0&limit=10",
                authHeaders());

        assertEquals(1, result.path("data").path("total").asInt(), result.toString());
    }

    @Test
    @Order(8)
    void exportOmitsAdministratorCredentialsByDefault() throws Exception {
        ResponseEntity<String> response = rest.exchange(baseUrl + "/api/admin/system/export", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode exported = mapper.readTree(response.getBody());
        assertTrue(exported.has("applications"), "applications must be exported");
        assertFalse(exported.has("admin"), "password hashes must not be exported by default");
    }

    @Test
    @Order(9)
    void blacklistActionRequiresAppKeyAndOwnedDevice() throws Exception {
        // No app_key: anonymous callers must not be able to blacklist arbitrary devices
        ResponseEntity<String> anonymous = rest.exchange(baseUrl + "/api/verify", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"ban_machine\",\"device_hash\":\"victim-device\"}", jsonHeaders()),
                String.class);
        JsonNode body = mapper.readTree(anonymous.getBody());
        assertEquals(403, body.path("code").asInt(), anonymous.getBody());

        // With a valid app_key but a device that does not belong to it, still refused
        ResponseEntity<String> foreign = rest.exchange(baseUrl + "/api/verify", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"ban_machine\",\"app_key\":\"" + appKey
                        + "\",\"device_hash\":\"some-other-device\"}", jsonHeaders()), String.class);
        assertEquals(403, mapper.readTree(foreign.getBody()).path("code").asInt(), foreign.getBody());
    }

    @Test
    @Order(15)
    void repeatedLoginFailuresLockTheAccount() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseEntity<String> response = rest.exchange(baseUrl + "/api/auth/login", HttpMethod.POST,
                    new HttpEntity<>("{\"username\":\"GuYi\",\"password\":\"wrong\"}", jsonHeaders()),
                    String.class);
            JsonNode body = mapper.readTree(response.getBody());
            assertEquals(401, body.path("code").asInt(), "attempt " + attempt + ": " + response.getBody());
        }

        // Even the correct password is refused while the lock is active
        ResponseEntity<String> locked = rest.exchange(baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"GuYi\",\"password\":\"secret123\"}", jsonHeaders()),
                String.class);
        JsonNode lockedBody = mapper.readTree(locked.getBody());
        assertEquals(429, lockedBody.path("code").asInt(), locked.getBody());
    }

    @Test
    @Order(16)
    void logoutRevokesPreviouslyIssuedTokens() throws Exception {
        JsonNode logout = post("/api/auth/logout", "{}", authHeaders());
        assertEquals(200, logout.path("code").asInt());

        // The very token used above must now be rejected
        ResponseEntity<String> afterLogout = rest.exchange(baseUrl + "/api/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode(),
                "a revoked token must no longer authenticate");
    }

    @Test
    @Order(10)
    void batchTimeAdjustmentsApplyAndMaintenanceDeactivatesExpiredSessions() throws Exception {
        int cardId = get("/api/admin/cards?q=" + cardCode + "&page=0&limit=5", authHeaders())
                .path("data").path("cards").get(0).path("id").asInt();

        // +12h then -48h: covers the batch add/sub queries (previously MySQL-only native SQL that
        // could not be executed by this test at all)
        assertEquals(200, post("/api/admin/cards/batch-add-time",
                "{\"ids\":[" + cardId + "],\"hours\":12}", authHeaders()).path("code").asInt());
        assertEquals(200, post("/api/admin/cards/batch-sub-time",
                "{\"ids\":[" + cardId + "],\"hours\":48}", authHeaders()).path("code").asInt());

        // The card is past its expiry now, so the housekeeping pass must deactivate its device session
        maintenanceService.deactivateExpiredDevices();

        JsonNode stats = get("/api/admin/dashboard", authHeaders()).path("data").path("stats");
        assertEquals(1, stats.path("expired").asInt(), "the card must now count as expired");
        assertEquals(0, stats.path("active").asInt(), "its device session must have been deactivated");
    }

    @Test
    @Order(11)
    void expiredCardsArePurgedOnlyWhenTheSettingIsEnabled() throws Exception {
        // Default is off: an expired card must survive the purge pass
        maintenanceService.purgeExpiredCardsIfEnabled();
        assertEquals(3, get("/api/admin/dashboard", authHeaders())
                .path("data").path("stats").path("total").asInt(), "purge is off by default");

        // Enabled explicitly through the settings endpoint, the expired card is removed
        assertEquals(200, post("/api/admin/settings",
                "{\"auto_clean_expired_cards\":\"1\"}", authHeaders()).path("code").asInt());
        maintenanceService.purgeExpiredCardsIfEnabled();

        assertEquals(2, get("/api/admin/dashboard", authHeaders())
                .path("data").path("stats").path("total").asInt(), "the expired card must be gone");
    }

    @Test
    @Order(12)
    void apiTokenProtectsAdministrativeVerifyActions() throws Exception {
        // The admin actions inside /api/verify must still demand the configured api_token
        ResponseEntity<String> response = rest.exchange(baseUrl + "/api/verify", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"generate\",\"app_id\":" + appId + ",\"type\":\"day\",\"num\":1}",
                        jsonHeaders()), String.class);
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals(403, body.path("code").asInt(), response.getBody());

        JsonNode withToken = post("/api/verify",
                "{\"action\":\"generate\",\"app_id\":" + appId + ",\"type\":\"day\",\"num\":1,"
                        + "\"api_token\":\"test-api-token\"}", jsonHeaders());
        assertEquals(200, withToken.path("code").asInt(), withToken.toString());
    }

    @Test
    @Order(13)
    void installIsClosedOnceAnAdministratorExists() throws Exception {
        JsonNode body = post("/api/admin/install", "{\"admin_password\":\"another-pass\"}", jsonHeaders());
        assertEquals(400, body.path("code").asInt(), body.toString());
        assertTrue(body.path("msg").asText().contains("已安装"), body.toString());
    }

    @Test
    @Order(14)
    void unknownApiPathAnswersJsonWithoutLeakingInternals() throws Exception {
        ResponseEntity<String> response = rest.exchange(baseUrl + "/api/admin/does-not-exist", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        // Must not be the HTML whitelabel page: the API answers with its own envelope
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "status=" + response.getStatusCode()
                        + " contentType=" + response.getHeaders().getContentType()
                        + " body=" + response.getBody());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals(404, body.path("code").asInt(), response.getBody());
        assertFalse(body.toString().contains("Exception"), "error bodies must not leak internals");
    }
}
