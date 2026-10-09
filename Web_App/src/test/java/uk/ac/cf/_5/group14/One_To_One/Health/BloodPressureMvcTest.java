package uk.ac.cf._5.group14.One_To_One.Health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.DayMode.DayModeService;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureController;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureReading;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureService;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BloodPressureController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = uk.ac.cf._5.group14.One_To_One.WorkoutTemplate.WorkoutDisplayAdvice.class))
@ActiveProfiles("test")
class BloodPressureMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthHelper authHelper;

    @MockitoBean
    private BloodPressureService service;

    @MockitoBean
    private DayModeService dayModeService;

    @MockitoBean
    private UserSettingsService userSettingsService;

    private User testUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }

    @Test
    void hubRedirectsToLoginWhenNotAuthenticated() throws Exception {
        given(authHelper.getAuthenticatedUser(any())).willReturn(null);

        mvc.perform(get("/health/blood-pressure"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void hubReturns200WhenAuthenticated() throws Exception {
        User user = testUser();
        given(authHelper.getAuthenticatedUser(any())).willReturn(user);

        BloodPressureService.BpStats stats = new BloodPressureService.BpStats(120, 80, 110, 130, 75, 85, 5, 3);
        given(service.getRecent(eq(user))).willReturn(Collections.emptyList());
        given(service.history(eq(user), org.mockito.ArgumentMatchers.anyInt())).willReturn(org.springframework.data.domain.Page.empty());
        given(service.getRange(eq(user), any(LocalDate.class), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(service.computeStats(any())).willReturn(stats);
        given(service.computeStreak(eq(user))).willReturn(3);

        mvc.perform(get("/health/blood-pressure").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("client-views/health/blood-pressure"));
    }

    @Test
    void hubPopulatesModelAttributes() throws Exception {
        User user = testUser();
        given(authHelper.getAuthenticatedUser(any())).willReturn(user);

        BloodPressureService.BpStats stats = new BloodPressureService.BpStats(0, 0, 0, 0, 0, 0, 0, 0);
        given(service.getRecent(eq(user))).willReturn(List.of());
        given(service.history(eq(user), org.mockito.ArgumentMatchers.anyInt())).willReturn(org.springframework.data.domain.Page.empty());
        given(service.getRange(eq(user), any(LocalDate.class), any(LocalDate.class))).willReturn(List.of());
        given(service.computeStats(any())).willReturn(stats);
        given(service.computeStreak(eq(user))).willReturn(0);

        mvc.perform(get("/health/blood-pressure").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("quickAdd", "recent", "stats", "streak", "range"));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean("bpTestSecurityFilterChain")
        SecurityFilterChain bpTestSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        public AuthHelper authHelper() {
            return mock(AuthHelper.class);
        }
        @Bean
        public Clock systemClock() {
            return Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneId.of("UTC"));
        }
    
        @Bean
        public DevModeProperties devModeProperties() {
            return new DevModeProperties();
        }
}
}
