package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseService;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalLinkService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalLinkSource;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalStatus;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/workouts")
public class WorkoutBuilderController {

    private final WorkoutBuilderService workoutBuilderService;
    private final ExerciseService exerciseService;
    private final CustomExerciseService customExerciseService;
    private final AuthHelper authHelper;
    private final GoalService goalService;
    private final GoalLinkService goalLinkService;
    private final WorkoutPerformanceService workoutPerformanceService;
    private final UserSettingsService userSettingsService;
    private final WorkoutFormFeedbackService recordingService;

    public WorkoutBuilderController(WorkoutBuilderService workoutBuilderService,
                                    ExerciseService exerciseService,
                                    CustomExerciseService customExerciseService,
                                    AuthHelper authHelper,
                                    GoalService goalService,
                                    GoalLinkService goalLinkService,
                                    WorkoutPerformanceService workoutPerformanceService,
                                    UserSettingsService userSettingsService, WorkoutFormFeedbackService recordingService) {
        this.workoutBuilderService = workoutBuilderService;
        this.exerciseService = exerciseService;
        this.customExerciseService = customExerciseService;
        this.authHelper = authHelper;
        this.goalService = goalService;
        this.goalLinkService = goalLinkService;
        this.workoutPerformanceService = workoutPerformanceService;
        this.userSettingsService = userSettingsService;
        this.recordingService = recordingService;
    }

