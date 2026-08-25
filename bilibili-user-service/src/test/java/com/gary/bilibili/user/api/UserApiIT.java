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
        assertThat(migrationCount).isEqualTo(2);

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

        JsonNode register = post("/api/user/register", Map.of(
                "phone", PHONE,
                "code", code,
                "username", USERNAME,
                "password", PASSWORD,
                "terminal", "api-e2e"));
        assertThat(register.path("success").asBoolean()).isTrue();
        String token = register.at("/data/token").asText();
        assertThat(token).isNotBlank();

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
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
