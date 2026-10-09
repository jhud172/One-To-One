package uk.ac.cf._5.group14.One_To_One.Workout;

import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRequest;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseService;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseView;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@AllArgsConstructor
public class WorkoutController {

    private final ExerciseService exerciseService;
    private final CustomExerciseService customExerciseService;
    private final WorkoutService workoutService;
    private final WorkoutAiSuggestionService workoutAiSuggestionService;
    private final PlatformSubscriptionService platformSubscriptionService;
    private final Clock clock;
    private final AuthHelper authHelper;

    @GetMapping("/workout")
    public ModelAndView createWorkout(@RequestParam(required = false) Long edit, @RequestParam(required = false) Long customEdit,
            @RequestParam Map<String, String> parameters) {
        ModelAndView result = builder(edit == null ? new SaveWorkoutDTO() : toDraft(workoutService.getWorkoutToEdit(edit)), null);
        if (customEdit != null) {
            CustomExercise exercise = customExerciseService.getCustomExerciseById(customEdit, authHelper.getAuthenticatedUser().getId());
            if (exercise == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            result.addObject("customDraft", toView(exercise));
        }
        result.addObject("searchValues", searchValues(parameters));
        return result;
    }

    private ModelAndView builder(SaveWorkoutDTO draft, String error) {
        ModelAndView mav = new ModelAndView("trainer-views/schedule/workout");
        User user = authHelper.getAuthenticatedUser();
        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);

        List<Exercise> suggestedExercises = exerciseService.suggestExercises(user);
        mav.addObject("suggestedExercises", suggestedExercises);

        List<Workout> usersWorkouts = workoutService.getWorkouts();
        mav.addObject("usersWorkouts", usersWorkouts);

        List<CustomExerciseView> customExercises = customExerciseService.getCustomExercisesByUser(user.getId())
            .stream()
            .map(this::toView)
            .collect(Collectors.toList());
        mav.addObject("customExercises", customExercises);
        mav.addObject("isPremium", isPremium);
        mav.addObject("aiSuggestionsAvailable", workoutAiSuggestionService.isAvailable());
        populateDraft(mav.getModelMap(), draft);
        mav.addObject("workoutError", error);
        mav.addObject("customDraft", new CustomExerciseView(null, "", "", "", "", "", ""));

        return mav;
    }

    @GetMapping("/workout/create")
    public String getCreateFragment(Model model) {
        populateDraft(model.asMap(), new SaveWorkoutDTO());
        return "trainer-views/workouts/fragments/workout-frags :: createWorkout";
    }

    @GetMapping("/workout/edit/{id}")
    public String editWorkout(@PathVariable Long id, Model model) {
        Workout workout = workoutService.getWorkoutToEdit(id);
        model.addAttribute("workout", workout);
        populateDraft(model.asMap(), toDraft(workout));
        Map<Long, String> customExerciseEmbeds = workout.getCustomExercises() == null
                ? Map.of()
                : workout.getCustomExercises().stream()
                .collect(Collectors.toMap(CustomExercise::getId, ex -> java.util.Objects.toString(toEmbedUrl(ex.getVideoUrl()), "")));
        model.addAttribute("customExerciseEmbeds", customExerciseEmbeds);

        return "trainer-views/workouts/fragments/workout-frags.html :: editWorkout";
    }

