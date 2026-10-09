package uk.ac.cf._5.group14.One_To_One.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses navigation action tags from AI-generated text and validates them against a whitelist.
 * <p>
 * The AI is instructed to embed tags of the form {@code [NAV:/path:Label]} in its replies.
 * This parser extracts those tags, checks the URL against an allowed-list of regular user
 * pages, and returns both the cleaned reply text and the validated nav actions.
 * Admin and system routes are intentionally absent from the whitelist so users cannot be
 * directed to privileged areas via AI-generated links.
 */
public class ChatNavParser {

    private ChatNavParser() {}

    /** Pattern matching: [NAV:/some/path:Human Readable Label] */
    private static final Pattern NAV_PATTERN =
            Pattern.compile("\\[NAV:(/[^:]*):([^\\]]+)\\]");

    /**
     * Allowed URL prefixes – regular user-facing pages only.
     * No admin, platform-admin, gym-admin, actuator, or internal API paths.
     */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/dashboard",
            "/calendar",
            "/vault",
            "/levels",
            "/profile",
            "/explore",
            "/inbox",
            "/notes",
            "/health-records",
            "/nutrition",
            "/goals",
            "/schedule",
            "/workouts",
            "/client/trainers",
            "/pricing",
            "/about",
            "/faq",
            "/login",
            "/signup",
            "/merch",
            "/chat"
    );

    public record NavAction(String url, String label) {}

    public record ParseResult(String cleanText, List<NavAction> navActions) {}

    /**
     * Extracts {@code [NAV:…]} tags from {@code text}, validates each URL against the
     * whitelist, and returns the cleaned text together with the validated actions.
     */
    public static ParseResult parse(String text) {
        if (text == null) {
            return new ParseResult("", List.of());
        }

        List<NavAction> navActions = new ArrayList<>();
        Matcher m = NAV_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String url   = m.group(1).trim();
            String label = m.group(2).trim();

            if (isAllowedUrl(url) && !label.isBlank()) {
                navActions.add(new NavAction(url, label));
            }
            // Always remove the tag from the reply text (even if the URL was rejected)
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);

        return new ParseResult(sb.toString().trim(), navActions);
    }

    /** Returns {@code true} only if {@code url} is on the whitelist of user-facing pages. */
    private static boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("/"))         return false;
        // Reject path traversal attempts
        if (url.contains("..") || url.contains("//")) return false;
        String lower = url.toLowerCase();
        return ALLOWED_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
