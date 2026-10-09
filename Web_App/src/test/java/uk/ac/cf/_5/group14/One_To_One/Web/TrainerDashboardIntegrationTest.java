package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Checkins.*;
import uk.ac.cf._5.group14.One_To_One.Dashboard.TrainerDashboardService;
import uk.ac.cf._5.group14.One_To_One.Messaging.*;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerDashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired TrainerClientLinkRepository links;
    @Autowired WeeklyCheckInRepository checkIns;
    @Autowired MessageThreadRepository threads;
    @Autowired ThreadMessageRepository messages;
    @Autowired MessageReadStateRepository readStates;
    @Autowired TrainerDashboardService dashboard;
    @Autowired Clock clock;
    User trainer,client,otherClient;

    @BeforeEach void independentTrainer() {
        trainer=users.findByUsername("trainer_demo"); client=users.findByUsername("demo"); otherClient=users.findByUsername("user2");
        // Synthetic test state only; preserve existing rows while removing fixture relationships from this case.
        for(var link:links.findByTrainerIdOrderByUpdatedAtDesc(trainer.getId())) {
            link.setStatus(TrainerClientLinkStatus.ENDED); links.save(link);
        }
    }
    private TrainerClientLink link(User client,TrainerClientLinkStatus status) {
        return links.saveAndFlush(new TrainerClientLink(client.getId(),trainer.getId(),status));
    }
    private WeeklyCheckIn checkIn(User client,long trainerId,int week,int age,WeeklyCheckInStatus status) {
        var row=new WeeklyCheckIn(); row.setClientId(client.getId()); row.setTrainerId(trainerId);
        row.setWeekStartDate(LocalDate.of(2024,1,1).plusWeeks(week)); row.setStatus(status);
        row.setSubmittedAt(clock.instant().minus(age,ChronoUnit.DAYS));
        return checkIns.saveAndFlush(row);
    }
    private org.jsoup.nodes.Document render(String lang) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/trainer/dashboard").with(user("trainer_demo").roles("TRAINER")).param("lang",lang))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void realPrioritiesAreOwnedActiveOldestFirstBoundedAndLabelledInEveryLocale() throws Exception {
        link(client,TrainerClientLinkStatus.ACTIVE); link(otherClient,TrainerClientLinkStatus.REQUESTED);
        for(int i=1;i<=7;i++) checkIn(client,trainer.getId(),i,i,WeeklyCheckInStatus.SUBMITTED);
        checkIn(client,trainer.getId(),8,30,WeeklyCheckInStatus.RESPONDED);
        checkIn(otherClient,trainer.getId(),9,40,WeeklyCheckInStatus.SUBMITTED);
        checkIn(client,users.findByUsername("gymadmin_demo").getId(),10,50,WeeklyCheckInStatus.SUBMITTED);
        var summary=dashboard.forTrainer(trainer);
        assertThat(summary.activeClients()).isEqualTo(1); assertThat(summary.pendingRequests()).isEqualTo(1);
        assertThat(summary.awaitingReview()).isEqualTo(7); assertThat(summary.waitingSevenDays()).isEqualTo(1);
        assertThat(summary.reviews()).extracting(TrainerDashboardService.Review::waitingDays).containsExactly(7L,6L,5L,4L,3L);
        long readBefore=readStates.count();
        for(String lang:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc=render(lang);
            assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.select(".trainer-review-list__row")).hasSize(5);
            assertThat(doc.select(".trainer-review-queue").text()).doesNotContain("ui.trainerHome.");
            assertThat(doc.selectFirst(".trainer-review-queue .dashboard-action--primary").attr("href"))
                    .isEqualTo("/checkins/trainer-review/"+summary.reviews().getFirst().id());
            assertThat(doc.select(".trainer-review-list__row a[aria-label]")).hasSize(5);
            assertThat(doc.selectFirst("details.trainer-personal-day").hasAttr("open")).isFalse();
            assertThat(doc.selectFirst("details.trainer-personal-day summary").text()).isNotBlank();
        }
        assertThat(readStates.count()).isEqualTo(readBefore);
    }

    @Test void unreadMessagesRequireCurrentMatchingLinkAndChangeOnlyWhenInboxIsOpened() throws Exception {
        var active=link(client,TrainerClientLinkStatus.ACTIVE);
        var ended=link(otherClient,TrainerClientLinkStatus.ENDED);
        var thread=threads.saveAndFlush(new MessageThread(client.getId(),trainer.getId(),active.getId(),MessageThreadStatus.OPEN));
        var oldThread=threads.saveAndFlush(new MessageThread(otherClient.getId(),trainer.getId(),ended.getId(),MessageThreadStatus.OPEN));
        var incoming=messages.saveAndFlush(new Message(thread,client.getId(),MessageType.TEXT,"Synthetic unread check"));
        var alreadyRead=messages.saveAndFlush(new Message(thread,client.getId(),MessageType.TEXT,"Already reviewed"));
        readStates.saveAndFlush(new MessageReadState(alreadyRead.getId(),thread.getId(),trainer.getId(),Instant.now()));
        messages.saveAndFlush(new Message(thread,trainer.getId(),MessageType.TEXT,"Own synthetic message"));
        messages.saveAndFlush(new Message(oldThread,otherClient.getId(),MessageType.TEXT,"Historic unread check"));
        checkIn(client,trainer.getId(),1,8,WeeklyCheckInStatus.SUBMITTED);
        assertThat(dashboard.forTrainer(trainer).unreadMessages()).isEqualTo(1);
        long before=readStates.count(); render("en"); assertThat(readStates.count()).isEqualTo(before);
        mvc.perform(get("/inbox/"+thread.getId()).with(user("trainer_demo").roles("TRAINER"))).andExpect(status().isOk());
        assertThat(dashboard.forTrainer(trainer).unreadMessages()).isZero();
        assertThat(readStates.findByUserIdAndMessageIdIn(trainer.getId(),List.of(incoming.getId()))).hasSize(1);
        active.setStatus(TrainerClientLinkStatus.PAUSED); links.saveAndFlush(active);
        var paused=dashboard.forTrainer(trainer);
        assertThat(paused.awaitingReview()).isZero(); assertThat(paused.reviews()).isEmpty();
        assertThat(paused.unreadMessages()).isZero();
    }

    @Test void emptyWorkspaceKeepsNativeClientAndPersonalScheduleActions() throws Exception {
        var doc=render("en");
        assertThat(doc.select(".trainer-review-list__row")).isEmpty();
        assertThat(doc.selectFirst(".trainer-review-queue .dashboard-empty-note").text()).contains("No submitted check-ins");
        assertThat(doc.selectFirst(".trainer-review-queue .dashboard-action--primary").attr("href")).isEqualTo("/trainer/clients");
        assertThat(doc.select("details.trainer-personal-day a[href^=/calendar]")).isNotEmpty();
        mvc.perform(get("/trainer/dashboard").with(user("demo").roles("CLIENT")))
                .andExpect(redirectedUrl("/access-denied"));
    }

    @Test void unverifiedTrainerGetsVerificationBeforePrivateClientReviewActions() throws Exception {
        link(client,TrainerClientLinkStatus.ACTIVE);
        checkIn(client,trainer.getId(),1,8,WeeklyCheckInStatus.SUBMITTED);
        trainer.setTrainerVerified(false); userRepository.saveAndFlush(trainer);
        var doc=render("en");
        assertThat(doc.select(".trainer-client-priorities,.trainer-review-list,#trainer-reviews")).isEmpty();
        assertThat(doc.selectFirst(".trainer-review-queue .dashboard-action--primary").attr("href"))
                .isEqualTo("/trainer/verification");
        assertThat(doc.select(".trainer-review-queue").text()).contains("needs verification");
        assertThat(doc.select("details.trainer-personal-day a[href^=/calendar]")).isNotEmpty();
    }
}