    @PostMapping(value = "/save-workout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveWorkout(
            @RequestBody SaveWorkoutDTO dto
    )
    {
        try {
            Workout saved = workoutService.saveWorkout(dto);
            return ResponseEntity.ok(Map.of("message", "Workout saved successfully", "messageKey", "ui.studio.saved",
                    "id", saved.getId(), "name", saved.getName()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("messageKey", ex.getMessage()));
        }
    }

    @PostMapping(value = "/save-workout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ModelAndView saveWorkoutForm(@ModelAttribute SaveWorkoutDTO draft,
            @RequestParam(defaultValue = "save") String editAction, @RequestParam Map<String, String> parameters,
            RedirectAttributes flash) {
        if (draft.getId() != null) workoutService.getWorkoutToEdit(draft.getId());
        if (!editAction.equals("save")) {
            String error = changeSelection(draft, editAction);
            ModelAndView result = builder(draft, error);
            result.addObject("workoutDraftChanged", true);
            result.addObject("searchValues", searchValues(parameters));
            return result;
        }
        try {
            Workout saved = workoutService.saveWorkout(draft);
            flash.addFlashAttribute("workoutNotice", "ui.studio.saved");
            var destination = org.springframework.web.util.UriComponentsBuilder.fromPath("/workout").queryParam("edit", saved.getId());
            searchValues(parameters).forEach((key, value) -> { if (!value.isBlank()) destination.queryParam(key, value); });
            return new ModelAndView("redirect:" + destination.build().encode().toUriString());
        } catch (IllegalArgumentException ex) {
            ModelAndView result = builder(draft, ex.getMessage());
            result.addObject("searchValues", searchValues(parameters));
            return result;
        }
    }

    @PostMapping(value = "/delete-workout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteWorkout(
            @RequestBody Map <String, Long> payload
    )
    {
        Long id = payload.get("id");
        if (id == null) return ResponseEntity.badRequest().body(Map.of("messageKey", "ui.scheduleWorkout.invalid"));
        try {
            workoutService.deleteWorkout(id);
            return ResponseEntity.ok(Map.of("message", "Workout deleted successfully"));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(Map.of("messageKey", "ui.scheduleWorkout.deleteBlocked"));
        }
    }

    @PostMapping(value = "/delete-workout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String deleteWorkoutForm(@RequestParam Long id, RedirectAttributes flash) {
        try {
            workoutService.deleteWorkout(id);
            flash.addFlashAttribute("workoutNotice", "ui.scheduleWorkout.deleted");
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("workoutNotice", "ui.scheduleWorkout.deleteBlocked");
        }
        return "redirect:/workout";
    }

    private SaveWorkoutDTO toDraft(Workout workout) {
        SaveWorkoutDTO draft = new SaveWorkoutDTO();
        draft.setId(workout.getId()); draft.setName(workout.getName()); draft.setWorkoutNotes(workout.getNotes());
        draft.setExerciseIds(workout.getExercises() == null ? List.of() : workout.getExercises().stream().map(Exercise::getId).toList());
        draft.setCustomExerciseIds(workout.getCustomExercises() == null ? List.of() : workout.getCustomExercises().stream().map(CustomExercise::getId).toList());
        return draft;
    }

    private void populateDraft(Map<String, Object> model, SaveWorkoutDTO draft) {
        if (draft.getExerciseIds() == null) draft.setExerciseIds(new ArrayList<>());
        if (draft.getCustomExerciseIds() == null) draft.setCustomExerciseIds(new ArrayList<>());
        Long userId = authHelper.getAuthenticatedUser().getId();
        model.put("workoutDraft", draft);
        model.put("selectedExercises", exerciseService.getAllExercises().stream()
                .filter(ex -> draft.getExerciseIds().contains(ex.getId())).toList());
        model.put("selectedCustomExercises", customExerciseService.getCustomExercisesByUser(userId).stream()
                .filter(ex -> draft.getCustomExerciseIds().contains(ex.getId())).map(this::toView).toList());
    }

    private Map<String, String> searchValues(Map<String, String> parameters) {
        return List.of("exercise-search-input", "custom-exercise-search", "workout-search-input").stream()
                .collect(Collectors.toMap(key -> key, key -> {
                    String value = parameters.getOrDefault(key, "");
                    return value.substring(0, Math.min(value.length(), 200));
                }));
    }

    private String changeSelection(SaveWorkoutDTO draft, String command) {
        String[] parts = command.split(":");
        if (parts.length != 3 || !(parts[0].equals("add") || parts[0].equals("remove"))
                || !(parts[1].equals("e") || parts[1].equals("c"))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        Long id;
        try { id = Long.valueOf(parts[2]); }
        catch (NumberFormatException ex) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST); }
        if (id <= 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        List<Long> ids = new ArrayList<>(parts[1].equals("c")
                ? java.util.Objects.requireNonNullElse(draft.getCustomExerciseIds(), List.of())
                : java.util.Objects.requireNonNullElse(draft.getExerciseIds(), List.of()));
        if (parts[0].equals("remove")) ids.remove(id);
        else if (!ids.contains(id)) {
            int total = (draft.getExerciseIds() == null ? 0 : draft.getExerciseIds().size())
                    + (draft.getCustomExerciseIds() == null ? 0 : draft.getCustomExerciseIds().size());
            if (total >= 50) return "ui.scheduleWorkout.invalid";
            boolean exists = parts[1].equals("c")
                    ? customExerciseService.getCustomExerciseById(id, authHelper.getAuthenticatedUser().getId()) != null
                    : exerciseService.getAllExercises().stream().anyMatch(ex -> ex.getId().equals(id));
            if (!exists) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            ids.add(id);
        }
        if (parts[1].equals("c")) draft.setCustomExerciseIds(ids); else draft.setExerciseIds(ids);
        return null;
    }

    @GetMapping("/workout/custom-exercises")
    @ResponseBody
    public ResponseEntity<List<CustomExerciseView>> listCustomExercises() {
        User user = authHelper.getAuthenticatedUser();
        List<CustomExerciseView> views = customExerciseService.getCustomExercisesByUser(user.getId())
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    @PostMapping("/workout/custom-exercises")
    @ResponseBody
    public ResponseEntity<?> createCustomExercise(@RequestBody CustomExerciseRequest request) {
        User user = authHelper.getAuthenticatedUser();
        if (!validCustomRequest(request)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Check the custom exercise details.", "messageKey", "ui.studio.customFailed"));
        }

        String sanitizedVideo = sanitizeVideoUrl(request.videoUrl());
        if (request.videoUrl() != null && !request.videoUrl().isBlank() && sanitizedVideo == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Video URL must be a valid YouTube or Vimeo link."));
        }

        CustomExercise exercise = new CustomExercise();
        exercise.setUserId(user.getId());
        exercise.setName(request.name().trim());
        exercise.setDescription(trimToNull(request.description()));
        exercise.setHowTo(trimToNull(request.howTo()));
        exercise.setVideoUrl(sanitizedVideo);

        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);
        exercise.setColorTag(isPremium ? trimToNull(request.colorTag()) : null);

        CustomExercise saved = customExerciseService.saveCustomExercise(exercise);
        return ResponseEntity.ok(toView(saved));
    }

    @PostMapping("/workout/custom-exercises/{id}")
    @ResponseBody
    public ResponseEntity<?> updateCustomExercise(@PathVariable Long id, @RequestBody CustomExerciseRequest request) {
        User user = authHelper.getAuthenticatedUser();
        if (!validCustomRequest(request)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Check the custom exercise details.", "messageKey", "ui.studio.customFailed"));
        }

        String sanitizedVideo = sanitizeVideoUrl(request.videoUrl());
        if (request.videoUrl() != null && !request.videoUrl().isBlank() && sanitizedVideo == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Video URL must be a valid YouTube or Vimeo link."));
        }

        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);
        CustomExerciseRequest adjusted = new CustomExerciseRequest(
                request.name().trim(),
                trimToNull(request.description()),
                trimToNull(request.howTo()),
                sanitizedVideo,
                isPremium ? trimToNull(request.colorTag()) : null
        );

        CustomExercise updated = customExerciseService.updateCustomExercise(user.getId(), id, adjusted);
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Custom exercise not found."));
        }

