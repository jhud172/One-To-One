package uk.ac.cf._5.group14.One_To_One.Profile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.DataExport.DataExportRequestService;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogService;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileRepository;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileService;
import uk.ac.cf._5.group14.One_To_One.HealthConditions.HealthConditionType;
import uk.ac.cf._5.group14.One_To_One.HealthConditions.UserHealthCondition;
import uk.ac.cf._5.group14.One_To_One.HealthConditions.UserHealthConditionService;
import uk.ac.cf._5.group14.One_To_One.Level.LevelProgress;
import uk.ac.cf._5.group14.One_To_One.Level.LevelService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrder;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscription;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfile;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileService;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.SocialLinkValidator;
import uk.ac.cf._5.group14.One_To_One.UserSettings.CalendarTaskLayoutPreference;
import uk.ac.cf._5.group14.One_To_One.UserSettings.ThemePreference;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

@lombok.extern.slf4j.Slf4j
@Controller
public class ProfileController {

    private static final long MAX_PROFILE_IMAGE_BYTES = 2L * 1024L * 1024L;
    private static final String DEFAULT_GYM_NAME = "My Gym";

    private final AuthHelper authHelper;
    private final UserService userService;
    private final ExerciseLogService exerciseLogService;
    private final PlatformSubscriptionService platformSubscriptionService;
    private final UserSettingsService userSettingsService;
    private final UserHealthConditionService conditionService;
    private final DataExportRequestService dataExportRequestService;
    private final LevelService levelService;
    private final FileStorageService profileImageStorageService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SavedPaymentMethodService cardService;
    private final MerchOrderService orderService;
    private final DevModeProperties devModeProperties;
    private final TrainerProfileService trainerProfileService;
    private final GymProfileService gymProfileService;
    private final GymProfileRepository gymProfileRepository;


