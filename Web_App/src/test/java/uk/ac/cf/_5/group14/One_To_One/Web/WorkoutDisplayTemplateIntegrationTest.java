package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.WorkoutTemplate.*;
import uk.ac.cf._5.group14.One_To_One.UserSettings.*;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkoutDisplayTemplateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired WorkoutUiTemplateRepository templates;
    @Autowired WorkoutTemplateService service;
    @Autowired WorkoutDisplayConfig config;
    @Autowired UserSettingsService settings;
    @Autowired UserSettingsRepository settingsRepository;
    @Autowired WorkoutPlayerSessionRepository sessions;
    @Autowired uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository workouts;
    @Autowired uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutBuilderService personal;
    @Autowired uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository scheduled;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private WorkoutTemplate template(String owner) {
        WorkoutTemplate item = new WorkoutTemplate(); item.setUser(owner == null ? null : users.findByUsername(owner));
        item.setName("Layout <example>"); item.setLayoutType(TemplateLayoutType.FUTURISTIC_FLOW);
        item.setConfigJson(config.serialise(config.defaults(item.getLayoutType())));
        return templates.save(item);
    }
    private org.jsoup.nodes.Document doc(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test void allLocalesRenderLabelledNativeFormsAndSeparateSamplePreviews() throws Exception {
        template("demo"); template(null);
        for (String lang : List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var list=doc(mvc.perform(get("/workout-templates").param("lang",lang).with(user("demo").roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn());
            assertThat(list.select("main")).hasSize(1);
            assertThat(list.select(".display-preview")).isNotEmpty();
            assertThat(list.select("form[action$=/prefer]")).isNotEmpty();
            assertThat(list.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            for (var form:list.select(".display-studio form")) assertThat(form.select("input[name=_csrf]")).hasSize(1);
            var builder=doc(mvc.perform(get("/workout-templates/builder").param("lang",lang).with(user("demo").roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn());
            for(var input:builder.select(".display-builder__settings input,.display-builder__settings select"))
                assertThat(builder.select("label[for="+input.id()+"]")).hasSize(1);
            assertThat(builder.selectFirst("#display-template-form").select("input[name=_csrf]")).hasSize(1);
            assertThat(builder.selectFirst(".display-builder__help").text()).doesNotContain("ui.display.");
        }
    }

    @Test void nativeCommandsRetainSettingsAndOrderWithoutCreatingRecords() throws Exception {
        long before=templates.count();
        var result=mvc.perform(post("/workout-templates").with(user("demo").roles("CLIENT")).with(csrf())
                .param("displayForm","true").param("name","Draft <name>").param("layoutType","CUSTOM")
                .param("theme","dark-pro").param("density","spacious").param("transition","fade")
                .param("components","exerciseCard","setEntry","notes").param("editAction","up:notes"))
                .andExpect(status().isOk()).andReturn();
        var page=doc(result);
        assertThat(page.select("#display-canvas input[name=components]").eachAttr("value")).containsExactly("exerciseCard","notes","setEntry");
        assertThat(page.selectFirst("#prop-name").val()).isEqualTo("Draft <name>");
        assertThat(page.select("#prop-theme option[selected]").attr("value")).isEqualTo("dark-pro");
        assertThat(page.select("#prop-density option[selected]").attr("value")).isEqualTo("spacious");
        assertThat(templates.count()).isEqualTo(before);
    }

    @Test void nativeSaveReloadsBoundedConfigurationAndRejectedUpdateDoesNotMutate() throws Exception {
        mvc.perform(post("/workout-templates").with(user("demo").roles("CLIENT")).with(csrf())
                .param("displayForm","true").param("name"," Saved layout ").param("layoutType","CUSTOM")
                .param("theme","dark-pro").param("density","spacious").param("transition","fade")
                .param("progress","on").param("components","notes","setEntry","exerciseCard"))
                .andExpect(redirectedUrl("/workout-templates")).andExpect(flash().attribute("displayFeedback","ui.display.saved"));
        var item=templates.findByUserOrderByUpdatedAtDesc(users.findByUsername("demo")).getFirst();
        assertThat(item.getName()).isEqualTo("Saved layout");
        var parsed=config.parse(item.getLayoutType(),item.getConfigJson());
        assertThat(parsed.components()).containsExactly("notes","setEntry","exerciseCard");
        assertThat(parsed.restTimer()).isFalse();
        var page=doc(mvc.perform(get("/workout-templates/builder/"+item.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.select("#prop-density option[selected]").attr("value")).isEqualTo("spacious");
        String before=item.getConfigJson();
        mvc.perform(post("/workout-templates/"+item.getId()).with(user("demo").roles("CLIENT")).with(csrf())
                .param("name","Rejected replacement").param("layoutType","CUSTOM").param("configJson","{\"theme\":\"<script>\"}"))
                .andExpect(status().isOk()).andExpect(model().attribute("displayError","ui.display.invalid"));
        assertThat(item.getName()).isEqualTo("Saved layout"); assertThat(item.getConfigJson()).isEqualTo(before);
        mvc.perform(post("/workout-templates/"+item.getId()).with(user("demo").roles("CLIENT")).with(csrf())
                .param("name"," ").param("layoutType","CUSTOM").param("configJson",before))
                .andExpect(status().isOk()).andExpect(model().attribute("displayError","ui.display.invalid"));
        assertThat(item.getName()).isEqualTo("Saved layout");
    }

    @Test void globalLayoutsAreCopyOnlyAndForeignLayoutsArePrivateForEveryAction() throws Exception {
        var global=template(null); var foreign=template("trainer_demo");
        for(var item:List.of(global,foreign)) {
            mvc.perform(get("/workout-templates/builder/"+item.getId()).with(user("demo").roles("CLIENT"))).andExpect(status().isNotFound());
            mvc.perform(post("/workout-templates/"+item.getId()).with(user("demo").roles("CLIENT")).with(csrf()).param("name","Hacked"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/workout-templates/"+item.getId()+"/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                    .andExpect(status().isNotFound());
            assertThat(item.getName()).isEqualTo("Layout <example>");
        }
        mvc.perform(get("/workout-templates/builder").param("copy",foreign.getId().toString()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
        var copy=doc(mvc.perform(get("/workout-templates/builder").param("copy",global.getId().toString()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn());
        assertThat(copy.selectFirst("#display-template-form").attr("action")).isEqualTo("/workout-templates");
        assertThat(copy.selectFirst("#prop-name").val()).isEqualTo(global.getName());
        for(String route:List.of("/prefer","/set-preferred"))
            mvc.perform(post("/workout-templates/"+foreign.getId()+route).with(user("demo").roles("CLIENT")).with(csrf()))
                    .andExpect(status().isNotFound());
    }

    @Test void preferenceAppliesToBothActualPlayersAndForeignStoredPreferenceFallsBack() throws Exception {
        var client=users.findByUsername("demo"); var own=template("demo");
        mvc.perform(post("/workout-templates/"+own.getId()+"/prefer").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workout-templates"));
        assertThat(settings.getOrCreate(client).getPreferredWorkoutTemplateId()).isEqualTo(own.getId());
        var form=new uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplateForm(); form.setName("Appearance proof");
        var movement=new uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutExerciseForm(); movement.setExerciseName("Movement"); form.getExercises().add(movement);
        var personalTemplate=personal.createTemplate(client,form); var player=personal.startSession(client,personalTemplate.getId());
        var scheduledPlayer=new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession(); scheduledPlayer.setUser(client);
        scheduledPlayer.setNameSnapshot("Scheduled appearance proof"); scheduledPlayer.setDate(java.time.LocalDate.now());
        scheduledPlayer.setCreatedAt(java.time.LocalDateTime.now()); scheduled.save(scheduledPlayer);
        for(String path:List.of("/workouts/studio/"+player.getId(),"/workout-session/"+scheduledPlayer.getId())) {
            var page=doc(mvc.perform(get(path).with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn());
            assertThat(page.selectFirst(".session-workout").attr("data-display-theme")).isEqualTo("futuristic");
            assertThat(page.selectFirst(".session-workout").attr("data-display-layout")).isEqualTo("futuristic_flow");
        }
        var foreign=template("trainer_demo"); settings.updatePreferredWorkoutTemplate(client,foreign.getId());
        assertThat(service.getDefaultTemplateForUser(client.getId()).getId()).isNotEqualTo(foreign.getId());
    }

    @Test void deletionPreservesReferencedSessionAndClearsPreferenceForUnusedLayout() throws Exception {
        var client=users.findByUsername("demo"); var used=template("demo");
        var workout=new uk.ac.cf._5.group14.One_To_One.Workout.Workout(); workout.setUserId(client.getId()); workout.setName("Reference proof"); workouts.save(workout);
        var session=new WorkoutSession(); session.setUser(client); session.setWorkout(workout); session.setTemplateUsed(used);
        session.setTemplateNameSnapshot(used.getName()); session.setConfigJsonSnapshot(used.getConfigJson()); session.setStartedAt(java.time.Instant.now()); sessions.save(session); entityManager.flush();
        mvc.perform(post("/workout-templates/"+used.getId()+"/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workout-templates")).andExpect(flash().attribute("displayError","ui.display.deleteBlocked"));
        assertThat(templates.existsById(used.getId())).isTrue(); assertThat(sessions.existsById(session.getId())).isTrue();
        var unused=template("demo"); settings.updatePreferredWorkoutTemplate(client,unused.getId());
        mvc.perform(post("/workout-templates/"+unused.getId()+"/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workout-templates")); entityManager.flush(); entityManager.clear();
        assertThat(templates.existsById(unused.getId())).isFalse();
        assertThat(settingsRepository.findById(client.getId()).orElseThrow().getPreferredWorkoutTemplateId()).isNull();
    }

    @Test void duplicateUnknownAndOversizedConfigurationsNeverPersistAndCsrfStillProtectsSave() throws Exception {
        long before=templates.count();
        for(String source:List.of("{\"components\":[\"notes\",\"notes\"]}","{\"components\":[\"unknown\"]}","[1]"," "+"x".repeat(16384)))
            mvc.perform(post("/workout-templates").with(user("demo").roles("CLIENT")).with(csrf()).param("name","Invalid")
                    .param("configJson",source)).andExpect(status().isOk()).andExpect(model().attribute("displayError","ui.display.invalid"));
        mvc.perform(post("/workout-templates").with(user("demo").roles("CLIENT")).param("name","Without token"))
                .andExpect(status().is4xxClientError());
        assertThat(templates.count()).isEqualTo(before);
    }

    @Test void unsupportedStoredSettingsShowSafeFeedbackWithoutChangingTheStoredBlueprint() throws Exception {
        var item=template("demo"); item.setConfigJson("{\"theme\":\"future-theme\",\"customMarkup\":\"<script>\"}"); templates.save(item);
        String before=item.getConfigJson();
        var page=doc(mvc.perform(get("/workout-templates/builder/"+item.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("displayError","ui.display.invalid")).andReturn());
        assertThat(page.selectFirst("#prop-name").val()).isEqualTo(item.getName());
        assertThat(page.select("#prop-theme option[selected]").attr("value")).isEqualTo("futuristic");
        assertThat(page.text()).doesNotContain("customMarkup"); assertThat(item.getConfigJson()).isEqualTo(before);
        for(String source:List.of("{\"customMarkup\":\"anything\"}","{\"layout\":\"unknown\"}","{\"progress\":\"true\"}"))
            mvc.perform(post("/workout-templates/"+item.getId()).with(user("demo").roles("CLIENT")).with(csrf())
                    .param("name","Do not replace").param("configJson",source))
                    .andExpect(status().isOk()).andExpect(model().attribute("displayError","ui.display.invalid"));
        assertThat(item.getConfigJson()).isEqualTo(before); assertThat(item.getName()).isEqualTo("Layout <example>");
    }
}
