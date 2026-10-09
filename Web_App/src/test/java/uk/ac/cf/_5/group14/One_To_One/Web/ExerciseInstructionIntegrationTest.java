package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.*;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.*;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExerciseInstructionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutSessionRepository sessions;
    @Autowired ExerciseSessionRepository entries;
    @Autowired SetLogRepository sets;
    @Autowired uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExercises;
    User owner,other;
    Exercise exercise;
    WorkoutSession session;
    ExerciseSession first,second;
    String[] locales={"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"};

    @BeforeEach void setup() {
        owner=client();other=client();exercise=exercise("Saved movement <literal>");
        session=new WorkoutSession();session.setUser(owner);session.setDate(LocalDate.of(2026,10,4));
        session.setCreatedAt(java.time.LocalDateTime.of(2026,10,4,0,0));
        session.setNameSnapshot("Saved session <literal>");session=sessions.saveAndFlush(session);
        first=entry(exercise,0);second=entry(exercise,1);
        var custom=new uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise();custom.setUserId(owner.getId());custom.setName("Owned custom movement");
        custom=customExercises.save(custom);var customEntry=new ExerciseSession();customEntry.setWorkoutSession(session);
        customEntry.setOrderIndex(2);customEntry.setCustomExercise(custom);entries.saveAndFlush(customEntry);
    }

    @Test void realSessionLinksReturnToExactRepeatedExerciseAndCompletedContextWithoutCreatingWork() throws Exception {
        long beforeSessions=sessions.count(),beforeSets=sets.count();
        for(String locale:locales) {
            var player=mvc.perform(get("/workout-session/{id}",session.getId()).param("lang",locale)
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var html=Jsoup.parse(player.getResponse().getContentAsString());
            assertThat(html.select("[data-session-instruction]")).hasSize(2);
            var link=html.select("[data-session-instruction]").get(1);
            assertThat(link.attr("href")).contains("/exercise/"+exercise.getId(),"sessionId="+session.getId(),"entryId="+second.getId());
            var guide=mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId",session.getId().toString())
                    .param("entryId",second.getId().toString()).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn();
            html=Jsoup.parse(guide.getResponse().getContentAsString());
            assertThat(html.selectFirst(".instruction-context").text()).contains("Saved session <literal>","2026-10-04");
            assertThat(html.selectFirst(".detail-header a.detail-btn-primary").attr("href"))
                    .isEqualTo("/workout-session/"+session.getId()+"#exercise-title-"+second.getId());
            assertThat(html.text()).doesNotContain("??ui.instruction.");
        }
        session.setCompleted(true);sessions.saveAndFlush(session);
        mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId",session.getId().toString())
                .param("entryId",second.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("instructionReturnUrl","/workout-session/"+session.getId()+"/complete"));
        assertThat(sessions.count()).isEqualTo(beforeSessions);assertThat(sets.count()).isEqualTo(beforeSets);
    }

    @Test void contextRequiresOwnedSessionAndActualExerciseMembership() throws Exception {
        mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId",session.getId().toString())
                .with(user(other.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        var unrelated=exercise("Unrelated catalogue exercise");
        mvc.perform(get("/exercise/{id}",unrelated.getId()).param("sessionId",session.getId().toString())
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId",session.getId().toString()).param("entryId",Long.MAX_VALUE+"")
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId","bad-context").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/exercise/{id}",exercise.getId()).param("sessionId",session.getId().toString())
                .accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/exercise/{id}",Long.MAX_VALUE).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
    }

    @Test void writtenGuidanceStaysLiteralAndEmptyUnsafeOrExternalMediaNeverLoadsAutomatically() throws Exception {
        exercise.setDescription("Saved <technique>\nSecond authored line");exercise.setVideoUrl("javascript:alert(1)");exercises.saveAndFlush(exercise);
        for(String locale:locales) {
            var response=mvc.perform(get("/exercise/{id}",exercise.getId()).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn();var html=Jsoup.parse(response.getResponse().getContentAsString());
            assertThat(html.selectFirst(".plan-coaching-note").wholeText()).isEqualTo("Saved <technique>\nSecond authored line");
            assertThat(html.select("main iframe, main video, main audio")).isEmpty();
            assertThat(html.select(".instruction-media a")).isEmpty();
            assertThat(html.text()).contains("Strength","Bodyweight").doesNotContain("javascript:alert(1)","??ui.instruction.");
            assertThat(html.select(".instruction-context")).isEmpty();
        }
        exercise.setDescription("  ");exercise.setVideoUrl("https://example.invalid/authored-guide");exercises.saveAndFlush(exercise);
        var response=mvc.perform(get("/exercise/{id}",exercise.getId()).param("lang","en").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();var html=Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(html.text()).contains("No written guidance has been added","Ask your coach for technique and equipment guidance");
        var video=html.selectFirst(".instruction-media a");assertThat(video.attr("href")).isEqualTo("https://example.invalid/authored-guide");
        assertThat(video.attr("target")).isEqualTo("_blank");assertThat(video.attr("rel")).contains("noopener","noreferrer");
        assertThat(html.select("main iframe, main video, main audio")).isEmpty();
    }

    private User client() {
        String name="guide-"+UUID.randomUUID();var user=new User(name+"@example.invalid","Local","Client",name,"test-password");
        user.setRole(Role.CLIENT);return users.saveAndFlush(user);
    }
    private Exercise exercise(String name) {
        var exercise=new Exercise();exercise.setName(name);exercise.setCategory("Bodyweight");exercise.setType("Strength");exercise.setDifficulty(1);
        return exercises.saveAndFlush(exercise);
    }
    private ExerciseSession entry(Exercise exercise,int order) {
        var entry=new ExerciseSession();entry.setWorkoutSession(session);entry.setExercise(exercise);entry.setOrderIndex(order);
        return entries.saveAndFlush(entry);
    }
}
