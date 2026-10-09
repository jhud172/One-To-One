package uk.ac.cf._5.group14.One_To_One.ErrorHandling;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom error controller for handling common HTTP errors (404, 500, etc.)
 * Provides user-friendly error pages with helpful navigation options.
 */
@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    @GetMapping("/access-denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        model.addAttribute("statusCode", HttpStatus.FORBIDDEN.value());
        model.addAttribute("requestPath", "/access-denied");
        return "system-views/error/403";
    }

    @RequestMapping(ERROR_PATH)
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        // Get the error status code
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object requestPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = 500;
        if (status != null) {
            try {
                int candidate = Integer.parseInt(status.toString());
                if (candidate >= 400 && candidate <= 599) statusCode = candidate;
            } catch (NumberFormatException ignored) {
                // A malformed dispatcher attribute must not break error recovery.
            }
        }
        response.setStatus(statusCode);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        // Add error information to model for templates
        model.addAttribute("statusCode", statusCode);
        model.addAttribute("requestPath", safePath(requestPath));

        // Log the error for debugging
        logError(statusCode, requestPath);

        // Route to appropriate error template
        switch (statusCode) {
            case 404:
                return "system-views/error/404";
            case 403:
                return "system-views/error/403";
            case 500:
                return "system-views/error/500";
            default:
                return "system-views/error/error";
        }
    }

    /**
     * Log error details for debugging purposes
     */
    private void logError(int statusCode, Object requestPath) {
        String path = safePath(requestPath);
        if (statusCode >= 500) {
            log.error("HTTP error {} for path {}", statusCode, path);
            return;
        }

        if (statusCode == 403) {
            log.info("HTTP error {} for path {}", statusCode, path);
            return;
        }

        log.debug("HTTP error {} for path {}", statusCode, path);
    }

    private String safePath(Object requestPath) {
        String path = requestPath == null ? "Unknown" : requestPath.toString().replaceAll("[\\r\\n]", "");
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) path = path.substring(0, queryStart);
        return path.substring(0, Math.min(1000, path.length()));
    }
}
