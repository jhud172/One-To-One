package uk.ac.cf._5.group14.One_To_One.ProfileTests;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import uk.ac.cf._5.group14.One_To_One.DataExport.DataExportRequestService;
import uk.ac.cf._5.group14.One_To_One.DayMode.DayModeService;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogService;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileRepository;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileService;
import uk.ac.cf._5.group14.One_To_One.HealthConditions.HealthConditionType;
import uk.ac.cf._5.group14.One_To_One.HealthConditions.UserHealthConditionService;
import uk.ac.cf._5.group14.One_To_One.Level.LevelProgress;
import uk.ac.cf._5.group14.One_To_One.Level.LevelService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.Profile.FileStorageService;
import uk.ac.cf._5.group14.One_To_One.Profile.ProfileController;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfile;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;

/**
 * Verifies that the profile route dispatches to the correct view based on the authenticated
 * user's role: CLIENT → client profile, TRAINER → trainer profile, GYM_ADMIN → gym-admin
 * profile, PLATFORM_ADMIN/SUPER_ADMIN → redirect to admin dashboard. Also asserts that a
 * missing session user always redirects to login rather than resolving a fallback demo user.
 *
 * POST coverage verifies that /profile/update correctly delegates to role-specific profile
 * update helpers and sets the profileUpdated flash attribute on success.
 */
