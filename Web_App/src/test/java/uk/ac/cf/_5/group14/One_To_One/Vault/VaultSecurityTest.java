package uk.ac.cf._5.group14.One_To_One.Vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import java.time.LocalDate;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VaultSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VaultNoteRepository vaultNoteRepository;

    @Autowired private VaultNoteService service;
    @Autowired private WorkoutSessionRepository sessions;

    private User userA;
    private User userB;

    @BeforeEach
    void setup() {
        vaultNoteRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().replace("-", "");

        userA = new User("vaultA+" + suffix + "@example.com", "Vault", "A", "vault_a_" + suffix, "password123");
        userA.setRole(Role.CLIENT);
        userA = userRepository.save(userA);

        userB = new User("vaultB+" + suffix + "@example.com", "Vault", "B", "vault_b_" + suffix, "password123");
        userB.setRole(Role.CLIENT);
        userB = userRepository.save(userB);
    }

    @Test
    void cannotViewAnotherUsersVaultNote() throws Exception {
        VaultNote note = new VaultNote(userA.getId(), VaultNoteType.TRAINING, "Private", "secret");
        note = vaultNoteRepository.save(note);

        mockMvc.perform(get("/vault/" + note.getId())
                .with(user(userB.getUsername()).roles("CLIENT")))
            .andExpect(status().isNotFound());
    }

    @Test
    void cannotInvokeAiOnAnotherUsersNote() throws Exception {
        VaultNote note = new VaultNote(userA.getId(), VaultNoteType.REFLECTION, "Private", "secret");
        note = vaultNoteRepository.save(note);

        mockMvc.perform(post("/vault/ai/summarise-week")
                .with(user(userB.getUsername()).roles("CLIENT"))
                .with(csrf())
                .param("noteIds", String.valueOf(note.getId()))
                .param("returnTo", "/vault/" + note.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanReadButAiRequiresExplicitConsent() throws Exception {
        VaultNote note = new VaultNote(userA.getId(), VaultNoteType.TRAINING, "Week check-in", "Felt good.");
        note = vaultNoteRepository.save(note);

        mockMvc.perform(get("/vault/" + note.getId())
                .with(user(userA.getUsername()).roles("CLIENT")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/vault/ai/rewrite-checkin")
                .with(user(userA.getUsername()).roles("CLIENT"))
                .with(csrf())
                .param("noteIds", String.valueOf(note.getId()))
                .param("returnTo", "/vault/" + note.getId()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/vault/" + note.getId()))
            .andExpect(flash().attribute("vaultAiError", "consent"));
    }

    @Test
    void invalidDraftAndForeignSessionDoNotChangeTheReflection() throws Exception {
        var session = new WorkoutSession(); session.setUser(userB); session.setDate(LocalDate.now());
        session.setCreatedAt(java.time.LocalDateTime.now()); session.setNameSnapshot("Private workout name"); session = sessions.save(session);
        var note = service.create(userA.getId(), VaultNoteType.TRAINING, "Original", "Original reflection", null, null);
        service.saveAiSummary(note.getId(), userA.getId(), "Existing insight");
        mockMvc.perform(post("/vault/" + note.getId() + "/edit").with(user(userA.getUsername()).roles("CLIENT")).with(csrf())
                .param("noteType", "REFLECTION").param("title", "Changed").param("content", "Changed reflection")
                .param("linkedWorkoutSessionId", session.getId().toString()))
            .andExpect(status().isNotFound());
        var unchanged = vaultNoteRepository.findById(note.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Original"); assertThat(unchanged.getAiSummary()).isEqualTo("Existing insight");
        mockMvc.perform(post("/vault/" + note.getId() + "/edit").with(user(userA.getUsername()).roles("CLIENT")).with(csrf())
                .param("noteType", "REFLECTION").param("title", "Keep <draft>").param("content", "Keep <reflection>")
                .param("linkedDate", "2026-02-31").param("tags", "recovery").param("mood", "GOOD"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Keep &lt;draft&gt;")))
            .andExpect(content().string(containsString("Keep &lt;reflection&gt;")))
            .andExpect(content().string(containsString("value=\"2026-02-31\"")))
            .andExpect(content().string(not(containsString("Private workout name"))));
        assertThat(vaultNoteRepository.findById(note.getId()).orElseThrow().getContent()).isEqualTo("Original reflection");
        mockMvc.perform(post("/vault/" + note.getId() + "/edit").with(user(userA.getUsername()).roles("CLIENT")).with(csrf())
                .param("noteType", "REFLECTION").param("title", "Updated").param("content", "Updated reflection"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("vaultSaved", true));
        assertThat(vaultNoteRepository.findById(note.getId()).orElseThrow().getAiSummary()).isNull();
    }

    @Test
    void nativeFiltersSelectionsAndHistoricLinksRemainPrivate() throws Exception {
        var foreign = new WorkoutSession(); foreign.setUser(userB); foreign.setDate(LocalDate.now());
        foreign.setCreatedAt(java.time.LocalDateTime.now()); foreign.setNameSnapshot("Never reveal this workout"); foreign = sessions.save(foreign);
        var note = new VaultNote(userA.getId(), VaultNoteType.REFLECTION, "Owned <title>", "Literal <script>private()</script>");
        note.setLinkedWorkoutSessionId(foreign.getId()); note = vaultNoteRepository.save(note);
        mockMvc.perform(get("/vault").with(user(userA.getUsername()).roles("CLIENT")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("vault-workspace-v2")))
            .andExpect(content().string(containsString("form=\"vaultSelection\"")))
            .andExpect(content().string(containsString("Owned &lt;title&gt;")))
            .andExpect(content().string(not(containsString("Never reveal this workout"))));
        mockMvc.perform(get("/vault/" + note.getId()).with(user(userA.getUsername()).roles("CLIENT")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;private()&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("id=\"confirmOverlay\""))));
        mockMvc.perform(get("/vault").with(user(userA.getUsername()).roles("CLIENT")).param("from", "2026-10-02").param("to", "2026-10-01"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"2026-10-02\"")));
        mockMvc.perform(post("/vault/ai/insight/" + note.getId()).with(user(userA.getUsername()).roles("CLIENT")).with(csrf())
                .param("aiConsent", "true").param("returnTo", "/vaultevil"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/vault/" + note.getId()))
            .andExpect(flash().attribute("vaultAiError", "premium"));
        assertThat(vaultNoteRepository.findById(note.getId()).orElseThrow().getAiSummary()).isNull();
    }

    @Test
    void serviceRejectsInvalidDataBeforeChangingAnyField() {
        var note = service.create(userA.getId(), VaultNoteType.TRAINING, "Original", "Reflection", null, null);
        assertThatThrownBy(() -> service.update(note.getId(), userA.getId(), VaultNoteType.GOAL, "Changed", "Changed", null, null, "x".repeat(256), "GOOD"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(vaultNoteRepository.findById(note.getId()).orElseThrow().getTitle()).isEqualTo("Original");
        assertThatThrownBy(() -> service.create(userA.getId(), VaultNoteType.TRAINING, "x".repeat(121), "Reflection", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
