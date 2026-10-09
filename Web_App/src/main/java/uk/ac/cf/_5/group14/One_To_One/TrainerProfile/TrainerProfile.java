package uk.ac.cf._5.group14.One_To_One.TrainerProfile;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "trainer_profiles")
public class TrainerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Size(max = 500)
    @Column(name = "bio", length = 500)
    private String bio;

    @Size(max = 200)
    @Column(name = "specializations", length = 200)
    private String specializations;

    @Size(max = 120)
    @Column(name = "location", length = 120)
    private String location;

    @Size(max = 200)
    @Column(name = "primary_gym", length = 200)
    private String primaryGym;

    @Min(0)
    @Column(name = "price_per_session")
    private Integer pricePerSession;

    // Social Media URLs
    @Size(max = 500)
    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Size(max = 500)
    @Column(name = "tiktok_url", length = 500)
    private String tiktokUrl;

    @Size(max = 500)
    @Column(name = "youtube_url", length = 500)
    private String youtubeUrl;

    @Size(max = 500)
    @Column(name = "linkedin_url", length = 500)
    private String linkedInUrl;

    @Size(max = 500)
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    // Visibility Flags
    @Column(name = "show_instagram", nullable = false)
    private Boolean showInstagram = false;

    @Column(name = "show_tiktok", nullable = false)
    private Boolean showTikTok = false;

    @Column(name = "show_youtube", nullable = false)
    private Boolean showYouTube = false;

    @Column(name = "show_linkedin", nullable = false)
    private Boolean showLinkedIn = false;

    @Column(name = "show_website", nullable = false)
    private Boolean showWebsite = false;

    @Column(name = "trainer_code", length = 12, unique = true)
    private String trainerCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TrainerProfile() {
    }

    public TrainerProfile(Long userId) {
        this.userId = userId;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getSpecializations() {
        return specializations;
    }

    public void setSpecializations(String specializations) {
        this.specializations = specializations;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPrimaryGym() {
        return primaryGym;
    }

    public void setPrimaryGym(String primaryGym) {
        this.primaryGym = primaryGym;
    }

    public Integer getPricePerSession() {
        return pricePerSession;
    }

    public void setPricePerSession(Integer pricePerSession) {
        this.pricePerSession = pricePerSession;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getTiktokUrl() {
        return tiktokUrl;
    }

    public void setTiktokUrl(String tiktokUrl) {
        this.tiktokUrl = tiktokUrl;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public String getLinkedInUrl() {
        return linkedInUrl;
    }

    public void setLinkedInUrl(String linkedInUrl) {
        this.linkedInUrl = linkedInUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public Boolean getShowInstagram() {
        return showInstagram;
    }

    public void setShowInstagram(Boolean showInstagram) {
        this.showInstagram = showInstagram;
    }

    public Boolean getShowTikTok() {
        return showTikTok;
    }

    public void setShowTikTok(Boolean showTikTok) {
        this.showTikTok = showTikTok;
    }

    public Boolean getShowYouTube() {
        return showYouTube;
    }

    public void setShowYouTube(Boolean showYouTube) {
        this.showYouTube = showYouTube;
    }

    public Boolean getShowLinkedIn() {
        return showLinkedIn;
    }

    public void setShowLinkedIn(Boolean showLinkedIn) {
        this.showLinkedIn = showLinkedIn;
    }

    public Boolean getShowWebsite() {
        return showWebsite;
    }

    public void setShowWebsite(Boolean showWebsite) {
        this.showWebsite = showWebsite;
    }

    public String getTrainerCode() {
        return trainerCode;
    }

    public void setTrainerCode(String trainerCode) {
        this.trainerCode = trainerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
