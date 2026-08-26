package com.gary.bilibili.user.api;

import com.gary.bilibili.user.BilibiliUserApplication;
import com.gary.bilibili.user.constant.UserRedisConstant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(
        classes = BilibiliUserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiIT {

    private static final String PHONE = "13800009999";
    private static final String USERNAME = "api_e2e_user";
    private static final String PASSWORD = "ApiE2e123";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili")
            .withUsername("bilibili")
            .withPassword("bilibili_test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldMigrateRegisterLoginAndReadCurrentUserThroughHttpApi() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(migrationCount).isEqualTo(4);

        HttpHeaders operationsHeaders = jsonHeaders();
        operationsHeaders.set("X-Admin-Token", "change-me-in-production");
        ResponseEntity<JsonNode> failedMessages = restTemplate.exchange(
                "/api/admin/mq/failures",
                HttpMethod.GET,
                new HttpEntity<>(operationsHeaders),
                JsonNode.class);
        assertThat(failedMessages.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(failedMessages.getBody()).isNotNull();
        assertThat(failedMessages.getBody().at("/data/total").asLong()).isZero();
        assertThat(failedMessages.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(failedMessages.getHeaders().getFirst("X-Trace-Id")).matches("[0-9a-f]{16,32}");

        JsonNode sendCode = post("/api/user/code", Map.of("phone", PHONE));
        assertThat(sendCode.path("success").asBoolean()).isTrue();

        String code = redisTemplate.opsForValue().get(UserRedisConstant.LOGIN_CODE_PREFIX + PHONE);
        assertThat(code).matches("\\d{6}");

        ResponseEntity<JsonNode> registerResponse = postResponse("/api/user/register", Map.of(
                "phone", PHONE,
                "code", code,
                "username", USERNAME,
                "password", PASSWORD,
                "terminal", "api-e2e"));
        JsonNode register = registerResponse.getBody();
        assertThat(register).isNotNull();
        assertThat(register.path("success").asBoolean()).isTrue();
        String token = register.at("/data/token").asText();
        assertThat(token).isNotBlank();
        assertThat(register.at("/data/expiresIn").asLong()).isEqualTo(900);
        assertThat(register.toString()).doesNotContain("bili_refresh");
        String originalRefreshCookie = refreshCookie(registerResponse);
        assertThat(registerResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/user");
        assertThat(registerResponse.getHeaders().get(HttpHeaders.SET_COOKIE))
                .allSatisfy(cookie -> assertThat(cookie).doesNotStartWith("satoken="));

        HttpHeaders authenticatedHeaders = jsonHeaders();
        authenticatedHeaders.set("satoken", token);
        ResponseEntity<JsonNode> currentUserResponse = restTemplate.exchange(
                "/api/user/me",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);
        assertThat(currentUserResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(currentUserResponse.getBody()).isNotNull();
        assertThat(currentUserResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(currentUserResponse.getBody().at("/data/username").asText()).isEqualTo(USERNAME);

        HttpHeaders refreshHeaders = jsonHeaders();
        refreshHeaders.set(HttpHeaders.COOKIE, originalRefreshCookie);
        ResponseEntity<JsonNode> refreshResponse = restTemplate.exchange(
                "/api/user/refresh",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), refreshHeaders),
                JsonNode.class);
        assertThat(refreshResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(refreshResponse.getBody().at("/data/token").asText()).isNotEqualTo(token);
        String rotatedRefreshCookie = refreshCookie(refreshResponse);
        assertThat(rotatedRefreshCookie).isNotEqualTo(originalRefreshCookie);

        ResponseEntity<JsonNode> replayResponse = restTemplate.exchange(
                "/api/user/refresh",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), refreshHeaders),
                JsonNode.class);
        assertThat(replayResponse.getStatusCode().value()).isEqualTo(401);
        assertThat(replayResponse.getBody()).isNotNull();
        assertThat(replayResponse.getBody().path("success").asBoolean()).isFalse();

        HttpHeaders rotatedHeaders = jsonHeaders();
        rotatedHeaders.set(HttpHeaders.COOKIE, rotatedRefreshCookie);
        ResponseEntity<JsonNode> logoutResponse = restTemplate.exchange(
                "/api/user/logout",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), rotatedHeaders),
                JsonNode.class);
        assertThat(logoutResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(logoutResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("bili_refresh=")
                .contains("Max-Age=0")
                .contains("HttpOnly");

        JsonNode login = post("/api/user/login", Map.of(
                "username", USERNAME,
                "password", PASSWORD,
                "terminal", "api-e2e"));
        assertThat(login.path("success").asBoolean()).isTrue();
        assertThat(login.at("/data/token").asText()).isNotBlank();

        ResponseEntity<String> metrics = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(metrics.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(metrics.getBody())
                .contains("bilibili_api_duration_seconds_count")
                .contains("api=\"user_login\"")
                .contains("api=\"user_register\"");
    }

    private JsonNode post(String path, Map<String, String> body) {
        return postResponse(path, body).getBody();
    }

    private ResponseEntity<JsonNode> postResponse(String path, Map<String, String> body) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response;
    }

    private String refreshCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
