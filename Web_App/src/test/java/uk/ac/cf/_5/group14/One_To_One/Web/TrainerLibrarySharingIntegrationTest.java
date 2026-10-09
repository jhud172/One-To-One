package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.*;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerLibrarySharingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired TrainerLibraryService library;
    @Autowired TrainerLibrarySharedTemplateRepository shares;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    User trainer, client;
    Long exerciseId, workoutId, programmeId;

    @BeforeEach void setup() {
        trainer = createUser(Role.TRAINER, "Owner");
        client = createUser(Role.CLIENT, "Recipient <literal>");
        links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var exercise = new TrainerLibraryExerciseForm();
        exercise.setName("Exercise <literal>"); exercise.setPrimaryMuscles("Back");
        exercise.setEquipment("Dumbbells"); exercise.setDifficulty("Beginner");
        exerciseId = library.createExercise(trainer.getId(), exercise).getId();
        var workout = new TrainerLibraryWorkoutTemplateForm(); workout.setTitle("Workout <literal>");
        workoutId = library.createWorkout(trainer.getId(), workout).getId();
        var programme = new TrainerLibraryProgrammeTemplateForm(); programme.setTitle("Programme <literal>"); programme.setWeeks(4);
        programmeId = library.createProgramme(trainer.getId(), programme).getId();
        em.flush();
    }

    @Test void repeatedSharesRemainSingleReferencesAndRevokedRelationshipsHideAllTypes() {
        for (var type : TrainerLibraryTemplateType.values()) {
            library.shareTemplate(trainer.getId(), form(type));
            library.shareTemplate(trainer.getId(), form(type));
        }
        em.flush();
        assertThat(shares.findByClientIdAndTrainerIdOrderBySharedAtDesc(client.getId(), trainer.getId())).hasSize(3);
        assertThat(library.getAssignedExercisesForClient(client.getId())).hasSize(1);
        assertThat(library.getAssignedWorkoutsForClient(client.getId())).hasSize(1);
        assertThat(library.getAssignedProgrammesForClient(client.getId())).hasSize(1);
        var link = links.findActiveByClientId(client.getId()).orElseThrow();
        link.setStatus(TrainerClientLinkStatus.ENDED); links.saveAndFlush(link);
        assertThat(library.getAssignedExercisesForClient(client.getId())).isEmpty();
        assertThat(library.getAssignedWorkoutsForClient(client.getId())).isEmpty();
        assertThat(library.getAssignedProgrammesForClient(client.getId())).isEmpty();
        assertThatThrownBy(() -> library.shareTemplate(trainer.getId(), form(TrainerLibraryTemplateType.EXERCISE)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void disabledClientIsRefreshedBeforeAnyShareIsSaved() {
        assertThat(users.findById(client.getId()).orElseThrow().isEnabled()).isTrue();
        jdbc.update("update users set enabled = false where id = ?", client.getId());
        assertThatThrownBy(() -> library.shareTemplate(trainer.getId(), form(TrainerLibraryTemplateType.EXERCISE)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(shares.findByClientIdAndTrainerIdOrderBySharedAtDesc(client.getId(), trainer.getId())).isEmpty();
    }

    @Test void nativeRejectedFormKeepsEligibleRecipientAndCanonicalCatalogueContext() throws Exception {
        var result = mvc.perform(post("/trainer/library/share").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "EXERCISE").param("templateId", exerciseId.toString()).param("clientId", client.getId().toString())
                .param("returnUrl", "https://outside.invalid/" + "a".repeat(200)).param("q", "literal <search>").param("page", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("libraryShareClientId", client.getId()))
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).startsWith("/trainer/library/exercises/" + exerciseId + "?q=")
                .contains("page=2").doesNotContain("outside.invalid");
        // Keep the raw query string as well as decoded parameters for Spring's flash-map matching.
        var response = mvc.perform(get(java.net.URI.create(result.getResponse().getRedirectedUrl()))
                .with(user(trainer.getUsername()).roles("TRAINER")).cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk()).andReturn();
        var html = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(html.selectFirst("#shareDialog").hasAttr("open")).isTrue();
        assertThat(html.selectFirst("#shareDialog-clientId option[selected]").val()).isEqualTo(client.getId().toString());
        assertThat(html.selectFirst("#shareDialog-clientId").attr("aria-describedby")).contains("shareDialog-error");
        var foreign = createUser(Role.CLIENT, "Foreign recipient");
        var foreignResult = mvc.perform(post("/trainer/library/share").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "EXERCISE").param("templateId", exerciseId.toString()).param("clientId", foreign.getId().toString())
                .param("returnUrl", "x".repeat(201))).andExpect(status().is3xxRedirection()).andReturn();
        var foreignResponse = mvc.perform(get(foreignResult.getResponse().getRedirectedUrl())
                .with(user(trainer.getUsername()).roles("TRAINER")).cookie(foreignResult.getResponse().getCookies()))
                .andExpect(status().isOk()).andReturn();
        assertThat(Jsoup.parse(foreignResponse.getResponse().getContentAsString()).select("#shareDialog-clientId option[selected]")).isEmpty();
        assertThat(shares.findByClientIdAndTrainerIdOrderBySharedAtDesc(client.getId(), trainer.getId())).isEmpty();
    }

    @Test void allResourcePickersRenderEscapedScopeNamedSuccessAndOneCsrfInEveryLocale() throws Exception {
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            for (var type : TrainerLibraryTemplateType.values()) {
                String route = switch(type) { case EXERCISE -> "exercises"; case WORKOUT -> "workouts"; case PROGRAMME -> "programmes"; };
                var saved = mvc.perform(post("/trainer/library/share").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                        .param("templateType", type.name()).param("templateId", form(type).getTemplateId().toString())
                        .param("clientId", client.getId().toString())).andExpect(status().is3xxRedirection()).andReturn();
                var response = mvc.perform(get("/trainer/library/" + route + "/" + form(type).getTemplateId()).param("lang", locale)
                        .with(user(trainer.getUsername()).roles("TRAINER")).cookie(saved.getResponse().getCookies()))
                        .andExpect(status().isOk()).andReturn();
                var html = Jsoup.parse(response.getResponse().getContentAsString());
                assertThat(html.selectFirst(".library-share-resource h3").text()).endsWith("<literal>");
                assertThat(html.selectFirst("#shareDialog-scope").text()).isNotBlank();
                assertThat(html.selectFirst(".library-share-v2 [role=status]").text()).contains(client.getFullName());
                assertThat(html.select("#shareDialog form input[name=_csrf]")).hasSize(1);
                assertThat(html.select("#shareDialog script,#shareDialog style,.library-share-resource literal")).isEmpty();
                assertThat(html.text()).doesNotContain("??ui.library.");
            }
        }
    }

    private User createUser(Role role, String firstName) {
        String name = "share-" + UUID.randomUUID();
        var value = new User(name + "@example.invalid", firstName, "Evidence", name, "test-password");
        value.setRole(role); value.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(value);
    }
    private TrainerLibraryShareForm form(TrainerLibraryTemplateType type) {
        var form = new TrainerLibraryShareForm(); form.setClientId(client.getId()); form.setTemplateType(type);
        form.setTemplateId(switch(type) { case EXERCISE -> exerciseId; case WORKOUT -> workoutId; case PROGRAMME -> programmeId; });
        return form;
    }
}
