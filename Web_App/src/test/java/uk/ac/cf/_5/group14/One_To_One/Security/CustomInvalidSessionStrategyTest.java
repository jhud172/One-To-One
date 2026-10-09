package uk.ac.cf._5.group14.One_To_One.Security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import static org.assertj.core.api.Assertions.assertThat;

class CustomInvalidSessionStrategyTest {
    @Test
    void expiredNavigationCreatesGuestSessionAndPreservesProtectedDestination() throws Exception {
        var request = new MockHttpServletRequest("GET", "/dashboard");
        request.setRequestedSessionId("stale-session");
        request.setRequestedSessionIdValid(false);
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Sec-Fetch-Dest", "document");
        var response = new MockHttpServletResponse();
        new CustomInvalidSessionStrategy().onInvalidSessionDetected(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(java.net.URI.create(new HttpSessionRequestCache().getRequest(request, response).getRedirectUrl()).getPath())
                .isEqualTo("/dashboard");
    }

    @Test
    void loginWithStaleCookieGetsNewSessionWithoutCachingLogin() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login");
        request.setRequestedSessionId("stale-session");
        request.setRequestedSessionIdValid(false);
        var response = new MockHttpServletResponse();
        new CustomInvalidSessionStrategy().onInvalidSessionDetected(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void backgroundRequestsStillReturnUnauthorizedWithoutRedirectOrSession() throws Exception {
        for (String method : new String[]{"GET", "POST"}) {
            var request = new MockHttpServletRequest(method, "/chat/api/messages");
            request.addHeader("Accept", "application/json");
            var response = new MockHttpServletResponse();
            new CustomInvalidSessionStrategy().onInvalidSessionDetected(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(request.getSession(false)).isNull();
        }
    }
}
