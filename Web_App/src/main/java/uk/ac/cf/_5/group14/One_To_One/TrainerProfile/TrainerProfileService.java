package uk.ac.cf._5.group14.One_To_One.TrainerProfile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

@Service
public class TrainerProfileService {
    private static final int TRAINER_CODE_LENGTH = 12;

    private final TrainerProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final SocialLinkValidator validator;

    public TrainerProfileService(TrainerProfileRepository profileRepository,
                                UserRepository userRepository,
                                SocialLinkValidator validator) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    /**
     * Get or create a trainer profile for a user.
     */
    public TrainerProfile getOrCreateProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.TRAINER && user.getRole() != Role.PLATFORM_ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw new IllegalStateException("Only trainers can have profiles");
        }

        return profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    TrainerProfile profile = new TrainerProfile(userId);
                    profile.setTrainerCode(generateUniqueTrainerCode());
                    return profileRepository.save(profile);
                });
    }

    /**
     * Get trainer profile by user ID (returns empty if not found).
     */
    public Optional<TrainerProfile> getProfileByUserId(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    public java.util.List<SocialLink> getVisibleSocialLinks(TrainerProfile profile) {
        if (profile == null) return java.util.List.of();
        var links = new java.util.ArrayList<SocialLink>();
        addSocialLink(links, profile.getShowInstagram(), profile.getInstagramUrl(), "ui.01243", validator.isValidInstagramUrl(profile.getInstagramUrl()));
        addSocialLink(links, profile.getShowTikTok(), profile.getTiktokUrl(), "ui.01245", validator.isValidTikTokUrl(profile.getTiktokUrl()));
        addSocialLink(links, profile.getShowYouTube(), profile.getYoutubeUrl(), "ui.01247", validator.isValidYouTubeUrl(profile.getYoutubeUrl()));
        addSocialLink(links, profile.getShowLinkedIn(), profile.getLinkedInUrl(), "ui.01249", validator.isValidLinkedInUrl(profile.getLinkedInUrl()));
        addSocialLink(links, profile.getShowWebsite(), profile.getWebsiteUrl(), "ui.01251", validator.isValidWebsiteUrl(profile.getWebsiteUrl()));
        return java.util.List.copyOf(links);
    }

    private void addSocialLink(java.util.List<SocialLink> links, Boolean visible, String url, String key, boolean valid) {
        if (Boolean.TRUE.equals(visible) && valid && url != null && !url.isBlank()) links.add(new SocialLink(key, url.trim()));
    }

    public record SocialLink(String labelKey, String url) { }

    /**
     * Update trainer profile with validation.
     */
    @Transactional
    public TrainerProfile updateProfile(Long userId, TrainerProfile updatedProfile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.TRAINER && user.getRole() != Role.PLATFORM_ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw new IllegalStateException("Only trainers can update profiles");
        }

        if (updatedProfile == null) throw new IllegalArgumentException("Profile is required");
        validateLength(updatedProfile.getBio(), 500);
        validateLength(updatedProfile.getSpecializations(), 200);
        validateLength(updatedProfile.getLocation(), 120);
        validateLength(updatedProfile.getPrimaryGym(), 200);
        validateLength(updatedProfile.getInstagramUrl(), 500);
        validateLength(updatedProfile.getTiktokUrl(), 500);
        validateLength(updatedProfile.getYoutubeUrl(), 500);
        validateLength(updatedProfile.getLinkedInUrl(), 500);
        validateLength(updatedProfile.getWebsiteUrl(), 500);
        if (updatedProfile.getPricePerSession() != null && updatedProfile.getPricePerSession() < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }

        // Validate all input before changing the persisted profile.
        String validationError = validator.validateProfile(updatedProfile);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        TrainerProfile profile = getOrCreateProfile(userId);
        
        // Update fields
        profile.setBio(updatedProfile.getBio());
        profile.setSpecializations(updatedProfile.getSpecializations());
        profile.setLocation(updatedProfile.getLocation());
        profile.setPrimaryGym(updatedProfile.getPrimaryGym());
        profile.setPricePerSession(updatedProfile.getPricePerSession());
        
        // Update social links
        profile.setInstagramUrl(updatedProfile.getInstagramUrl());
        profile.setTiktokUrl(updatedProfile.getTiktokUrl());
        profile.setYoutubeUrl(updatedProfile.getYoutubeUrl());
        profile.setLinkedInUrl(updatedProfile.getLinkedInUrl());
        profile.setWebsiteUrl(updatedProfile.getWebsiteUrl());
        
        // Update visibility flags
        profile.setShowInstagram(Boolean.TRUE.equals(updatedProfile.getShowInstagram()));
        profile.setShowTikTok(Boolean.TRUE.equals(updatedProfile.getShowTikTok()));
        profile.setShowYouTube(Boolean.TRUE.equals(updatedProfile.getShowYouTube()));
        profile.setShowLinkedIn(Boolean.TRUE.equals(updatedProfile.getShowLinkedIn()));
        profile.setShowWebsite(Boolean.TRUE.equals(updatedProfile.getShowWebsite()));

        return profileRepository.save(profile);
    }

    private void validateLength(String value, int maximum) {
        if (value != null && value.length() > maximum) throw new IllegalArgumentException("Profile field is too long");
    }

    /**
     * Generate a unique 12-character alphanumeric trainer code (format: XXXX-XXXX-XXXX).
     */
    private String generateUniqueTrainerCode() {
        String code;
        int attempts = 0;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            attempts++;
            if (attempts > 100) {
                throw new IllegalStateException("Unable to generate unique trainer code");
            }
        } while (profileRepository.existsByTrainerCode(code));
        return code;
    }

    public static String normalizeTrainerCode(String trainerCode) {
        if (trainerCode == null) {
            return null;
        }
        String normalized = trainerCode.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() == TRAINER_CODE_LENGTH ? normalized : null;
    }
}