@WebMvcTest(value = ProfileController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = uk.ac.cf._5.group14.One_To_One.WorkoutTemplate.WorkoutDisplayAdvice.class))
@ActiveProfiles("test")
class ProfileRouteAccessTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private UserService userService;
    @MockitoBean private ExerciseLogService exerciseLogService;
    @MockitoBean private PlatformSubscriptionService platformSubscriptionService;
    @MockitoBean private UserSettingsService userSettingsService;
    @MockitoBean private UserHealthConditionService conditionService;
    @MockitoBean private DataExportRequestService dataExportRequestService;
    @MockitoBean private LevelService levelService;
    @MockitoBean private FileStorageService fileStorageService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private AuthHelper authHelper;
    @MockitoBean private DayModeService dayModeService;
    @MockitoBean private SavedPaymentMethodService savedPaymentMethodService;
    @MockitoBean private MerchOrderService merchOrderService;
    @MockitoBean private TrainerProfileService trainerProfileService;
    @MockitoBean private GymProfileService gymProfileService;
    @MockitoBean private GymProfileRepository gymProfileRepository;

    private User makeUser(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("testuser" + id);
        user.setEmail("test" + id + "@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(role);
        return user;
    }

    private void stubCommonMocks(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setUserId(user.getId());

        given(authHelper.getAuthenticatedUser()).willReturn(user);
        given(userSettingsService.getOrCreate(eq(user))).willReturn(settings);
        given(dataExportRequestService.getRecentRequests(eq(user))).willReturn(Collections.emptyList());
    }

    @Test
    void clientRoleReturnsClientProfileView() throws Exception {
        User user = makeUser(1L, Role.CLIENT);

        LevelProgress levelProgress = new LevelProgress();
        levelProgress.setUser(user);
        levelProgress.setPoints(0);
        levelProgress.setLevel(1);

        stubCommonMocks(user);
        given(platformSubscriptionService.findByUserId(eq(user.getId()))).willReturn(Optional.empty());
        given(levelService.getProgress(eq(user))).willReturn(levelProgress);
        given(conditionService.getConditionsByType(eq(user), eq(HealthConditionType.PERMANENT)))
                .willReturn(Collections.emptyList());
        given(conditionService.getConditionsByType(eq(user), eq(HealthConditionType.TIMED)))
                .willReturn(Collections.emptyList());
        given(exerciseLogService.getLogsByUser(eq(user))).willReturn(Collections.emptyList());
        given(savedPaymentMethodService.getCardsForUser(eq(user.getId()))).willReturn(Collections.emptyList());
        given(merchOrderService.getRecentOrdersForUser(eq(user.getId()))).willReturn(Collections.emptyList());

        mvc.perform(get("/profile").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("client-views/profile/profile"));
    }

    @Test
    void trainerRoleReturnsTrainerProfileView() throws Exception {
        User user = makeUser(2L, Role.TRAINER);
        TrainerProfile trainerProfile = new TrainerProfile(user.getId());

        stubCommonMocks(user);
        given(trainerProfileService.getOrCreateProfile(eq(user.getId()))).willReturn(trainerProfile);

        mvc.perform(get("/profile").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("trainer-views/profile/profile"));
    }

    @Test
    void gymAdminRoleReturnsGymProfileView() throws Exception {
        User user = makeUser(3L, Role.GYM_ADMIN);

        stubCommonMocks(user);
        given(gymProfileRepository.findByUserId(eq(user.getId()))).willReturn(Optional.empty());

        mvc.perform(get("/profile").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("gym-views/profile/profile"));
    }

    @Test
    void platformAdminRoleRedirectsToDashboard() throws Exception {
        User user = makeUser(4L, Role.PLATFORM_ADMIN);
        given(authHelper.getAuthenticatedUser()).willReturn(user);

        mvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void profileRouteRedirectsToLoginWhenNoSessionUser() throws Exception {
        given(authHelper.getAuthenticatedUser()).willReturn(null);

        mvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void trainerProfileUpdatePersistsTrainerFieldsAndSetsFlash() throws Exception {
        User user = makeUser(2L, Role.TRAINER);
        TrainerProfile trainerProfile = new TrainerProfile(user.getId());
        trainerProfile.setBio("Existing bio");

        stubCommonMocks(user);
        given(trainerProfileService.getOrCreateProfile(eq(user.getId()))).willReturn(trainerProfile);
        given(trainerProfileService.updateProfile(eq(user.getId()), any())).willReturn(trainerProfile);

        mvc.perform(post("/profile/update")
                        .param("firstName", "Test")
                        .param("lastName", "User")
                        .param("trainerBio", "Updated trainer bio")
                        .param("specializations", "Strength, Cardio")
                        .sessionAttr("user", user))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("profileUpdated", true));

        verify(trainerProfileService).updateProfile(eq(user.getId()), any());
    }

    @Test
    void gymAdminProfileUpdatePersistsGymFieldsAndSetsFlash() throws Exception {
        User user = makeUser(3L, Role.GYM_ADMIN);
        uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile gymProfile =
                new uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile(user.getId(), "Test Gym");

        stubCommonMocks(user);
        given(gymProfileRepository.findByUserId(eq(user.getId()))).willReturn(Optional.of(gymProfile));
        given(gymProfileService.saveProfile(any())).willReturn(gymProfile);

        mvc.perform(post("/profile/update")
                        .param("firstName", "Test")
                        .param("lastName", "User")
                        .param("gymName", "Updated Gym Name")
                        .param("gymAddress", "123 High Street")
                        .sessionAttr("user", user))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("profileUpdated", true));

        verify(gymProfileService).saveProfile(any());
    }


    @Test
    void invalidGymDetailsRetainDraftAndDoNotMutateAccountOrSave() throws Exception {
        User user = makeUser(30L, Role.GYM_ADMIN);
        stubCommonMocks(user);
        var result = mvc.perform(post("/profile/update").param("firstName", "Changed")
                .param("gymName", "").param("gymAddress", "x".repeat(201)))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("profileDraft", "profileFieldErrors"))
            .andReturn();
        assertEquals("Test", user.getFirstName());
        verify(userRepository, never()).save(any());
        verify(gymProfileService, never()).saveProfile(any());
        var gym = new uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile(user.getId(), "Stored gym");
        given(gymProfileRepository.findByUserId(user.getId())).willReturn(Optional.of(gym));
        mvc.perform(get("/profile").flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("x".repeat(201))))
            .andExpect(content().string(containsString("Gym name is required")));
    }

    @Test
    void rejectedAccountChangeCannotPartiallyChangeTheManagedUser() throws Exception {
        User user = makeUser(31L, Role.GYM_ADMIN);
        stubCommonMocks(user);
        mvc.perform(post("/profile/update").param("firstName", "Changed").param("email", "not-an-email"))
            .andExpect(flash().attributeExists("profileFieldErrors"));
        assertEquals("Test", user.getFirstName());
        assertEquals("test31@example.com", user.getEmail());
        verify(userRepository, never()).save(any());
    }

    @Test
    void failedPhotoReplacementCannotRemoveTheCurrentPhoto() throws Exception {
        User user = makeUser(32L, Role.GYM_ADMIN);
        user.setProfileImageUrl("/uploads/profiles/old-photo.png");
        stubCommonMocks(user);
        MockMultipartFile bad = new MockMultipartFile("profileImage", "bad.txt", "text/plain", "bad".getBytes());
        mvc.perform(multipart("/profile/update").file(bad).param("removeProfileImage", "true").param("firstName", "Changed"))
            .andExpect(flash().attributeExists("profileFieldErrors"));
        assertEquals("/uploads/profiles/old-photo.png", user.getProfileImageUrl());
        assertEquals("Test", user.getFirstName());
        verify(fileStorageService, never()).deleteProfileImage(any());
        verify(fileStorageService, never()).storeProfileImage(any(), any());
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean("testProfileSecurityFilterChain")
        SecurityFilterChain testProfileSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        public AuthHelper authHelper() {
            return mock(AuthHelper.class);
        }

        @Bean
        public PlatformSubscriptionService platformSubscriptionService() {
            return mock(PlatformSubscriptionService.class);
        }

        @Bean
        public Clock systemClock() {
            return Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneId.of("UTC"));
        }
    
        @Bean
        public DevModeProperties devModeProperties() {
            return new DevModeProperties();
        }
    }
}

