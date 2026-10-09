package uk.ac.cf._5.group14.One_To_One.Security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.InvalidSessionStrategy;

public class CustomInvalidSessionStrategy implements InvalidSessionStrategy {

    private final RequestCache requestCache;
    private final RedirectStrategy redirectStrategy;
    private final String destination;

    public CustomInvalidSessionStrategy() {
        this(new HttpSessionRequestCache(), new DefaultRedirectStrategy(), "/?expired=1");
    }

    CustomInvalidSessionStrategy(RequestCache requestCache, RedirectStrategy redirectStrategy, String destination) {
        this.requestCache = requestCache;
        this.redirectStrategy = redirectStrategy;
        this.destination = destination;
    }

    @Override
    public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String uri = request.getRequestURI();

        // For API/AJAX/background requests, do NOT redirect to the login page and do NOT cache the request.
        // Returning 401 prevents "login page HTML" from being served to JS callers and avoids redirecting
        // back to JSON endpoints after login.
        if (isNonNavigationalRequest(request, uri)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Preserve the original URL so that after the user logs in they return to the page they clicked.
        // Only cache navigational GET requests, and skip login/error/static resources to avoid loops.
        if (uri != null && shouldCacheRequest(request, uri)) {
            requestCache.saveRequest(request, response);
        }

        redirectStrategy.sendRedirect(request, response, destination);
    }

    private static boolean shouldCacheRequest(HttpServletRequest request, String uri) {
        return isNavigationalGet(request) && !isIgnorablePath(uri);
    }

    private static boolean isNavigationalGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        // Modern browsers provide these and they are reliable for distinguishing page loads from fetch/XHR.
        String secFetchMode = request.getHeader("Sec-Fetch-Mode");
        if (secFetchMode != null && !"navigate".equalsIgnoreCase(secFetchMode)) {
            return false;
        }

        String secFetchDest = request.getHeader("Sec-Fetch-Dest");
        if (secFetchDest != null && !"document".equalsIgnoreCase(secFetchDest)) {
            return false;
        }

        // Legacy XHR signal.
        String requestedWith = request.getHeader("X-Requested-With");
        if (requestedWith != null && !requestedWith.isBlank()) {
            return false;
        }

        String accept = request.getHeader("Accept");
        return accept == null
                || accept.contains("text/html")
                || accept.contains("application/xhtml+xml")
                || accept.contains("*/*");
    }

    private static boolean isNonNavigationalRequest(HttpServletRequest request, String uri) {
        if (uri != null && (uri.startsWith("/chat/api")
                || uri.startsWith("/chat/history")
                || uri.startsWith("/chat/clear"))) {
            return true;
        }

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return true;
        }

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }

        String secFetchMode = request.getHeader("Sec-Fetch-Mode");
        if (secFetchMode != null && !"navigate".equalsIgnoreCase(secFetchMode)) {
            return true;
        }

        String secFetchDest = request.getHeader("Sec-Fetch-Dest");
        if (secFetchDest != null && !"document".equalsIgnoreCase(secFetchDest)) {
            return true;
        }

        return false;
    }

    private static boolean isIgnorablePath(String uri) {
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/img/")
                || uri.startsWith("/models/one-to-one/")
                || uri.startsWith("/static/")
                || uri.startsWith("/webjars/")
                || uri.equals("/favicon.ico")
                || uri.startsWith("/login")
                || uri.startsWith("/error")
                || uri.startsWith("/access-denied")
                || uri.startsWith("/signup")
                || uri.startsWith("/chat/api")
                || uri.startsWith("/chat/history")
                || uri.startsWith("/chat/clear");
    }
}
