package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Security.TrainerAccessException;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.*;

@Controller
@RequestMapping("/trainer/library")
public class TrainerLibraryController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainerLibraryService trainerLibraryService;
    private final TrainerClientLinkService trainerClientLinkService;
    private final TrainerLibraryOverviewService libraryOverview;

    public TrainerLibraryController(AuthHelper authHelper,
                                   UserService userService,
                                   UserRepository userRepository,
                                   TrainerLibraryService trainerLibraryService,
                                   TrainerClientLinkService trainerClientLinkService,
                                   TrainerLibraryOverviewService libraryOverview) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.trainerLibraryService = trainerLibraryService;
        this.trainerClientLinkService = trainerClientLinkService;
        this.libraryOverview = libraryOverview;
    }

    private User currentUserOrThrow() {
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser != null) {
            return sessionUser;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        User user = userService.findByUsername(auth.getName());
        if (user == null) {
            throw new AccessDeniedException("User not found");
        }
        return user;
    }

    private User currentTrainerOrThrow() {
        User user = currentUserOrThrow();
        if (user.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("Not a trainer");
        }
        if (!user.isEnabled()) throw new AccessDeniedException("Account disabled");
        if (!user.isTrainerVerified()) {
            throw new TrainerAccessException(TrainerAccessException.Reason.TRAINER_NOT_VERIFIED);
        }
        return user;
    }

    private Long currentTrainerIdOrThrow() {
        return currentTrainerOrThrow().getId();
    }

    private List<User> activeClientsForTrainer(Long trainerId) {
        List<TrainerClientLink> activeLinks = trainerClientLinkService.getActiveClientsForTrainer(trainerId);
        List<Long> clientIds = activeLinks.stream().map(TrainerClientLink::getClientUserId).distinct().toList();
        if (clientIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(clientIds).stream()
                .filter(client -> client.getRole() == Role.CLIENT && client.isEnabled()).toList();
    }

    @GetMapping
    public ModelAndView overview(@RequestParam(value = "error", required = false) String error,
                                 @RequestParam(defaultValue = "") String q, Model model) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER || !trainer.isEnabled()) throw new AccessDeniedException("Trainer access required");
        ModelAndView mav = new ModelAndView("trainer-views/trainer/library");
        addLibraryFeedback(mav, model);
        if (!trainer.isTrainerVerified()) {
            mav.addObject("libraryReady", false);
            mav.addObject("error", "trainer-unverified");
            return mav;
        }
        Long trainerId = trainer.getId();
        mav.addObject("pageTitle", "Trainer Library");
        var overview = libraryOverview.overview(trainerId, q);
        mav.addObject("libraryOverview", overview);
        mav.addObject("query", overview.query());
        mav.addObject("exerciseCount", overview.exerciseCount());
        mav.addObject("workoutCount", overview.workoutCount());
        mav.addObject("programmeCount", overview.programmeCount());
        mav.addObject("libraryReady", true);
        mav.addObject("error", error);
        return mav;
    }

    @ExceptionHandler(TrainerAccessException.class)
    public ModelAndView handleTrainerAccess(TrainerAccessException ex) {
        return new ModelAndView("redirect:/trainer/library?error=trainer-unverified");
    }

    // -------------------------
    // Exercises
    // -------------------------

    @GetMapping("/exercises")
    public ModelAndView exercisesList(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        var catalogue = trainerLibraryService.searchExercises(trainerId, q, page);
        ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/list");
        mav.addObject("pageTitle", "Library - Exercises");
        mav.addObject("exercises", catalogue.page().getContent());
        mav.addObject("exercisePage", catalogue.page());
        mav.addObject("exerciseCount", catalogue.ownedCount());
        mav.addObject("videoExerciseIds", catalogue.page().stream()
                .filter(exercise -> exercise.getVideoUrl() != null && !exercise.getVideoUrl().isBlank()
                        && TrainerLibraryExerciseForm.isSafeVideoUrl(exercise.getVideoUrl()))
                .map(TrainerLibraryExercise::getId).collect(java.util.stream.Collectors.toSet()));
        addLibraryContext(mav, catalogue.query(), catalogue.page().getNumber());
        return mav;
    }

    private void addLibraryContext(ModelAndView mav, String q, int page) {
        mav.addObject("query", TrainerLibraryService.libraryQuery(q));
        mav.addObject("libraryPage", Math.clamp(page, 0, 9999));
    }

    private ModelAndView libraryRedirect(String path, String q, int page) {
        var destination = org.springframework.web.util.UriComponentsBuilder.fromPath(path);
        String query = TrainerLibraryService.libraryQuery(q);
        if (!query.isBlank()) destination.queryParam("q", "{query}");
        if (page > 0) destination.queryParam("page", Math.min(page, 9999));
        String url = destination.encode().buildAndExpand(Map.of("query", query)).toUriString();
        return new ModelAndView("redirect:" + url);
    }

    @GetMapping("/exercises/create")
    public ModelAndView exercisesCreate(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page) {
        currentTrainerIdOrThrow();
        ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/create");
        mav.addObject("pageTitle", "Create Exercise");
        mav.addObject("form", new TrainerLibraryExerciseForm());
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/exercises/create")
    public ModelAndView exercisesCreateSubmit(@Valid @ModelAttribute("form") TrainerLibraryExerciseForm form,
                                              BindingResult bindingResult,
                                              @RequestParam(defaultValue = "") String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/create");
            mav.addObject("pageTitle", "Create Exercise");
            mav.addObject("form", form);
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }
        TrainerLibraryExercise created = trainerLibraryService.createExercise(trainerId, form);
        redirectAttributes.addFlashAttribute("libraryExerciseSaved", true);
        return libraryRedirect("/trainer/library/exercises/" + created.getId(), q, page);
    }

    @GetMapping("/exercises/{id}")
    public ModelAndView exercisesView(@PathVariable Long id, Model model,
                                     @RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryExercise exercise;
        try {
            exercise = trainerLibraryService.getExerciseOwned(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/view");
        mav.addObject("pageTitle", "Exercise - " + exercise.getName());
        mav.addObject("exercise", exercise);
        addLibraryContext(mav, q, page);
        mav.addObject("safeVideoUrl", TrainerLibraryExerciseForm.isSafeVideoUrl(exercise.getVideoUrl()) ? exercise.getVideoUrl() : null);
        addLibraryFeedback(mav, model);
        mav.addObject("notes", trainerLibraryService.getExerciseNotes(id));

        mav.addObject("shareForm", defaultShareForm(TrainerLibraryTemplateType.EXERCISE, id, "/trainer/library/exercises/" + id));
        mav.addObject("activeClients", activeClientsForTrainer(trainerId));
        restoreShareRecipient(mav, model);

        return mav;
    }

    @GetMapping("/exercises/{id}/edit")
    public ModelAndView exercisesEdit(@PathVariable Long id,
                                     @RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryExercise exercise;
        try {
            exercise = trainerLibraryService.getExerciseOwned(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName(exercise.getName());
        form.setDescription(exercise.getDescription());
        form.setPrimaryMuscles(exercise.getPrimaryMuscles());
        form.setEquipment(exercise.getEquipment());
        form.setDifficulty(exercise.getDifficulty());
        form.setVideoUrl(exercise.getVideoUrl());
        form.setNotesText(String.join("\n", trainerLibraryService.getExerciseNotes(id).stream().map(TrainerLibraryExerciseNote::getNoteText).toList()));

        ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/edit");
        mav.addObject("pageTitle", "Edit Exercise");
        mav.addObject("exerciseId", id);
        mav.addObject("form", form);
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/exercises/{id}/edit")
    public ModelAndView exercisesEditSubmit(@PathVariable Long id,
                                            @Valid @ModelAttribute("form") TrainerLibraryExerciseForm form,
                                            BindingResult bindingResult,
                                            @RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.getExerciseOwned(trainerId, id);
        } catch (AccessDeniedException denied) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/exercises/edit");
            mav.addObject("pageTitle", "Edit Exercise");
            mav.addObject("exerciseId", id);
            mav.addObject("form", form);
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }

        try {
            trainerLibraryService.updateExercise(trainerId, id, form);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        redirectAttributes.addFlashAttribute("libraryExerciseSaved", true);
        return libraryRedirect("/trainer/library/exercises/" + id, q, page);
    }

    @PostMapping("/exercises/{id}/delete")
    public ModelAndView exercisesDelete(@PathVariable Long id, RedirectAttributes redirectAttributes,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.deleteExercise(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException inUse) {
            redirectAttributes.addFlashAttribute("libraryDeleteBlocked", true);
            return libraryRedirect("/trainer/library/exercises/" + id, q, page);
        }
        return libraryRedirect("/trainer/library/exercises", q, page);
    }

    // -------------------------
    // Workouts
    // -------------------------

    @GetMapping("/workouts")
    public ModelAndView workoutsList(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        var catalogue = trainerLibraryService.searchWorkouts(trainerId, q, page);
        ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/list");
        mav.addObject("pageTitle", "Library - Workouts");
        mav.addObject("workouts", catalogue.page().getContent());
        mav.addObject("workoutPage", catalogue.page());
        mav.addObject("workoutCount", catalogue.ownedCount());
        mav.addObject("workoutItemCounts", trainerLibraryService.getWorkoutItemCounts(trainerId,
                catalogue.page().stream().map(TrainerLibraryWorkoutTemplate::getId).toList()));
        addLibraryContext(mav, catalogue.query(), catalogue.page().getNumber());
        return mav;
    }

    @GetMapping("/workouts/create")
    public ModelAndView workoutsCreate(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "0") int page) {
        currentTrainerIdOrThrow();
        ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/create");
        mav.addObject("pageTitle", "Create Workout");
        mav.addObject("form", new TrainerLibraryWorkoutTemplateForm());
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/workouts/create")
    public ModelAndView workoutsCreateSubmit(@Valid @ModelAttribute("form") TrainerLibraryWorkoutTemplateForm form,
                                             BindingResult bindingResult,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/create");
            mav.addObject("pageTitle", "Create Workout");
            mav.addObject("form", form);
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }
        TrainerLibraryWorkoutTemplate created = trainerLibraryService.createWorkout(trainerId, form);
        redirectAttributes.addFlashAttribute("libraryWorkoutSaved", true);
        return libraryRedirect("/trainer/library/workouts/" + created.getId(), q, page);
    }

    @GetMapping("/workouts/{id}")
    public ModelAndView workoutsView(@PathVariable Long id, Model model,
                                    @RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryWorkoutTemplate workout;
        try {
            workout = trainerLibraryService.getWorkoutOwned(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        List<TrainerLibraryWorkoutItem> items = trainerLibraryService.getWorkoutItems(id);
        List<TrainerLibraryWorkoutNote> notes = trainerLibraryService.getWorkoutNotes(id);

        Set<Long> exerciseIds = new HashSet<>();
        for (TrainerLibraryWorkoutItem item : items) {
            exerciseIds.add(item.getExerciseId());
        }
        var ownedExercises = trainerLibraryService.listExercises(trainerId);
        Map<Long, TrainerLibraryExercise> exercisesById = new HashMap<>();
        if (!exerciseIds.isEmpty()) {
            for (TrainerLibraryExercise ex : ownedExercises) {
                if (exerciseIds.contains(ex.getId())) {
                    exercisesById.put(ex.getId(), ex);
                }
            }
        }

        ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/view");
        mav.addObject("pageTitle", "Workout - " + workout.getTitle());
        mav.addObject("workout", workout);
        addLibraryContext(mav, q, page);
        mav.addObject("items", items);
        mav.addObject("notes", notes);
        mav.addObject("exercisesById", exercisesById);

        var itemForm = new TrainerLibraryWorkoutItemForm();
        itemForm.setSets(3); itemForm.setReps(8); itemForm.setRestSeconds(60);
        mav.addObject("itemForm", itemForm);
        mav.addObject("exercises", ownedExercises);

        mav.addObject("shareForm", defaultShareForm(TrainerLibraryTemplateType.WORKOUT, id, "/trainer/library/workouts/" + id));
        addLibraryFeedback(mav, model);
        mav.addObject("activeClients", activeClientsForTrainer(trainerId));
        restoreShareRecipient(mav, model);

        return mav;
    }

    @GetMapping("/workouts/{id}/edit")
    public ModelAndView workoutsEdit(@PathVariable Long id,
                                    @RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryService.WorkoutMetadataSnapshot snapshot;
        try {
            snapshot = trainerLibraryService.getWorkoutMetadataSnapshot(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        TrainerLibraryWorkoutTemplateForm form = new TrainerLibraryWorkoutTemplateForm();
        var workout = snapshot.workout();
        form.setTitle(workout.getTitle());
        form.setSummary(workout.getSummary());
        form.setNotesText(String.join("\n", snapshot.notes().stream().map(TrainerLibraryWorkoutNote::getNoteText).toList()));
        form.setExpectedRevision(snapshot.revision());

        ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/edit");
        mav.addObject("pageTitle", "Edit Workout");
        mav.addObject("workoutId", id);
        mav.addObject("form", form);
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/workouts/{id}/edit")
    public ModelAndView workoutsEditSubmit(@PathVariable Long id,
                                           @Valid @ModelAttribute("form") TrainerLibraryWorkoutTemplateForm form,
                                           BindingResult bindingResult,
                                           @RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.getWorkoutOwned(trainerId, id);
        } catch (AccessDeniedException denied) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/workouts/edit");
            mav.addObject("pageTitle", "Edit Workout");
            mav.addObject("workoutId", id);
            mav.addObject("form", form);
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }
        try {
            trainerLibraryService.updateWorkout(trainerId, id, form);
        } catch (TrainerLibraryRevisionConflictException conflict) {
            bindingResult.reject("ui.library.workoutChanged");
            var latest = trainerLibraryService.getWorkoutMetadataSnapshot(trainerId, id);
            form.setExpectedRevision(latest.revision());
            var mav = new ModelAndView("trainer-views/trainer/workouts/edit");
            mav.addObject("pageTitle", "Edit Workout");
            mav.addObject("workoutId", id); mav.addObject("form", form);
            mav.addObject("workoutConflict", true); mav.addObject("latestWorkout", latest.workout());
            mav.addObject("latestWorkoutNotes", latest.notes());
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.CONFLICT);
            return mav;
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        redirectAttributes.addFlashAttribute("libraryWorkoutSaved", true);
        return libraryRedirect("/trainer/library/workouts/" + id, q, page);
    }

    @PostMapping("/workouts/{id}/items")
    public ModelAndView workoutsAddItem(@PathVariable Long id,
                                        @Valid @ModelAttribute("itemForm") TrainerLibraryWorkoutItemForm itemForm,
                                        BindingResult bindingResult,
                                        Model model, RedirectAttributes redirectAttributes,
                                        @RequestParam(defaultValue = "") String q,
                                        @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        ModelAndView destination = workoutsView(id, model, q, page);
        if (!"trainer-views/trainer/workouts/view".equals(destination.getViewName())) return destination;
        if (!bindingResult.hasErrors()) {
            try {
                trainerLibraryService.addWorkoutItem(trainerId, id, itemForm);
                redirectAttributes.addFlashAttribute("libraryItemSaved", true);
                return libraryRedirect("/trainer/library/workouts/" + id, q, page);
            } catch (AccessDeniedException denied) {
                return new ModelAndView("redirect:/access-denied");
            } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException invalidItem) {
                bindingResult.reject("ui.library.prescriptionInvalid");
            }
        }
        destination.addObject("itemForm", itemForm);
        destination.addObject(BindingResult.MODEL_KEY_PREFIX + "itemForm", bindingResult);
        destination.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        return destination;
    }

    @PostMapping("/workouts/{workoutId}/items/{itemId}/move")
    public ModelAndView workoutsMoveItem(@PathVariable Long workoutId, @PathVariable Long itemId,
                                         @RequestParam(defaultValue = "") String direction,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            if (trainerLibraryService.moveWorkoutItem(trainerId, workoutId, itemId, direction)) {
                redirectAttributes.addFlashAttribute("libraryOrderSaved", true);
            }
        } catch (AccessDeniedException denied) {
            return new ModelAndView("redirect:/access-denied");
        } catch (IllegalArgumentException invalidDirection) {
            redirectAttributes.addFlashAttribute("libraryOrderError", true);
        }
        var destination = libraryRedirect("/trainer/library/workouts/" + workoutId, q, page);
        destination.setViewName(destination.getViewName() + "#workout-item-" + itemId);
        return destination;
    }

    @PostMapping("/workouts/{workoutId}/items/{itemId}/delete")
    public ModelAndView workoutsDeleteItem(@PathVariable Long workoutId,
                                           @PathVariable Long itemId,
                                           @RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.deleteWorkoutItem(trainerId, workoutId, itemId);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        return libraryRedirect("/trainer/library/workouts/" + workoutId, q, page);
    }

    @PostMapping("/workouts/{id}/delete")
    public ModelAndView workoutsDelete(@PathVariable Long id, RedirectAttributes redirectAttributes,
                                      @RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.deleteWorkout(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException inUse) {
            redirectAttributes.addFlashAttribute("libraryWorkoutDeleteBlocked", true);
            return libraryRedirect("/trainer/library/workouts/" + id, q, page);
        }
        return libraryRedirect("/trainer/library/workouts", q, page);
    }

    // -------------------------
    // Programmes
    // -------------------------

    @GetMapping("/programmes")
    public ModelAndView programmesList(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        var catalogue = trainerLibraryService.searchProgrammes(trainerId, q, page);
        var programmes = catalogue.page().getContent();
        ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/list");
        mav.addObject("pageTitle", "Library - Programmes");
        mav.addObject("programmes", programmes);
        mav.addObject("programmePage", catalogue.page());
        mav.addObject("programmeCount", catalogue.ownedCount());
        mav.addObject("programmeDayCounts", trainerLibraryService.getProgrammeDayCounts(trainerId,
                programmes.stream().map(TrainerLibraryProgrammeTemplate::getId).toList()));
        addLibraryContext(mav, catalogue.query(), catalogue.page().getNumber());
        return mav;
    }

    @GetMapping("/programmes/create")
    public ModelAndView programmesCreate(@RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "0") int page) {
        currentTrainerIdOrThrow();
        ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/create");
        mav.addObject("pageTitle", "Create Programme");
        mav.addObject("form", new TrainerLibraryProgrammeTemplateForm());
        mav.addObject("draftWeeks", null);
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/programmes/create")
    public ModelAndView programmesCreateSubmit(@Valid @ModelAttribute("form") TrainerLibraryProgrammeTemplateForm form,
                                               BindingResult bindingResult,
                                               @RequestParam(defaultValue = "") String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/create");
            mav.addObject("pageTitle", "Create Programme");
            mav.addObject("form", form);
            mav.addObject("draftWeeks", bindingResult.getFieldValue("weeks"));
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }
        TrainerLibraryProgrammeTemplate created = trainerLibraryService.createProgramme(trainerId, form);
        redirectAttributes.addFlashAttribute("libraryProgrammeSaved", true);
        return libraryRedirect("/trainer/library/programmes/" + created.getId(), q, page);
    }

    @GetMapping("/programmes/{id}")
    public ModelAndView programmesView(@PathVariable Long id, Model model,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryProgrammeTemplate programme;
        try {
            programme = trainerLibraryService.getProgrammeOwned(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        List<TrainerLibraryProgrammeDay> days = trainerLibraryService.getProgrammeDays(id);
        List<TrainerLibraryProgrammeNote> notes = trainerLibraryService.getProgrammeNotes(id);

        Set<Long> workoutIds = new HashSet<>();
        for (TrainerLibraryProgrammeDay d : days) {
            workoutIds.add(d.getWorkoutId());
        }
        var ownedWorkouts = trainerLibraryService.listWorkouts(trainerId);
        Map<Long, TrainerLibraryWorkoutTemplate> workoutsById = new HashMap<>();
        if (!workoutIds.isEmpty()) {
            for (TrainerLibraryWorkoutTemplate w : ownedWorkouts) {
                if (workoutIds.contains(w.getId())) {
                    workoutsById.put(w.getId(), w);
                }
            }
        }

        ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/view");
        mav.addObject("pageTitle", "Programme - " + programme.getTitle());
        mav.addObject("programme", programme);
        addLibraryContext(mav, q, page);
        mav.addObject("days", days);
        mav.addObject("notes", notes);
        mav.addObject("workoutsById", workoutsById);

        mav.addObject("dayForm", new TrainerLibraryProgrammeDayForm());
        mav.addObject("daySuggestions", Arrays.stream(java.time.DayOfWeek.values())
                .map(day -> day.getDisplayName(java.time.format.TextStyle.FULL, org.springframework.context.i18n.LocaleContextHolder.getLocale())).toList());
        mav.addObject("workouts", ownedWorkouts);

        mav.addObject("shareForm", defaultShareForm(TrainerLibraryTemplateType.PROGRAMME, id, "/trainer/library/programmes/" + id));
        addLibraryFeedback(mav, model);
        mav.addObject("activeClients", activeClientsForTrainer(trainerId));
        restoreShareRecipient(mav, model);

        return mav;
    }

    @GetMapping("/programmes/{id}/edit")
    public ModelAndView programmesEdit(@PathVariable Long id,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        TrainerLibraryService.ProgrammeMetadataSnapshot snapshot;
        try {
            snapshot = trainerLibraryService.getProgrammeMetadataSnapshot(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }

        TrainerLibraryProgrammeTemplateForm form = new TrainerLibraryProgrammeTemplateForm();
        var programme = snapshot.programme();
        form.setTitle(programme.getTitle());
        form.setWeeks(programme.getWeeks());
        form.setNotesText(String.join("\n", snapshot.notes().stream().map(TrainerLibraryProgrammeNote::getNoteText).toList()));
        form.setExpectedRevision(snapshot.revision());

        ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/edit");
        mav.addObject("pageTitle", "Edit Programme");
        mav.addObject("programmeId", id);
        mav.addObject("form", form);
        mav.addObject("draftWeeks", form.getWeeks());
        addLibraryContext(mav, q, page);
        return mav;
    }

    @PostMapping("/programmes/{id}/edit")
    public ModelAndView programmesEditSubmit(@PathVariable Long id,
                                             @Valid @ModelAttribute("form") TrainerLibraryProgrammeTemplateForm form,
                                             BindingResult bindingResult,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.getProgrammeOwned(trainerId, id);
        } catch (AccessDeniedException denied) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (bindingResult.hasErrors()) {
            ModelAndView mav = new ModelAndView("trainer-views/trainer/programmes/edit");
            mav.addObject("pageTitle", "Edit Programme");
            mav.addObject("programmeId", id);
            mav.addObject("form", form);
            mav.addObject("draftWeeks", bindingResult.getFieldValue("weeks"));
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return mav;
        }
        try {
            trainerLibraryService.updateProgramme(trainerId, id, form);
        } catch (TrainerLibraryRevisionConflictException conflict) {
            bindingResult.reject("ui.library.programmeChanged");
            var latest = trainerLibraryService.getProgrammeMetadataSnapshot(trainerId, id);
            form.setExpectedRevision(latest.revision());
            var mav = new ModelAndView("trainer-views/trainer/programmes/edit");
            mav.addObject("pageTitle", "Edit Programme"); mav.addObject("programmeId", id);
            mav.addObject("form", form); mav.addObject("draftWeeks", form.getWeeks());
            mav.addObject("programmeConflict", true); mav.addObject("latestProgramme", latest.programme());
            mav.addObject("latestProgrammeNotes", latest.notes());
            addLibraryContext(mav, q, page);
            mav.addObject(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            mav.setStatus(org.springframework.http.HttpStatus.CONFLICT);
            return mav;
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        redirectAttributes.addFlashAttribute("libraryProgrammeSaved", true);
        return libraryRedirect("/trainer/library/programmes/" + id, q, page);
    }

    @PostMapping("/programmes/{id}/days")
    public ModelAndView programmesAddDay(@PathVariable Long id,
                                         @Valid @ModelAttribute("dayForm") TrainerLibraryProgrammeDayForm dayForm,
                                         BindingResult bindingResult,
                                         Model model, RedirectAttributes redirectAttributes,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        ModelAndView destination = programmesView(id, model, q, page);
        if (!"trainer-views/trainer/programmes/view".equals(destination.getViewName())) return destination;
        if (!bindingResult.hasErrors()) {
            try {
                trainerLibraryService.addProgrammeDay(trainerId, id, dayForm);
                redirectAttributes.addFlashAttribute("libraryDaySaved", true);
                return libraryRedirect("/trainer/library/programmes/" + id, q, page);
            } catch (AccessDeniedException denied) {
                return new ModelAndView("redirect:/access-denied");
            } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException invalidDay) {
                bindingResult.reject("ui.library.dayInvalid");
            }
        }
        destination.addObject("dayForm", dayForm);
        destination.addObject(BindingResult.MODEL_KEY_PREFIX + "dayForm", bindingResult);
        destination.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        return destination;
    }

    @PostMapping("/programmes/{programmeId}/days/{dayId}/move")
    public ModelAndView programmesMoveDay(@PathVariable Long programmeId, @PathVariable Long dayId,
                                          @RequestParam(defaultValue = "") String direction,
                                          @RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          RedirectAttributes redirectAttributes) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            if (trainerLibraryService.moveProgrammeDay(trainerId, programmeId, dayId, direction)) {
                redirectAttributes.addFlashAttribute("libraryOrderSaved", true);
            }
        } catch (AccessDeniedException denied) {
            return new ModelAndView("redirect:/access-denied");
        } catch (IllegalArgumentException invalidDirection) {
            redirectAttributes.addFlashAttribute("libraryOrderError", true);
        }
        var destination = libraryRedirect("/trainer/library/programmes/" + programmeId, q, page);
        destination.setViewName(destination.getViewName() + "#programme-day-" + dayId);
        return destination;
    }

    @PostMapping("/programmes/{programmeId}/days/{dayId}/delete")
    public ModelAndView programmesDeleteDay(@PathVariable Long programmeId,
                                            @PathVariable Long dayId,
                                            @RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.deleteProgrammeDay(trainerId, programmeId, dayId);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        return libraryRedirect("/trainer/library/programmes/" + programmeId, q, page);
    }

    @PostMapping("/programmes/{id}/delete")
    public ModelAndView programmesDelete(@PathVariable Long id,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "0") int page) {
        Long trainerId = currentTrainerIdOrThrow();
        try {
            trainerLibraryService.deleteProgramme(trainerId, id);
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        }
        return libraryRedirect("/trainer/library/programmes", q, page);
    }

    // -------------------------
    // Share
    // -------------------------

    @PostMapping("/share")
    public ModelAndView share(@Valid @ModelAttribute("shareForm") TrainerLibraryShareForm form,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              @RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "0") int page) {
        User trainer = currentTrainerOrThrow();
        Long trainerId = trainer.getId();

        String returnUrl = shareDestination(form);
        ModelAndView destination = form.getTemplateType() != null
                && form.getTemplateId() != null && form.getTemplateId() > 0
                ? libraryRedirect(returnUrl, q, page) : new ModelAndView("redirect:" + returnUrl);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("libraryShareError", true);
            if (form.getClientId() != null) redirectAttributes.addFlashAttribute("libraryShareClientId", form.getClientId());
            return destination;
        }

        try {
            trainerLibraryService.shareTemplate(trainerId, form);
            redirectAttributes.addFlashAttribute("libraryShared", true);
            userRepository.findById(form.getClientId()).ifPresent(client ->
                    redirectAttributes.addFlashAttribute("librarySharedRecipient",
                            client.getFullName() == null || client.getFullName().isBlank() ? client.getUsername() : client.getFullName()));
        } catch (AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("libraryShareError", true);
            if (form.getClientId() != null) redirectAttributes.addFlashAttribute("libraryShareClientId", form.getClientId());
        }

        return destination;
    }

    private String shareDestination(TrainerLibraryShareForm form) {
        if (form.getTemplateType() == null || form.getTemplateId() == null || form.getTemplateId() <= 0) return "/trainer/library";
        String resource = switch (form.getTemplateType()) {
            case EXERCISE -> "exercises";
            case WORKOUT -> "workouts";
            case PROGRAMME -> "programmes";
        };
        return "/trainer/library/" + resource + "/" + form.getTemplateId();
    }

    private void addLibraryFeedback(ModelAndView mav, Model model) {
        for (String key : List.of("libraryShared", "libraryShareError", "libraryDeleteBlocked", "libraryItemSaved", "libraryWorkoutDeleteBlocked", "libraryDaySaved", "libraryExerciseSaved", "libraryWorkoutSaved", "libraryOrderSaved", "libraryOrderError", "libraryProgrammeSaved")) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        if (model.containsAttribute("librarySharedRecipient")) mav.addObject("librarySharedRecipient", model.getAttribute("librarySharedRecipient"));
    }

    private void restoreShareRecipient(ModelAndView mav, Model model) {
        if (!(model.getAttribute("libraryShareClientId") instanceof Long clientId)) return;
        @SuppressWarnings("unchecked")
        var clients = (List<User>) mav.getModel().get("activeClients");
        if (clients.stream().anyMatch(client -> client.getId().equals(clientId))) {
            ((TrainerLibraryShareForm) mav.getModel().get("shareForm")).setClientId(clientId);
        }
    }

    private TrainerLibraryShareForm defaultShareForm(TrainerLibraryTemplateType type, Long templateId, String returnUrl) {
        TrainerLibraryShareForm form = new TrainerLibraryShareForm();
        form.setTemplateType(type);
        form.setTemplateId(templateId);
        form.setReturnUrl(returnUrl);
        return form;
    }
}
