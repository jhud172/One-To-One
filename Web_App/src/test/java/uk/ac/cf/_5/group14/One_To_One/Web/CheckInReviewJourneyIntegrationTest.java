package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Checkins.*;
import uk.ac.cf._5.group14.One_To_One.Goals.*;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationRepository;
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
class CheckInReviewJourneyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired WeeklyCheckInRepository checkIns;
    @Autowired GoalRepository goals;
    @Autowired NotificationRepository notifications;
    User trainer, client;
    TrainerClientLink link;
    WeeklyCheckIn checkIn;
    Goal goal;

    private User account(Role role) {
        String username = "review-" + UUID.randomUUID();
        var user = new User(username + "@example.invalid", "Local", "Fixture", username, "test-password");
        user.setRole(role); user.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        trainer = account(Role.TRAINER); client = account(Role.CLIENT);
        link = links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        checkIn = new WeeklyCheckIn(); checkIn.setTrainerId(trainer.getId()); checkIn.setClientId(client.getId());
        checkIn.setWeekStartDate(LocalDate.of(2026, 9, 28));
        checkIn.setResponsesJson("[{\"prompt\":\"Training <question>\",\"answer\":\"Literal <answer>\\nSecond line\"}]");
        checkIn.setClientNotes("Client note <note>\nKeep this context"); checkIn = checkIns.saveAndFlush(checkIn);
        goal = new Goal(); goal.setOwnerUser(client); goal.setCreatedByUser(trainer);
        goal.setTitle("Owned goal <goal>"); goal.setStatus(GoalStatus.ACTIVE); goal = goals.saveAndFlush(goal);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command() {
        return post("/checkins/trainer-review/{id}", checkIn.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf());
    }
    private org.jsoup.nodes.Document trainerPage(String locale) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/checkins/trainer-review/{id}", checkIn.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).param("lang", locale))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private org.jsoup.nodes.Document clientPage(String locale) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/checkins/client-review/{id}", checkIn.getId())
                .with(user(client.getUsername()).roles("CLIENT")).param("lang", locale))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void saveSharesResponseAndFocusWithOwnerAndOneLinkedAccountNotification() throws Exception {
        String reply = "Helpful <reply>\nKeep the second line";
        var submitted = mvc.perform(command().param("trainerResponse", reply).param("nextWeekFocus", "f".repeat(600))
                .param("goalId", goal.getId().toString()))
                .andExpect(redirectedUrl("/checkins/trainer-review/" + checkIn.getId()))
                .andExpect(flash().attribute("reviewSaved", true)).andReturn();
        var savedPage = org.jsoup.Jsoup.parse(mvc.perform(get("/checkins/trainer-review/{id}", checkIn.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).cookie(submitted.getResponse().getCookies()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(savedPage.select("main [role=status]").text()).contains("Response saved");
        assertThat(checkIn.getStatus()).isEqualTo(WeeklyCheckInStatus.RESPONDED);
        var savedAt = checkIn.getRespondedAt();
        var notification = notifications.findByUserOrderByCreatedAtDesc(client, Pageable.unpaged());
        assertThat(notification).hasSize(1);
        assertThat(notification.getFirst().getCtaUrl()).isEqualTo("/checkins/client-review/" + checkIn.getId());
        assertThat(notification.getFirst().getMessage()).doesNotContain(reply);
        assertThat(clientPage("en").select(".checkin-answer").eachText()).contains("Helpful <reply> Keep the second line", "f".repeat(600));
        mvc.perform(command().param("trainerResponse", reply).param("nextWeekFocus", "f".repeat(600)).param("goalId", goal.getId().toString()))
                .andExpect(flash().attribute("reviewSaved", true));
        assertThat(checkIn.getRespondedAt()).isEqualTo(savedAt);
        assertThat(notifications.findByUserOrderByCreatedAtDesc(client, Pageable.unpaged())).hasSize(1);
        goal.setStatus(GoalStatus.COMPLETED); goals.saveAndFlush(goal);
        assertThat(trainerPage("en").select("#coachCheckinGoal option[selected]").eachAttr("value"))
                .containsExactly(goal.getId().toString());
        mvc.perform(get("/checkins/client-submit").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/checkins/client-review/" + checkIn.getId())));
    }

    @Test void rejectedFocusKeepsEveryFieldAndCannotRespondOrNotify() throws Exception {
        var rejected = mvc.perform(command().param("trainerResponse", "Keep <draft>\nNext line")
                .param("nextWeekFocus", "x".repeat(601)).param("goalId", goal.getId().toString()))
                .andExpect(status().isBadRequest()).andReturn();
        var page = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(page.selectFirst("#coachCheckinResponse").wholeText()).isEqualTo("Keep <draft>\nNext line");
        assertThat(page.selectFirst("#coachCheckinFocus").wholeText()).isEqualTo("x".repeat(601));
        assertThat(page.select("#coachCheckinGoal option[selected]").eachAttr("value")).containsExactly(goal.getId().toString());
        assertThat(page.select("#coachCheckinFocus[aria-invalid=true]")).hasSize(1);
        assertThat(page.select("main [role=alert]")).hasSize(1);
        assertThat(checkIn.getStatus()).isEqualTo(WeeklyCheckInStatus.SUBMITTED);
        assertThat(checkIn.getRespondedAt()).isNull();
        assertThat(notifications.findByUserOrderByCreatedAtDesc(client, Pageable.unpaged())).isEmpty();
        mvc.perform(command().param("trainerResponse", " ").param("nextWeekFocus", " "))
                .andExpect(status().isBadRequest());
    }

    @Test void bothViewsEscapeAnswersShowClientNotesAndTranslateAcrossLocales() throws Exception {
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var trainerView = trainerPage(locale); var clientView = clientPage(locale);
            assertThat(trainerView.select("main form input[name=_csrf]")).hasSize(1);
            assertThat(trainerView.select(".checkin-answer").eachText()).contains("Literal <answer> Second line", "Client note <note> Keep this context");
            assertThat(clientView.select(".checkin-answer").eachText()).contains("Literal <answer> Second line", "Client note <note> Keep this context");
            assertThat(clientView.select("main form")).isEmpty();
            for (var page : new org.jsoup.nodes.Document[]{trainerView, clientView}) {
                assertThat(page.select("answer,note,question")).isEmpty();
                assertThat(page.text()).doesNotContain("??ui.checkin.");
            }
        }
    }

    @Test void damagedAnswersWarnWithoutLosingValidAnswersOrClientNotes() throws Exception {
        for (String json : new String[]{"null", "{broken", "[null,{\"prompt\":\"Available question\",\"answer\":\"Available answer\"}]"}) {
            checkIn.setResponsesJson(json); checkIns.saveAndFlush(checkIn);
            for (var page : new org.jsoup.nodes.Document[]{trainerPage("en"), clientPage("en")}) {
                assertThat(page.select("main [role=alert]").text()).contains("could not be displayed");
                assertThat(page.text()).contains("Client note <note>").doesNotContain("No responses captured");
                if (json.startsWith("[")) assertThat(page.text()).contains("Available question", "Available answer");
            }
        }
    }

    @Test void ownershipVerificationAndPausedRelationshipRejectReviewWrites() throws Exception {
        var otherClient = account(Role.CLIENT); var otherTrainer = account(Role.TRAINER);
        mvc.perform(get("/checkins/client-review/{id}", checkIn.getId()).with(user(otherClient.getUsername()).roles("CLIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/checkins/trainer-review/{id}", checkIn.getId()).with(user(otherTrainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isForbidden());
        trainer.setTrainerVerified(false); users.saveAndFlush(trainer);
        mvc.perform(command().param("trainerResponse", "Rejected unverified reply")).andExpect(status().isForbidden());
        trainer.setTrainerVerified(true); users.saveAndFlush(trainer);
        link.setStatus(TrainerClientLinkStatus.PAUSED); links.saveAndFlush(link);
        mvc.perform(command().param("trainerResponse", "Rejected paused reply")).andExpect(status().isForbidden());
        assertThat(checkIn.getTrainerResponse()).isNull();
        assertThat(notifications.findByUserOrderByCreatedAtDesc(client, Pageable.unpaged())).isEmpty();
        // A client retains access to their own saved history after coaching is paused.
        assertThat(clientPage("en").text()).contains("Client note <note>");
    }
}