    @InitBinder("form")
    public void bindWorkout(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "exercises[*].id", "exercises[*].exerciseRef", "exercises[*].exerciseName",
                "exercises[*].sets", "exercises[*].reps", "exercises[*].restSeconds", "exercises[*].notes");
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "my-workouts") String mode,
                        @RequestParam(required = false) String exerciseRef, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        WorkoutTemplateForm form = new WorkoutTemplateForm();
        WorkoutExerciseForm row = new WorkoutExerciseForm();
        row.setExerciseRef(exerciseRef);
        form.getExercises().add(row);
        model.addAttribute("form", form);
        prepareStudio(model, user, exerciseRef == null ? mode : "builder");
        return "trainer-views/workouts/index";
    }

    @PostMapping
    public String create(@ModelAttribute("form") WorkoutTemplateForm form,
                         org.springframework.validation.BindingResult binding,
                         @RequestParam(defaultValue = "save") String editAction, Model model,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("ui.studio.invalid");
            if (editDraft(form, editAction, model)) {
                prepareStudio(model, user, "builder");
                return "trainer-views/workouts/index";
            }
            WorkoutTemplate created = workoutBuilderService.createTemplate(user, form);
            redirect.addFlashAttribute("studioSaved", true);
            return "redirect:/workouts/" + created.getId() + "/edit";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("studioError", exception.getMessage());
            prepareStudio(model, user, "builder");
            return "trainer-views/workouts/index";
        }
    }

    private void prepareStudio(Model model, User user, String mode) {
        model.addAttribute("templates", workoutBuilderService.listTemplates(user));
        model.addAttribute("studioMode", List.of("my-workouts", "library", "builder").contains(mode) ? mode : "my-workouts");
        hydrateExerciseOptions(model, user);
        applySmartDefaults(model, user);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        WorkoutTemplate template = workoutBuilderService.getTemplate(user, id);
        WorkoutTemplateForm form = toForm(template);
        model.addAttribute("template", template);
        model.addAttribute("form", form);
        hydrateExerciseOptions(model, user);
        applySmartDefaults(model, user);
        return "trainer-views/workouts/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("form") WorkoutTemplateForm form,
                         org.springframework.validation.BindingResult binding,
                         @RequestParam(defaultValue = "save") String editAction, Model model,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        WorkoutTemplate template = workoutBuilderService.getTemplate(user, id);
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("ui.studio.invalid");
            if (editDraft(form, editAction, model)) {
                model.addAttribute("template", template);
                hydrateExerciseOptions(model, user);
                applySmartDefaults(model, user);
                return "trainer-views/workouts/edit";
            }
            workoutBuilderService.updateTemplate(user, id, form);
            redirect.addFlashAttribute("studioSaved", true);
            return "redirect:/workouts/" + id + "/edit";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("template", template);
            model.addAttribute("studioError", exception.getMessage());
            hydrateExerciseOptions(model, user);
            applySmartDefaults(model, user);
            return "trainer-views/workouts/edit";
        }
    }

    /** Native row controls return the submitted draft without persisting a template. */
    private boolean editDraft(WorkoutTemplateForm form, String action, Model model) {
        if ("save".equals(action)) return false;
        List<WorkoutExerciseForm> rows = form.getExercises();
        if (rows == null || rows.size() > 50) throw new IllegalArgumentException("ui.studio.limits");
        int focus;
        if ("add".equals(action)) {
            if (rows.size() == 50) throw new IllegalArgumentException("ui.studio.limits");
            rows.add(new WorkoutExerciseForm());
            focus = rows.size() - 1;
        } else {
            if (action == null || !action.matches("(remove|up|down):[0-9]{1,2}")) throw new IllegalArgumentException("ui.studio.invalid");
            int index = Integer.parseInt(action.substring(action.indexOf(':') + 1));
            if (index >= rows.size()) throw new IllegalArgumentException("ui.studio.invalid");
            if (action.startsWith("remove:")) {
                rows.remove(index);
                focus = Math.min(index, rows.size() - 1);
            } else {
                int destination = index + (action.startsWith("up:") ? -1 : 1);
                if (destination < 0 || destination >= rows.size()) throw new IllegalArgumentException("ui.studio.invalid");
                java.util.Collections.swap(rows, index, destination);
                focus = destination;
            }
        }
        model.addAttribute("studioDraftChanged", true);
        model.addAttribute("rowFocusIndex", focus);
        return true;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        try { workoutBuilderService.deleteTemplate(user, id); }
        catch (IllegalArgumentException exception) { redirect.addFlashAttribute("studioError", exception.getMessage()); }
        return "redirect:/workouts";
    }

    @GetMapping("/{id}/start")
    public String confirmStart(@PathVariable Long id, Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        var open = workoutBuilderService.findOpenSession(user, id);
        if (open.isPresent()) return "redirect:/workouts/studio/" + open.get().getId();
        var template = workoutBuilderService.getTemplate(user, id);
        if (template.getExercises().isEmpty()) {
            redirect.addFlashAttribute("studioError", "ui.studio.emptyExercise");
            return "redirect:/workouts";
        }
        model.addAttribute("template", template);
        return "trainer-views/workouts/confirm-start";
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        try {
            var playerSession = workoutBuilderService.startSession(user, id);
            return "redirect:/workouts/studio/" + playerSession.getId();
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("studioError", exception.getMessage());
            return "redirect:/workouts";
        }
    }

    @GetMapping("/studio/{sessionId}")
    public String viewSession(@PathVariable Long sessionId, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        preparePlayer(model, user, sessionId);
        return "trainer-views/workouts/start";
    }

    private void preparePlayer(Model model, User user, Long sessionId) {
        WorkoutSession playerSession = workoutBuilderService.getSession(user, sessionId);
        model.addAttribute("playerSession", playerSession);
        model.addAttribute("exerciseViews", buildPlayerViews(playerSession));
        model.addAttribute("playerSummary", playerSummary(playerSession));
        model.addAttribute("goalOptions", goalService.listGoalsForViewer(user, null, GoalStatus.ACTIVE, null, false));
        model.addAttribute("selectedGoal", goalLinkService.findGoalForWorkoutSession(user, sessionId));
        model.addAttribute("recordings", recordingService.listLatestVideos(user, sessionId));
    }

    private Map<String, Object> playerSummary(WorkoutSession playerSession) {
        int total = playerSession.getSetLogs().size();
        int done = (int) playerSession.getSetLogs().stream().filter(WorkoutSetLog::isCompleted).count();
        return Map.of("totalSets", total, "completedSets", done, "percent", total == 0 ? 0 : done * 100 / total,
                "totalVolume", playerSession.getTotalVolume() == null ? 0.0 : playerSession.getTotalVolume(),
                "completed", playerSession.isCompleted());
    }

    @PostMapping("/studio/{sessionId}/goal")
    public String updateSessionGoal(@PathVariable Long sessionId,
                                    @RequestParam(required = false) Long goalId) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        workoutBuilderService.getSession(user, sessionId);
        goalLinkService.replaceWorkoutSessionLink(user, goalId, sessionId, GoalLinkSource.SELF);
        return "redirect:/workouts/studio/" + sessionId;
    }

    @PostMapping(value = "/studio/{sessionId}/sets/{setId}", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<?> updateSet(@PathVariable Long sessionId,
                                       @PathVariable Long setId,
                                       @RequestBody WorkoutSetUpdateRequest request) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            workoutBuilderService.updateSet(user, sessionId, setId, request);
            var summary = new HashMap<String, Object>(playerSummary(workoutBuilderService.getSession(user, sessionId)));
            summary.put("setId", setId);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("messageKey", "ui.personal.invalid"));
        }
    }

    @PostMapping(value = "/studio/{sessionId}/sets/{setId}", consumes = "application/x-www-form-urlencoded")
    public String saveSet(@PathVariable Long sessionId, @PathVariable Long setId,
                          @RequestParam(defaultValue = "") String weight,
                          @RequestParam(defaultValue = "") String reps,
                          @RequestParam(defaultValue = "") String notes,
                          @RequestParam(defaultValue = "false") boolean completed, Model model,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        // Ownership must be resolved before an invalid draft is returned.
        workoutBuilderService.updateSet(user, sessionId, setId, null);
        try {
            var request = new WorkoutSetUpdateRequest();
            request.setReplaceValues(true);
            request.setWeight(weight.isBlank() ? null : Double.valueOf(weight.trim()));
            request.setReps(reps.isBlank() ? null : Integer.valueOf(reps.trim()));
            request.setNotes(notes); request.setCompleted(completed);
            workoutBuilderService.updateSet(user, sessionId, setId, request);
            redirect.addFlashAttribute("playerSaved", true);
            return "redirect:/workouts/studio/" + sessionId + "#set-" + setId;
        } catch (IllegalArgumentException exception) {
            model.addAttribute("playerError", "ui.personal.invalid");
            model.addAttribute("failedSetId", setId);
            model.addAttribute("setDraft", Map.of("weight", weight, "reps", reps, "notes", notes, "completed", completed));
            preparePlayer(model, user, sessionId);
            return "trainer-views/workouts/start";
        }
    }

    @PostMapping("/studio/{sessionId}/sets/{setId}/recording")
    public String uploadRecording(@PathVariable Long sessionId, @PathVariable Long setId,
                                  @RequestParam("video") org.springframework.web.multipart.MultipartFile video,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        workoutBuilderService.getSession(user, sessionId);
        try {
            recordingService.storeVideo(user, sessionId, setId, video);
            redirect.addFlashAttribute("recordingSaved", true);
        } catch (IllegalArgumentException | java.io.IOException exception) {
            redirect.addFlashAttribute("playerError", "ui.personal.videoFailed");
        }
        return "redirect:/workouts/studio/" + sessionId + "#recording-" + setId;
    }

    @PostMapping("/studio/{sessionId}/sets/{setId}/recording/{videoId}/delete")
    public String deleteRecording(@PathVariable Long sessionId, @PathVariable Long setId, @PathVariable Long videoId,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        workoutBuilderService.getSession(user, sessionId);
        try { recordingService.deleteVideo(user, sessionId, setId, videoId); }
        catch (IllegalArgumentException | java.io.IOException exception) { redirect.addFlashAttribute("playerError", "ui.personal.videoFailed"); }
        return "redirect:/workouts/studio/" + sessionId + "#recording-" + setId;
    }

    private void hydrateExerciseOptions(Model model, User user) {
        List<Exercise> exercises = exerciseService.getAllExercises();
        List<CustomExercise> customExercises = customExerciseService.getCustomExercisesByUser(user.getId());
        model.addAttribute("exercises", exercises);
        model.addAttribute("customExercises", customExercises);
        model.addAttribute("categories", java.util.stream.Stream.concat(
                exercises.stream().map(Exercise::getCategory), customExercises.stream().map(CustomExercise::getCategory))
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList());
    }

    private void applySmartDefaults(Model model, User user) {
        UserSettings settings = userSettingsService.getOrCreate(user);
        int defaultSets = settings != null ? settings.getDefaultSets() : 3;
        int repMin = settings != null ? settings.getDefaultRepMin() : 8;
        int repMax = settings != null ? settings.getDefaultRepMax() : 12;
        model.addAttribute("defaultSets", defaultSets);
        model.addAttribute("defaultRepMin", repMin);
        model.addAttribute("defaultRepMax", repMax);
    }

    private WorkoutTemplateForm toForm(WorkoutTemplate template) {
        WorkoutTemplateForm form = new WorkoutTemplateForm();
        form.setName(template.getName());
        form.setDescription(template.getDescription());

        List<WorkoutExercise> ordered = new ArrayList<>(template.getExercises());
        ordered.sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex));
        for (WorkoutExercise exercise : ordered) {
            WorkoutExerciseForm row = new WorkoutExerciseForm();
            row.setId(exercise.getId());
            row.setExerciseName(exercise.getExerciseName());
            row.setExerciseId(exercise.getExerciseId());
            row.setCustomExerciseId(exercise.getCustomExerciseId());
            row.setSets(exercise.getSets());
            row.setReps(exercise.getReps());
            row.setRestSeconds(exercise.getRestSeconds());
            row.setNotes(exercise.getNotes());
            if (exercise.getExerciseId() != null) {
                row.setExerciseRef("e:" + exercise.getExerciseId());
            } else if (exercise.getCustomExerciseId() != null) {
                row.setExerciseRef("c:" + exercise.getCustomExerciseId());
            }
            form.getExercises().add(row);
        }
        return form;
    }

    private List<WorkoutPlayerExerciseView> buildPlayerViews(WorkoutSession session) {
        Map<Integer, WorkoutPlayerExerciseView> grouped = new HashMap<>();
        for (WorkoutSetLog setLog : session.getSetLogs()) {
            WorkoutPlayerExerciseView view = grouped.computeIfAbsent(setLog.getExerciseOrder(), key -> {
                WorkoutPlayerExerciseView v = new WorkoutPlayerExerciseView();
                v.setOrderIndex(key);
                v.setName(setLog.getExerciseName());
                return v;
            });
            view.getSets().add(setLog);
        }
        List<WorkoutPlayerExerciseView> list = new ArrayList<>(grouped.values());
        list.sort(Comparator.comparingInt(WorkoutPlayerExerciseView::getOrderIndex));
        for (WorkoutPlayerExerciseView view : list) {
            view.getSets().sort(Comparator.comparingInt(WorkoutSetLog::getSetNumber));
            WorkoutPerformanceHint hint = workoutPerformanceService.buildHint(
                    session.getUser(),
                    view.getName(),
                    session.getStartedAt()
            );
            if (hint != null) {
                view.setLastSummary(hint.lastSummary());
                view.setBestSummary(hint.bestSummary());
            }
        }
        return list;
    }

}
