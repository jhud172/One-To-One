package uk.ac.cf._5.group14.One_To_One.Security;

public final class SafeHttpUrl {
    private SafeHttpUrl() {}

    public static boolean isSafe(String url) {
        if (url == null || url.isBlank()) return true;
        if (url.length() > 500) return false;
        try {
            var uri = java.net.URI.create(url.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
    }
}
