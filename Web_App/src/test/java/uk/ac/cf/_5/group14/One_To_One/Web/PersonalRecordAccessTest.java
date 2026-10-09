package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogRepository;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonalRecordAccessTest {
    @Autowired private MockMvc mvc;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Notes.NoteFolderService noteFolders;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Notes.NoteService noteService;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Notes.NoteRepository noteRepository;
    @Autowired private UserRepository users;
    @Autowired private CalendarTaskRepository tasks;
    @Autowired private ExerciseLogRepository logs;
    @Autowired private HealthRecordRepository records;
    @Autowired private HealthRecordService healthService;
    @Autowired private uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository exercises;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureReadingRepository bloodPressureReadings;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    @Autowired private uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogRepository nutrition;
    @Autowired private uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService settingsService;

    private User client(String name) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        User client = new User(unique + "@example.com", name, "Record", "record_" + unique, "fixture-only");
        client.setRole(Role.CLIENT); return users.save(client);
    }

    private CalendarTask task(User owner, String title) {
        CalendarTask task = new CalendarTask(); task.setUser(owner); task.setDate(LocalDate.now()); task.setTitle(title);
        task.setExercise(true); task.setRequiresLog(true); return tasks.save(task);
    }

    @Test
    void logLinkingChecksOwnerAndPreservesOneCalendarLogAndRejectedDrafts() throws Exception {
        User owner = client("Owner"), other = client("Other");
        CalendarTask own = task(owner, "Owned calendar exercise"), foreign = task(other, "Private calendar exercise");
        mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date", LocalDate.now().toString()).param("moodBefore", "2").param("moodAfter", "3").param("confidence", "3")
                .param("calendarTaskId", foreign.getId().toString()).param("durationMinutes", "30"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        assertThat(logs.findByUser(owner)).isEmpty(); assertThat(foreign.getExerciseLog()).isNull();
        mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date", LocalDate.now().toString()).param("moodBefore", "2").param("moodAfter", "3").param("confidence", "3")
                .param("calendarTaskId", own.getId().toString()).param("durationMinutes", "30").param("comments", "Owned searchable reflection"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/calendar"));
        var log = logs.findByUser(owner).getFirst();
        assertThat(log.getCalendarTask().getId()).isEqualTo(own.getId());
        assertThat(own.getExerciseLog().getId()).isEqualTo(log.getId());
        mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date", LocalDate.now().toString()).param("moodBefore", "2").param("moodAfter", "3").param("confidence", "3")
                .param("calendarTaskId", own.getId().toString()).param("comments", "Keep <duplicate draft>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Keep &lt;duplicate draft&gt;")));
        mvc.perform(post("/exercise-log/edit/" + log.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date", LocalDate.now().toString()).param("moodBefore", "2").param("moodAfter", "3").param("confidence", "3")
                .param("calendarTaskId", own.getId().toString()).param("durationMinutes", "bad-time").param("comments", "Keep <reflection>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-time\"")));
        mvc.perform(get("/exercise-log/edit/" + log.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("personal-log-rating")));
        mvc.perform(get("/exercise-log/list").param("q", "searchable").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Owned searchable reflection")))
                .andExpect(content().string(not(containsString("Private calendar exercise"))));
        mvc.perform(get("/exercise-log/view/" + log.getId()).with(user(other.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/exercise-log/list"));
        assertThat(logs.findByUser(owner)).hasSize(1); assertThat(log.getComments()).isEqualTo("Owned searchable reflection");
        for (int index = 0; index < 7; index++) {
            var paged = new uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLog(); paged.setUser(owner);
            paged.setDate(LocalDate.now().minusDays(index)); paged.setMoodBefore(2); paged.setMoodAfter(3); paged.setConfidence(3);
            paged.setComments("Paging fixture " + index); logs.save(paged);
        }
        mvc.perform(get("/exercise-log/list").param("q", "Paging fixture").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("logPageCount", 2))
                .andExpect(content().string(containsString("Paging fixture 0")))
                .andExpect(content().string(not(containsString("Paging fixture 6"))));
        mvc.perform(get("/exercise-log/list").param("q", "Paging fixture").param("page", "2").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Paging fixture 6")))
                .andExpect(content().string(not(containsString("Paging fixture 0"))));
    }

    @Test
    void healthRecordsRejectForeignReadsAndForgedIdentityAndRetainInvalidMeasurements() throws Exception {
        User owner = client("Owner"), other = client("Other");
        HealthRecord foreign = new HealthRecord(); foreign.setUser(other); foreign.setBaselineDate(LocalDateTime.now());
        foreign.setWeightKg(77.0); foreign.setHeightCm(180.0); foreign.setWaistCm(85.0); foreign.setBmi(23.77);
        foreign.setWaistHeightRatio(0.47); foreign.setSystolicBloodPressure(120); foreign.setDiastolicBloodPressure(80); foreign.setCholesterol(4.2);
        foreign.setActivityLevel("Private health baseline"); foreign = records.saveAndFlush(foreign);
        mvc.perform(get("/health-record/" + foreign.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/health-record").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("name=\"user.id\""))));
        mvc.perform(post("/health-record").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("id", foreign.getId().toString()).param("user.id", other.getId().toString()).param("bmi", "999")
                .param("baselineDate", "2026-10-01T12:00").param("systolicBloodPressure", "120").param("diastolicBloodPressure", "80")
                .param("cholesterol", "4.2").param("weightKg", "80").param("heightCm", "180").param("waistCm", "85").param("activityLevel", "Moderately Active"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/health-record/list?success"));
        var saved = records.findAllByUser(owner).getFirst(); assertThat(saved.getId()).isNotEqualTo(foreign.getId());
        assertThat(saved.getBmi()).isEqualTo(24.69); assertThat(foreign.getWeightKg()).isEqualTo(77.0);
        mvc.perform(get("/health-record/" + saved.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("personal-records-v2")));
        mvc.perform(get("/health-record/list").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Private health baseline"))));
        mvc.perform(post("/health-record").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("baselineDate", "2026-10-01T12:00").param("systolicBloodPressure", "120").param("diastolicBloodPressure", "80")
                .param("cholesterol", "4.2").param("weightKg", "bad-weight").param("heightCm", "180").param("waistCm", "85").param("activityLevel", "Moderately Active"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-weight\"")));
        assertThat(healthService.getAllHealthRecords(owner)).hasSize(1);
    }

    @Test
    void exerciseInstructionsRejectMissingResourcesAndUnsafeVideoLinks() throws Exception {
        User owner = client("Owner");
        var exercise = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        exercise.setName("Instruction regression exercise"); exercise.setCategory("Bodyweight");
        exercise.setDifficulty(1); exercise.setType("Strength"); exercise.setDescription("Saved <technique> guidance");
        exercise.setVideoUrl("javascript:alert(1)"); exercise = exercises.save(exercise);
        String route = "/exercise/" + exercise.getId();
        mvc.perform(get(route).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Saved &lt;technique&gt; guidance")))
                .andExpect(content().string(not(containsString("javascript:alert(1)"))));
        exercise.setVideoUrl("https://example.com/instruction"); exercises.save(exercise);
        mvc.perform(get(route).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"https://example.com/instruction\"")))
                .andExpect(content().string(not(containsString("<iframe"))));
        mvc.perform(get("/exercise/" + Long.MAX_VALUE).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void bloodPressureCreationCannotReplaceForeignReadingOrReturnAccountObjects() throws Exception {
        User owner = client("Owner"), other = client("Other");
        var foreign = new uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureReading();
        foreign.setUser(other); foreign.setReadingDate(LocalDate.now()); foreign.setSystolic(120); foreign.setDiastolic(80);
        foreign = bloodPressureReadings.saveAndFlush(foreign);
        String body = json.writeValueAsString(java.util.Map.of("id", foreign.getId(), "user", java.util.Map.of("id", other.getId()),
                "readingDate", LocalDate.now().toString(), "systolic", 121, "diastolic", 81));
        mvc.perform(post("/api/blood-pressure").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").exists());
        var owned = bloodPressureReadings.findByUserOrderByReadingDateDescReadingTimeDesc(owner);
        assertThat(owned).hasSize(1); assertThat(owned.getFirst().getId()).isNotEqualTo(foreign.getId());
        assertThat(bloodPressureReadings.findById(foreign.getId()).orElseThrow().getSystolic()).isEqualTo(120);
        mvc.perform(get("/api/blood-pressure").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(owned.getFirst().getId()))
                .andExpect(jsonPath("$[0].user").doesNotExist()).andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(get("/health/blood-pressure").param("range", "-3").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("range", 30));
        mvc.perform(post("/health/blood-pressure").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("readingDate", LocalDate.now().toString()).param("systolic", "121").param("diastolic", "81").param("notes", "Keep <duplicate reading>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Keep &lt;duplicate reading&gt;")));
        String edit = "/health/blood-pressure/edit/" + owned.getFirst().getId();
        mvc.perform(post(edit).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("readingDate", LocalDate.now().toString()).param("systolic", "bad-bp").param("diastolic", "81"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-bp\"")))
                .andExpect(content().string(containsString("action=\"" + edit + "\"")));
        mvc.perform(post("/health/blood-pressure").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("id", foreign.getId().toString()).param("user.id", other.getId().toString())
                .param("readingDate", LocalDate.now().toString()).param("readingTime", "12:00").param("systolic", "123").param("diastolic", "82"))
                .andExpect(status().is3xxRedirection());
        assertThat(bloodPressureReadings.findByUserOrderByReadingDateDescReadingTimeDesc(owner)).hasSize(2);
        assertThat(bloodPressureReadings.findById(foreign.getId()).orElseThrow().getSystolic()).isEqualTo(120);
    }

    @Test
    void nutritionSeparatesTargetsAndPreservesOwnedEntriesAndInvalidDrafts() throws Exception {
        User owner = client("NutritionOwner"), other = client("NutritionOther");
        var settings = settingsService.getOrCreate(owner);
        settings.setMacroTargetCalories(2200); settings.setMacroTargetProtein(140);
        settings.setMacroTargetCarbs(250); settings.setMacroTargetFat(65);
        String date = LocalDate.now().toString();
        var empty = mvc.perform(get("/nutrition").param("date", date).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("hasEntry", false))
                .andExpect(content().string(containsString("nutrition-targets")))
                .andExpect(content().string(containsString("2200"))).andReturn();
        var emptyForm = (uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogForm)
                empty.getModelAndView().getModel().get("nutritionForm");
        assertThat(emptyForm.getCalories()).isNull(); assertThat(emptyForm.getProteinGrams()).isNull();
        assertThat(nutrition.findByUserAndDate(owner, LocalDate.now())).isEmpty();
        var otherPage = mvc.perform(get("/nutrition").param("date", date).with(user(other.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/nutrition").with(user(other.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision", (String) otherPage.getModelAndView().getModel().get("nutritionRevision"))
                .param("date", date).param("calories", "1900").param("proteinGrams", "110")
                .param("carbsGrams", "200").param("fatGrams", "60").param("notes", "Foreign private intake"))
                .andExpect(status().is3xxRedirection());
        var foreign = nutrition.findByUserAndDate(other, LocalDate.now()).orElseThrow();
        mvc.perform(post("/nutrition").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision", (String) empty.getModelAndView().getModel().get("nutritionRevision"))
                .param("id", foreign.getId().toString()).param("user.id", other.getId().toString())
                .param("date", date).param("calories", "2000").param("proteinGrams", "120")
                .param("carbsGrams", "200").param("fatGrams", "60").param("waterMl", "2010").param("notes", "Own <intake>"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/nutrition?date=" + date))
                .andExpect(flash().attribute("nutritionSaved", true));
        var own = nutrition.findByUserAndDate(owner, LocalDate.now()).orElseThrow();
        assertThat(own.getId()).isNotEqualTo(foreign.getId()); assertThat(foreign.getCalories()).isEqualTo(1900);
        assertThat(own.getWaterMl()).isEqualTo(2010);
        mvc.perform(get("/nutrition").param("date", date).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Saved entry")))
                .andExpect(content().string(containsString("Own &lt;intake&gt;")))
                .andExpect(content().string(not(containsString("Foreign private intake"))));
        mvc.perform(post("/nutrition").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date", date).param("calories", "bad-calories").param("proteinGrams", "120")
                .param("carbsGrams", "200").param("fatGrams", "60").param("notes", "Keep <nutrition draft>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-calories\"")))
                .andExpect(content().string(containsString("Keep &lt;nutrition draft&gt;")));
        assertThat(own.getCalories()).isEqualTo(2000); assertThat(own.getNotes()).isEqualTo("Own <intake>");
        mvc.perform(get("/api/nutrition/range").param("start", date).param("end", LocalDate.now().minusDays(1).toString())
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/nutrition/range").param("start", date).param("end", date)
                .with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].calories").value(2000))
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(content().string(not(containsString("Foreign private intake"))));
    }

    @Test
    void notesProtectHistoricMarkupDestinationsAndNativeDraftsAndPlainText() throws Exception {
        User owner = client("NoteOwner"), other = client("NoteOther");
        var folder = noteFolders.createFolder(owner, "Training <drafts>", "slate");
        var destination = noteFolders.createFolder(owner, "Moved notes", "slate");
        var foreignFolder = noteFolders.createFolder(other, "Foreign private folder", "slate");
        var note = noteService.create(owner, folder.getId(), "Owned <note>", "<p>Original reflection</p>", "orange");
        note.setContent("<script>badHistoricalScript()</script><p onclick='badHandler()'>Historic safe text</p>");
        noteRepository.saveAndFlush(note);
        mvc.perform(get("/notes/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Historic safe text")))
                .andExpect(content().string(not(containsString("badHistoricalScript()"))))
                .andExpect(content().string(not(containsString("badHandler()"))));
        mvc.perform(get("/notes/export/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("badHistoricalScript()"))));
        mvc.perform(get("/notes/api/notes/" + note.getId()).with(user(other.getUsername()).roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notes/api/notes/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Unwanted mutation\",\"content\":\"Changed\",\"folderId\":" + foreignFolder.getId() + "}"))
                .andExpect(status().isNotFound());
        assertThat(note.getTitle()).isEqualTo("Owned <note>"); assertThat(note.getFolder().getId()).isEqualTo(folder.getId());
        mvc.perform(get("/notes/" + note.getId() + "/edit").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"plainContent\"")))
                .andExpect(content().string(containsString("value=\"orange\" checked")));
        mvc.perform(post("/notes/" + note.getId() + "/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("title", "x".repeat(121)).param("content", "<p>Keep rejected reflection</p>").param("folderId", destination.getId().toString()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Keep rejected reflection")))
                .andExpect(content().string(containsString("/notes/" + note.getId() + "/edit")));
        assertThat(note.getTitle()).isEqualTo("Owned <note>");
        mvc.perform(post("/notes/folders/" + folder.getId() + "/new").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("title", "Plain captured note").param("content", "").param("plainContent", "Literal <tag>\nSecond line")
                .param("folderId", destination.getId().toString()).param("noteColour", "red"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("noteSaved", true));
        var created = noteService.getNotesForFolder(owner, destination.getId(), "Plain captured").getFirst();
        assertThat(created.getContent()).contains("&lt;tag&gt;", "<br>"); assertThat(created.getColour()).isEqualTo("red");
        mvc.perform(get("/notes").param("folderId", folder.getId().toString()).param("noteId", created.getId().toString())
                .with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notes?folderId=" + destination.getId() + "&noteId=" + created.getId()));
        mvc.perform(post("/notes/folders/new").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("name", "x".repeat(81)))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"" + "x".repeat(81) + "\"")));
    }
}
