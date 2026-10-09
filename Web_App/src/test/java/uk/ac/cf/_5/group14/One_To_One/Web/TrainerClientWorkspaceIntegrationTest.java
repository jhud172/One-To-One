package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.UUID;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Messaging.*;
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
class TrainerClientWorkspaceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.MessageSource messageSource;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired TrainerClientLinkService relationships;
    @Autowired MessagingService messaging;
    @Autowired MessageThreadRepository threads;
    User trainer, otherTrainer, client, otherClient;

    private User account(String name, Role role) {
        String username = "workspace-" + UUID.randomUUID();
        var account = new User(username + "@example.invalid", name, "Fixture", username, "test-password");
        account.setRole(role);
        account.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() {
        trainer = account("Trainer", Role.TRAINER);
        otherTrainer = account("Other Trainer", Role.TRAINER);
        client = account("Client", Role.CLIENT);
        otherClient = account("Pending Client", Role.CLIENT);
    }
    private TrainerClientLink link(User member, TrainerClientLinkStatus status) {
        var link = links.saveAndFlush(new TrainerClientLink(member.getId(), trainer.getId(), status));
        messaging.ensureThreadForLink(link);
        return link;
    }
    private org.jsoup.nodes.Document render(String locale) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/trainer/clients").param("lang", locale)
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void change(String action, String flash) throws Exception {
        mvc.perform(post("/trainer/clients/{id}/" + action, client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/clients"))
                .andExpect(flash().attribute(flash, true));
    }

    @Test void translatedNativeActionsHaveOneCsrfFieldAndNamedConsequences() throws Exception {
        link(client, TrainerClientLinkStatus.ACTIVE);
        link(otherClient, TrainerClientLinkStatus.REQUESTED);
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var page = render(locale);
            var forms = page.select("main form[method=post]");
            assertThat(forms).hasSize(5);
            for (var form : forms) {
                assertThat(form.select("input[name=_csrf]")).hasSize(1);
                assertThat(form.selectFirst("button").attr("aria-label")).contains("Fixture");
                if (!form.attr("action").startsWith("/inbox/")) {
                    assertThat(form.attr("data-confirm")).contains("Fixture");
                    String action = form.attr("action").substring(form.attr("action").lastIndexOf('/') + 1);
                    assertThat(form.attr("data-confirm")).contains(messageSource.getMessage(
                            "v2.clients." + action + "Help", null, Locale.forLanguageTag(locale)));
                }
            }
            assertThat(page.select("details.client-relationship-help dl > div")).hasSize(4);
            assertThat(page.select("[data-client-row] .rounded-full").text()).isNotEqualTo("ACTIVE");
            assertThat(page.text()).doesNotContain("??v2.clients.");
        }
    }

    @Test void actualPostsAcceptPauseResumeEndAndRetainTheSameHistoryAndThread() throws Exception {
        var link = link(client, TrainerClientLinkStatus.REQUESTED);
        var thread = threads.findByLinkId(link.getId()).orElseThrow();
        Long threadId = thread.getId();
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.LOCKED);
        change("accept", "relationshipAccepted");
        var activated = links.findById(link.getId()).orElseThrow().getActivatedAt();
        assertThat(activated).isNotNull();
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.OPEN);
        change("pause", "relationshipPaused");
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.PAUSED);
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.LOCKED);
        assertThat(render("en").select("main form[action$='/resume']")).hasSize(1);
        change("resume", "relationshipResumed");
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.ACTIVE);
        assertThat(link.getActivatedAt()).isEqualTo(activated);
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.OPEN);
        change("end", "relationshipEnded");
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.ENDED);
        assertThat(link.getEndedAt()).isNotNull();
        assertThat(threads.findByLinkId(link.getId()).orElseThrow().getId()).isEqualTo(threadId);
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.LOCKED);
        assertThat(render("en").select("[data-client-row]")).isEmpty();
    }

    @Test void unverifiedTrainerSeesVerificationAndCannotSubmitDisabledActions() throws Exception {
        var link = link(client, TrainerClientLinkStatus.ACTIVE);
        link(otherClient, TrainerClientLinkStatus.REQUESTED);
        trainer.setTrainerVerified(false); users.saveAndFlush(trainer);
        var page = render("en");
        assertThat(page.select("main a[href='/trainer/verification']")).hasSize(1);
        assertThat(page.select(".client-row__actions a")).isEmpty();
        assertThat(page.select("main form button:not([disabled])")).isEmpty();
        mvc.perform(post("/trainer/clients/{id}/pause", client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl("/trainer/clients?error=trainer-unverified"));
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.ACTIVE);
    }

    @Test void missingCsrfAndAnotherTrainerCannotChangeOwnedRelationships() throws Exception {
        var link = link(client, TrainerClientLinkStatus.ACTIVE);
        mvc.perform(post("/trainer/clients/{id}/pause", client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/trainer/clients/{id}/pause", client.getId())
                .with(user(otherTrainer.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl("/trainer/clients?error=invalid"));
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.ACTIVE);
        assertThat(threads.findByLinkId(link.getId()).orElseThrow().getStatus()).isEqualTo(MessageThreadStatus.OPEN);
    }

    @Test void disabledOrNonClientAccountsCannotBeAcceptedOrResumed() {
        var link = link(client, TrainerClientLinkStatus.REQUESTED);
        client.setEnabled(false); users.saveAndFlush(client);
        assertThatThrownBy(() -> relationships.acceptRequest(trainer.getId(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.REQUESTED);
        link.setStatus(TrainerClientLinkStatus.PAUSED); links.saveAndFlush(link);
        assertThatThrownBy(() -> relationships.resumeLink(trainer.getId(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        client.setEnabled(true); client.setRole(Role.TRAINER); users.saveAndFlush(client);
        assertThatThrownBy(() -> relationships.resumeLink(trainer.getId(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        link.setStatus(TrainerClientLinkStatus.REQUESTED); links.saveAndFlush(link);
        assertThatThrownBy(() -> relationships.acceptRequest(trainer.getId(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(link.getStatus()).isEqualTo(TrainerClientLinkStatus.REQUESTED);
    }

    @Test void declineClosesOnlyTheOwnedPendingRequestAndPreservesHistory() throws Exception {
        var requested = link(client, TrainerClientLinkStatus.REQUESTED);
        var active = link(otherClient, TrainerClientLinkStatus.ACTIVE);
        Long threadId = threads.findByLinkId(requested.getId()).orElseThrow().getId();
        mvc.perform(post("/trainer/clients/{id}/decline", client.getId())
                .with(user(otherTrainer.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl("/trainer/clients?error=invalid"));
        assertThat(requested.getStatus()).isEqualTo(TrainerClientLinkStatus.REQUESTED);
        change("decline", "relationshipDeclined");
        assertThat(requested.getStatus()).isEqualTo(TrainerClientLinkStatus.ENDED);
        assertThat(requested.getActivatedAt()).isNull();
        var endedAt = requested.getEndedAt();
        assertThat(endedAt).isNotNull();
        var thread = threads.findByLinkId(requested.getId()).orElseThrow();
        assertThat(thread.getId()).isEqualTo(threadId);
        assertThat(thread.getStatus()).isEqualTo(MessageThreadStatus.LOCKED);
        assertThat(render("en").select("#clientRequests form")).isEmpty();
        for (Long id : new Long[]{client.getId(), otherClient.getId()}) {
            mvc.perform(post("/trainer/clients/{id}/decline", id)
                    .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
                    .andExpect(redirectedUrl("/trainer/clients?error=invalid"));
        }
        assertThat(requested.getEndedAt()).isEqualTo(endedAt);
        assertThat(active.getStatus()).isEqualTo(TrainerClientLinkStatus.ACTIVE);
    }

    @Test void historicalRequestsLinkUsesCanonicalWorkspace() throws Exception {
        mvc.perform(get("/trainer/requests").with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(redirectedUrl("/trainer/clients"));
    }
}