    public ProfileController(AuthHelper authHelper,
                             UserService userService,
                             ExerciseLogService exerciseLogService,
                             PlatformSubscriptionService platformSubscriptionService,
                             UserSettingsService userSettingsService,
                             UserHealthConditionService conditionService,
                             DataExportRequestService dataExportRequestService,
                             LevelService levelService,
                             FileStorageService profileImageStorageService,
                             UserRepository userRepository,
                             Clock clock,
                             SavedPaymentMethodService cardService,
                             MerchOrderService orderService,
                             DevModeProperties devModeProperties,
                             TrainerProfileService trainerProfileService,
                             GymProfileService gymProfileService,
                             GymProfileRepository gymProfileRepository) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.exerciseLogService = exerciseLogService;
        this.platformSubscriptionService = platformSubscriptionService;
        this.userSettingsService = userSettingsService;
        this.conditionService = conditionService;
        this.dataExportRequestService = dataExportRequestService;
        this.levelService = levelService;
        this.profileImageStorageService = profileImageStorageService;
        this.userRepository = userRepository;
        this.clock = clock;
        this.cardService = cardService;
        this.orderService = orderService;
        this.devModeProperties = devModeProperties;
        this.trainerProfileService = trainerProfileService;
        this.gymProfileService = gymProfileService;
        this.gymProfileRepository = gymProfileRepository;
    }

    @GetMapping("/profile")
    public ModelAndView getProfile() {
        User user = authHelper.getAuthenticatedUser();

        if (user == null) {
            return new ModelAndView("redirect:/login");
        }

        Role role = user.getRole();

        if (role == Role.PLATFORM_ADMIN || role == Role.SUPER_ADMIN) {
            return new ModelAndView("redirect:/admin/dashboard");
        }

        if (role == Role.TRAINER) {
            return buildTrainerProfileView(user);
        }

        if (role == Role.GYM_ADMIN) {
            return buildGymAdminProfileView(user);
        }

        // CLIENT (default)
        return buildClientProfileView(user);
    }

    private ModelAndView buildClientProfileView(User user) {
        ModelAndView modelAndView = new ModelAndView("client-views/profile/profile");
        PlatformSubscription platformSubscription = platformSubscriptionService.findByUserId(user.getId()).orElse(null);
        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);
        UserSettings settings = userSettingsService.getOrCreate(user);
        LevelProgress levelProgress = levelService.getProgress(user);

        List<UserHealthCondition> permanentConditions = conditionService.getConditionsByType(user, HealthConditionType.PERMANENT);
        List<UserHealthCondition> timedConditions = conditionService.getConditionsByType(user, HealthConditionType.TIMED);

        int exerciseLogCount = exerciseLogService.getLogsByUser(user).size();

        modelAndView.addObject("user", user);
        modelAndView.addObject("platformSubscription", platformSubscription);
        modelAndView.addObject("subscriptionDaysLeft", platformSubscriptionService.getDaysUntilRenewal(user.getId(), clock));
        modelAndView.addObject("userSettings", settings);
        modelAndView.addObject("levelProgress", levelProgress);
        modelAndView.addObject("exerciseLogCount", exerciseLogCount);
        modelAndView.addObject("permanentConditions", permanentConditions);
        modelAndView.addObject("timedConditions", timedConditions);
        modelAndView.addObject("recentExportRequests", dataExportRequestService.getRecentRequests(user));
        modelAndView.addObject("today", LocalDate.now(clock));
        modelAndView.addObject("compactTopContent", true);
        modelAndView.addObject("isPremium", isPremium);
        modelAndView.addObject("profileBannerThemes", List.of("NONE", "AURORA", "SUNSET", "OCEAN", "ROSE", "CARBON", "LAGOON", "MEADOW", "MIDNIGHT"));
        modelAndView.addObject("profileRingStyles", List.of(
            "NONE",
            "NEON_DUAL",
            "SOLAR_FLARE",
            "CRYSTAL",
            "STARRY_SPARK",
            "AURORA_PULSE",
            "COMET_TRAIL",
            "EMBER_CROWN",
            "KING_CROWN",
            "CYBER_ARMS",
            "UFO_BEAM"
        ));
        modelAndView.addObject("profileCardBackStyles", List.of(
            "NONE",
            "GLASS",
            "TOPO",
            "CARBON",
            "MATRIX",
            "NEBULA",
            "CIRCUIT",
            "SUNBURST",
            "RETRO_GRID"
        ));

        Set<String> selectedMilestones = parseCsvKeys(settings != null ? settings.getProfileMilestoneKeys() : null, 6);
        List<MilestoneOption> availableMilestones = buildAvailableMilestones(user, platformSubscription, levelProgress, exerciseLogCount);
        selectedMilestones = selectedMilestones.stream()
            .filter(key -> availableMilestones.stream().anyMatch(option -> option.key().equals(key)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        modelAndView.addObject("profileMilestoneOptions", availableMilestones);
        modelAndView.addObject("selectedProfileMilestones", selectedMilestones);

        // Payment cards & orders
        List<SavedPaymentMethod> savedCards = cardService.getCardsForUser(user.getId());
        List<MerchOrder> allOrders = orderService.getRecentOrdersForUser(user.getId());
        modelAndView.addObject("savedCards", savedCards);
        modelAndView.addObject("allOrders", allOrders);
        modelAndView.addObject("orderCount", orderService.countOrdersForUser(user.getId()));

        return modelAndView;
    }

    @PostMapping("/profile/settings/customiser")
    public String updateProfileCustomiser(@RequestParam(value = "bannerTheme", required = false) String bannerTheme,
                                          @RequestParam(value = "ringStyle", required = false) String ringStyle,
                                          @RequestParam(value = "cardBackStyle", required = false) String cardBackStyle,
                                          @RequestParam(value = "textColor", required = false) String textColor,
                                          @RequestParam(value = "generalTextColor", required = false) String generalTextColor,
                                          @RequestParam(value = "milestoneKeys", required = false) List<String> milestoneKeys,
                                          RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (!platformSubscriptionService.isPremium(user.getId(), clock)) {
            redirectAttributes.addFlashAttribute("profileError", "Profile customiser is a Premium feature.");
            return "redirect:/profile";
        }

        UserSettings settings = userSettingsService.getOrCreate(user);
        LevelProgress levelProgress = levelService.getProgress(user);
        int exerciseLogCount = exerciseLogService.getLogsByUser(user).size();
        PlatformSubscription platformSubscription = platformSubscriptionService.findByUserId(user.getId()).orElse(null);

        Set<String> unlockedMilestones = buildAvailableMilestones(user, platformSubscription, levelProgress, exerciseLogCount)
                .stream()
                .map(MilestoneOption::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> selected = normalizeMilestoneKeys(milestoneKeys, unlockedMilestones);

        userSettingsService.updateProfileCustomizer(
                user,
                bannerTheme,
                ringStyle,
                cardBackStyle,
                textColor,
                generalTextColor,
                selected
        );

        redirectAttributes.addFlashAttribute("settingsUpdated", true);
        redirectAttributes.addFlashAttribute("customiserUpdated", true);
        return "redirect:/profile";
    }

    private ModelAndView buildTrainerProfileView(User user) {
        ModelAndView mav = new ModelAndView("trainer-views/profile/profile");
        TrainerProfile trainerProfile = trainerProfileService.getOrCreateProfile(user.getId());
        UserSettings settings = userSettingsService.getOrCreate(user);

        mav.addObject("user", user);
        mav.addObject("trainerProfile", trainerProfile);
        mav.addObject("userSettings", settings);
        mav.addObject("recentExportRequests", dataExportRequestService.getRecentRequests(user));
        mav.addObject("today", LocalDate.now(clock));
        return mav;
    }

    private GymProfile getOrCreateGymProfile(Long userId) {
        return gymProfileRepository.findByUserId(userId).orElseGet(() -> {
            GymProfile p = new GymProfile(userId, DEFAULT_GYM_NAME);
            gymProfileService.saveProfile(p);
            return gymProfileRepository.findByUserId(userId).orElse(p);
        });
    }

    private ModelAndView buildGymAdminProfileView(User user) {
        ModelAndView mav = new ModelAndView("gym-views/profile/profile");
        GymProfile gymProfile = getOrCreateGymProfile(user.getId());
        UserSettings settings = userSettingsService.getOrCreate(user);

        mav.addObject("user", user);
        mav.addObject("gymProfile", gymProfile);
        mav.addObject("userSettings", settings);
        mav.addObject("recentExportRequests", dataExportRequestService.getRecentRequests(user));
        mav.addObject("today", LocalDate.now(clock));
        return mav;
    }

    /**
     * Groups a list of orders by their primary product name.
     * This is called from Thymeleaf templates to organize purchases in the purchases drawer.
     * 
     * @param orders List of MerchOrder to group
     * @return List of OrderGroup objects, each representing a product with all matching orders
     */
    public List<OrderGroup> groupOrdersByProduct(List<MerchOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        // Group orders by the first item's product name (assuming single-item orders)
        Map<String, List<MerchOrder>> grouped = orders.stream()
            .collect(Collectors.groupingBy(
                order -> {
                    if (order.getItems() != null && !order.getItems().isEmpty()) {
                        String name = order.getItems().get(0).getProductNameSnapshot();
                        return name == null || name.isBlank() ? "" : name;
                    }
                    return "";
                },
                LinkedHashMap::new,
                Collectors.toList()
            ));

        // Convert to OrderGroup objects
        return grouped.entrySet().stream()
            .map(entry -> {
                String productName = entry.getKey();
                List<MerchOrder> productOrders = entry.getValue();
                int count = productOrders.size();
                
                // Sum up total amount across all orders for this product
                BigDecimal totalAmount = productOrders.stream()
                    .map(MerchOrder::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                return new OrderGroup(productName, count, totalAmount, productOrders);
            })
            .collect(Collectors.toList());
    }

    @GetMapping("/profile/orders")
    public ModelAndView getOrders() {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");
        return new ModelAndView("redirect:/orders");
    }

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute ProfileUpdateRequest request,
                                BindingResult bindingResult,
                                @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                                @RequestParam(value = "bannerTheme", required = false) String bannerTheme,
                                @RequestParam(value = "ringStyle", required = false) String ringStyle,
                                @RequestParam(value = "cardBackStyle", required = false) String cardBackStyle,
                                @RequestParam(value = "textColor", required = false) String textColor,
                                @RequestParam(value = "generalTextColor", required = false) String generalTextColor,
                                @RequestParam(value = "milestoneKeys", required = false) List<String> milestoneKeys,
                                RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error -> fieldErrors.put(error.getField(),
                "Enter a valid value."));
        TrainerProfile trainerDraft = user.getRole() == Role.TRAINER
                ? prepareTrainerProfileUpdates(request, user.getId(), fieldErrors) : null;
        User candidate = new User();
        candidate.setId(user.getId());
        copyEditableAccountFields(user, candidate);
        boolean changed = applyBasicProfileUpdates(request, candidate, fieldErrors);
        if (user.getRole() == Role.GYM_ADMIN) validateGymProfileFields(request, fieldErrors);
        // Check textual fields before touching stored images or the managed account.
        if (fieldErrors.isEmpty()) changed |= applyProfileImageUpdates(request, profileImage, candidate, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            redirectAttributes.addFlashAttribute("profileFieldErrors", fieldErrors);
            if (user.getRole() == Role.GYM_ADMIN || user.getRole() == Role.TRAINER) {
                redirectAttributes.addFlashAttribute("profileDraft", request);
                if (bindingResult.hasFieldErrors("pricePerSession")) {
                    redirectAttributes.addFlashAttribute("trainerPriceDraft", bindingResult.getFieldValue("pricePerSession"));
                }
            }
            redirectAttributes.addFlashAttribute("profileError", "Please fix the highlighted fields.");
            return "redirect:/profile";
        }

        if (changed) {
            copyEditableAccountFields(candidate, user);
            userRepository.save(user);
        }

        // Apply role-specific profile updates
        Role role = user.getRole();
        if (role == Role.TRAINER && trainerDraft != null) {
            trainerProfileService.updateProfile(user.getId(), trainerDraft);
            changed = true;
        } else if (role == Role.GYM_ADMIN) {
            applyGymProfileUpdates(request, user.getId());
            changed = true;
        }

        // A valid repeat submission also needs a visible acknowledgement.
        redirectAttributes.addFlashAttribute("profileUpdated", true);

        if (role == Role.CLIENT && platformSubscriptionService.isPremium(user.getId(), clock)) {
            userSettingsService.updateProfileCustomizer(
                    user,
                    bannerTheme,
                    ringStyle,
                    cardBackStyle,
                    textColor,
                    generalTextColor,
                    normalizeMilestoneKeys(milestoneKeys, null)
            );
            redirectAttributes.addFlashAttribute("customiserUpdated", true);
        }

        return "redirect:/profile";
    }

    private TrainerProfile prepareTrainerProfileUpdates(ProfileUpdateRequest request, Long userId,
                                                        Map<String, String> errors) {
        if (request.getTrainerBio() == null && request.getSpecializations() == null
                && request.getLocation() == null && request.getPrimaryGym() == null
                && !request.isPricePerSessionProvided() && request.getInstagramUrl() == null
                && request.getTiktokUrl() == null && request.getYoutubeUrl() == null
                && request.getLinkedInUrl() == null && request.getWebsiteUrl() == null
                && request.getShowInstagram() == null && request.getShowTikTok() == null
                && request.getShowYouTube() == null && request.getShowLinkedIn() == null
                && request.getShowWebsite() == null) return null;
        TrainerProfile stored = trainerProfileService.getOrCreateProfile(userId);
        TrainerProfile profile = new TrainerProfile(userId);
        profile.setBio(stored.getBio());
        profile.setSpecializations(stored.getSpecializations());
        profile.setLocation(stored.getLocation());
        profile.setPrimaryGym(stored.getPrimaryGym());
        profile.setPricePerSession(stored.getPricePerSession());
        profile.setInstagramUrl(stored.getInstagramUrl());
        profile.setTiktokUrl(stored.getTiktokUrl());
        profile.setYoutubeUrl(stored.getYoutubeUrl());
        profile.setLinkedInUrl(stored.getLinkedInUrl());
        profile.setWebsiteUrl(stored.getWebsiteUrl());
        profile.setShowInstagram(stored.getShowInstagram());
        profile.setShowTikTok(stored.getShowTikTok());
        profile.setShowYouTube(stored.getShowYouTube());
        profile.setShowLinkedIn(stored.getShowLinkedIn());
        profile.setShowWebsite(stored.getShowWebsite());
        validateOptionalProfileField(request.getTrainerBio(), "trainerBio", "Bio", 500, false, errors);
        validateOptionalProfileField(request.getSpecializations(), "specializations", "Specialisations", 200, false, errors);
        validateOptionalProfileField(request.getLocation(), "location", "Location", 120, false, errors);
        validateOptionalProfileField(request.getPrimaryGym(), "primaryGym", "Primary gym", 200, false, errors);
        if (request.getPricePerSession() != null && (request.getPricePerSession() < 0 || request.getPricePerSession() > 9999)) {
            errors.put("pricePerSession", "Enter a whole-pound price from 0 to 9999.");
        }
        SocialLinkValidator validator = new SocialLinkValidator();
        if (!validator.isValidInstagramUrl(request.getInstagramUrl())) errors.put("instagramUrl", "Enter a valid Instagram link.");
        if (!validator.isValidTikTokUrl(request.getTiktokUrl())) errors.put("tiktokUrl", "Enter a valid TikTok link.");
        if (!validator.isValidYouTubeUrl(request.getYoutubeUrl())) errors.put("youtubeUrl", "Enter a valid YouTube link.");
        if (!validator.isValidLinkedInUrl(request.getLinkedInUrl())) errors.put("linkedInUrl", "Enter a valid LinkedIn link.");
        if (!validator.isValidWebsiteUrl(request.getWebsiteUrl())) errors.put("websiteUrl", "Enter a valid website link using http or https.");
        if (request.getTrainerBio() != null) {
            String trimmed = request.getTrainerBio().trim();
            profile.setBio(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getSpecializations() != null) {
            String trimmed = request.getSpecializations().trim();
            profile.setSpecializations(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getLocation() != null) {
            String trimmed = request.getLocation().trim();
            profile.setLocation(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getPrimaryGym() != null) {
            String trimmed = request.getPrimaryGym().trim();
            profile.setPrimaryGym(trimmed.isBlank() ? null : trimmed);
        }
        if (request.isPricePerSessionProvided()) {
            profile.setPricePerSession(request.getPricePerSession());
        }
        if (request.getInstagramUrl() != null) {
            String trimmed = request.getInstagramUrl().trim();
            profile.setInstagramUrl(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getTiktokUrl() != null) {
            String trimmed = request.getTiktokUrl().trim();
            profile.setTiktokUrl(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getYoutubeUrl() != null) {
            String trimmed = request.getYoutubeUrl().trim();
            profile.setYoutubeUrl(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getLinkedInUrl() != null) {
            String trimmed = request.getLinkedInUrl().trim();
            profile.setLinkedInUrl(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getWebsiteUrl() != null) {
            String trimmed = request.getWebsiteUrl().trim();
            profile.setWebsiteUrl(trimmed.isBlank() ? null : trimmed);
        }
        // An unchecked control is absent: update it only when its associated link was submitted.
        if (request.getInstagramUrl() != null || request.getShowInstagram() != null) profile.setShowInstagram(Boolean.TRUE.equals(request.getShowInstagram()));
        if (request.getTiktokUrl() != null || request.getShowTikTok() != null) profile.setShowTikTok(Boolean.TRUE.equals(request.getShowTikTok()));
        if (request.getYoutubeUrl() != null || request.getShowYouTube() != null) profile.setShowYouTube(Boolean.TRUE.equals(request.getShowYouTube()));
        if (request.getLinkedInUrl() != null || request.getShowLinkedIn() != null) profile.setShowLinkedIn(Boolean.TRUE.equals(request.getShowLinkedIn()));
        if (request.getWebsiteUrl() != null || request.getShowWebsite() != null) profile.setShowWebsite(Boolean.TRUE.equals(request.getShowWebsite()));
        return profile;
    }

    private void copyEditableAccountFields(User source, User target) {
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setUsername(source.getUsername());
        target.setUsernameChangedAt(source.getUsernameChangedAt());
        target.setEmail(source.getEmail());
        target.setEmailVerified(source.isEmailVerified());
        target.setEmailVerifiedAt(source.getEmailVerifiedAt());
        target.setPhoneNumber(source.getPhoneNumber());
        target.setPhoneCountry(source.getPhoneCountry());
        target.setPhoneVerified(source.isPhoneVerified());
        target.setPhoneVerifiedAt(source.getPhoneVerifiedAt());
        target.setBio(source.getBio());
        target.setDateOfBirth(source.getDateOfBirth());
        target.setProfileImageUrl(source.getProfileImageUrl());
    }

    private void validateGymProfileFields(ProfileUpdateRequest request, Map<String, String> errors) {
        validateOptionalProfileField(request.getGymName(), "gymName", "Gym name", 120, true, errors);
        validateOptionalProfileField(request.getGymAddress(), "gymAddress", "Address", 200, false, errors);
        validateOptionalProfileField(request.getGymCity(), "gymCity", "City", 120, false, errors);
        validateOptionalProfileField(request.getGymContactName(), "gymContactName", "Contact name", 120, false, errors);
        validateOptionalProfileField(request.getGymContactPhone(), "gymContactPhone", "Contact phone", 40, false, errors);
    }

    private void validateOptionalProfileField(String value, String field, String label, int maximum, boolean required, Map<String, String> errors) {
        if (value == null) return;
        if (required && value.isBlank()) errors.put(field, label + " is required.");
        else if (value.trim().length() > maximum) errors.put(field, label + " must be " + maximum + " characters or fewer.");
    }

    private void applyGymProfileUpdates(ProfileUpdateRequest request, Long userId) {
        if (request == null) {
            return;
        }
        GymProfile profile = getOrCreateGymProfile(userId);
        if (request.getGymName() != null) {
            String trimmed = request.getGymName().trim();
            if (!trimmed.isBlank()) {
                profile.setGymName(trimmed);
            }
        }
        if (request.getGymAddress() != null) {
            String trimmed = request.getGymAddress().trim();
            profile.setAddress(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getGymCity() != null) {
            String trimmed = request.getGymCity().trim();
            profile.setCity(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getGymContactName() != null) {
            String trimmed = request.getGymContactName().trim();
            profile.setContactName(trimmed.isBlank() ? null : trimmed);
        }
        if (request.getGymContactPhone() != null) {
            String trimmed = request.getGymContactPhone().trim();
            profile.setContactPhone(trimmed.isBlank() ? null : trimmed);
        }
        gymProfileService.saveProfile(profile);
    }

    private boolean applyBasicProfileUpdates(ProfileUpdateRequest request,
                                             User user,
                                             Map<String, String> fieldErrors) {
        boolean changed = false;

        changed |= applyNameField(
                request != null ? request.getFirstName() : null,
                user.getFirstName(),
                "firstName",
                "First name",
                user::setFirstName,
                fieldErrors
        );
        changed |= applyNameField(
                request != null ? request.getLastName() : null,
                user.getLastName(),
                "lastName",
                "Last name",
                user::setLastName,
                fieldErrors
        );
        changed |= applyBioUpdate(request != null ? request.getBio() : null, user, fieldErrors);
        changed |= applyDateOfBirthUpdate(request != null ? request.getDateOfBirth() : null, user, fieldErrors);
        changed |= applyUsernameUpdate(request != null ? request.getUsername() : null, user, fieldErrors);
        changed |= applyEmailUpdate(request != null ? request.getEmail() : null, user, fieldErrors);
        changed |= applyPhoneUpdate(
                request != null ? request.getPhoneNumber() : null,
                request != null ? request.getPhoneCountry() : "GB",
                user,
                fieldErrors
        );

        return changed;
    }

    private boolean applyNameField(String rawValue,
                                   String currentValue,
                                   String fieldKey,
                                   String label,
                                   Consumer<String> setter,
                                   Map<String, String> fieldErrors) {
        if (rawValue == null) {
            return false;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isBlank()) {
            fieldErrors.put(fieldKey, label + " cannot be empty.");
            return false;
        }
        if (trimmed.length() > 100) {
            fieldErrors.put(fieldKey, label + " must be 100 characters or fewer.");
            return false;
        }
        if (trimmed.equals(currentValue)) {
            return false;
        }

        setter.accept(trimmed);
        return true;
    }

    private boolean applyBioUpdate(String bio, User user, Map<String, String> fieldErrors) {
        if (bio == null) {
            return false;
        }

        String trimmedBio = bio.trim();
        if (trimmedBio.length() > 800) {
            fieldErrors.put("bio", "Bio must be 800 characters or fewer.");
            return false;
        }

        String normalizedBio = trimmedBio.isBlank() ? null : trimmedBio;
        if (java.util.Objects.equals(normalizedBio, user.getBio())) {
            return false;
        }

        user.setBio(normalizedBio);
        return true;
    }

    private boolean applyDateOfBirthUpdate(String dateOfBirth, User user, Map<String, String> fieldErrors) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) {
            return false;
        }
        if (user.getDateOfBirth() != null) {
            fieldErrors.put("dateOfBirth", "Date of birth can only be set once.");
            return false;
        }

        try {
            LocalDate parsedDate = LocalDate.parse(dateOfBirth.trim());
            LocalDate today = LocalDate.now(clock);
            if (parsedDate.isAfter(today)) {
                fieldErrors.put("dateOfBirth", "Date of birth cannot be in the future.");
                return false;
            }
            if (parsedDate.isBefore(today.minusYears(120))) {
                fieldErrors.put("dateOfBirth", "Enter a valid date of birth.");
                return false;
            }

            user.setDateOfBirth(parsedDate);
            return true;
        } catch (RuntimeException ex) {
            fieldErrors.put("dateOfBirth", "Enter a valid date of birth.");
            return false;
        }
    }

    private boolean applyUsernameUpdate(String username, User user, Map<String, String> fieldErrors) {
        if (username == null || username.isBlank()) {
            return false;
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.equalsIgnoreCase(user.getUsername())) {
            return false;
        }
        if (normalized.length() < 3 || normalized.length() > 100) {
            fieldErrors.put("username", "Username must be between 3 and 100 characters.");
            return false;
        }
        if (userService.usernameExists(normalized)) {
            fieldErrors.put("username", "That username is already taken.");
            return false;
        }

        user.setUsername(normalized);
        user.setUsernameChangedAt(Instant.now(clock));
        return true;
    }

    private boolean applyEmailUpdate(String email, User user, Map<String, String> fieldErrors) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            return false;
        }
        if (!isValidEmail(normalizedEmail)) {
            fieldErrors.put("email", "Enter a valid email address.");
            return false;
        }
        if (userService.emailExists(normalizedEmail)) {
            fieldErrors.put("email", "That email is already in use.");
            return false;
        }

        user.setEmail(normalizedEmail);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        return true;
    }

    private boolean applyPhoneUpdate(String phoneNumber,
                                     String phoneCountry,
                                     User user,
                                     Map<String, String> fieldErrors) {
        if (phoneNumber == null) {
            return false;
        }

        String normalizedPhone = phoneNumber.trim();
        String normalizedCountry = phoneCountry != null ? phoneCountry.trim().toUpperCase(Locale.ROOT) : "GB";
        if (normalizedCountry.length() != 2) {
            normalizedCountry = "GB";
        }

        String localDigits = extractLocalPhoneDigits(normalizedPhone);
        if (normalizedPhone.isBlank()) {
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return false;
            }
            user.setPhoneNumber(null);
            user.setPhoneCountry("GB");
            user.setPhoneVerified(false);
            user.setPhoneVerifiedAt(null);
            return true;
        }
        if (!isValidPhone(normalizedPhone)) {
            fieldErrors.put("phoneNumber", "Please enter a valid phone number (numbers and +- only).");
            return false;
        }
        if (localDigits.startsWith("0")) {
            fieldErrors.put("phoneNumber", "Phone digits should not start with 0 after the country code.");
            return false;
        }
        if (localDigits.length() > 10) {
            fieldErrors.put("phoneNumber", "Phone number can include up to 10 digits after the country code.");
            return false;
        }
        if (normalizedPhone.length() > 30) {
            fieldErrors.put("phoneNumber", "Phone number must be 30 characters or fewer.");
            return false;
        }
        if (normalizedPhone.equals(user.getPhoneNumber())
                && java.util.Objects.equals(normalizedCountry, user.getPhoneCountry())) {
            return false;
        }

        user.setPhoneNumber(normalizedPhone);
        user.setPhoneCountry(normalizedCountry);
        user.setPhoneVerified(false);
        user.setPhoneVerifiedAt(null);
        return true;
    }

    private boolean applyProfileImageUpdates(ProfileUpdateRequest request,
                                             MultipartFile profileImage,
                                             User user,
                                             Map<String, String> fieldErrors) {
        boolean removeProfileImage = request != null && request.isRemoveProfileImage();
        boolean hasUpload = profileImage != null && !profileImage.isEmpty();
        if (!hasUpload) {
            if (!removeProfileImage || user.getProfileImageUrl() == null) return false;
            try { profileImageStorageService.deleteProfileImage(user.getProfileImageUrl()); }
            catch (IOException ignored) { /* A missing file does not prevent removing its reference. */ }
            user.setProfileImageUrl(null);
            return true;
        }

        String imageType = profileImage.getContentType();
        boolean allowedType = "image/png".equals(imageType) || "image/jpeg".equals(imageType) || "image/webp".equals(imageType);
        if (!allowedType || profileImage.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            fieldErrors.put("profileImage", "Please choose a PNG, JPG, or WebP file under 2MB.");
            return false;
        }
        try {
            String previousImageUrl = user.getProfileImageUrl();
            String imageUrl = profileImageStorageService.storeProfileImage(user.getId(), profileImage);
            if (imageUrl == null) return false;
            user.setProfileImageUrl(imageUrl);
            if (previousImageUrl != null && !previousImageUrl.equals(imageUrl)) {
                try { profileImageStorageService.deleteProfileImage(previousImageUrl); }
                catch (IOException ex) { log.warn("Could not clean up previous profile image for user {}", user.getId()); }
            }
            return true;
        } catch (IllegalArgumentException | IOException ex) {
            fieldErrors.put("profileImage", safeExceptionMessage(ex, "We couldn't update your profile image."));
            return false;
        }
    }

    private Set<String> normalizeMilestoneKeys(List<String> milestoneKeys, Set<String> allowedKeys) {
        Set<String> selected = new LinkedHashSet<>();
        if (milestoneKeys == null) {
            return selected;
        }

        for (String key : milestoneKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.trim().toUpperCase(Locale.ROOT);
            if (allowedKeys == null || allowedKeys.contains(normalized)) {
                selected.add(normalized);
            }
            if (selected.size() >= 6) {
                break;
            }
        }

        return selected;
    }

    private String safeExceptionMessage(Exception ex, String fallback) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }

    @GetMapping(value = "/profile/username-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> usernameAvailability(@RequestParam("username") String username) {
        User user = authHelper.getAuthenticatedUser();
        Map<String, Object> result = new LinkedHashMap<>();

        if (user == null) {
            result.put("available", false);
            result.put("message", "Sign in to check usernames.");
            return result;
        }

        String normalized = username != null ? username.trim().toLowerCase(Locale.ROOT) : "";
        if (normalized.isBlank()) {
            result.put("available", false);
            result.put("message", "Username is required.");
            return result;
        }
        if (normalized.length() < 3 || normalized.length() > 100) {
            result.put("available", false);
            result.put("message", "Username must be between 3 and 100 characters.");
            return result;
        }
        if (normalized.equalsIgnoreCase(user.getUsername())) {
            result.put("available", true);
            result.put("message", "This is your current username.");
            return result;
        }

        boolean taken = userService.usernameExists(normalized);
        result.put("available", !taken);
        result.put("message", taken ? "That username is already taken." : "Username is available.");
        return result;
    }

    @PostMapping("/profile/image")
    public String updateProfileImage(@RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                                     RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (profileImage == null || profileImage.isEmpty()) {
            redirectAttributes.addFlashAttribute("profileImageError", "Choose an image to upload.");
            return "redirect:/profile";
        }

        try {
            String previousImageUrl = user.getProfileImageUrl();
            String imageUrl = profileImageStorageService.storeProfileImage(user.getId(), profileImage);
            if (imageUrl != null) {
                user.setProfileImageUrl(imageUrl);
                if (previousImageUrl != null && !previousImageUrl.equals(imageUrl)) {
                    profileImageStorageService.deleteProfileImage(previousImageUrl);
                }
                userRepository.save(user);
                redirectAttributes.addFlashAttribute("profileUpdated", true);
            }
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("profileImageError", safeExceptionMessage(ex, "We couldn't update your profile image."));
        }

        return "redirect:/profile";
    }

    @PostMapping("/profile/settings/accessibility")
    public String updateAccessibility(@RequestParam(value = "colorBlindMode", required = false) String colorBlindMode,
                                      @RequestParam(value = "disabilityHearing", required = false) String disabilityHearing,
                                      @RequestParam(value = "disabilityMobility", required = false) String disabilityMobility,
                                      @RequestParam(value = "disabilityVision", required = false) String disabilityVision,
                                      RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        userSettingsService.updateAccessibility(
                user,
                colorBlindMode != null,
                disabilityHearing != null,
                disabilityMobility != null,
                disabilityVision != null
        );
        redirectAttributes.addFlashAttribute("settingsUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/settings/trainer-sharing")
    public String updateTrainerSharing(@RequestParam(value = "shareRecoverySignals", required = false) String shareRecoverySignals,
                                       @RequestParam(value = "shareNutritionSignals", required = false) String shareNutritionSignals,
                                       @RequestParam(value = "shareSleepSignals", required = false) String shareSleepSignals,
                                       @RequestParam(value = "shareFatigueSignals", required = false) String shareFatigueSignals,
                                       @RequestParam(value = "shareWeightTrend", required = false) String shareWeightTrend,
                                       RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        userSettingsService.updateTrainerSharing(
                user,
                shareRecoverySignals != null,
                shareNutritionSignals != null,
                shareSleepSignals != null,
                shareFatigueSignals != null,
                shareWeightTrend != null
        );
        redirectAttributes.addFlashAttribute("settingsUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/settings/calendar-display")
    public String updateCalendarDisplay(@RequestParam(value = "layout", required = false) String layout,
                                        RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        try {
            if (layout == null || layout.isBlank()) throw new IllegalArgumentException();
            CalendarTaskLayoutPreference layoutPref = CalendarTaskLayoutPreference.valueOf(layout.trim());
            userSettingsService.updateCalendarPreferences(user, null, layoutPref);
            redirectAttributes.addFlashAttribute("settingsUpdated", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("settingsError", "Invalid calendar layout preference.");
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/settings/theme")
    public String updateTheme(@RequestParam(value = "theme", required = false) String theme,
                              RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        ThemePreference themePref;
        try {
            if (theme == null || theme.isBlank()) throw new IllegalArgumentException();
            themePref = ThemePreference.valueOf(theme.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("settingsError", "Choose a valid appearance preference.");
            return "redirect:/profile";
        }
        UserSettings settings = userSettingsService.getOrCreate(user);
        userSettingsService.update(user,
                settings != null ? settings.getLanguage() : "en",
                themePref,
                settings != null && settings.isEasyMode());
        redirectAttributes.addFlashAttribute("settingsUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/conditions/permanent")
    public String addPermanentCondition(@RequestParam("conditionName") String conditionName,
                                        RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (conditionName == null || conditionName.isBlank()) {
            redirectAttributes.addFlashAttribute("conditionError", "Enter a condition name.");
            return "redirect:/profile";
        }
        conditionService.addPermanentCondition(user, conditionName);
        redirectAttributes.addFlashAttribute("conditionUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/conditions/timed")
    public String addTimedCondition(@RequestParam("conditionName") String conditionName,
                                    @RequestParam(value = "startDate", required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam("durationDays") Integer durationDays,
                                    RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (conditionName == null || conditionName.isBlank()) {
            redirectAttributes.addFlashAttribute("conditionError", "Enter a condition name.");
            return "redirect:/profile";
        }
        int safeDuration = durationDays != null ? durationDays : 1;
        conditionService.addTimedCondition(user, conditionName, startDate, safeDuration);
        redirectAttributes.addFlashAttribute("conditionUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/conditions/{id}/delete")
    public String deleteCondition(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        conditionService.deleteCondition(user, id);
        redirectAttributes.addFlashAttribute("conditionUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/conditions/{id}/recover")
    public String recoverCondition(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        conditionService.markRecovered(user, id);
        redirectAttributes.addFlashAttribute("conditionUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/conditions/{id}/extend")
    public String extendCondition(@PathVariable Long id,
                                  @RequestParam("extraDays") Integer extraDays,
                                  RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        int safeExtra = extraDays != null ? extraDays : 1;
        conditionService.extendTimedCondition(user, id, safeExtra);
        redirectAttributes.addFlashAttribute("conditionUpdated", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/data-export")
    public String requestDataExport(RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        dataExportRequestService.createRequest(user);
        redirectAttributes.addFlashAttribute("exportRequested", true);
        return "redirect:/profile";
    }

    @PostMapping("/profile/delete")
    public String deleteAccount(@RequestParam(value = "confirmText", required = false) String confirmText,
                                RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (confirmText == null || !confirmText.trim().equalsIgnoreCase("DELETE")) {
            redirectAttributes.addFlashAttribute("deleteError", "Type DELETE to confirm account deletion.");
            return "redirect:/profile";
        }
        userRepository.delete(user);
        return "redirect:/logout?deleted=1";
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        return pattern.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        // Allow numbers, spaces, hyphens, plus, and parentheses
        // Require at least 5 digits
        Pattern pattern = Pattern.compile("^[+]?[\\d\\s\\-()]+$");
        if (!pattern.matcher(phone).matches()) {
            return false;
        }
        // Count digits (must have at least 5)
        long digitCount = phone.replaceAll("\\D", "").length();
        return digitCount >= 5;
    }

    private String extractLocalPhoneDigits(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String strippedCountry = phone.trim().replaceFirst("^\\+\\d+\\s*", "");
        return strippedCountry.replaceAll("\\D", "");
    }

    private Set<String> parseCsvKeys(String raw, int max) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return keys;
        }
        for (String part : raw.split(",")) {
            if (part != null && !part.isBlank()) {
                keys.add(part.trim().toUpperCase(Locale.ROOT));
            }
            if (keys.size() >= max) {
                break;
            }
        }
        return keys;
    }

    private List<MilestoneOption> buildAvailableMilestones(User user,
                                                            PlatformSubscription platformSubscription,
                                                            LevelProgress levelProgress,
                                                            int exerciseLogCount) {
        List<MilestoneOption> milestones = new java.util.ArrayList<>();

        if (exerciseLogCount >= 1) {
            milestones.add(new MilestoneOption("FIRST_WORKOUT", "First workout logged", "You recorded your first workout."));
        }
        if (exerciseLogCount >= 10) {
            milestones.add(new MilestoneOption("TEN_WORKOUTS", "10 workouts", "You reached ten logged workouts."));
        }
        if (levelProgress != null && levelProgress.getLevel() >= 5) {
            milestones.add(new MilestoneOption("LEVEL_5", "Reached Level 5", "You reached level 5 in progress."));
        }
        if (levelProgress != null && levelProgress.getLevel() >= 10) {
            milestones.add(new MilestoneOption("LEVEL_10", "Reached Level 10", "You reached level 10 in progress."));
        }
        if (user != null && user.isEmailVerified()) {
            milestones.add(new MilestoneOption("VERIFIED_EMAIL", "Verified email", "Your email has been verified."));
        }
        if (user != null && user.getPhoneNumber() != null && user.isPhoneVerified()) {
            milestones.add(new MilestoneOption("VERIFIED_PHONE", "Verified phone", "Your phone has been verified."));
        }
        if (platformSubscription != null) {
            milestones.add(new MilestoneOption("PREMIUM_MEMBER", "Premium member", "You unlocked premium access."));
        }

        return milestones;
    }

    public record MilestoneOption(String key, String title, String subtitle) {
    }
}