        return ResponseEntity.ok(toView(updated));
    }

    @PostMapping("/workout/custom-exercises/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteCustomExercise(@PathVariable Long id) {
        User user = authHelper.getAuthenticatedUser();
        if (customExerciseService.getCustomExerciseById(id, user.getId()) == null) return ResponseEntity.notFound().build();
        try {
            customExerciseService.deleteCustomExercise(user.getId(), id);
            return ResponseEntity.ok(Map.of("message", "Custom exercise deleted."));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(Map.of("messageKey", "ui.scheduleWorkout.customBlocked"));
        }
    }

    @PostMapping(value = "/workout/custom-exercises/save", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ModelAndView saveCustomExerciseForm(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam(defaultValue = "") String description, @RequestParam(defaultValue = "") String howTo,
            @RequestParam(defaultValue = "") String videoUrl, @RequestParam(defaultValue = "") String colorTag,
            RedirectAttributes flash) {
        CustomExerciseRequest request = new CustomExerciseRequest(name, description, howTo, videoUrl, colorTag);
        ResponseEntity<?> response = id == null ? createCustomExercise(request) : updateCustomExercise(id, request);
        if (response.getStatusCode().value() == 404) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (response.getStatusCode().is2xxSuccessful()) {
            flash.addFlashAttribute("workoutNotice", "ui.studio.customSaved");
            return new ModelAndView("redirect:/workout");
        }
        ModelAndView result = builder(new SaveWorkoutDTO(), "ui.studio.customFailed");
        result.addObject("customDraft", new CustomExerciseView(id, name, description, howTo, videoUrl, "", colorTag));
        return result;
    }

    @PostMapping("/workout/custom-exercises/{id}/remove")
    public String removeCustomExerciseForm(@PathVariable Long id, RedirectAttributes flash) {
        ResponseEntity<?> response = deleteCustomExercise(id);
        if (response.getStatusCode().value() == 404) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        flash.addFlashAttribute("workoutNotice", response.getStatusCode().is2xxSuccessful()
                ? "ui.scheduleWorkout.deleted" : "ui.scheduleWorkout.customBlocked");
        return "redirect:/workout";
    }

    private boolean validCustomRequest(CustomExerciseRequest request) {
        return request != null && request.name() != null && !request.name().isBlank() && request.name().trim().length() <= 200
                && (request.description() == null || request.description().length() <= 5000)
                && (request.howTo() == null || request.howTo().length() <= 5000)
                && (request.videoUrl() == null || request.videoUrl().length() <= 255)
                && (request.colorTag() == null || request.colorTag().isBlank()
                    || Set.of("#2563eb", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#0ea5e9").contains(request.colorTag()));
    }

    @PostMapping("/workout/ai-suggestions")
    @ResponseBody
    public ResponseEntity<?> aiSuggestions(@RequestBody(required = false) Map<String, String> payload) {
        User user = authHelper.getAuthenticatedUser();
        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);
        if (!isPremium) {
            return ResponseEntity.status(403).body(Map.of("message", "Premium required"));
        }
        String prompt = payload != null ? payload.getOrDefault("prompt", "") : "";
        if (prompt == null || prompt.length() > 2000) return ResponseEntity.badRequest().body(Map.of("messageKey", "ui.scheduleWorkout.aiUnavailable"));
        try {
            List<String> suggestions = workoutAiSuggestionService.generateSuggestions(prompt);
            return ResponseEntity.ok(Map.of("suggestions", suggestions));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).body(Map.of("messageKey", "ui.scheduleWorkout.aiUnavailable"));
        }
    }

    private CustomExerciseView toView(CustomExercise exercise) {
        if (exercise == null) {
            return null;
        }
        String embedUrl = toEmbedUrl(exercise.getVideoUrl());
        return new CustomExerciseView(
                exercise.getId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getHowTo(),
                exercise.getVideoUrl(),
                embedUrl,
                exercise.getColorTag()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String sanitizeVideoUrl(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String normalizedHost = host.toLowerCase();
            if (!ALLOWED_VIDEO_HOSTS.contains(normalizedHost)) {
                return null;
            }
            return trimmed;
        } catch (Exception ex) {
            return null;
        }
    }

    private String toEmbedUrl(String value) {
        String safe = sanitizeVideoUrl(value);
        if (safe == null) {
            return null;
        }
        try {
            URI uri = URI.create(safe);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath();

            if (host.contains("youtu.be")) {
                String id = path.replaceFirst("/", "");
                return id.isBlank() ? null : "https://www.youtube.com/embed/" + id;
            }

            if (host.contains("youtube.com")) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        String[] kv = part.split("=");
                        if (kv.length == 2 && kv[0].equals("v")) {
                            return "https://www.youtube.com/embed/" + kv[1];
                        }
                    }
                }
                if (path.startsWith("/embed/")) {
                    return "https://www.youtube.com" + path;
                }
            }

            if (host.contains("vimeo.com")) {
                String id = path.replaceFirst("/", "");
                if (id.startsWith("video/")) {
                    id = id.substring("video/".length());
                }
                return id.isBlank() ? null : "https://player.vimeo.com/video/" + id;
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private static final Set<String> ALLOWED_VIDEO_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
            "vimeo.com",
            "www.vimeo.com",
            "player.vimeo.com"
    );
}
