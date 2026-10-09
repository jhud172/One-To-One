package uk.ac.cf._5.group14.One_To_One.Profile;

public class ProfileUpdateRequest implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phoneNumber;
    private String phoneCountry = "GB";
    private String bio;
    private String dateOfBirth;
    private boolean removeProfileImage;

    // Trainer-specific fields
    private String trainerBio;
    private String specializations;
    private String location;
    private String primaryGym;
    private Integer pricePerSession;
    private boolean pricePerSessionProvided;
    private String instagramUrl;
    private String tiktokUrl;
    private String youtubeUrl;
    private String linkedInUrl;
    private String websiteUrl;
    private Boolean showInstagram;
    private Boolean showTikTok;
    private Boolean showYouTube;
    private Boolean showLinkedIn;
    private Boolean showWebsite;

    // Gym admin-specific fields
    private String gymName;
    private String gymAddress;
    private String gymCity;
    private String gymContactName;
    private String gymContactPhone;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneCountry() {
        return phoneCountry != null ? phoneCountry : "GB";
    }

    public void setPhoneCountry(String phoneCountry) {
        this.phoneCountry = phoneCountry != null ? phoneCountry : "GB";
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public boolean isRemoveProfileImage() {
        return removeProfileImage;
    }

    public void setRemoveProfileImage(boolean removeProfileImage) {
        this.removeProfileImage = removeProfileImage;
    }

    // Trainer-specific getters/setters
    public String getTrainerBio() { return trainerBio; }
    public void setTrainerBio(String trainerBio) { this.trainerBio = trainerBio; }

    public String getSpecializations() { return specializations; }
    public void setSpecializations(String specializations) { this.specializations = specializations; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPrimaryGym() { return primaryGym; }
    public void setPrimaryGym(String primaryGym) { this.primaryGym = primaryGym; }

    public Integer getPricePerSession() { return pricePerSession; }
    public void setPricePerSession(Integer pricePerSession) {
        this.pricePerSession = pricePerSession;
        this.pricePerSessionProvided = true;
    }
    public boolean isPricePerSessionProvided() { return pricePerSessionProvided; }

    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String instagramUrl) { this.instagramUrl = instagramUrl; }

    public String getTiktokUrl() { return tiktokUrl; }
    public void setTiktokUrl(String tiktokUrl) { this.tiktokUrl = tiktokUrl; }

    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }

    public String getLinkedInUrl() { return linkedInUrl; }
    public void setLinkedInUrl(String linkedInUrl) { this.linkedInUrl = linkedInUrl; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public Boolean getShowInstagram() { return showInstagram; }
    public void setShowInstagram(Boolean showInstagram) { this.showInstagram = showInstagram; }

    public Boolean getShowTikTok() { return showTikTok; }
    public void setShowTikTok(Boolean showTikTok) { this.showTikTok = showTikTok; }

    public Boolean getShowYouTube() { return showYouTube; }
    public void setShowYouTube(Boolean showYouTube) { this.showYouTube = showYouTube; }

    public Boolean getShowLinkedIn() { return showLinkedIn; }
    public void setShowLinkedIn(Boolean showLinkedIn) { this.showLinkedIn = showLinkedIn; }

    public Boolean getShowWebsite() { return showWebsite; }
    public void setShowWebsite(Boolean showWebsite) { this.showWebsite = showWebsite; }

    // Gym admin-specific getters/setters
    public String getGymName() { return gymName; }
    public void setGymName(String gymName) { this.gymName = gymName; }

    public String getGymAddress() { return gymAddress; }
    public void setGymAddress(String gymAddress) { this.gymAddress = gymAddress; }

    public String getGymCity() { return gymCity; }
    public void setGymCity(String gymCity) { this.gymCity = gymCity; }

    public String getGymContactName() { return gymContactName; }
    public void setGymContactName(String gymContactName) { this.gymContactName = gymContactName; }

    public String getGymContactPhone() { return gymContactPhone; }
    public void setGymContactPhone(String gymContactPhone) { this.gymContactPhone = gymContactPhone; }
}
