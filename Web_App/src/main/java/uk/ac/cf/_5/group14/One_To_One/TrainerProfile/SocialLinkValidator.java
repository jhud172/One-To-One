package uk.ac.cf._5.group14.One_To_One.TrainerProfile;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validator for social media URLs.
 * Ensures URLs use http/https and match expected domain patterns.
 */
@Component
public class SocialLinkValidator {

    private static final Pattern INSTAGRAM_PATTERN = Pattern.compile(
            "^https?://(www\\.)?instagram\\.com/.*", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern TIKTOK_PATTERN = Pattern.compile(
            "^https?://(www\\.)?tiktok\\.com/.*", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "^https?://(www\\.)?(youtube\\.com|youtu\\.be)/.*", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile(
            "^https?://(www\\.)?linkedin\\.com/.*", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern HTTP_HTTPS_PATTERN = Pattern.compile(
            "^https?://.*", Pattern.CASE_INSENSITIVE);

    /**
     * Validate Instagram URL format.
     */
    public boolean isValidInstagramUrl(String url) {
        if (url == null || url.isBlank()) {
            return true; // Empty is valid
        }
        return isValidWebsiteUrl(url) && INSTAGRAM_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Validate TikTok URL format.
     */
    public boolean isValidTikTokUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        return isValidWebsiteUrl(url) && TIKTOK_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Validate YouTube URL format.
     */
    public boolean isValidYouTubeUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        return isValidWebsiteUrl(url) && YOUTUBE_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Validate LinkedIn URL format.
     */
    public boolean isValidLinkedInUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        return isValidWebsiteUrl(url) && LINKEDIN_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Validate website URL format (must use http/https).
     */
    public boolean isValidWebsiteUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        if (url.length() > 500 || !HTTP_HTTPS_PATTERN.matcher(url.trim()).matches()) return false;
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            return uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Validate all URLs in a trainer profile.
     * Returns error message if validation fails, null if all valid.
     */
    public String validateProfile(TrainerProfile profile) {
        if (!isValidInstagramUrl(profile.getInstagramUrl())) {
            return "Instagram URL must be a valid instagram.com link (http:// or https://)";
        }
        if (!isValidTikTokUrl(profile.getTiktokUrl())) {
            return "TikTok URL must be a valid tiktok.com link (http:// or https://)";
        }
        if (!isValidYouTubeUrl(profile.getYoutubeUrl())) {
            return "YouTube URL must be a valid youtube.com or youtu.be link (http:// or https://)";
        }
        if (!isValidLinkedInUrl(profile.getLinkedInUrl())) {
            return "LinkedIn URL must be a valid linkedin.com link (http:// or https://)";
        }
        if (!isValidWebsiteUrl(profile.getWebsiteUrl())) {
            return "Website URL must use http:// or https://";
        }
        return null; // All valid
    }
}
