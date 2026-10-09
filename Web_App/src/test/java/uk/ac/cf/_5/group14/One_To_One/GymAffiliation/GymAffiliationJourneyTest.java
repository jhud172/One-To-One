package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.GymProfile.*;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Verification.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class GymAffiliationJourneyTest {
    @Autowired private GymAffiliationService service;
    @Autowired private GymAffiliationRepository affiliations;
    @Autowired private GymAffiliationEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private GymProfileRepository gyms;
    @Autowired private MockMvc mvc;
    @Autowired private TrainerVerificationService verification;
    @Autowired private TrainerVerificationRequestRepository reviews;
    @MockitoBean private EmailService mail;

    private User account(Role role) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User(key + "@example.test", "Network", "Fixture", "net_" + key, "fixture-only");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private GymProfile gym(User admin) {
        GymProfile gym = new GymProfile(admin.getId(), "Network " + admin.getUsername());
        gym.setGymCode(String.format("%016d", admin.getId()));
        gym = gyms.saveAndFlush(gym);
        admin.setGymId(gym.getId()); users.saveAndFlush(admin);
        return gym;
    }

    @Test
    void twoGymConsentAndEndingOnePreserveTheOtherAndVerification() {
        User trainer = account(Role.TRAINER), first = account(Role.GYM_ADMIN), second = account(Role.GYM_ADMIN);
        GymProfile firstGym = gym(first), secondGym = gym(second);
        trainer.setTrainerVerified(true); users.saveAndFlush(trainer);
        service.invite(first.getId(), trainer.getUsername());
        assertThat(users.isTrainerAffiliatedWithGym(trainer.getId(), firstGym.getId())).isFalse();
        assertThat(users.findByRoleAndGymId(Role.TRAINER, firstGym.getId())).isEmpty();
        assertThatThrownBy(() -> service.decide(first.getId(), trainer.getId(), firstGym.getId(), "accept")).isInstanceOf(IllegalStateException.class);
        service.decide(trainer.getId(), trainer.getId(), firstGym.getId(), "accept");
        service.requestToJoin(trainer.getId(), second.getUsername());
        service.decide(second.getId(), trainer.getId(), secondGym.getId(), "accept");
        assertThat(users.findByRoleAndGymId(Role.TRAINER, secondGym.getId())).extracting(User::getId).containsExactly(trainer.getId());
        service.decide(trainer.getId(), trainer.getId(), firstGym.getId(), "end");
        assertThat(users.isTrainerAffiliatedWithGym(trainer.getId(), firstGym.getId())).isFalse();
        assertThat(users.isTrainerAffiliatedWithGym(trainer.getId(), secondGym.getId())).isTrue();
        User fresh = users.findById(trainer.getId()).orElseThrow();
        assertThat(fresh.getGymId()).isNull();
        assertThat(fresh.isTrainerVerified()).isTrue();
        verifyNoInteractions(mail);
    }

    @Test
    void legacyEndingNeverReappearsAndHistoryIsScoped() throws Exception {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN), outsider = account(Role.GYM_ADMIN);
        GymProfile gym = gym(admin); gym(outsider);
        trainer.setGymId(gym.getId()); users.saveAndFlush(trainer);
        assertThat(service.connections(trainer)).singleElement().satisfies(connection -> assertThat(connection.legacy()).isTrue());
        service.decide(admin.getId(), trainer.getId(), gym.getId(), "end");
        assertThat(users.findByRoleAndGymId(Role.TRAINER, gym.getId())).isEmpty();
        assertThat(users.findById(trainer.getId()).orElseThrow().getGymId()).isEqualTo(gym.getId());
        assertThat(service.history(trainer, trainer.getId(), gym.getId())).extracting(GymAffiliationEvent::getAction).containsExactly("ENDED", "LEGACY_IMPORTED");
        assertThatThrownBy(() -> service.decide(outsider.getId(), trainer.getId(), gym.getId(), "end")).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(get("/trainer/gyms/history").with(user(trainer.getUsername()).roles("TRAINER"))
                .param("trainerId", trainer.getId().toString()).param("gymId", gym.getId().toString()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("affiliation-history-title")))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/gym/admin/trainers/affiliations/history").with(user(outsider.getUsername()).roles("GYM_ADMIN"))
                .param("trainerId", trainer.getId().toString()).param("gymId", gym.getId().toString()))
            .andExpect(status().isNotFound());
    }

    @Test
    void concurrentFirstRequestsProduceOnePairAndOneEvent() throws Exception {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN);
        GymProfile gym = gym(admin);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> request = () -> {
            start.await(5, TimeUnit.SECONDS);
            try { service.requestToJoin(trainer.getId(), admin.getUsername()); return true; }
            catch (IllegalStateException alreadyExists) { return false; }
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = workers.submit(request), second = workers.submit(request);
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        var row = affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId()).orElseThrow();
        assertThat(events.findByAffiliationIdOrderByCreatedAtDescIdDesc(row.getId())).hasSize(1);
    }

    @Test
    void declinedRequestsCanReopenWithoutLosingTheirHistory() {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN);
        GymProfile gym = gym(admin);
        service.requestToJoin(trainer.getId(), admin.getUsername());
        Long id = affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId()).orElseThrow().getId();
        service.decide(admin.getId(), trainer.getId(), gym.getId(), "decline");
        service.invite(admin.getId(), trainer.getUsername());
        assertThat(affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId()).orElseThrow().getId()).isEqualTo(id);
        service.decide(trainer.getId(), trainer.getId(), gym.getId(), "decline");
        assertThat(events.findByAffiliationIdOrderByCreatedAtDescIdDesc(id)).hasSize(4);
    }

    @Test
    void outgoingPartyCannotAcceptAndOnlyThatPartyCanCancel() {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN);
        GymProfile gym = gym(admin);
        service.requestToJoin(trainer.getId(), admin.getUsername());
        assertThatThrownBy(() -> service.decide(trainer.getId(), trainer.getId(), gym.getId(), "accept")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.decide(admin.getId(), trainer.getId(), gym.getId(), "cancel")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.decide(trainer.getId(), trainer.getId(), gym.getId(), null)).isInstanceOf(IllegalArgumentException.class);
        service.decide(trainer.getId(), trainer.getId(), gym.getId(), "cancel");
        assertThatThrownBy(() -> service.decide(admin.getId(), trainer.getId(), gym.getId(), "accept")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void routeRequiresRoleCsrfAndConfirmationAndKeepsInvalidDraft() throws Exception {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN), client = account(Role.CLIENT);
        GymProfile gym = gym(admin);
        mvc.perform(get("/trainer/gyms").with(user(client.getUsername()).roles("CLIENT"))).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/trainer/gyms/request").with(user(trainer.getUsername()).roles("TRAINER")).param("gymUsername", admin.getUsername()))
            .andExpect(status().isUnauthorized());
        assertThat(affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId())).isEmpty();
        mvc.perform(post("/trainer/gyms/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("gymUsername", "bad-code"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-code\"")));
        service.invite(admin.getId(), trainer.getUsername());
        mvc.perform(get("/trainer/gyms").with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"accept\"")))
            .andExpect(content().string(containsString("/css/bundles/training.css")));
        mvc.perform(post("/trainer/gyms/decide").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("trainerId", trainer.getId().toString()).param("gymId", gym.getId().toString()).param("action", "accept"))
            .andExpect(status().isBadRequest());
        assertThat(users.isTrainerAffiliatedWithGym(trainer.getId(), gym.getId())).isFalse();
        mvc.perform(post("/trainer/gyms/decide").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("trainerId", trainer.getId().toString()).param("gymId", gym.getId().toString()).param("action", "accept").param("confirmed", "true"))
            .andExpect(status().is3xxRedirection());
        assertThat(users.isTrainerAffiliatedWithGym(trainer.getId(), gym.getId())).isTrue();
        mvc.perform(get("/gym/admin/trainers/affiliations").with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Network Fixture")));
    }

    @Test
    void missingGymAndNonexistentHistoryUseControlledResponses() throws Exception {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN);
        mvc.perform(get("/gym/admin/trainers/affiliations").with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/trainer/gyms/history").with(user(trainer.getUsername()).roles("TRAINER"))
                .param("trainerId", trainer.getId().toString()).param("gymId", "99999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletingTrainerCascadesPrivateAffiliationHistory() {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN);
        GymProfile gym = gym(admin);
        service.invite(admin.getId(), trainer.getUsername());
        Long id = affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId()).orElseThrow().getId();
        users.deleteById(trainer.getId()); users.flush();
        assertThat(affiliations.findById(id)).isEmpty();
        assertThat(events.findByAffiliationIdOrderByCreatedAtDescIdDesc(id)).isEmpty();
    }

    @Test
    void endedGymCannotReadOrResubmitPrivateVerificationEvidence() {
        User trainer = account(Role.TRAINER), admin = account(Role.GYM_ADMIN), reviewer = account(Role.PLATFORM_ADMIN);
        GymProfile gym = gym(admin);
        service.requestToJoin(trainer.getId(), admin.getUsername());
        service.decide(admin.getId(), trainer.getId(), gym.getId(), "accept");
        var request = verification.createVerificationRequest(trainer.getId(), gym.getId(), "Original evidence");
        verification.requestMoreInfo(request.getId(), reviewer.getId(), "More detail needed");
        service.decide(trainer.getId(), trainer.getId(), gym.getId(), "end");
        assertThatThrownBy(() -> verification.getRequestForGym(request.getId(), gym.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verification.updateTrainerNotesForGym(request.getId(), gym.getId(), "Changed evidence")).isInstanceOf(IllegalArgumentException.class);
        var retained = reviews.findById(request.getId()).orElseThrow();
        assertThat(retained.getNotes()).isEqualTo("Original evidence");
        assertThat(retained.getStatus()).isEqualTo(VerificationStatus.NEEDS_INFO);
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isFalse();
    }
}
