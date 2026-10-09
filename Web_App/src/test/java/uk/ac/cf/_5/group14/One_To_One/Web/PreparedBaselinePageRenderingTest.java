package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Real template rendering catches failures missed by controller-only view-name tests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreparedBaselinePageRenderingTest {
    @Autowired private uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository calendarTasks;
    @Autowired private uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskWarningRepository calendarWarnings;

    @Autowired private uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.ScheduledWorkoutSessionService scheduledTraining;
    @Autowired private java.time.Clock applicationClock;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogRepository trainingLogs;

    @Test
    void trainingLaunchRetainsSavedSetsSourcesAndLocalisedNativeActions() throws Exception {
        var client = new uk.ac.cf._5.group14.One_To_One.Users.User(
                "training-probe@example.com", "Training", "Probe", "training_probe", "test-only-password");
        userRepository.saveAndFlush(client);
        var date = java.time.LocalDate.now(applicationClock);
        var empty = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-management").with(user("training_probe").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(empty.select(".training-launch__featured, progress")).isEmpty();
        assertThat(empty.text()).contains("Nothing planned for today", "Completed workouts will appear here");

        var trainer = users.findByUsername("trainer_demo");
        var template = new uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate();
        template.setTrainerId(trainer.getId()); template.setName("Coach provenance probe");
        checkInTemplates.saveAndFlush(template);
        var plan = new uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule();
        plan.setUser(client); plan.setName("Resumed <coach> plan"); schedules.save(plan);
        var exercise = exercises.findAll().getFirst();
        var occurrences = new java.util.ArrayList<uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence>();
        for (int index = 0; index < 2; index++) {
            var occurrence = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
            occurrence.setUser(client); occurrence.setDate(date); occurrence.setExercise(exercise);
            occurrence.setSchedule(plan); occurrence.setScheduleName(plan.getName()); occurrence.setTrainerTemplateId(template.getId());
            occurrenceRepository.saveAndFlush(occurrence); occurrences.add(occurrence);
        }
        var saved = scheduledTraining.launchFromOccurrence(client, occurrences.getFirst().getId());
        var firstSet = saved.getExerciseSessions().getFirst().getSetLogs().getFirst();
        scheduledTraining.updateSet(client, saved.getId(), firstSet.getId(), 42.5, 8, "Retained set", true);
        var launches = scheduledTraining.listOpenLaunchItems(client, date);
        assertThat(launches).hasSize(1);
        assertThat(launches.getFirst().started()).isTrue();
        assertThat(launches.getFirst().sourceKey()).isEqualTo("ui.training.sourceCoach");
        assertThat(launches.getFirst().summary().completedSets()).isEqualTo(1);
        assertThat(launches.getFirst().summary().totalSets()).isEqualTo(2);
        assertThat(launches.getFirst().launchUrl()).isEqualTo("/workout-session/launch/session/" + saved.getId());
        var foreign = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
        foreign.setUser(trainer); foreign.setDate(date); foreign.setExercise(exercise); foreign.setScheduleName("Private trainer launch");
        occurrenceRepository.saveAndFlush(foreign);
        var log = new uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLog();
        log.setUser(client); log.setDate(date); log.setDurationMinutes(25); log.setComments("Saved <script>comment</script>");
        trainingLogs.saveAndFlush(log);

        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-management").param("lang", locale)
                    .with(user("training_probe").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var root = doc.selectFirst(".training-launch");
            assertThat(root.text()).doesNotContain("??", "ui.training.", "{0}", "{1}", "Private trainer launch");
            assertThat(root.select("h1")).hasSize(1);
            assertThat(root.select("nav a[href^='#training-']")).hasSize(4);
            assertThat(root.select(".training-launch__featured h3").text()).isEqualTo(plan.getName());
            var primary = root.selectFirst(".training-launch__action--primary");
            assertThat(primary.attr("href")).isEqualTo("/workout-session/launch/session/" + saved.getId());
            assertThat(primary.attr("aria-label")).contains(plan.getName());
            assertThat(root.selectFirst("progress").attr("value")).isEqualTo("50");
            assertThat(root.selectFirst(".training-launch__source").attr("href")).isEqualTo("/calendar/day/" + date);
            assertThat(root.select("a[href='/exercise-log/edit/" + log.getId() + "']")).hasSize(1);
            assertThat(root.select("#training-logs script")).isEmpty();
            assertThat(root.selectFirst(".training-launch__comment").text()).isEqualTo(log.getComments());
            assertThat(root.select("[style], script")).isEmpty();
            assertThat(doc.select("link[href^=/css/bundles/training.css]")).hasSize(1);
        }
        mvc.perform(get("/workout-session/launch/session/" + saved.getId()).with(user("training_probe").roles("CLIENT")))
                .andExpect(redirectedUrl("/workout-session/" + saved.getId()));
        mvc.perform(get("/workout-session/launch/session/" + saved.getId()).with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unopenedWorkoutsDoNotInventSetsAndRemovedSourcesKeepSavedSessionsReachable() {
        var client = users.findByUsername("demo");
        var date = java.time.LocalDate.of(2099, 11, 2);
        var plan = new uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule();
        plan.setUser(client); plan.setName("Unopened plan"); schedules.save(plan);
        var exercise = exercises.findAll().getFirst();
        for (int index = 0; index < 2; index++) {
            var occurrence = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
            occurrence.setUser(client); occurrence.setDate(date); occurrence.setExercise(exercise);
            occurrence.setSchedule(plan); occurrence.setScheduleName(plan.getName());
            occurrenceRepository.saveAndFlush(occurrence);
        }
        var unopened = scheduledTraining.listUpcomingLaunchItems(client, date, date).getFirst();
        assertThat(unopened.summary().exerciseCount()).isEqualTo(2);
        assertThat(unopened.summary().totalSets()).isZero();
        assertThat(unopened.summary().completedSets()).isZero();
        assertThat(unopened.started()).isFalse();
        assertThat(unopened.sourceKey()).isEqualTo("ui.training.sourceCalendar");
        var occurrence = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
        occurrence.setUser(client); occurrence.setDate(date); occurrence.setExercise(exercise); occurrence.setScheduleName("Saved after source removal");
        occurrenceRepository.saveAndFlush(occurrence);
        var saved = scheduledTraining.launchFromOccurrence(client, occurrence.getId());
        occurrenceRepository.delete(occurrence); occurrenceRepository.flush();
        var retained = scheduledTraining.listOpenLaunchItems(client, date);
        assertThat(retained).hasSize(2);
        assertThat(retained.getFirst().started()).isTrue();
        assertThat(retained.getFirst().launchUrl()).isEqualTo("/workout-session/launch/session/" + saved.getId());
        assertThat(retained.getFirst().summary().totalSets()).isEqualTo(1);
        assertThat(retained.getFirst().sourceKey()).isEqualTo("ui.training.sourceSaved");
    }

    @Test
    void completedTrainingHistoryRemainsOwnedAndVisibleBehindNewerUnfinishedSessions() throws Exception {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var date = java.time.LocalDate.of(2099, 1, 1);
        for (int index = 0; index < 39; index++) {
            var session = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
            session.setUser(index == 38 ? trainer : client);
            session.setDate(index < 16 ? date : date.plusDays(index));
            session.setCompleted(index < 16 || index == 38);
            session.setNameSnapshot(index < 16 ? "Owned completed " + index : index == 38
                    ? "Private trainer history" : "Newer unfinished " + index);
            session.setCreatedAt(session.getDate().atStartOfDay());
            sessions.saveAndFlush(session);
        }
        var expected = java.util.stream.IntStream.range(4, 16).mapToObj(index -> "Owned completed " + (19 - index)).toList();
        assertThat(scheduledTraining.listRecentCompletedSessions(client, 12))
                .extracting(item -> item.title()).containsExactlyElementsOf(expected);
        assertThat(scheduledTraining.listRecentCompletedSessions(client, 0)).isEmpty();
        assertThat(scheduledTraining.listRecentCompletedSessions(client, -1)).isEmpty();
        var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-management").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(doc.text()).contains("Owned completed 15", "Owned completed 4")
                .doesNotContain("Private trainer history", "Newer unfinished 16", "Owned completed 3");
        assertThat(doc.select("a[href^=/workout-session/][href$=/complete]")).hasSize(12);
    }

    @Test
    void loggedCompletionHistoryRendersOwnedModernLogsAndAccessibleDatesInEveryUiLocale() throws Exception {
        var client = users.findByUsername("demo");
        var date = java.time.LocalDate.of(2099, 10, 4);
        var task = new uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask();
        task.setUser(client); task.setDate(date); task.setTitle("Completed synthetic task"); task.setCompleted(true);
        calendarTasks.save(task);
        var needsLog = new uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask();
        needsLog.setUser(client); needsLog.setDate(date); needsLog.setTitle("Required log still missing");
        needsLog.setCompleted(true); needsLog.setRequiresLog(true);
        calendarTasks.save(needsLog);
        var occurrence = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
        occurrence.setUser(client); occurrence.setDate(date); occurrence.setExercise(exercises.findAll().getFirst());
        occurrence.setScheduleName("Synthetic logged plan"); occurrence.setCompleted(true);
        occurrenceRepository.saveAndFlush(occurrence);
        for (boolean linked : new boolean[]{true, false}) {
            var workout = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
            workout.setUser(client); workout.setDate(date); workout.setCompleted(true);
            workout.setCreatedAt(java.time.LocalDateTime.of(2099, 10, 4, 7, 0));
            workout.setNameSnapshot("Synthetic training record");
            if (linked) workout.setSourceOccurrenceId(occurrence.getId());
            sessions.saveAndFlush(workout);
        }
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/calendar/day/" + date).param("lang", locale)
                    .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var history = doc.selectFirst(".calendar-history");
            assertThat(history.text()).doesNotContain("??", "{0}", "{1}", "{2}");
            assertThat(history.select("nav a")).hasSize(14).allSatisfy(link -> {
                assertThat(link.attr("aria-label")).isNotBlank();
                assertThat(link.attr("href")).startsWith("/calendar/day/");
            });
            var selected = history.selectFirst("nav a[aria-current=date]");
            assertThat(selected.attr("data-date")).isEqualTo(date.toString());
            assertThat(selected.attr("data-status")).isEqualTo("partial");
            assertThat(selected.selectFirst(".calendar-history__count").text()).isEqualTo("3/4");
            assertThat(history.select("details summary")).hasSize(1);
            assertThat(history.select("details li")).hasSize(14);
            assertThat(doc.select("#calendar-history-title")).hasSize(1);
            assertThat(doc.select("link[href^=/css/bundles/calendar.css]")).hasSize(1);
        }
    }

    @Test
    void taskEditorAndRulesRetainValidationAndProtectOwnedRelationships() throws Exception {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var date = java.time.LocalDate.of(2099, 10, 5);
        var task = new uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask();
        task.setUser(client); task.setDate(date); task.setTitle("Task <editor>"); task.setNotes("Safe <notes>");
        task.setTime(java.time.LocalTime.of(9, 30));
        calendarTasks.save(task);
        var trigger = new uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask();
        trigger.setUser(client); trigger.setDate(date); trigger.setTitle("Owned trigger");
        calendarTasks.save(trigger);
        var foreign = new uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask();
        foreign.setUser(trainer); foreign.setDate(date); foreign.setTitle("Private trainer trigger");
        calendarTasks.save(foreign);
        var path = "/calendar/task/" + task.getId();
        var doc = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(doc.selectFirst("h1").text()).isEqualTo("Task <editor>");
        assertThat(doc.title()).isEqualTo("Task — Task <editor>");
        assertThat(doc.selectFirst("#task-notes").text()).isEqualTo("Safe <notes>");
        assertThat(doc.select("link[href^=/css/bundles/calendar-task.css]")).hasSize(1);
        assertThat(doc.select("link[href^=/css/bundles/calendar.css]")).isEmpty();
        assertThat(doc.selectFirst("a[href$='#tab-panel-timeline']").attr("href"))
                .isEqualTo("/calendar/day/" + date + "#tab-panel-timeline");
        assertThat(doc.selectFirst("#task-delete-acknowledge").hasAttr("required")).isTrue();
        assertThat(doc.select("#warning-trigger-task option").eachText()).containsExactly("Owned trigger");
        assertThat(doc.select(".task-workspace form")).allSatisfy(form -> assertThat(form.selectFirst("input[name=_csrf]")).isNotNull());
        mvc.perform(post(path + "/edit-inline").with(user("demo").roles("CLIENT")).with(csrf())
                .param("title", "Retained invalid title").param("notes", "Retained invalid notes").param("time", "29:99").param("returnTo", "detail"))
                .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.invalidEdit"))
                .andExpect(flash().attribute("taskEditTitle", "Retained invalid title"))
                .andExpect(flash().attribute("taskEditNotes", "Retained invalid notes"));
        assertThat(task.getTitle()).isEqualTo("Task <editor>");
        assertThat(task.getTime()).isEqualTo(java.time.LocalTime.of(9, 30));
        mvc.perform(post(path + "/edit-inline").with(user("demo").roles("CLIENT")).with(csrf())
                .param("title", "Saved <task>").param("notes", "Updated <notes>").param("time", "").param("returnTo", "detail"))
                .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.saved"));
        assertThat(task.getTitle()).isEqualTo("Saved <task>");
        assertThat(task.getTime()).isNull();
        for (String input : java.util.List.of("-5", "not-a-number")) {
            mvc.perform(post(path + "/grace-period").with(user("demo").roles("CLIENT")).with(csrf()).param("gracePeriodMinutes", input))
                    .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.invalidGrace"));
        }
        mvc.perform(post(path + "/grace-period").with(user("demo").roles("CLIENT")).with(csrf()).param("gracePeriodMinutes", "15"))
                .andExpect(redirectedUrl(path));
        assertThat(task.getGracePeriodMinutes()).isEqualTo(15);
        mvc.perform(post(path + "/warning-time").with(user("demo").roles("CLIENT")).with(csrf()).param("triggerTime", "29:99"))
                .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.invalidTime"));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(path + "/warning-time").with(user("demo").roles("CLIENT")).with(csrf()).param("triggerTime", "10:00"))
                    .andExpect(redirectedUrl(path));
        }
        assertThat(calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId())).hasSize(1);
        for (Long invalidTrigger : java.util.List.of(task.getId(), foreign.getId())) {
            mvc.perform(post(path + "/warning-on-complete").with(user("demo").roles("CLIENT")).with(csrf()).param("triggerTaskId", invalidTrigger.toString()))
                    .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.invalidTrigger"));
        }
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(path + "/warning-on-complete").with(user("demo").roles("CLIENT")).with(csrf()).param("triggerTaskId", trigger.getId().toString()))
                    .andExpect(redirectedUrl(path));
        }
        var rules = calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId());
        assertThat(rules).hasSize(2);
        var rulesPage = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rulesPage.select(".task-rule-title").eachText()).containsExactly("At 10:00 on this date", "After completing Owned trigger");
        assertThat(rulesPage.select(".task-rule-list form")).allSatisfy(form -> assertThat(form.selectFirst("input[name=_csrf]")).isNotNull());
        mvc.perform(get(path).with(user("trainer_demo").roles("TRAINER"))).andExpect(redirectedUrl("/calendar"));
        mvc.perform(post(path + "/edit-inline").with(user("trainer_demo").roles("TRAINER")).with(csrf()).param("title", "Foreign edit"))
                .andExpect(redirectedUrl("/calendar"));
        mvc.perform(post(path + "/warning/" + rules.getFirst().getId() + "/delete").with(user("trainer_demo").roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl("/calendar"));
        assertThat(task.getTitle()).isEqualTo("Saved <task>");
        assertThat(calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId())).hasSize(2);
        mvc.perform(post("/calendar/task/" + trigger.getId() + "/warning/" + rules.getFirst().getId() + "/delete")
                .with(user("demo").roles("CLIENT")).with(csrf())).andExpect(redirectedUrl("/calendar/task/" + trigger.getId()));
        assertThat(calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId())).hasSize(2);
        mvc.perform(post(path + "/warning/" + rules.getFirst().getId() + "/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl(path)).andExpect(flash().attribute("taskFeedback", "ui.task.ruleRemoved"));
        mvc.perform(post("/calendar/day/" + date + "/toggle-complete").with(user("demo").roles("CLIENT")).with(csrf())
                .param("taskId", trigger.getId().toString()).param("returnTo", "detail"))
                .andExpect(redirectedUrl("/calendar/task/" + trigger.getId()));
        assertThat(calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId()).getFirst().getTriggeredAt()).isNotNull();
        var triggeredPage = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(triggeredPage.selectFirst(".task-state").text()).isEqualTo("Within grace period");
        mvc.perform(post(path + "/grace-period").with(user("demo").roles("CLIENT")).with(csrf()).param("gracePeriodMinutes", ""))
                .andExpect(redirectedUrl(path));
        assertThat(task.getGracePeriodMinutes()).isNull();
        mvc.perform(post("/calendar/task/" + trigger.getId() + "/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/calendar/day/" + date));
        assertThat(calendarWarnings.findByTaskIdOrderByCreatedAtAsc(task.getId())).isEmpty();
    }

    @Test
    void focusAgendaRendersOwnedMixedItemsAndCompletesTasksWithoutScripts() throws Exception {
        var date = java.time.LocalDate.of(2099, 10, 3);
        var dayPath = "/calendar/day/" + date;
        var focusPath = "/calendar/focus/" + date;
        var client = users.findByUsername("demo");
        var empty = org.jsoup.Jsoup.parse(mvc.perform(get(focusPath).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(empty.selectFirst(".focus-empty").text()).contains("Nothing is planned for this day.");
        mvc.perform(post(dayPath + "/add-task").with(user("demo").roles("CLIENT")).with(csrf())
                .param("title", "Focus <task> & check").param("time", "09:30").param("notes", "Safe <notes>"))
                .andExpect(status().is3xxRedirection());
        var workout = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        workout.setUser(client);
        workout.setDate(date);
        workout.setNameSnapshot("Morning strength");
        workout.setCreatedAt(java.time.LocalDateTime.of(2099, 10, 3, 7, 0));
        sessions.saveAndFlush(workout);
        var occurrence = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
        occurrence.setUser(client);
        occurrence.setDate(date);
        occurrence.setScheduleName("Synthetic focus plan");
        occurrence.setExercise(exercises.findAll().getFirst());
        occurrenceRepository.saveAndFlush(occurrence);
        var linkedSession = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        linkedSession.setUser(client);
        linkedSession.setDate(date);
        linkedSession.setCreatedAt(java.time.LocalDateTime.of(2099, 10, 3, 7, 0));
        linkedSession.setSourceOccurrenceId(occurrence.getId());
        linkedSession.setNameSnapshot("Linked occurrence session");
        sessions.saveAndFlush(linkedSession);
        var timedSession = mvc.perform(post(dayPath + "/timeline-slot").with(user("demo").roles("CLIENT")).with(csrf())
                .param("itemType", "workout").param("itemId", workout.getId().toString()).param("time", "08:30"))
                .andExpect(status().isOk()).andReturn();
        var cookies = timedSession.getResponse().getCookies();
        mvc.perform(post(dayPath + "/timeline-slot").cookie(cookies).with(user("demo").roles("CLIENT")).with(csrf())
                .param("itemType", "occurrence").param("itemId", occurrence.getId().toString()).param("time", "17:00"))
                .andExpect(status().isOk());
        var html = mvc.perform(get(focusPath).cookie(cookies).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = org.jsoup.Jsoup.parse(html);
        assertThat(html).contains("Focus &lt;task&gt; &amp; check", "Safe &lt;notes&gt;", "/css/bundles/calendar-focus.css?v=")
                .doesNotContain("/css/global.css", "day-enhancements.js", "/css/bundles/calendar.css?", "id=\"quick-actions-shelf\"", "id=\"global-chat-widget\"");
        assertThat(document.select(".focus-items > li time").eachText()).containsExactly("08:30", "09:30", "17:00");
        assertThat(document.select(".focus-items > li").eachAttr("id")).doesNotHaveDuplicates();
        assertThat(document.selectFirst(".focus-primary-action").attr("href"))
                .isEqualTo("/workout-session/launch/session/" + workout.getId());
        assertThat(document.select(".focus-workout-action").eachAttr("href"))
                .containsExactly("/workout-session/launch/session/" + workout.getId(), "/workout-session/launch/occurrence/" + occurrence.getId());
        assertThat(document.selectFirst(".focus-exit").attr("href"))
                .isEqualTo(dayPath + "?fromFocus=1#tab-panel-timeline");
        var form = document.selectFirst(".focus-task-form");
        assertThat(form.selectFirst("input[name=_csrf]")).isNotNull();
        assertThat(form.selectFirst("input[name=returnTo]").val()).isEqualTo("focus");
        var taskId = form.selectFirst("input[name=taskId]").val();
        mvc.perform(post(form.attr("action")).cookie(cookies).with(user("demo").roles("CLIENT"))
                .param("taskId", taskId).param("returnTo", "focus"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        var rejected = org.jsoup.Jsoup.parse(mvc.perform(get(focusPath).cookie(cookies).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rejected.selectFirst(".focus-progress-label").text()).isEqualTo("0 of 3 complete");
        mvc.perform(post(form.attr("action")).with(user("demo").roles("CLIENT")).with(csrf())
                .param("taskId", taskId).param("returnTo", "focus"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(focusPath));
        var complete = org.jsoup.Jsoup.parse(mvc.perform(get(focusPath).cookie(cookies).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(complete.selectFirst(".focus-progress-label").text()).isEqualTo("1 of 3 complete");
        assertThat(complete.selectFirst("progress").attr("value")).isEqualTo("33");
        assertThat(complete.selectFirst("progress").text()).isEqualTo("33%");
        assertThat(complete.selectFirst("details.focus-completed .focus-task-form button").attr("aria-pressed")).isEqualTo("true");
        mvc.perform(post(form.attr("action")).with(user("demo").roles("CLIENT")).with(csrf())
                .param("taskId", taskId).param("returnTo", "https://example.invalid/"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(dayPath));
        var day = org.jsoup.Jsoup.parse(mvc.perform(get(dayPath).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(day.getElementById("workout-drawer-content-session-" + workout.getId())).isNotNull();
        assertThat(day.getElementById("workout-drawer-content-occurrence-" + occurrence.getId())).isNotNull();
        assertThat(day.select("[data-workout-card]")).hasSize(2);
        assertThat(day.selectFirst("[data-progress-summary]").text()).isEqualTo("Planned items still to complete: 3.");
        assertThat(day.selectFirst("[data-workout-dialog]").attr("aria-labelledby")).isEqualTo("workout-drawer-heading");
    }

    @Test
    void dayPlannerCreatesAndCompletesTasksUsingNativeForms() throws Exception {
        var path = "/calendar/day/2099-10-02";
        var empty = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(empty.selectFirst("[data-progress-summary]").text()).isEqualTo("Nothing is planned for this day.");
        assertThat(empty.select("#day-section-navigation a")).hasSize(4).allSatisfy(link -> {
            var panel = empty.getElementById(link.attr("aria-controls"));
            assertThat(link.attr("href")).isEqualTo("#" + panel.id());
            assertThat(panel.hasClass("hidden")).isFalse();
            assertThat(panel.hasAttr("hidden")).isFalse();
            assertThat(panel.hasAttr("inert")).isFalse();
        });
        assertThat(empty.selectFirst("#add-task-modal").hasAttr("aria-hidden")).isFalse();
        assertThat(empty.selectFirst("#add-task-modal").hasClass("hidden")).isFalse();
        assertThat(empty.selectFirst("#overview-next-priority").text()).isEqualTo("Nothing is planned for this day.");
        assertThat(empty.selectFirst("[data-add-task-dialog]").hasAttr("aria-modal")).isFalse();
        assertThat(empty.selectFirst("label[for=add-task-title]")).isNotNull();
        mvc.perform(post(path + "/add-task").with(user("demo").roles("CLIENT")).with(csrf())
                .param("title", "Native planner journey").param("time", "09:30").param("notes", "Synthetic task"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path));
        var planned = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var form = planned.selectFirst("form.day-native-completion-form");
        assertThat(form.attr("action")).isEqualTo(path + "/toggle-complete");
        assertThat(form.selectFirst("input[name=_csrf]")).isNotNull();
        assertThat(planned.selectFirst("[data-progress-summary]").text()).isEqualTo("Planned items still to complete: 1.");
        mvc.perform(post(form.attr("action")).with(user("demo").roles("CLIENT")).with(csrf())
                .param("taskId", form.selectFirst("input[name=taskId]").val()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path));
        var complete = org.jsoup.Jsoup.parse(mvc.perform(get(path).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(complete.selectFirst("[data-progress-summary]").text()).isEqualTo("All planned items are complete.");
        assertThat(complete.selectFirst("[data-task-item]").attr("data-task-status")).isEqualTo("done");
    }

    @Test
    void clientDashboardProvidesNativeDestinationsAndAccessiblePanelsBeforeEnhancement() throws Exception {
        var html = mvc.perform(get("/dashboard").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = org.jsoup.Jsoup.parse(html);
        var shortcuts = document.select("a[data-dashboard-flyout-handle]");
        assertThat(shortcuts.eachAttr("href")).containsExactly(
                "/explore", "/client/trainers", "/goals", "/support", "/profile");
        assertThat(document.select("a[data-week-day-button]")).hasSize(7).allSatisfy(day -> {
            assertThat(day.attr("href")).matches("/calendar/day/\\d{4}-\\d{2}-\\d{2}");
            assertThat(day.attr("href")).isEqualTo(day.attr("data-day-path"));
        });
        assertThat(document.select("[data-action-view], [data-goal-view]"))
                .isNotEmpty().allSatisfy(panel -> {
                    assertThat(panel.hasAttr("inert")).isFalse();
                    assertThat(panel.attr("aria-hidden")).isNotEqualTo("true");
                });
        var goalPanelIds = document.select("[data-goal-view]").eachAttr("id");
        assertThat(goalPanelIds).hasSize(6).doesNotHaveDuplicates();
        assertThat(document.select("a[data-goal-tab], a[data-action-tab]")).allSatisfy(tab -> {
            assertThat(tab.attr("href")).isEqualTo("#" + tab.attr("aria-controls"));
            assertThat(document.getElementById(tab.attr("aria-controls"))).isNotNull();
        });
    }

    @Autowired private MockMvc mvc;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ChatV2.ChatV2ThreadService legacyChats;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ChatV2.ChatThreadRepository legacyThreadRepository;
    @Autowired private UserSettingsService settings;
    @Autowired private UserService users;
    @Autowired private uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository sessions;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Users.UserRepository userRepository;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository links;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInRepository checkIns;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplateRepository checkInTemplates;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestionRepository checkInQuestions;
    @Autowired private uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionRepository platformSubscriptions;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService messaging;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Messaging.OffPlatformPaymentAttemptRepository paymentAttempts;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedScheduleRepository assignedSchedules;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedWorkoutRepository assignedWorkouts;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplateRepository workoutTemplates;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleRepository schedules;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryRepository scheduleEntries;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository occurrenceRepository;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository exercises;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerAssignments.TrainerAssignmentService assignments;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Reviews.ClientAssessmentService assessments;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Reviews.TrainerReviewRepository trainerReviews;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Reviews.ReviewModerationService moderation;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileService trainerProfiles;
    @Autowired private uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileRepository trainerProfileRepository;

    @Test
    void trainerEditorRetainsInvalidInputAndProtectsProfileIdentity() throws Exception {
        var trainer = users.findByUsername("trainer_demo");
        var profile = trainerProfiles.getOrCreateProfile(trainer.getId());
        profile.setTrainerCode("AB12CD34EF56");
        profile.setBio("Original saved bio");
        profile.setPricePerSession(70);
        trainerProfileRepository.saveAndFlush(profile);
        mvc.perform(get("/trainer/profile/edit").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("trainer-profile-editor-v2")))
                .andExpect(content().string(containsString("AB12-CD34-EF56")))
                .andExpect(content().string(containsString("/trainers/" + trainer.getId() + "/profile")));
        var invalid = mvc.perform(post("/trainer/profile/save").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("bio", "Draft <bio> & details").param("pricePerSession", "not-a-number")
                .param("websiteUrl", "javascript:alert(1)").param("trainerCode", "FAKECODE1234"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(invalid).contains("Draft &lt;bio&gt; &amp; details", "value=\"not-a-number\"", "profile-field-invalid", "AB12-CD34-EF56");
        assertThat(profile.getBio()).isEqualTo("Original saved bio");
        assertThat(profile.getPricePerSession()).isEqualTo(70);
        mvc.perform(post("/trainer/profile/save").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("bio", "x".repeat(501)).param("pricePerSession", "-1"))
                .andExpect(status().isBadRequest());
        var saved = mvc.perform(post("/trainer/profile/save").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("bio", "Saved public bio").param("pricePerSession", "0")
                .param("websiteUrl", "https://example.com/trainer").param("showWebsite", "true")
                .param("trainerCode", "FAKECODE1234").param("userId", users.findByUsername("demo").getId().toString()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(profile.getUserId()).isEqualTo(trainer.getId());
        assertThat(profile.getTrainerCode()).isEqualTo("AB12CD34EF56");
        assertThat(profile.getPricePerSession()).isZero();
        mvc.perform(get("/trainer/profile/edit").with(user("trainer_demo").roles("TRAINER")).cookie(saved.getResponse().getCookies()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Saved successfully.")));
        profile.setTrainerCode("SHORT");
        trainerProfileRepository.saveAndFlush(profile);
        var malformed = mvc.perform(get("/trainer/profile/edit").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(malformed).doesNotContain("id=\"trainerCodeVal\"");
    }

    @Test
    void trainerProfileServiceRejectsOversizedInputBeforeMutation() {
        var trainer = users.findByUsername("trainer_demo");
        var profile = trainerProfiles.getOrCreateProfile(trainer.getId());
        profile.setBio("Preserve saved profile");
        trainerProfileRepository.saveAndFlush(profile);
        var invalid = new uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfile();
        invalid.setBio("x".repeat(501));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> trainerProfiles.updateProfile(trainer.getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(profile.getBio()).isEqualTo("Preserve saved profile");
    }

    @Test
    void missingConversationsReturnNotFound() throws Exception {
        mvc.perform(get("/inbox/9223372036854775807").with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/messages/9223372036854775807").with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicPortfolioHasSafeNamedLinksAndProtectsTrainerSignInCode() throws Exception {
        var trainer = users.findByUsername("trainer_demo");
        var profile = trainerProfiles.getOrCreateProfile(trainer.getId());
        profile.setTrainerCode("AB12CD34EF56");
        profile.setInstagramUrl("https://www.instagram.com/one-to-one/");
        profile.setShowInstagram(true);
        profile.setWebsiteUrl("javascript:alert(1)");
        profile.setShowWebsite(true);
        trainerProfileRepository.saveAndFlush(profile);
        mvc.perform(get("/explore")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/trainers/" + trainer.getId() + "/profile\"")));
        var publicPage = mvc.perform(get("/trainers/" + trainer.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("trainer-portfolio-v2")))
                .andExpect(content().string(containsString("https://www.instagram.com/one-to-one/"))).andReturn();
        assertThat(publicPage.getResponse().getContentAsString()).contains(">Instagram</a>")
                .doesNotContain("javascript:alert(1)", "AB12-CD34-EF56", "name=\"reason\"");
        mvc.perform(get("/trainers/" + trainer.getId() + "/profile").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AB12-CD34-EF56")))
                .andExpect(content().string(containsString("Keep it private.")));
        profile.setTrainerCode("SHORT");
        trainerProfileRepository.saveAndFlush(profile);
        var malformed = mvc.perform(get("/trainers/" + trainer.getId() + "/profile").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(malformed.getResponse().getContentAsString()).doesNotContain("id=\"trainerCodeVal\"");
        trainer.setEnabled(false);
        userRepository.saveAndFlush(trainer);
        mvc.perform(get("/trainers/" + trainer.getId() + "/profile")).andExpect(status().isNotFound());
        mvc.perform(get("/u/" + trainer.getUsername())).andExpect(status().isNotFound());
    }

    @Test
    void nativeReviewReportFormHasCsrfAndRetainsRejectedReason() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var review = trainerReviews.saveAndFlush(new uk.ac.cf._5.group14.One_To_One.Reviews.TrainerReview(
                trainer.getId(), client.getId(), links.findActiveByClientId(client.getId()).orElseThrow().getId(), 5, null, "Coaching report test"));
        mvc.perform(get("/trainers/" + trainer.getId() + "/profile").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/reviews/" + review.getId() + "/report\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("for=\"report-reason-" + review.getId() + "\"")));
        var failed = mvc.perform(post("/reviews/" + review.getId() + "/report").with(user("demo").roles("CLIENT")).with(csrf())
                .param("trainerId", trainer.getId().toString()).param("reason", "Retain <reason> " + "x".repeat(1001)))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get("/trainers/" + trainer.getId() + "/profile").with(user("demo").roles("CLIENT")).cookie(failed.getResponse().getCookies()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Retain &lt;reason&gt;")));
    }

    @Test
    void moderationRendersReportedReviewAndResolvesItsQueueWithoutStaleReversal() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var admin = users.findByUsername("admin_demo");
        var review = trainerReviews.saveAndFlush(new uk.ac.cf._5.group14.One_To_One.Reviews.TrainerReview(
                trainer.getId(), client.getId(), links.findActiveByClientId(client.getId()).orElseThrow().getId(), 4, "Patient", "Reported <review>"));
        var first = moderation.reportReview(review.getId(), client.getId(), "Reason <one>");
        var second = moderation.reportReview(review.getId(), admin.getId(), "Reason two");
        mvc.perform(get("/admin/moderation").with(user("admin_demo").roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reported &lt;review&gt;")))
                .andExpect(content().string(containsString("Reason &lt;one&gt;")))
                .andExpect(content().string(containsString("formaction=\"/admin/moderation/" + first.getId() + "/keep\"")));
        var invalid = mvc.perform(post("/admin/moderation/" + first.getId() + "/hide").with(user("admin_demo").roles("PLATFORM_ADMIN")).with(csrf())
                .param("notes", " "))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(moderation.getPendingModerations(admin.getId())).hasSize(2);
        mvc.perform(get("/admin/moderation").with(user("admin_demo").roles("PLATFORM_ADMIN")).cookie(invalid.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This report may already be resolved.")));
        mvc.perform(post("/admin/moderation/" + first.getId() + "/keep").with(user("admin_demo").roles("PLATFORM_ADMIN")).with(csrf())
                .param("notes", "Reviewed both reports"))
                .andExpect(status().is3xxRedirection());
        assertThat(moderation.getPendingModerations(admin.getId())).isEmpty();
        assertThat(moderation.getModerationsForReview(review.getId(), admin.getId()))
                .allMatch(report -> report.isResolved() && admin.getId().equals(report.getResolvedByUserId()));
        mvc.perform(post("/admin/moderation/" + second.getId() + "/hide").with(user("admin_demo").roles("PLATFORM_ADMIN")).with(csrf())
                .param("notes", "Stale action must not override the decision"))
                .andExpect(status().is3xxRedirection());
        assertThat(trainerReviews.findById(review.getId()).orElseThrow().getStatus())
                .isEqualTo(uk.ac.cf._5.group14.One_To_One.Reviews.ReviewStatus.VISIBLE);
        mvc.perform(get("/admin/moderation").with(user("admin_demo").roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No reports awaiting review.")));
    }

    @Test
    void moderationRejectsNonAdminsAndDisabledAdmins() {
        var client = users.findByUsername("demo");
        var admin = users.findByUsername("admin_demo");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> moderation.getReviewsForModeration(java.util.List.of(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        admin.setEnabled(false);
        userRepository.saveAndFlush(admin);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> moderation.getPendingModerations(admin.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void reviewUsesNativeRatingAndTagsAndRetainsInvalidDraft() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        mvc.perform(get("/trainers/" + trainer.getId() + "/review").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type=\"radio\" name=\"stars\"")))
                .andExpect(content().string(containsString("name=\"tags\" value=\"Professional\"")))
                .andExpect(content().string(containsString("Your review is public.")));
        var invalid = mvc.perform(post("/trainers/" + trainer.getId() + "/review").with(user("demo").roles("CLIENT")).with(csrf())
                .param("stars", "7").param("tags", "Professional", "Patient").param("comment", "Keep <my draft>"))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get("/trainers/" + trainer.getId() + "/review").with(user("demo").roles("CLIENT")).cookie(invalid.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep &lt;my draft&gt;")))
                .andExpect(content().string(containsString("Choose a rating from 1 to 5.")));
        var valid = mvc.perform(post("/trainers/" + trainer.getId() + "/review").with(user("demo").roles("CLIENT")).with(csrf())
                .param("stars", "4").param("tags", "Professional", "Patient").param("comment", "Good <coaching>"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var saved = trainerReviews.findByTrainerIdAndClientIdAndLinkId(trainer.getId(), client.getId(), links.findActiveByClientId(client.getId()).orElseThrow().getId()).orElseThrow();
        assertThat(saved.getTags()).isEqualTo("Professional,Patient");
        mvc.perform(get("/trainers/" + trainer.getId() + "/profile").with(user("demo").roles("CLIENT")).cookie(valid.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review submitted.")))
                .andExpect(content().string(containsString("Good &lt;coaching&gt;")));
    }

    @Test
    void privateAssessmentProtectsUnrelatedClientsAndRetainsInvalidDraft() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        assessments.saveAssessment(trainer.getId(), client.getId(), 4, 3, "Private <coaching> notes");
        mvc.perform(get("/trainer/clients/" + client.getId() + "/assessment").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("assessment-workspace-v2")))
                .andExpect(content().string(containsString("for=\"reliabilityScore\"")))
                .andExpect(content().string(containsString("Private &lt;coaching&gt; notes")));
        var result = mvc.perform(post("/trainer/clients/" + client.getId() + "/assessment").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("reliabilityScore", "6").param("communicationScore", "2").param("privateNotes", "Retained <draft>"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(assessments.getAssessment(trainer.getId(), client.getId()).orElseThrow().getReliabilityScore()).isEqualTo(4);
        mvc.perform(get("/trainer/clients/" + client.getId() + "/assessment").with(user("trainer_demo").roles("TRAINER")).cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Retained &lt;draft&gt;")))
                .andExpect(content().string(containsString("Scores must be between 1 and 5.")));
        mvc.perform(get("/trainer/assessments").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Private assessment history")))
                .andExpect(content().string(containsString("Private &lt;coaching&gt; notes")));
        var unrelated = users.findByUsername("trainer_demo");
        mvc.perform(get("/trainer/clients/" + unrelated.getId() + "/assessment").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawnRequestsAndRevokedTrainersCannotAccessPrivateAssessments() {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var link = links.findActiveByClientId(client.getId()).orElseThrow();
        link.setStatus(uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ENDED);
        link.setActivatedAt(null);
        links.saveAndFlush(link);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assessments.getAssessment(trainer.getId(), client.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        link.setActivatedAt(java.time.Instant.now());
        links.saveAndFlush(link);
        assessments.saveAssessment(trainer.getId(), client.getId(), 5, null, "Past coaching");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assessments.saveAssessment(trainer.getId(), client.getId(), 5, 2, "n".repeat(10001)))
                .isInstanceOf(IllegalArgumentException.class);
        trainer.setTrainerVerified(false);
        userRepository.saveAndFlush(trainer);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assessments.getAllAssessmentsByTrainer(trainer.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void premiumCharlieWorkspaceRendersAccessibleComposerAndLimitDialog() throws Exception {
        var client = users.findByUsername("demo");
        var subscription = platformSubscriptions.findByUserId(client.getId())
                .orElseGet(uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscription::new);
        subscription.setUserId(client.getId());
        subscription.setPlan(uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan.INFINITE);
        subscription.setStatus(uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionStatus.ACTIVE);
        platformSubscriptions.saveAndFlush(subscription);
        mvc.perform(get("/chat").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("coach-workspace-v2")))
                .andExpect(content().string(containsString("for=\"coachInput\"")))
                .andExpect(content().string(containsString("<dialog id=\"limitModal\"")))
                .andExpect(content().string(containsString("data-error=\"Unable to load.")))
                .andExpect(content().string(containsString("coach-more-actions")));
    }


    @Test
    void legacyMessagingLinksAndStructuredCheckinsShareCanonicalHistoryAndKeepDrafts() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var thread = messaging.ensureThreadForLink(links.findActiveByClientId(client.getId()).orElseThrow());
        mvc.perform(get("/client/messages").with(user("demo").roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/inbox"));
        mvc.perform(get("/trainer/messages").with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/inbox"));
        mvc.perform(get("/messages/" + thread.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/inbox/" + thread.getId()));
        mvc.perform(post("/messages/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .param("type", "CHECKIN").param("checkinMood", "8").param("checkinEnergy", "7").param("checkinNotes", "Original <check-in>"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("inboxSent", true));
        var original = messaging.getMessagesForThread(thread.getId(), client.getId());
        assertThat(original).hasSize(1);
        assertThat(original.getFirst().getType()).isEqualTo(uk.ac.cf._5.group14.One_To_One.Messaging.MessageType.CHECKIN);
        mvc.perform(get("/inbox/" + thread.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("inbox-checkin-composer")))
                .andExpect(content().string(containsString("Original &lt;check-in&gt;")));
        var rejected = mvc.perform(post("/messages/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .param("type", "CHECKIN").param("checkinMood", "11").param("checkinEnergy", "7").param("checkinNotes", "Keep <draft>"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("inboxSendError", "INVALID_MESSAGE"))
                .andReturn();
        mvc.perform(get("/inbox/" + thread.getId()).cookie(rejected.getResponse().getCookies()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"11\"")))
                .andExpect(content().string(containsString("Keep &lt;draft&gt;")));
        mvc.perform(post("/api/inbox/threads/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bodyText\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(messaging.getMessagesForThread(thread.getId(), client.getId())).hasSize(1);
    }


    @Test
    void earlierAssistantHistoryKeepsOwnedFoldersArchivedThreadsAndEscapedMessages() throws Exception {
        var owner = users.findByUsername("demo"); var other = users.findByUsername("trainer_demo");
        var folder = legacyChats.createFolder(owner, "Earlier <training>", "#0f172a", "chat");
        var thread = legacyChats.createThread(owner, folder);
        legacyChats.updateThreadSettings(thread, "Historic <plan>", null, null, true, true, "Original <instructions>");
        legacyChats.appendMessage(thread, uk.ac.cf._5.group14.One_To_One.ChatV2.ChatMessageRole.USER, "Historic <private question>");
        var foreign = legacyChats.createThread(other, null);
        legacyChats.updateThreadSettings(foreign, "Foreign assistant history", null, null, null, null, null);
        mvc.perform(get("/chatv2/history").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Historic &lt;plan&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Foreign assistant history"))));
        mvc.perform(get("/chatv2/history/folder/" + folder.getId()).param("q", "Historic").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Earlier &lt;training&gt;")));
        mvc.perform(get("/chatv2/history/thread/" + thread.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Historic &lt;private question&gt;")))
                .andExpect(content().string(containsString("Original &lt;instructions&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chat-v2.js"))));
        mvc.perform(get("/chatv2/history/thread/" + thread.getId()).with(user("trainer_demo").roles("TRAINER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/chatv2/" + thread.getId() + "/move").param("folderId", "9223372036854775807")
                .with(user("demo").roles("CLIENT")).with(csrf())).andExpect(status().isNotFound());
        assertThat(legacyThreadRepository.findById(thread.getId()).orElseThrow().getFolder().getId()).isEqualTo(folder.getId());
        mvc.perform(post("/chatv2/folder/" + folder.getId() + "/settings").param("name", "Bad replacement")
                .param("colorHex", "javascript:bad").param("iconKey", "chat").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(folder.getName()).isEqualTo("Earlier <training>");
    }

    private uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate prepareClientCheckInTemplate() {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        links.findByClientUserIdAndStatusOrderByUpdatedAtDesc(client.getId(),
                uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE).forEach(link -> {
            link.setStatus(uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ENDED);
            link.setEndedAt(java.time.Instant.now());
            links.save(link);
        });
        var link = new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(client.getId(), trainer.getId(),
                uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE);
        link.setActivatedAt(java.time.Instant.now());
        links.saveAndFlush(link);
        var template = new uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate();
        template.setTrainerId(trainer.getId());
        template.setName("Weekly coaching questions");
        return checkInTemplates.saveAndFlush(template);
    }

    @Test
    void clientSeesAndCanWithdrawAnOwnedPendingTrainerRequest() throws Exception {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var pending = links.saveAndFlush(new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(
                client.getId(), trainer.getId(), uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.REQUESTED));
        mvc.perform(get("/client/trainers").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pending requests")))
                .andExpect(content().string(containsString("Withdraw request")))
                .andExpect(content().string(containsString("/client/trainers/" + trainer.getId() + "/withdraw")))
                .andExpect(content().string(containsString("trainer-connection-v2")));
        mvc.perform(post("/client/trainers/" + trainer.getId() + "/withdraw").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(links.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ENDED);
        mvc.perform(post("/client/trainers/" + trainer.getId() + "/withdraw").with(user("trainer_demo").roles("TRAINER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/access-denied"));
    }

    @Test
    void inboxApiRejectsUnsafeAndPaymentLinksAndKeepsBlockedAttemptAudit() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var thread = messaging.ensureThreadForLink(links.findActiveByClientId(client.getId()).orElseThrow());
        long originalAttempts = paymentAttempts.count();
        mvc.perform(post("/api/inbox/threads/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bodyText\":\"Exercise demo\",\"attachmentUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inbox/threads/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bodyText\":\"Here is the link\",\"attachmentUrl\":\"https://paypal.me/example\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reason").value("OFF_PLATFORM_PAYMENT"));
        assertThat(paymentAttempts.count()).isEqualTo(originalAttempts + 1);
    }

    @Test
    void inboxRetainsFailedFormDraftAndRendersPausedThreadAsReadOnly() throws Exception {
        prepareClientCheckInTemplate();
        var client = users.findByUsername("demo");
        var link = links.findActiveByClientId(client.getId()).orElseThrow();
        var thread = messaging.ensureThreadForLink(link);
        var result = mvc.perform(post("/inbox/" + thread.getId() + "/send").with(user("demo").roles("CLIENT")).with(csrf())
                .param("body", "Draft <message> & text").param("attachmentUrl", "javascript:alert(1)"))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get("/inbox/" + thread.getId()).with(user("demo").roles("CLIENT")).cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Draft &lt;message&gt; &amp; text")))
                .andExpect(content().string(containsString("for=\"inboxAttachmentUrl\"")));
        link.setStatus(uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.PAUSED);
        links.saveAndFlush(link);
        mvc.perform(get("/inbox/" + thread.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-thread-status=\"LOCKED\"")))
                .andExpect(content().string(containsString("This coaching conversation is read-only.")))
                .andExpect(content().string(containsString("readonly=\"readonly\"")));
    }

    @Test
    void assignedScheduleHasAnOwnedReadOnlyViewIncludingCustomCycleDays() throws Exception {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var schedule = new uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule();
        schedule.setUser(trainer);
        schedule.setName("Coach <cycle>");
        schedule.setScheduleType(uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleType.CUSTOM);
        schedule.setCustomDayCount(10);
        schedule = schedules.save(schedule);
        var exercise = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        exercise.setName("Shared cycle exercise"); exercise.setCategory("Strength"); exercise.setType("Strength"); exercise.setDifficulty(1);
        exercise = exercises.saveAndFlush(exercise);
        var entry = new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntry();
        entry.setSchedule(schedule); entry.setExercise(exercise); entry.setDayOfWeek(10); entry.setOrderNumber(1);
        scheduleEntries.saveAndFlush(entry);
        var assignment = new uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedSchedule();
        assignment.setClientUserId(client.getId()); assignment.setTrainerUserId(trainer.getId()); assignment.setSchedule(schedule);
        assignment = assignedSchedules.saveAndFlush(assignment);
        mvc.perform(get("/client/plan").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/client/plan/schedules/" + assignment.getId())));
        mvc.perform(get("/client/plan/schedules/" + assignment.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Coach &lt;cycle&gt;")))
                .andExpect(content().string(containsString("Day 10")))
                .andExpect(content().string(containsString("Shared cycle exercise")))
                .andExpect(content().string(containsString("Read-only schedule shared with you.")));
        Long assignmentId = assignment.getId();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> assignments.getScheduleForClient(trainer.getId(), assignmentId));
    }

    @Test
    void overlongAssignmentFeedbackDoesNotAlterSavedValuesAndRetainsTheDraft() throws Exception {
        var client = users.findByUsername("demo");
        var trainer = users.findByUsername("trainer_demo");
        var template = new uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplate();
        template.setOwnerUser(trainer); template.setOwnerRole(uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER); template.setName("Coach feedback assignment");
        template = workoutTemplates.saveAndFlush(template);
        var assignment = new uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedWorkout();
        assignment.setClientUserId(client.getId()); assignment.setTrainerUserId(trainer.getId()); assignment.setWorkoutTemplate(template);
        assignment.setClientNotes("Previously saved notes");
        assignment = assignedWorkouts.saveAndFlush(assignment);
        var result = mvc.perform(post("/client/plan/workouts/" + assignment.getId()).with(user("demo").roles("CLIENT")).with(csrf())
                .param("clientNotes", "Draft <notes> " + "x".repeat(1200)).param("clientFeedback", "Draft feedback").param("completed", "true"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(assignment.getClientNotes()).isEqualTo("Previously saved notes");
        assertThat(assignment.isCompleted()).isFalse();
        mvc.perform(get("/client/plan").with(user("demo").roles("CLIENT")).cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Draft &lt;notes&gt;")))
                .andExpect(content().string(containsString("no more than 1,200 characters")))
                .andExpect(content().string(containsString("planNotes-" + assignment.getId())))
                .andExpect(content().string(containsString("does not create a logged workout session")));
    }

    @Test
    void foreignTemplateCannotExposeQuestionsToClient() throws Exception {
        prepareClientCheckInTemplate();
        var foreignTrainer = new uk.ac.cf._5.group14.One_To_One.Users.User(
                "private-coach@example.com", "Private", "Coach", "private_coach", "test-only-password");
        foreignTrainer.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER);
        userRepository.saveAndFlush(foreignTrainer);
        var foreignTemplate = new uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate();
        foreignTemplate.setTrainerId(foreignTrainer.getId());
        foreignTemplate.setName("Private template");
        checkInTemplates.saveAndFlush(foreignTemplate);
        var question = new uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestion();
        question.setTemplateId(foreignTemplate.getId());
        question.setPrompt("Private coaching prompt must not be exposed");
        checkInQuestions.saveAndFlush(question);

        var html = mvc.perform(get("/checkins/client-submit").param("templateId", foreignTemplate.getId().toString())
                .with(user("demo").roles("CLIENT")))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain(question.getPrompt());
    }

    @Test
    void blankOptionalWeekSavesAndDuplicateSubmissionRetainsDraft() throws Exception {
        var template = prepareClientCheckInTemplate();
        var question = new uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestion();
        question.setTemplateId(template.getId());
        question.setPrompt("How was your training?");
        checkInQuestions.saveAndFlush(question);
        String answerKey = "q_" + question.getId();

        mvc.perform(post("/checkins/client-submit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("templateId", template.getId().toString()).param("weekStart", "")
                .param(answerKey, "A steady week").param("clientNotes", "Saved notes"))
                .andExpect(status().is3xxRedirection());
        var html = mvc.perform(post("/checkins/client-submit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("templateId", template.getId().toString()).param("weekStart", "")
                .param(answerKey, "Draft <answer> & detail").param("clientNotes", "Draft <notes> & detail"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Draft &lt;answer&gt; &amp; detail", "Draft &lt;notes&gt; &amp; detail", "already saved");
        assertThat(checkIns.findByTrainerIdOrderBySubmittedAtDesc(users.findByUsername("trainer_demo").getId()))
                .filteredOn(checkIn -> checkIn.getTemplateId().equals(template.getId())).hasSize(1);
    }

    @Test
    void invalidCoachResponseKeepsDraftAndRendersFieldLabels() throws Exception {
        var trainer = users.findByUsername("trainer_demo");
        var client = users.findByUsername("demo");
        var link = new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(
                client.getId(), trainer.getId(), uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE);
        link.setActivatedAt(java.time.Instant.now());
        links.saveAndFlush(link);
        var checkIn = new uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckIn();
        checkIn.setTrainerId(trainer.getId());
        checkIn.setClientId(client.getId());
        checkIn.setWeekStartDate(java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY));
        checkIns.saveAndFlush(checkIn);

        var html = mvc.perform(post("/checkins/trainer-review/" + checkIn.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("trainerResponse", "Draft <reply> & plan")
                .param("nextWeekFocus", "x".repeat(601)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Draft &lt;reply&gt; &amp; plan", "Keep next-week focus within 600 characters.");
        assertThat(html).contains("for=\"coachCheckinResponse\"", "for=\"coachCheckinFocus\"", "for=\"coachCheckinGoal\"");
        assertThat(checkIns.findById(checkIn.getId()).orElseThrow().getStatus())
                .isEqualTo(uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInStatus.SUBMITTED);
    }

    @Test
    void clientWorkspaceRendersSavedPhaseAndDisablesEmptyAssignments() throws Exception {
        var trainer = new uk.ac.cf._5.group14.One_To_One.Users.User(
                "workspace@example.com", "Workspace", "Trainer", "workspace_trainer", "test-only-password");
        trainer.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER);
        trainer.setTrainerVerified(true);
        trainer.setEnabled(true);
        userRepository.saveAndFlush(trainer);
        var client = users.findByUsername("demo");
        var link = new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(
                client.getId(), trainer.getId(), uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE);
        link.setActivatedAt(java.time.Instant.now());
        link.setCoachingPhase(uk.ac.cf._5.group14.One_To_One.TrainerClient.CoachingPhase.RECOVERY);
        link.setCoachingPhaseLabel("Recovery <week> & reset");
        links.saveAndFlush(link);

        var html = mvc.perform(get("/trainer/clients/" + client.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("client-workspace-v2", "id=\"clientGoals\"", "id=\"confirmationDialog\"");
        assertThat(html).containsPattern("<option[^>]*value=\"RECOVERY\"[^>]*selected=\"selected\"[^>]*>Recovery</option>");
        assertThat(html).contains("value=\"Recovery &lt;week&gt; &amp; reset\"");
        assertThat(html).containsPattern("<select[^>]*id=\"clientWorkoutTemplate\"[^>]*disabled=\"disabled\"");
        assertThat(html).containsPattern("<select[^>]*id=\"clientSchedule\"[^>]*disabled=\"disabled\"");
        assertThat(html).contains("for=\"clientPhaseNote\"", "for=\"clientWorkoutNotes\"");
    }

    @Test
    void populatedWorkoutPlayerAndCompletionRenderWithSavedSetValues() throws Exception {
        var member = users.findByUsername("demo");
        var session = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        session.setUser(member);
        session.setDate(java.time.LocalDate.now());
        session.setCreatedAt(java.time.LocalDateTime.now());
        session.setNameSnapshot("Completion regression workout");
        var exercise = new uk.ac.cf._5.group14.One_To_One.StrengthLog.ExerciseSession();
        exercise.setWorkoutSession(session);
        var set = new uk.ac.cf._5.group14.One_To_One.StrengthLog.SetLog();
        set.setExerciseSession(exercise);
        set.setSetNumber(1);
        set.setWeight(42.5);
        set.setReps(8);
        set.setNotes("Saved set <notes> & effort");
        exercise.getSetLogs().add(set);
        session.getExerciseSessions().add(exercise);
        sessions.saveAndFlush(session);

        mvc.perform(get("/workout-session/" + session.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("session-set__form")))
                .andExpect(content().string(containsString("data-rest-timer")));

        set.setCompleted(true);
        exercise.setCompleted(true);
        session.setCompleted(true);
        sessions.saveAndFlush(session);
        mvc.perform(get("/workout-session/" + session.getId() + "/complete").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Completion regression workout")))
                .andExpect(content().string(containsString("340.0 kg")))
                .andExpect(content().string(containsString("42.5 kg")))
                .andExpect(content().string(containsString("Saved set &lt;notes&gt; &amp; effort")));
    }

    @Test
    void finishedResultsStayTruthfulLocalisedAndOwned() throws Exception {
        var member = new uk.ac.cf._5.group14.One_To_One.Users.User(
                "results@example.com", "Result", "Member", "results_probe", "test-only-password");
        member.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.CLIENT);
        member.setEnabled(true);
        userRepository.saveAndFlush(member);
        var today = java.time.LocalDate.now(applicationClock);
        for (int state = 0; state <= 2; state++) {
            var session = resultSession(member, today, "Finished <session> " + state, state, state == 0 ? 0 : 2, true);
            var before = scheduledTraining.buildViewModel(member, session.getId());
            String route = "/workout-session/" + session.getId() + "/complete";
            for (String locale : java.util.List.of("en", "cy", "es", "fr", "de", "it", "pt", "pl", "nl", "zh", "ja", "ko", "ar", "hi")) {
                var doc = org.jsoup.Jsoup.parse(mvc.perform(get(route).param("lang", locale)
                        .with(user(member.getUsername()).roles("CLIENT")))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                var result = doc.selectFirst(".session-result");
                assertThat(result.text()).doesNotContain("??", "ui.result.", "{0}", "{1}", "Completed on", "sets recorded");
                assertThat(result.hasClass("session-result--complete")).isEqualTo(state == 2);
                assertThat(result.select("h1").text()).isEqualTo(session.getNameSnapshot());
                assertThat(result.selectFirst("progress").attr("aria-valuenow")).isEqualTo(state == 0 ? "0" : state == 1 ? "50" : "100");
                assertThat(result.select("a[href='/checkins/client-submit']")).isEmpty();
                assertThat(result.select("a[href^='/calendar/day/']")).hasSize(1);
                assertThat(result.select("[style],script,style")).isEmpty();
                if (state > 0) assertThat(result.select(".session-summary__set dd").text()).contains("Saved <notes> & effort");
                else assertThat(result.select(".session-result__empty")).hasSize(1);
                assertThat(doc.select("link[href^=/css/bundles/training.css]")).hasSize(1);
            }
            assertThat(scheduledTraining.buildViewModel(member, session.getId())).isEqualTo(before);
            mvc.perform(get(route).with(user("trainer_demo").roles("TRAINER"))).andExpect(status().isNotFound());
        }
        var unfinished = resultSession(member, today, "Keep working", 0, 1, false);
        mvc.perform(get("/workout-session/" + unfinished.getId() + "/complete").with(user(member.getUsername()).roles("CLIENT")))
                .andExpect(redirectedUrl("/workout-session/" + unfinished.getId()));
        assertThat(unfinished.isCompleted()).isFalse();

        var trainer = users.findByUsername("trainer_demo");
        links.saveAndFlush(new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(
                member.getId(), trainer.getId(), uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE));
        unfinished.setCompleted(true);
        sessions.saveAndFlush(unfinished);
        resultSession(member, today.minusDays(1), "Old unfinished", 0, 1, false);
        resultSession(member, today, "Empty saved draft", 0, 0, false);
        resultSession(trainer, today, "Private foreign plan", 0, 1, false);
        resultSession(member, today.plusDays(1), "Already finished next day", 1, 1, true);
        resultSession(member, today.plusDays(22), "Beyond lookahead", 0, 1, false);
        var next = resultSession(member, today.plusDays(2), "Owned <next> workout", 0, 1, false);
        assertThat(scheduledTraining.nextOpenLaunchItem(member, today).launchUrl()).isEqualTo("/workout-session/launch/session/" + next.getId());
        var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-session/" + unfinished.getId() + "/complete")
                .param("lang", "en").with(user(member.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(doc.select(".session-result__next h2").text()).contains("Owned <next> workout");
        assertThat(doc.select(".session-result__next a[href='/calendar/day/" + next.getDate() + "']")).hasSize(1);
        var coachForm = doc.selectFirst(".session-result__coach form");
        assertThat(coachForm.attr("action")).isEqualTo("/inbox/start/" + trainer.getId());
        assertThat(coachForm.attr("method")).isEqualTo("post");
        assertThat(coachForm.select("input[name='_csrf']")).hasSize(1);
        assertThat(coachForm.select("button[type='submit']")).hasSize(1);
        mvc.perform(post(coachForm.attr("action")).with(user(member.getUsername()).roles("CLIENT")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("/inbox/*"));
        assertThat(doc.select(".session-result__coach a[href='/checkins/client-submit']")).isEmpty();
        assertThat(doc.select(".session-result").text()).doesNotContain("Private foreign plan", "Already finished next day", "Beyond lookahead", "Old unfinished", "Empty saved draft");
        var coachTemplate = new uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate();
        coachTemplate.setTrainerId(trainer.getId());
        coachTemplate.setName("Available coach check-in");
        checkInTemplates.saveAndFlush(coachTemplate);
        var checkInDoc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-session/" + unfinished.getId() + "/complete")
                .with(user(member.getUsername()).roles("CLIENT"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(checkInDoc.select(".session-result__coach a[href='/checkins/client-submit']")).hasSize(1);
        assertThat(checkInDoc.select(".session-result__coach form")).isEmpty();
        var trainerResult = resultSession(trainer, today, "Trainer result", 1, 1, true);
        var trainerDoc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-session/" + trainerResult.getId() + "/complete")
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(trainerDoc.select(".session-result__coach")).isEmpty();
    }

    private uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession resultSession(
            uk.ac.cf._5.group14.One_To_One.Users.User owner, java.time.LocalDate date, String name,
            int doneSets, int totalSets, boolean finished) {
        var session = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        session.setUser(owner);
        session.setDate(date);
        session.setNameSnapshot(name);
        session.setCreatedAt(java.time.LocalDateTime.now(applicationClock));
        session.setCompleted(finished);
        if (totalSets > 0) {
            var exercise = new uk.ac.cf._5.group14.One_To_One.StrengthLog.ExerciseSession();
            exercise.setWorkoutSession(session);
            exercise.setExercise(exercises.findAll().getFirst());
            exercise.setCompleted(doneSets == totalSets);
            for (int number = 1; number <= totalSets; number++) {
                var set = new uk.ac.cf._5.group14.One_To_One.StrengthLog.SetLog();
                set.setExerciseSession(exercise);
                set.setSetNumber(number);
                set.setWeight(42.5);
                set.setReps(8);
                set.setNotes("Saved <notes> & effort");
                set.setCompleted(number <= doneSets);
                exercise.getSetLogs().add(set);
            }
            session.getExerciseSessions().add(exercise);
        }
        return sessions.saveAndFlush(session);
    }

    @Test
    void enhancedWorkoutSaveRetainsOwnershipValidationAndNativeFallback() throws Exception {
        var member = users.findByUsername("demo");
        var session = new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        session.setUser(member);
        session.setDate(java.time.LocalDate.now(applicationClock));
        session.setCreatedAt(java.time.LocalDateTime.now(applicationClock));
        session.setNameSnapshot("Saved <workout>");
        var exercise = new uk.ac.cf._5.group14.One_To_One.StrengthLog.ExerciseSession();
        exercise.setWorkoutSession(session);
        var movement = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        movement.setName("Owned <exercise>");
        movement.setCategory("Strength");
        movement.setType("Strength");
        movement.setDifficulty(1);
        exercise.setExercise(exercises.saveAndFlush(movement));
        for (int number = 1; number <= 2; number++) {
            var set = new uk.ac.cf._5.group14.One_To_One.StrengthLog.SetLog();
            set.setExerciseSession(exercise);
            set.setSetNumber(number);
            exercise.getSetLogs().add(set);
        }
        session.getExerciseSessions().add(exercise);
        sessions.saveAndFlush(session);
        var set = exercise.getSetLogs().getFirst();
        String path = "/workout-session/" + session.getId() + "/sets/" + set.getId();
        for (String locale : java.util.List.of("en", "cy", "es", "fr", "de", "it", "pt", "pl", "nl", "zh", "ja", "ko", "ar", "hi")) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workout-session/" + session.getId())
                    .param("lang", locale).with(user("demo").roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(doc.select(".session-workout").text()).doesNotContain("??", "ui.player.", "{0}", "{1}");
            assertThat(doc.select(".session-set__form")).hasSize(2).allSatisfy(form -> {
                assertThat(form.attr("aria-labelledby")).isNotBlank();
                assertThat(form.select("input[name=_csrf]")).hasSize(1);
                assertThat(form.select("label[for]")).hasSize(3);
                assertThat(form.select("[data-save-status]")).hasSize(1);
            });
            assertThat(doc.select(".session-workout style,.session-workout script,.session-workout [style]")).isEmpty();
            assertThat(doc.select("h1").text()).isEqualTo("Saved <workout>");
            assertThat(doc.select("h2").text()).contains("Owned <exercise>");
        }
        mvc.perform(post(path).header("X-Workout-Async", "1").with(user("demo").roles("CLIENT"))
                .param("weight", "42.5").param("reps", "8")).andExpect(status().isUnauthorized());
        assertThat(scheduledTraining.buildViewModel(member, session.getId()).exercises().getFirst().sets().getFirst().weight()).isNull();
        mvc.perform(post(path).header("X-Workout-Async", "1").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("weight", "42.5").param("reps", "8")).andExpect(status().isNotFound());
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post(path).header("X-Workout-Async", "1").with(user("demo").roles("CLIENT")).with(csrf())
                    .param("weight", "42.5").param("reps", "8").param("notes", "Saved <notes>").param("completed", "true"))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.sessionId").value(session.getId()))
                    .andExpect(jsonPath("$.completed").value(false))
                    .andExpect(jsonPath("$.summary.completedSets").value(1))
                    .andExpect(jsonPath("$.summary.totalSets").value(2))
                    .andExpect(jsonPath("$.summary.completionPercent").value(50))
                    .andExpect(jsonPath("$.summary.totalVolume").value(340.0));
        }
        mvc.perform(post(path).header("X-Workout-Async", "1").with(user("demo").roles("CLIENT")).with(csrf())
                .param("weight", "-1").param("notes", "Wrong")).andExpect(status().isBadRequest());
        assertThat(scheduledTraining.buildViewModel(member, session.getId()).exercises().getFirst().sets().getFirst().weight()).isEqualTo(42.5);
        mvc.perform(post(path).with(user("demo").roles("CLIENT")).with(csrf()).param("weight", "40").param("reps", "8"))
                .andExpect(redirectedUrl("/workout-session/" + session.getId() + "#set-" + set.getId()));
        scheduledTraining.completeSession(member, session.getId());
        mvc.perform(post(path).header("X-Workout-Async", "1").with(user("demo").roles("CLIENT")).with(csrf())
                .param("weight", "99")).andExpect(status().isConflict());
        assertThat(scheduledTraining.buildViewModel(member, session.getId()).exercises().getFirst().sets().getFirst().weight()).isEqualTo(40);
    }

    static Stream<Arguments> fallbackRoles() {
        return Stream.of(
            Arguments.of(null, null, "/", "/about"),
            Arguments.of("demo", "CLIENT", "/dashboard", "/client/trainers"),
            Arguments.of("trainer_demo", "TRAINER", "/trainer/dashboard", "/trainer/clients"),
            Arguments.of("gymadmin_demo", "GYM_ADMIN", "/gym/dashboard", "/gym/admin/trainers"),
            Arguments.of("admin_demo", "PLATFORM_ADMIN", "/admin/dashboard", "/admin/feedback"),
            Arguments.of("superadmin_demo", "SUPER_ADMIN", "/admin/dashboard", "/super-admin/verification/queue")
        );
    }

    @ParameterizedTest(name = "Native navigation for {1}")
    @MethodSource("fallbackRoles")
    void scriptDisabledNavigationRetainsRoleLinksAndProtectedLogout(
            String username, String role, String path, String destination) throws Exception {
        var request = get(path);
        if (username != null) request.with(user(username).roles(role));
        var html = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/css/components/core/shell-noscript.css");
        var start = html.indexOf("<details class=\"nav-fallback\">");
        assertThat(start).isNotNegative();
        var fallback = html.substring(start, html.indexOf("</details>", start));
        assertThat(fallback).contains("href=\"" + destination + "\"");
        if (username == null) {
            assertThat(fallback).contains("href=\"/login\"").doesNotContain("action=\"/logout\"", "/preferences/edit");
        } else {
            assertThat(fallback).contains("href=\"/preferences/edit\"", "action=\"/logout\"", "method=\"post\"", "name=\"_csrf\"");
            if (!"SUPER_ADMIN".equals(role)) assertThat(fallback).doesNotContain("/super-admin/verification/queue");
            if (!"TRAINER".equals(role)) assertThat(fallback).doesNotContain("href=\"/trainer/clients\"");
        }
    }

    static Stream<Arguments> pages() {
        return Stream.of(
            routes(null, null, "/", "/about", "/faq", "/pricing", "/explore", "/support", "/login", "/signup", "/signup/client", "/signup/trainer", "/signup/gym", "/forgot-password", "/policies/privacy", "/policies/terms", "/policies/payments", "/policies/subscription-terms", "/merch"),
            routes("demo", "CLIENT", "/dashboard", "/profile", "/client/trainers", "/client/plan", "/client/assigned-plan", "/calendar", "/calendar?view=week", "/goals", "/inbox", "/chat", "/workout-management", "/notes", "/vault", "/levels", "/health-record/list", "/exercise-log/list", "/nutrition", "/checkins/client-submit"),
            routes("trainer_demo", "TRAINER", "/trainer/dashboard", "/profile", "/trainer/clients", "/trainer/library", "/trainer/library/exercises", "/trainer/library/workouts", "/trainer/library/programmes", "/trainer/templates", "/workouts", "/workout-templates", "/schedules", "/inbox", "/calendar"),
            routes("gymadmin_demo", "GYM_ADMIN", "/gym/dashboard", "/profile", "/gym/admin/trainers", "/gym/admin/memberships", "/gym/admin/memberships/create", "/inbox"),
            routes("admin_demo", "PLATFORM_ADMIN", "/admin/dashboard", "/admin/feedback", "/admin/gym-applications", "/admin/off-platform-payments", "/admin/merch"),
            routes("superadmin_demo", "SUPER_ADMIN", "/admin/dashboard", "/super-admin/verification/queue")
        ).flatMap(stream -> stream);
    }

    private static Stream<Arguments> routes(String username, String role, String... paths) {
        return Stream.of(paths).map(path -> Arguments.of(username, role, path));
    }

    @ParameterizedTest(name = "{1} {2}")
    @MethodSource("pages")
    void coreRolePagesRenderOrRedirectWithoutServerErrors(String username, String role, String path) throws Exception {
        var request = get(path);
        if (username != null) request.with(user(username).roles(role));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s as %s", path, role).isBetween(200, 399);
        if (response.getStatus() == 200 && response.getContentType() != null && response.getContentType().startsWith("text/html")) {
            assertThat(response.getContentAsString()).startsWith("<!DOCTYPE html>");
        }
    }

    @Test
    void completePreferencesRenderWithBoundForm() throws Exception {
        settings.updateQuickPreferencesCompleted(users.findByUsername("demo"), true);
        mvc.perform(get("/select-preferences").with(user("demo").roles("CLIENT")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("preferences-editor-form")));
    }

    @Test
    void invalidPreferencesRenderErrorsInsteadOfLosingTheForm() throws Exception {
        mvc.perform(post("/select-preferences").with(user("demo").roles("CLIENT")).with(csrf())
                .param("language", "en").param("theme", "INVALID").param("defaultSets", "0"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.startsWith("<!DOCTYPE html>")))
            .andExpect(content().string(containsString("Please correct the highlighted fields")))
            .andExpect(content().string(containsString("preferences-editor-form")));
    }
}
