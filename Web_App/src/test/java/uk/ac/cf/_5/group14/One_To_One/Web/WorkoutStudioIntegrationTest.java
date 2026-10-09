package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Workouts.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise persistence, privacy and real template rendering for the personal studio. */
@SpringBootTest(properties = "app.storage.workout-video-dir=build/test-personal-recordings")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkoutStudioIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private WorkoutBuilderService studio;
    @Autowired private WorkoutTemplateRepository templates;
    @Autowired private WorkoutSessionRepository sessions;
    @Autowired private ExerciseRepository exercises;
    @Autowired private CustomExerciseRepository customs;

    private CustomExercise custom(String username, String name) {
        var exercise = new CustomExercise();
        exercise.setUserId(users.findByUsername(username).getId());
        exercise.setName(name); exercise.setDescription("Saved <script>description</script>");
        return customs.save(exercise);
    }

    @Test
    void createBindsAllRowsAndResolvesOwnedReferencesWithoutTrustingPostedIds() throws Exception {
        var client = users.findByUsername("demo");
        var standard = exercises.findAll().getFirst();
        var custom = custom("demo", "Owned <custom> movement");
        var result = mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Saved three-row workout").param("description", "Real description")
                .param("exercises[0].exerciseRef", "e:" + standard.getId())
                .param("exercises[0].exerciseName", "Forged catalogue name").param("exercises[0].sets", "2")
                .param("exercises[0].reps", "8").param("exercises[0].restSeconds", "0")
                .param("exercises[0].notes", "Retain zero rest").param("exercises[0].customExerciseId", "999999")
                .param("exercises[1].exerciseRef", "c:" + custom.getId()).param("exercises[1].sets", "1")
                .param("exercises[1].exerciseName", "Forged custom name")
                .param("exercises[2].exerciseName", " Manual movement ").param("exercises[2].sets", "3")
                .param("exercises[2].exerciseId", "999999").param("exercises[2].notes", "Retained notes"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var saved = templates.findByOwnerUserOrderByUpdatedAtDesc(client).stream()
                .filter(item -> item.getName().equals("Saved three-row workout")).findFirst().orElseThrow();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/workouts/" + saved.getId() + "/edit");
        assertThat(saved.getExercises()).extracting(WorkoutExercise::getExerciseName)
                .containsExactly(standard.getName(), custom.getName(), "Manual movement");
        assertThat(saved.getExercises()).extracting(WorkoutExercise::getOrderIndex).containsExactly(0, 1, 2);
        assertThat(saved.getExercises().getFirst().getRestSeconds()).isZero();
        assertThat(saved.getExercises().getFirst().getCustomExerciseId()).isNull();
        assertThat(saved.getExercises().get(2).getExerciseId()).isNull();
        var session = studio.startSession(client, saved.getId());
        assertThat(session.getSetLogs()).hasSize(6);
        assertThat(session.getSetLogs().getFirst().getRestSeconds()).isZero();
        mvc.perform(post("/workouts/" + saved.getId() + "/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workouts")).andExpect(flash().attribute("studioError", "ui.studio.deleteBlocked"));
        assertThat(templates.existsById(saved.getId())).isTrue();
        assertThat(sessions.existsById(session.getId())).isTrue();
    }

    @Test
    void rejectedChangesKeepExistingRowsAndRenderRetainedDraftInsteadOfServerErrors() throws Exception {
        var client = users.findByUsername("demo");
        var original = new WorkoutTemplateForm(); original.setName("Original workout");
        var row = new WorkoutExerciseForm(); row.setExerciseName("Original movement"); row.setNotes("Keep me");
        original.getExercises().add(row);
        var saved = studio.createTemplate(client, original);
        var foreign = custom("trainer_demo", "Private trainer movement");
        var invalid = new WorkoutTemplateForm(); invalid.setName("Rejected replacement");
        var bad = new WorkoutExerciseForm(); bad.setExerciseRef("c:" + foreign.getId()); invalid.getExercises().add(bad);
        assertThatThrownBy(() -> studio.updateTemplate(client, saved.getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("ui.studio.reference");
        assertThat(saved.getName()).isEqualTo("Original workout");
        assertThat(saved.getExercises()).hasSize(1);
        assertThat(saved.getExercises().getFirst().getNotes()).isEqualTo("Keep me");
        var doc = org.jsoup.Jsoup.parse(mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Retained invalid draft").param("description", "Keep description")
                .param("exercises[0].exerciseName", "Keep movement").param("exercises[0].sets", "51")
                .param("exercises[0].notes", "Keep notes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(doc.selectFirst(".studio-v2__error").text()).contains("50 exercises");
        assertThat(doc.selectFirst("#studio-name").val()).isEqualTo("Retained invalid draft");
        assertThat(doc.selectFirst("#studio-row-0-notes").val()).isEqualTo("Keep notes");
        assertThat(doc.selectFirst("#studio-row-0-sets").val()).isEqualTo("51");
        mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Type error draft").param("exercises[0].sets", "abc"))
                .andExpect(status().isOk()).andExpect(model().attribute("studioError", "ui.studio.invalid"));
        mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Notes without movement").param("exercises[0].notes", "Must not disappear"))
                .andExpect(status().isOk()).andExpect(model().attribute("studioError", "ui.studio.invalid"));
        assertThat(templates.findByOwnerUserOrderByUpdatedAtDesc(client)).extracting(WorkoutTemplate::getName)
                .doesNotContain("Retained invalid draft", "Type error draft", "Notes without movement");
        for (String route : new String[]{"/edit", "/start"}) {
            mvc.perform(get("/workouts/" + saved.getId() + route).with(user("trainer_demo").roles("TRAINER")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/workouts/" + saved.getId() + "/delete").with(user("trainer_demo").roles("TRAINER")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyDraftCannotStartAndUnusedTemplateCanBeDeleted() throws Exception {
        var client = users.findByUsername("demo");
        var form = new WorkoutTemplateForm(); form.setName("Empty draft");
        form.getExercises().add(new WorkoutExerciseForm());
        var saved = studio.createTemplate(client, form);
        assertThat(saved.getExercises()).isEmpty();
        mvc.perform(get("/workouts/" + saved.getId() + "/start").with(user("demo").roles("CLIENT")))
                .andExpect(redirectedUrl("/workouts")).andExpect(flash().attribute("studioError", "ui.studio.emptyExercise"));
        assertThat(sessions.existsByTemplate(saved)).isFalse();
        mvc.perform(post("/workouts/" + saved.getId() + "/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workouts"));
        assertThat(templates.existsById(saved.getId())).isFalse();
        mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).param("name", "No token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyUiLocaleRendersNativeStudioModesWithPrivateEscapedDataAndLabelledControls() throws Exception {
        var owned = custom("demo", "Owned <script>movement</script>");
        custom("trainer_demo", "Private trainer custom exercise");
        var form = new WorkoutTemplateForm(); form.setName("Owned <workout>"); form.setDescription("Escaped <script>description</script>");
        studio.createTemplate(users.findByUsername("demo"), form);
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            for (String mode : new String[]{"my-workouts", "library", "builder"}) {
                var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workouts").param("mode", mode).param("lang", locale)
                        .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                var root = doc.selectFirst(".studio-v2");
                assertThat(root.text()).doesNotContain("??", "ui.studio.", "{0}", "Private trainer custom exercise", "~45 min");
                assertThat(root.select("h1")).hasSize(1);
                assertThat(root.select("script, [style]")).isEmpty();
                assertThat(root.select("[role=tab][aria-selected=true]").attr("data-mode-tab")).isEqualTo(mode);
                assertThat(root.select("[role=tabpanel]:not([hidden])")).hasSize(1);
                assertThat(root.select("[data-library-card][data-ref='c:" + owned.getId() + "'] h3").text()).isEqualTo(owned.getName());
                assertThat(root.select("[data-studio-builder] input[name=_csrf]")).hasSize(1);
                root.select("template").remove(); // Jsoup traverses inert template contents; the browser does not.
                for (var field : root.select("[data-studio-rows] [data-field]:not([type=hidden])")) {
                    assertThat(root.select("label[for='" + field.id() + "']")).hasSize(1);
                }
            }
        }
        for (var role : new String[][]{{"trainer_demo", "TRAINER"}, {"gymadmin_demo", "GYM_ADMIN"}}) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workouts").with(user(role[0]).roles(role[1])))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(doc.select(".studio-v2").text()).doesNotContain("Owned <workout>", owned.getName());
        }
    }

    @Test
    void editorRendersOwnedRowsAndNativeControlsInEveryUiLocale() throws Exception {
        var client = users.findByUsername("demo");
        var form = new WorkoutTemplateForm(); form.setName("Edited <workout>");
        var row = new WorkoutExerciseForm(); row.setExerciseName("Owned <movement>"); row.setNotes("Saved <notes>"); row.setRestSeconds(0);
        form.getExercises().add(row);
        var saved = studio.createTemplate(client, form);
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workouts/" + saved.getId() + "/edit").param("lang", locale)
                    .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var root = doc.selectFirst(".studio-editor-v2"); root.select("template").remove();
            assertThat(root.text()).doesNotContain("??", "ui.studio.", "{0}");
            assertThat(root.select("h1").text()).isEqualTo(saved.getName());
            assertThat(root.select("script,[style]")).isEmpty();
            assertThat(root.select("input[name=_csrf]")).hasSize(1);
            assertThat(root.selectFirst("[data-field=id]").val()).isEqualTo(saved.getExercises().getFirst().getId().toString());
            assertThat(root.selectFirst("[data-field=notes]").val()).isEqualTo("Saved <notes>");
            assertThat(root.selectFirst("[data-field=restSeconds]").val()).isEqualTo("0");
            assertThat(root.selectFirst("[data-remove-row]").attr("value")).isEqualTo("remove:0");
            assertThat(root.selectFirst("[data-add-row]").hasAttr("formnovalidate")).isTrue();
            for (var field : root.select("input:not([type=hidden]),select,textarea")) {
                assertThat(root.select("label[for='" + field.id() + "']")).hasSize(1);
            }
            assertThat(doc.select("script[src^=/js/workouts/workouts-builder.js]")).hasSize(1);
            assertThat(doc.select("link[href^=/css/bundles/training.css]")).hasSize(1);
        }
    }

    @Test
    void nativeDraftControlsPreserveValuesWithoutPersistingAndFinalSaveRetainsRowIds() throws Exception {
        var client = users.findByUsername("demo");
        var form = new WorkoutTemplateForm(); form.setName("Original native workout");
        for (String name : new String[]{"First movement", "Second movement", "Third movement"}) {
            var row = new WorkoutExerciseForm(); row.setExerciseName(name); row.setNotes(name + " notes");
            form.getExercises().add(row);
        }
        var saved = studio.createTemplate(client, form);
        templates.flush();
        var ids = saved.getExercises().stream().map(WorkoutExercise::getId).toList();
        var started = studio.startSession(client, saved.getId());
        var savedNames = started.getSetLogs().stream().map(WorkoutSetLog::getExerciseName).toList();
        var request = post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("editAction", "up:2").param("name", "Native unsaved draft").param("description", "Keep description");
        for (int index = 0; index < 3; index++) request.param("exercises[" + index + "].id", ids.get(index).toString())
                .param("exercises[" + index + "].exerciseName", "Draft movement " + index)
                .param("exercises[" + index + "].notes", "Draft notes " + index).param("exercises[" + index + "].restSeconds", "0");
        var draft = org.jsoup.Jsoup.parse(mvc.perform(request).andExpect(status().isOk())
                .andExpect(model().attribute("studioDraftChanged", true)).andReturn().getResponse().getContentAsString());
        assertThat(draft.selectFirst("#studio-name").val()).isEqualTo("Native unsaved draft");
        assertThat(draft.select("[data-studio-rows] [data-field=exerciseName]")).extracting(org.jsoup.nodes.Element::val)
                .containsExactly("Draft movement 0", "Draft movement 2", "Draft movement 1");
        assertThat(draft.selectFirst("#studio-row-1-exerciseName").hasAttr("autofocus")).isTrue();
        assertThat(saved.getName()).isEqualTo("Original native workout");
        assertThat(saved.getExercises()).extracting(WorkoutExercise::getId).containsExactlyElementsOf(ids);
        mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("editAction", "add").param("name", "Unsaved add").param("exercises[0].exerciseName", "Keep movement"))
                .andExpect(status().isOk()).andExpect(model().attribute("rowFocusIndex", 1));
        mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("editAction", "remove:0").param("name", "Unsaved removal").param("exercises[0].exerciseName", "Remove draft row"))
                .andExpect(status().isOk()).andExpect(model().attribute("rowFocusIndex", -1));
        mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Saved reordered workout").param("exercises[0].id", ids.get(2).toString())
                .param("exercises[0].exerciseName", "Third updated").param("exercises[0].restSeconds", "0")
                .param("exercises[0].notes", "Retained edit notes").param("exercises[1].id", ids.getFirst().toString())
                .param("exercises[1].exerciseName", "First updated"))
                .andExpect(redirectedUrl("/workouts/" + saved.getId() + "/edit")).andExpect(flash().attribute("studioSaved", true));
        templates.flush();
        assertThat(saved.getExercises()).extracting(WorkoutExercise::getId).containsExactly(ids.get(2), ids.getFirst());
        assertThat(saved.getExercises()).extracting(WorkoutExercise::getOrderIndex).containsExactly(0, 1);
        assertThat(saved.getExercises().getFirst().getNotes()).isEqualTo("Retained edit notes");
        assertThat(saved.getExercises().getFirst().getRestSeconds()).isZero();
        assertThat(started.getSetLogs()).extracting(WorkoutSetLog::getExerciseName).containsExactlyElementsOf(savedNames);
    }

    @Test
    void editorRejectsForeignOrDuplicatedRowIdsAndRetainsInvalidDraftWithoutMutation() throws Exception {
        var client = users.findByUsername("demo");
        var form = new WorkoutTemplateForm(); form.setName("Owned row source");
        var row = new WorkoutExerciseForm(); row.setExerciseName("Retained original"); form.getExercises().add(row);
        var saved = studio.createTemplate(client, form);
        var foreign = studio.createTemplate(users.findByUsername("trainer_demo"), form);
        for (var id : new Long[]{foreign.getExercises().getFirst().getId(), 999999L}) {
            mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                    .param("name", "Rejected foreign row").param("exercises[0].id", id.toString())
                    .param("exercises[0].exerciseName", "Forged row"))
                    .andExpect(status().isOk()).andExpect(model().attribute("studioError", "ui.studio.reference"));
        }
        var ownId = saved.getExercises().getFirst().getId().toString();
        mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("demo").roles("CLIENT")).with(csrf())
                .param("name", "Rejected duplicate row").param("exercises[0].id", ownId).param("exercises[1].id", ownId)
                .param("exercises[0].exerciseName", "Duplicate one").param("exercises[1].exerciseName", "Duplicate two"))
                .andExpect(status().isOk()).andExpect(model().attribute("studioError", "ui.studio.reference"));
        var doc = org.jsoup.Jsoup.parse(mvc.perform(post("/workouts/" + saved.getId() + "/edit")
                .with(user("demo").roles("CLIENT")).with(csrf()).param("name", "Retained invalid edit")
                .param("description", "Retained description").param("exercises[0].id", ownId)
                .param("exercises[0].exerciseName", "Retained movement").param("exercises[0].notes", "Retained notes")
                .param("exercises[0].restSeconds", "-1"))
                .andExpect(status().isOk()).andExpect(model().attribute("studioError", "ui.studio.limits"))
                .andReturn().getResponse().getContentAsString());
        assertThat(doc.selectFirst("#studio-name").val()).isEqualTo("Retained invalid edit");
        assertThat(doc.selectFirst("#studio-row-0-notes").val()).isEqualTo("Retained notes");
        assertThat(doc.selectFirst(".studio-editor-v2").attr("data-draft-changed")).isEqualTo("true");
        assertThat(saved.getName()).isEqualTo("Owned row source");
        assertThat(saved.getExercises()).hasSize(1);
        assertThat(saved.getExercises().getFirst().getExerciseName()).isEqualTo("Retained original");
        mvc.perform(post("/workouts/" + saved.getId() + "/edit").with(user("trainer_demo").roles("TRAINER")).with(csrf())
                .param("editAction", "add")).andExpect(status().isNotFound());
        mvc.perform(post("/workouts").with(user("demo").roles("CLIENT")).with(csrf())
                .param("editAction", "add").param("name", "Unsaved create draft"))
                .andExpect(status().isOk()).andExpect(model().attribute("studioMode", "builder"));
        assertThat(templates.findByOwnerUserOrderByUpdatedAtDesc(client)).extracting(WorkoutTemplate::getName)
                .doesNotContain("Unsaved create draft", "Retained invalid edit");
    }

    private WorkoutSession personalSession(String username, int setCount) {
        var form = new WorkoutTemplateForm(); form.setName("Personal <session> proof");
        var row = new WorkoutExerciseForm(); row.setExerciseName("Movement <saved>");
        row.setSets(setCount); row.setReps(8); row.setRestSeconds(0); row.setNotes("Original <note>");
        form.getExercises().add(row);
        return studio.startSession(users.findByUsername(username), studio.createTemplate(users.findByUsername(username), form).getId());
    }

    @Test
    void personalPlayerUsesOwnedSessionContextAndNativeFormsInAllUiLocales() throws Exception {
        var session = personalSession("demo", 2);
        var httpSession = new org.springframework.mock.web.MockHttpSession();
        httpSession.setAttribute("nameSnapshot", "HTTP session distraction");
        for (String locale : java.util.List.of("en", "cy", "es", "fr", "de", "it", "pt", "pl", "nl", "zh", "ja", "ko", "ar", "hi")) {
            var html = mvc.perform(get("/workouts/studio/" + session.getId()).param("lang", locale)
                    .session(httpSession).with(user("demo").roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var doc = org.jsoup.Jsoup.parse(html);
            assertThat(doc.selectFirst("h1").text()).isEqualTo("Personal <session> proof");
            assertThat(doc.selectFirst(".personal-player").attr("data-workout-session-id")).isEqualTo(session.getId().toString());
            assertThat(doc.select("form[data-set-id]")).hasSize(2);
            for (var form : doc.select("form[data-set-id]")) {
                assertThat(form.attr("action")).startsWith("/workouts/studio/" + session.getId() + "/sets/");
                assertThat(form.select("input[name=_csrf]")).hasSize(1);
                assertThat(form.selectFirst("input[name=notes]").attr("maxlength")).isEqualTo("500");
                for (var input : form.select("input:not([type=hidden])")) {
                    assertThat(doc.select("label[for=" + input.id() + "]")).hasSize(1);
                }
            }
            assertThat(doc.selectFirst("progress").attr("value")).isEqualTo("0");
            assertThat(doc.selectFirst("[data-rest]").hasAttr("disabled")).isTrue();
            assertThat(doc.selectFirst("[data-camera]").hasAttr("hidden")).isTrue();
            assertThat(doc.selectFirst("input[type=file]").attr("accept")).isEqualTo("video/mp4,video/webm");
            assertThat(doc.select("script[src*=workouts-player.js]")).hasSize(1);
            assertThat(html).doesNotContain("??ui.personal.", "AI form feedback", "HTTP session distraction");
        }
        mvc.perform(get("/workouts/" + session.getTemplate().getId() + "/start").with(user("demo").roles("CLIENT")))
                .andExpect(redirectedUrl("/workouts/studio/" + session.getId()));
        assertThat(studio.startSession(users.findByUsername("demo"), session.getTemplate().getId()).getId()).isEqualTo(session.getId());
    }

    @Test
    void personalNativeAndJsonSavesRollUpClearValuesAndReopenWithoutLosingInvalidDrafts() throws Exception {
        var session = personalSession("demo", 1); var set = session.getSetLogs().getFirst();
        String route = "/workouts/studio/" + session.getId() + "/sets/" + set.getId();
        mvc.perform(post(route).with(user("demo").roles("CLIENT")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("weight", "42.5").param("reps", "8")
                .param("notes", "Saved <literal> note").param("completed", "true"))
                .andExpect(redirectedUrl("/workouts/studio/" + session.getId() + "#set-" + set.getId()))
                .andExpect(flash().attribute("playerSaved", true));
        assertThat(session.isCompleted()).isTrue(); assertThat(session.getCompletedAt()).isNotNull();
        assertThat(session.getTotalVolume()).isEqualTo(340.0);
        var invalid = org.jsoup.Jsoup.parse(mvc.perform(post(route).with(user("demo").roles("CLIENT")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("weight", "-1").param("reps", "6")
                .param("notes", "Retain rejected draft")).andExpect(status().isOk())
                .andExpect(model().attribute("playerError", "ui.personal.invalid"))
                .andReturn().getResponse().getContentAsString());
        assertThat(invalid.selectFirst("input[name=weight]").val()).isEqualTo("-1");
        assertThat(invalid.selectFirst("input[name=notes]").val()).isEqualTo("Retain rejected draft");
        assertThat(set.getWeight()).isEqualTo(42.5); assertThat(set.getNotes()).isEqualTo("Saved <literal> note");
        mvc.perform(post(route).with(user("demo").roles("CLIENT")).with(csrf()).contentType("application/json")
                .content("{\"weight\":null,\"reps\":null,\"notes\":\"\",\"completed\":false,\"replaceValues\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.percent").value(0)).andExpect(jsonPath("$.totalVolume").value(0));
        assertThat(set.getWeight()).isNull(); assertThat(set.getReps()).isNull();
        assertThat(session.getCompletedAt()).isNull();
        mvc.perform(post(route).with(user("demo").roles("CLIENT")).with(csrf()).contentType("application/json")
                .content("{\"notes\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.messageKey").value("ui.personal.invalid"));
        assertThat(set.getNotes()).isEmpty();
        var bad = new WorkoutSetUpdateRequest(); bad.setWeight(Double.NaN); bad.setNotes("Do not mutate");
        assertThatThrownBy(() -> studio.updateSet(users.findByUsername("demo"), session.getId(), set.getId(), bad))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(set.getWeight()).isNull(); assertThat(set.getNotes()).isEmpty();
        bad.setWeight(Double.MAX_VALUE); bad.setReps(2); bad.setCompleted(true);
        assertThatThrownBy(() -> studio.updateSet(users.findByUsername("demo"), session.getId(), set.getId(), bad))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(set.getWeight()).isNull(); assertThat(session.getTotalVolume()).isZero();
    }

    @Test
    void personalPlayerRejectsForeignSessionsSetsAndMissingCsrf() throws Exception {
        var own = personalSession("demo", 1); var foreign = personalSession("trainer_demo", 1);
        mvc.perform(get("/workouts/studio/" + foreign.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/workouts/studio/" + own.getId() + "/sets/" + foreign.getSetLogs().getFirst().getId())
                .with(user("demo").roles("CLIENT")).with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("weight", "not a number")).andExpect(status().isNotFound());
        mvc.perform(post("/workouts/studio/" + own.getId() + "/sets/" + own.getSetLogs().getFirst().getId())
                .with(user("demo").roles("CLIENT")).contentType("application/json").content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized());
        assertThat(own.isCompleted()).isFalse(); assertThat(foreign.isCompleted()).isFalse();
    }

    @Test
    void nativeRecordingUploadAndRemovalRetainOwnedVideoWithoutFabricatedAnalysis() throws Exception {
        var session = personalSession("demo", 1); var set = session.getSetLogs().getFirst();
        var file = new org.springframework.mock.web.MockMultipartFile("video", "test.webm", "video/webm",
                new byte[]{0x1A,0x45,(byte)0xDF,(byte)0xA3,0x01,0x00,0x00,0x00});
        String prefix = "/workouts/studio/" + session.getId() + "/sets/" + set.getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(prefix + "/recording")
                .file(file).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workouts/studio/" + session.getId() + "#recording-" + set.getId()))
                .andExpect(flash().attribute("recordingSaved", true));
        var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workouts/studio/" + session.getId())
                .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var remove = doc.selectFirst("form[action*=/recording/][action$=/delete]");
        assertThat(remove).isNotNull(); assertThat(remove.select("input[name=_csrf]")).hasSize(1);
        mvc.perform(get(prefix + "/video/latest").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.analysisAvailable").value(false)).andExpect(jsonPath("$.feedback").doesNotExist());
        mvc.perform(post(remove.attr("action")).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workouts/studio/" + session.getId() + "#recording-" + set.getId()));
        mvc.perform(get(prefix + "/video/latest").with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void startingRequiresProtectedPostAndLegacyGetOnlyConfirmsOrResumes() throws Exception {
        var owner = users.findByUsername("demo");
        var form = new WorkoutTemplateForm(); form.setName("Protected <start>");
        var row = new WorkoutExerciseForm(); row.setExerciseName("Safe movement"); row.setSets(1); form.getExercises().add(row);
        var template = studio.createTemplate(owner, form);
        long count = sessions.count();
        for (String locale : java.util.List.of("en", "cy", "es", "fr", "de", "it", "pt", "pl", "nl", "zh", "ja", "ko", "ar", "hi")) {
            var doc = org.jsoup.Jsoup.parse(mvc.perform(get("/workouts/" + template.getId() + "/start")
                    .param("lang",locale).with(user("demo").roles("CLIENT"))).andExpect(status().isOk())
                    .andExpect(view().name("trainer-views/workouts/confirm-start")).andReturn().getResponse().getContentAsString());
            assertThat(doc.selectFirst("h1").text()).isEqualTo("Protected <start>");
            assertThat(doc.selectFirst("main form").attr("method")).isEqualTo("post");
            assertThat(doc.select("main form input[name=_csrf]")).hasSize(1);
        }
        assertThat(sessions.count()).isEqualTo(count);
        mvc.perform(post("/workouts/" + template.getId() + "/start").with(user("demo").roles("CLIENT")))
                .andExpect(status().isUnauthorized());
        assertThat(sessions.count()).isEqualTo(count);
        mvc.perform(post("/workouts/" + template.getId() + "/start").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var open = studio.findOpenSession(owner, template.getId()).orElseThrow();
        assertThat(sessions.count()).isEqualTo(count + 1);
        mvc.perform(get("/workouts/" + template.getId() + "/start").with(user("demo").roles("CLIENT")))
                .andExpect(redirectedUrl("/workouts/studio/" + open.getId()));
        mvc.perform(post("/workouts/" + template.getId() + "/start").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workouts/studio/" + open.getId()));
        assertThat(sessions.count()).isEqualTo(count + 1);
    }
}
