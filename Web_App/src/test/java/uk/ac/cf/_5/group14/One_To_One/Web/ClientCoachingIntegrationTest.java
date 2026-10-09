package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.*;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplate;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplateRepository;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientCoachingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired TrainerClientLinkService relationshipService;
    @Autowired CoachingPhaseChangeRepository phases;
    @Autowired WorkoutTemplateRepository workouts;
    @Autowired ScheduleRepository schedules;
    @Autowired AssignedWorkoutRepository assignedWorkouts;
    @Autowired AssignedScheduleRepository assignedSchedules;
    @Autowired uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInRepository checkIns;
    @Autowired uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadRepository threads;
    @Autowired ScheduleEntryRepository scheduleEntries;
    @Autowired uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExercises;
    User trainer, client;
    TrainerClientLink link;
    WorkoutTemplate workout;
    Schedule schedule;

    private User account(String name, Role role) {
        var username = "coaching-" + UUID.randomUUID();
        var user = new User(username + "@example.invalid", name, "Fixture", username, "test-password");
        user.setRole(role); user.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        trainer = account("Trainer", Role.TRAINER); client = account("Client", Role.CLIENT);
        link = new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE);
        link.setCoachingPhase(CoachingPhase.BUILD);
        link.setCoachingPhaseStartedAt(Instant.now().minusSeconds(86400));
        link = links.saveAndFlush(link);
        workout = new WorkoutTemplate(); workout.setOwnerUser(trainer); workout.setOwnerRole(Role.TRAINER);
        workout.setName("Owned workout <draft>"); workout = workouts.saveAndFlush(workout);
        schedule = new Schedule(); schedule.setUser(trainer); schedule.setName("Owned schedule <draft>");
        schedule = schedules.save(schedule);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(String action) {
        return post("/trainer/clients/{id}/" + action, client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf());
    }
    private org.jsoup.nodes.Document render(String locale) throws Exception {
        return render(null, locale);
    }
    private org.jsoup.nodes.Document render(org.springframework.test.web.servlet.MvcResult submitted, String locale) throws Exception {
        var request = get("/trainer/clients/{id}", client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).param("lang", locale);
        // Follow the real JDBC session cookie; MockMvc flashAttrs uses a mock session
        // which the application's Spring Session filter replaces.
        if (submitted != null) request.cookie(submitted.getResponse().getCookies());
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        if (submitted != null) for (var entry : submitted.getFlashMap().entrySet()) {
            assertThat(result.getModelAndView().getModel()).containsEntry(entry.getKey(), entry.getValue());
        }
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test void oversizedAssignmentNotesRetainSelectionAndLiteralInputWithoutSaving() throws Exception {
        String notes = "<draft>" + "x".repeat(795);
        for (String kind : new String[]{"workout", "schedule"}) {
            Long id = kind.equals("workout") ? workout.getId() : schedule.getId();
            String parameter = kind.equals("workout") ? "templateId" : "scheduleId";
            var result = mvc.perform(command("assign-" + kind).param(parameter, id.toString()).param("trainerNotes", notes))
                    .andExpect(redirectedUrl("/trainer/clients/" + client.getId() + "#clientAssignments"))
                    .andExpect(flash().attribute("workspaceError", true))
                    .andExpect(flash().attribute(kind + "DraftNotes", notes)).andReturn();
            var page = render(result, "en");
            assertThat(page.select("main [role=alert]")).hasSize(1);
            assertThat(page.select("main [role=status]")).isEmpty();
            assertThat(page.selectFirst("textarea[name=trainerNotes]" + (kind.equals("schedule") ? "#clientScheduleNotes" : "#clientWorkoutNotes")).text()).isEqualTo(notes);
            assertThat(page.select("select#" + (kind.equals("schedule") ? "clientSchedule" : "clientWorkoutTemplate") + " option[selected]")).hasSize(1);
            assertThat(page.select("draft")).isEmpty();
        }
        assertThat(assignedWorkouts.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainer.getId(), client.getId())).isEmpty();
        assertThat(assignedSchedules.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainer.getId(), client.getId())).isEmpty();
    }

    @Test void assignmentBoundarySavesOwnedNotesAndShowsActualSuccess() throws Exception {
        String notes = "n".repeat(800);
        var result = mvc.perform(command("assign-workout").param("templateId", workout.getId().toString()).param("trainerNotes", notes))
                .andExpect(redirectedUrl("/trainer/clients/" + client.getId() + "#clientAssignments"))
                .andExpect(flash().attribute("workspaceOutcome", "workout")).andReturn();
        assertThat(render(result, "en").select("main [role=status]").text()).contains("Workout assigned");
        assertThat(assignedWorkouts.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainer.getId(), client.getId()).getFirst().getTrainerNotes()).isEqualTo(notes);
        mvc.perform(command("assign-schedule").param("scheduleId", schedule.getId().toString()).param("trainerNotes", " " + notes + " "))
                .andExpect(flash().attribute("workspaceOutcome", "schedule"));
        assertThat(assignedSchedules.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainer.getId(), client.getId()).getFirst().getTrainerNotes()).isEqualTo(notes);
        mvc.perform(command("assign-workout").param("trainerNotes", "Keep this missing-selection draft"))
                .andExpect(flash().attribute("workspaceError", true))
                .andExpect(flash().attribute("workoutDraftNotes", "Keep this missing-selection draft"));
    }

    @Test void invalidPhaseRetainsDraftAndDoesNotCreateAuditHistory() throws Exception {
        var started = link.getCoachingPhaseStartedAt();
        for (var inputs : new String[][]{{"RECOVERY", "l".repeat(121), "Literal <note>"}, {"RECOVERY", "Recovery", "n".repeat(801)}, {"", "Keep label", "Keep note"}}) {
            var request = command("phase").param("customLabel", inputs[1]).param("notes", inputs[2]);
            if (!inputs[0].isEmpty()) request.param("phase", inputs[0]);
            var result = mvc.perform(request)
                    .andExpect(flash().attribute("workspaceError", true))
                    .andExpect(flash().attribute("phaseLabelDraft", inputs[1]))
                    .andExpect(flash().attribute("phaseNoteDraft", inputs[2])).andReturn();
            var page = render(result, "en");
            assertThat(page.selectFirst("#clientPhaseLabel").val()).isEqualTo(inputs[1]);
            assertThat(page.selectFirst("#clientPhaseNote").text()).isEqualTo(inputs[2]);
            assertThat(page.select("#clientCoachingPhase option[selected]")).hasSize(1);
        }
        assertThat(phases.findByLinkIdOrderByChangedAtDesc(link.getId())).isEmpty();
        assertThat(link.getCoachingPhase()).isEqualTo(CoachingPhase.BUILD);
        assertThat(link.getCoachingPhaseStartedAt()).isEqualTo(started);
    }

    @Test void phaseBoundarySavesAuditAndInactiveClientsCannotReceiveAssignments() throws Exception {
        var started = link.getCoachingPhaseStartedAt();
        mvc.perform(command("phase").param("phase", "BUILD").param("customLabel", "l".repeat(120)).param("notes", "n".repeat(800)))
                .andExpect(flash().attribute("workspaceOutcome", "phase"));
        var audit = phases.findByLinkIdOrderByChangedAtDesc(link.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().getNotes()).hasSize(800);
        assertThat(audit.getFirst().getNewLabel()).hasSize(120);
        assertThat(link.getCoachingPhaseStartedAt()).isEqualTo(started);
        relationshipService.pauseLink(trainer.getId(), client.getId());
        mvc.perform(command("assign-workout").param("templateId", workout.getId().toString()))
                .andExpect(status().isForbidden());
        assertThat(assignedWorkouts.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainer.getId(), client.getId())).isEmpty();
    }

    @Test void realWorkspaceKeepsLabelsOneCsrfAndTranslatedPhasesAcrossLocales() throws Exception {
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var page = render(locale);
            assertThat(page.select("main form[action^=/trainer/clients/]")).hasSize(3);
            for (var form : page.select("main form")) assertThat(form.select("input[name=_csrf]")).hasSize(1);
            assertThat(page.select("#clientCoachingPhase option")).hasSize(6);
            assertThat(page.select("#clientCoachingPhase option[selected]")).hasSize(1);
            assertThat(page.select("textarea[maxlength=800]")).hasSize(3);
            assertThat(page.select(".client-workspace-limits")).hasSize(2);
            assertThat(page.select("nav.client-workspace-nav a")).hasSize(7);
            assertThat(page.text()).doesNotContain("??ui.coaching.");
        }
    }

    @Test void ownProgrammePreviewsKeepCustomDaysOrderAndLiteralNames() throws Exception {
        var movement = new uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutExercise();
        movement.setTemplate(workout); movement.setExerciseName("Local press <movement>");
        movement.setSets(4); movement.setReps(7); movement.setRestSeconds(90);
        workout.getExercises().add(movement); workouts.saveAndFlush(workout);
        var custom = new uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise();
        custom.setUserId(trainer.getId()); custom.setName("Custom hinge <movement>");
        custom = customExercises.save(custom);
        for (int day : new int[]{14, 2}) {
            var entry = new ScheduleEntry(); entry.setSchedule(schedule); entry.setCustomExercise(custom);
            entry.setDayOfWeek(day); entry.setOrderNumber(0); scheduleEntries.save(entry);
        }
        var other = account("Other trainer", Role.TRAINER);
        var foreign = new WorkoutTemplate(); foreign.setOwnerUser(other); foreign.setOwnerRole(Role.TRAINER);
        foreign.setName("Foreign private workout"); workouts.saveAndFlush(foreign);
        var foreignSchedule = new Schedule(); foreignSchedule.setUser(other); foreignSchedule.setName("Foreign private schedule"); schedules.save(foreignSchedule);
        var page = render("en");
        assertThat(page.select("[data-programme-previews=workout] details")).hasSize(1);
        assertThat(page.select("[data-programme-previews=workout] li").text()).contains("Local press <movement>", "4 Sets", "7 Reps", "90");
        assertThat(page.select("[data-programme-previews=schedule] li").eachText())
                .containsExactly("Day 2 Custom hinge <movement>", "Day 14 Custom hinge <movement>");
        assertThat(page.text()).doesNotContain("Foreign private");
        assertThat(page.select("movement")).isEmpty();
        mvc.perform(command("assign-workout").param("templateId", foreign.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(command("assign-schedule").param("scheduleId", foreignSchedule.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test void recentCheckInsConversationAndPhaseHistoryRemainInThisRelationship() throws Exception {
        var thread = threads.saveAndFlush(new uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread(
                client.getId(), trainer.getId(), link.getId(), uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadStatus.OPEN));
        for (int index = 0; index < 7; index++) {
            var checkIn = new uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckIn();
            checkIn.setTrainerId(trainer.getId()); checkIn.setClientId(client.getId());
            checkIn.setWeekStartDate(java.time.LocalDate.of(2026, 9, 28).minusWeeks(index));
            checkIn.setClientNotes("Private answers must stay on the review page"); checkIns.saveAndFlush(checkIn);
            var phase = new CoachingPhaseChange(); phase.setLinkId(link.getId()); phase.setTrainerId(trainer.getId());
            phase.setNewPhase(CoachingPhase.RECOVERY); phase.setNewLabel("History <label> " + (6 - index));
            phase.setNotes("Coach history <note> " + (6 - index));
            phases.saveAndFlush(phase);
        }
        var foreignTrainer = account("Foreign trainer", Role.TRAINER);
        var foreign = new uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckIn();
        foreign.setTrainerId(foreignTrainer.getId()); foreign.setClientId(client.getId());
        foreign.setWeekStartDate(java.time.LocalDate.of(2026, 10, 5)); checkIns.saveAndFlush(foreign);
        var page = render("en");
        assertThat(page.select("#clientCheckIns .client-review-list a")).hasSize(5);
        assertThat(page.select("#clientCheckIns time").eachAttr("datetime"))
                .containsExactly("2026-09-28", "2026-09-21", "2026-09-14", "2026-09-07", "2026-08-31");
        assertThat(page.select("#clientCheckIns a[href$='" + foreign.getId() + "']")).isEmpty();
        assertThat(page.selectFirst("#clientConversation a").attr("href")).isEqualTo("/inbox/" + thread.getId());
        assertThat(page.select("#clientPhase details li")).hasSize(5);
        assertThat(page.select("#clientPhase details").text()).contains("Coach history <note> 0").doesNotContain("Coach history <note> 6");
        assertThat(page.text()).doesNotContain("Private answers must stay");
        mvc.perform(get("/trainer/clients/{id}", client.getId()).with(user(foreignTrainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isForbidden());
    }
}
