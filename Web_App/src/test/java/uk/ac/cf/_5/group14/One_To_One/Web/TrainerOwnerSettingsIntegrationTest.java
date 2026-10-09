package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.*;
import uk.ac.cf._5.group14.One_To_One.UserSettings.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerOwnerSettingsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerProfileRepository profiles;
    @Autowired UserSettingsRepository settings;
    @Autowired UserSettingsService settingsService;
    @Autowired EntityManager entityManager;
    User trainer;
    TrainerProfile profile;

    private User account(Role role) {
        String username = "owner-" + UUID.randomUUID();
        var user = new User(username + "@example.invalid", "Local", "Owner", username, "test-password");
        user.setRole(role); user.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        trainer = account(Role.TRAINER);
        profile = new TrainerProfile(trainer.getId());
        profile.setBio("Saved bio"); profile.setLocation("Cardiff");
        profile.setPricePerSession(55); profile.setTrainerCode("TESTCODE1234");
        profile.setInstagramUrl("https://instagram.com/local_fixture"); profile.setShowInstagram(true);
        profile.setYoutubeUrl("https://youtube.com/@local_fixture"); profile.setShowYouTube(true);
        profile = profiles.saveAndFlush(profile);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(String path) {
        return post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf());
    }
    private org.jsoup.nodes.Document follow(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/profile").with(user(trainer.getUsername()).roles("TRAINER"))
                .cookie(result.getResponse().getCookies())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test void accountSavePreservesPublicFieldsAndVisibilityWhilePartialLinkSaveChangesOnlyItsOwnFlag() throws Exception {
        var result = mvc.perform(command("/profile/update").param("firstName", "Updated"))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attribute("profileUpdated", true)).andReturn();
        assertThat(follow(result).select("main [role=status]").text()).contains("Profile updated");
        entityManager.flush(); entityManager.clear();
        var stored = profiles.findByUserId(trainer.getId()).orElseThrow();
        assertThat(stored.getShowInstagram()).isTrue(); assertThat(stored.getShowYouTube()).isTrue();
        assertThat(stored.getBio()).isEqualTo("Saved bio"); assertThat(stored.getPricePerSession()).isEqualTo(55);
        assertThat(users.findById(trainer.getId()).orElseThrow().getFirstName()).isEqualTo("Updated");
        mvc.perform(command("/profile/update").param("firstName", "Updated"))
                .andExpect(flash().attribute("profileUpdated", true));
        mvc.perform(command("/profile/update").param("instagramUrl", "https://instagram.com/changed_fixture"))
                .andExpect(flash().attribute("profileUpdated", true));
        entityManager.flush(); entityManager.clear();
        stored = profiles.findByUserId(trainer.getId()).orElseThrow();
        assertThat(stored.getShowInstagram()).isFalse(); assertThat(stored.getShowYouTube()).isTrue();
        assertThat(stored.getYoutubeUrl()).isEqualTo("https://youtube.com/@local_fixture");
        assertThat(stored.getTrainerCode()).isEqualTo("TESTCODE1234");
        var editor = org.jsoup.Jsoup.parse(mvc.perform(get("/trainer/profile/edit").with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(editor.selectFirst("#bio").wholeText()).isEqualTo("Saved bio");
        assertThat(editor.selectFirst("#instagramUrl").val()).isEqualTo("https://instagram.com/changed_fixture");
        var publicView = org.jsoup.Jsoup.parse(mvc.perform(get("/trainers/{id}/profile", trainer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(publicView.select("a[href='https://instagram.com/changed_fixture']")).isEmpty();
        assertThat(publicView.select("a[href='https://youtube.com/@local_fixture']")).hasSize(1);
        assertThat(publicView.text()).doesNotContain("TESTCODE1234", "TEST-CODE-1234");
    }

    @Test void rejectedTrainerDetailsRetainLiteralDraftAndCannotPartiallyMutateEitherRecord() throws Exception {
        var rejected = mvc.perform(command("/profile/update").param("firstName", "Changed")
                .param("trainerBio", "Literal <draft>\nKeep line two").param("location", "x".repeat(121))
                .param("websiteUrl", "javascript:alert(1)").param("showWebsite", "on"))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("profileFieldErrors", "profileDraft"))
                .andReturn();
        var page = follow(rejected);
        assertThat(page.selectFirst("#trainerBio").wholeText()).isEqualTo("Literal <draft>\nKeep line two");
        assertThat(page.selectFirst("#firstName").val()).isEqualTo("Changed");
        assertThat(page.selectFirst("#location").val()).isEqualTo("x".repeat(121));
        assertThat(page.selectFirst("#websiteUrl").val()).isEqualTo("javascript:alert(1)");
        assertThat(page.select("#showWebsite[checked]")).hasSize(1);
        assertThat(page.select("#location[aria-invalid=true],#websiteUrl[aria-invalid=true]")).hasSize(2);
        assertThat(page.select("main [role=alert] a").eachAttr("href")).contains("#location", "#websiteUrl");
        assertThat(page.select("draft")).isEmpty();
        entityManager.flush(); entityManager.clear();
        assertThat(users.findById(trainer.getId()).orElseThrow().getFirstName()).isEqualTo("Local");
        var stored = profiles.findByUserId(trainer.getId()).orElseThrow();
        assertThat(stored.getBio()).isEqualTo("Saved bio"); assertThat(stored.getLocation()).isEqualTo("Cardiff");
        assertThat(stored.getWebsiteUrl()).isNull(); assertThat(stored.getShowInstagram()).isTrue();
    }

    @Test void malformedPriceReturnsDraftFeedbackAndBoundaryPriceAndBioCanSave() throws Exception {
        var rejected = mvc.perform(command("/profile/update").param("pricePerSession", "55.5")
                .param("trainerBio", "Unsaved draft")).andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attributeExists("trainerPriceDraft", "profileFieldErrors")).andReturn();
        var page = follow(rejected);
        assertThat(page.selectFirst("#pricePerSession").val()).isEqualTo("55.5");
        assertThat(page.select("#pricePerSession[aria-invalid=true]")).hasSize(1);
        for (String invalid : new String[]{"-1", "10000"}) {
            mvc.perform(command("/profile/update").param("pricePerSession", invalid))
                    .andExpect(flash().attributeExists("profileFieldErrors"));
        }
        mvc.perform(command("/profile/update").param("pricePerSession", "9999").param("trainerBio", "b".repeat(500)))
                .andExpect(flash().attribute("profileUpdated", true));
        entityManager.flush(); entityManager.clear();
        var stored = profiles.findByUserId(trainer.getId()).orElseThrow();
        assertThat(stored.getPricePerSession()).isEqualTo(9999); assertThat(stored.getBio()).hasSize(500);
        assertThat(stored.getShowInstagram()).isTrue(); assertThat(stored.getShowYouTube()).isTrue();
        mvc.perform(command("/profile/update").param("pricePerSession", ""))
                .andExpect(flash().attribute("profileUpdated", true));
        entityManager.flush(); entityManager.clear();
        assertThat(profiles.findByUserId(trainer.getId()).orElseThrow().getPricePerSession()).isNull();
    }

    @Test void preferencesPersistIndependentlyAndInvalidOrMissingChoicesCannotResetThem() throws Exception {
        var own = settingsService.getOrCreate(trainer); own.setLanguage("cy"); own.setEasyMode(true);
        own.setShareWeightTrend(true); settings.saveAndFlush(own);
        var saved = mvc.perform(command("/profile/settings/theme").param("theme", "dark"))
                .andExpect(flash().attribute("settingsUpdated", true)).andReturn();
        assertThat(follow(saved).select("#account-preferences [role=status]")).hasSize(1);
        for (String invalid : new String[]{"", "unknown"}) {
            mvc.perform(command("/profile/settings/theme").param("theme", invalid))
                    .andExpect(flash().attributeExists("settingsError"));
        }
        mvc.perform(command("/profile/settings/theme")).andExpect(flash().attributeExists("settingsError"));
        mvc.perform(command("/profile/settings/calendar-display").param("layout", "SEPARATED_BY_CATEGORY"))
                .andExpect(flash().attribute("settingsUpdated", true));
        mvc.perform(command("/profile/settings/calendar-display")).andExpect(flash().attributeExists("settingsError"));
        mvc.perform(command("/profile/settings/accessibility").param("colorBlindMode", "on").param("disabilityVision", "on"))
                .andExpect(flash().attribute("settingsUpdated", true));
        entityManager.flush(); entityManager.clear();
        own = settings.findById(trainer.getId()).orElseThrow();
        assertThat(own.getTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(own.getLanguage()).isEqualTo("cy"); assertThat(own.isEasyMode()).isTrue();
        assertThat(own.getCalendarTaskLayout()).isEqualTo(CalendarTaskLayoutPreference.SEPARATED_BY_CATEGORY);
        assertThat(own.isColorBlindMode()).isTrue(); assertThat(own.isDisabilityVision()).isTrue();
        assertThat(own.isDisabilityHearing()).isFalse(); assertThat(own.isDisabilityMobility()).isFalse();
        assertThat(own.isShareWeightTrend()).isTrue();
        mvc.perform(post("/profile/settings/theme").with(user(trainer.getUsername()).roles("TRAINER")).param("theme", "LIGHT"))
                .andExpect(status().isUnauthorized());
    }

    @Test void gymRejectedDraftAlsoSurvivesTheRealJdbcSessionRedirect() throws Exception {
        var gym = account(Role.GYM_ADMIN);
        var rejected = mvc.perform(post("/profile/update").with(user(gym.getUsername()).roles("GYM_ADMIN")).with(csrf())
                .param("gymName", "").param("gymAddress", "x".repeat(201)).param("firstName", "Changed"))
                .andExpect(flash().attributeExists("profileDraft", "profileFieldErrors")).andReturn();
        var page = org.jsoup.Jsoup.parse(mvc.perform(get("/profile").with(user(gym.getUsername()).roles("GYM_ADMIN"))
                .cookie(rejected.getResponse().getCookies())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.selectFirst("#gymAddress").val()).isEqualTo("x".repeat(201));
        assertThat(page.selectFirst("#firstName").val()).isEqualTo("Changed");
        assertThat(page.select("main [role=alert]").text()).contains("Gym name is required");
        assertThat(users.findById(gym.getId()).orElseThrow().getFirstName()).isEqualTo("Local");
    }

    @Test void trainerAndGymFormsRenderAcrossLocalesWithOneCsrfAndAccessibleNativeControls() throws Exception {
        var gym = account(Role.GYM_ADMIN);
        for (var owner : new User[]{trainer, gym}) {
            for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
                var page = org.jsoup.Jsoup.parse(mvc.perform(get("/profile").with(user(owner.getUsername()).roles(owner.getRole().name()))
                        .param("lang", locale)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                assertThat(page.select("#account-preferences form")).hasSize(3);
                assertThat(page.select("#account-preferences input[type=checkbox]")).hasSize(4);
                assertThat(page.select("#account-preferences input[name=theme][checked]")).hasSize(1);
                for (var form : page.select("main form")) assertThat(form.select("input[name=_csrf]")).hasSize(1);
                assertThat(page.text()).doesNotContain("??ui.owner.");
                if (owner.getRole() == Role.TRAINER) {
                    assertThat(page.select("main main,[style]")).isEmpty();
                    assertThat(page.select(".owner-section-nav a")).hasSize(5);
                    assertThat(page.select("#trainerCodeVal").text()).isEqualTo("TEST-CODE-1234");
                    assertThat(page.select("#instagramUrl[aria-label],#youtubeUrl[aria-label],#profileImage[aria-label],#confirmText[aria-label]")).hasSize(4);
                }
            }
        }
    }
}
