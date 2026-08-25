package com.gary.bilibili.user.service;

import com.gary.bilibili.user.config.AuthSessionProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieServiceTest {

    @Test
    void shouldWriteReadAndClearHardenedCookie() {
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.getCookie().setSecure(true);
        RefreshCookieService service = new RefreshCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(response, new RefreshSessionService.IssuedRefreshToken("refresh-secret", 3600));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains("bili_refresh=refresh-secret")
                .contains("Path=/api/user")
                .contains("Max-Age=3600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("unrelated", "x"), new Cookie("bili_refresh", "refresh-secret"));
        assertThat(service.read(request)).isEqualTo("refresh-secret");

        MockHttpServletResponse clearResponse = new MockHttpServletResponse();
        service.clear(clearResponse);
        assertThat(clearResponse.getHeader("Set-Cookie"))
                .contains("bili_refresh=")
                .contains("Max-Age=0")
                .contains("HttpOnly");
    }
}
