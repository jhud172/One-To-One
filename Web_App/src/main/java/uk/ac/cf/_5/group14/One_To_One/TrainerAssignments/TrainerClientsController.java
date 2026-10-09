package uk.ac.cf._5.group14.One_To_One.TrainerAssignments;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Goals.Goal;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalAdherenceService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalAdherenceWeek;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalStatus;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.HealthRecord;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.HealthRecordRepository;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLog;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogRepository;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogService;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.DayHealthData.DayHealth;
import uk.ac.cf._5.group14.One_To_One.DayHealthData.DayHealthRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.CoachingPhase;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkException;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Controller
@RequestMapping("/trainer/clients")
public class TrainerClientsController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ClientCoachingWorkspaceService workspaceService;
    private final TrainerAssignmentService trainerAssignmentService;
    private final AccessGuard accessGuard;
    private final GoalService goalService;
    private final GoalAdherenceService goalAdherenceService;
    private final TrainerClientLinkService trainerClientLinkService;
    private final UserSettingsService userSettingsService;
    private final DayHealthRepository dayHealthRepository;
    private final DailyNutritionLogRepository dailyNutritionLogRepository;
    private final DailyNutritionLogService dailyNutritionLogService;
    private final HealthRecordRepository healthRecordRepository;

    public TrainerClientsController(AuthHelper authHelper,
                                    UserService userService,
                                    UserRepository userRepository,
                                    ClientCoachingWorkspaceService workspaceService,
                                    TrainerAssignmentService trainerAssignmentService,
                                    AccessGuard accessGuard,
                                    GoalService goalService,
                                    GoalAdherenceService goalAdherenceService,
                                    TrainerClientLinkService trainerClientLinkService,
                                    UserSettingsService userSettingsService,
                                    DayHealthRepository dayHealthRepository,
                                    DailyNutritionLogRepository dailyNutritionLogRepository,
                                    DailyNutritionLogService dailyNutritionLogService,
                                    HealthRecordRepository healthRecordRepository) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.trainerAssignmentService = trainerAssignmentService;
        this.accessGuard = accessGuard;
        this.goalService = goalService;
        this.goalAdherenceService = goalAdherenceService;
        this.trainerClientLinkService = trainerClientLinkService;
        this.userSettingsService = userSettingsService;
        this.dayHealthRepository = dayHealthRepository;
        this.dailyNutritionLogRepository = dailyNutritionLogRepository;
        this.dailyNutritionLogService = dailyNutritionLogService;
        this.healthRecordRepository = healthRecordRepository;
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

    @GetMapping("/{clientId}")
    public ModelAndView clientDetail(@PathVariable Long clientId, Model model) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (!trainer.isTrainerVerified() || !trainer.isEnabled()) {
            return new ModelAndView("redirect:/trainer/clients?error=trainer-unverified");
        }
        accessGuard.requireTrainerAccessClient(trainer.getId(), clientId);

        User client = userRepository.findById(clientId).orElse(null);
        if (client == null) {
            return new ModelAndView("redirect:/trainer/clients");
        }

        ModelAndView mav = new ModelAndView("trainer-views/trainer/client-detail");
        for (String key : List.of("workspaceOutcome", "workspaceError", "workoutDraftId", "workoutDraftNotes",
                "scheduleDraftId", "scheduleDraftNotes", "phaseDraft", "phaseLabelDraft", "phaseNoteDraft")) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        mav.addObject("pageTitle", "Client Overview");
        mav.addObject("client", client);
        mav.addObject("assignedWorkouts", trainerAssignmentService.listWorkoutsForTrainerClient(trainer.getId(), clientId));
        mav.addObject("assignedSchedules", trainerAssignmentService.listSchedulesForTrainerClient(trainer.getId(), clientId));
        mav.addObject("adherence", trainerAssignmentService.getClientAdherence(trainer.getId(), clientId));
        List<Goal> goals = goalService.listGoalsForViewer(trainer, clientId, GoalStatus.ACTIVE, null, false);
        LocalDate weekStart = GoalAdherenceService.normalizeWeekStart(LocalDate.now());
        Map<Long, GoalAdherenceWeek> adherenceByGoalId = goals.stream()
            .collect(Collectors.toMap(Goal::getId, goal -> goalAdherenceService.calculateWeek(goal, weekStart)));
        mav.addObject("clientGoals", goals);
        mav.addObject("goalAdherenceById", adherenceByGoalId);
        var workspace = workspaceService.summary(trainer, clientId);
        mav.addObject("templates", workspace.workouts());
        mav.addObject("schedules", workspace.schedules());
        mav.addObject("workspace", workspace);
        TrainerClientLink activeLink = trainerClientLinkService.getActiveLinkForTrainerClient(trainer.getId(), clientId);
        mav.addObject("activeLink", activeLink);
        mav.addObject("coachingPhases", CoachingPhase.values());

        UserSettings clientSettings = userSettingsService.getOrCreate(client);
        boolean shareRecovery = clientSettings != null && clientSettings.isShareRecoverySignals();
        boolean shareNutrition = clientSettings != null && clientSettings.isShareNutritionSignals();
        boolean shareSleep = clientSettings != null && clientSettings.isShareSleepSignals();
        boolean shareFatigue = clientSettings != null && clientSettings.isShareFatigueSignals();
        boolean shareWeight = clientSettings != null && clientSettings.isShareWeightTrend();
        boolean hasSharedSignals = shareRecovery || shareNutrition || shareSleep || shareFatigue || shareWeight;

        mav.addObject("shareRecoverySignals", shareRecovery);
        mav.addObject("shareNutritionSignals", shareNutrition);
        mav.addObject("shareSleepSignals", shareSleep);
        mav.addObject("shareFatigueSignals", shareFatigue);
        mav.addObject("shareWeightTrend", shareWeight);
        mav.addObject("hasSharedSignals", hasSharedSignals);

        if (shareRecovery) {
            DayHealth latestRecovery = dayHealthRepository.findTopByUserOrderByDateDesc(client).orElse(null);
            mav.addObject("latestRecovery", latestRecovery);
        }

        if (shareNutrition) {
            DailyNutritionLog latestNutrition = dailyNutritionLogRepository.findTopByUserOrderByDateDescIdDesc(client).orElse(null);
            mav.addObject("latestNutritionSummary", dailyNutritionLogService.summarize(latestNutrition));
        }

        if (shareWeight) {
            List<HealthRecord> records = healthRecordRepository.findTop2ByUserOrderByBaselineDateDescIdDesc(client);
            HealthRecord latest = records.isEmpty() ? null : records.get(0);
            HealthRecord previous = records.size() > 1 ? records.get(1) : null;
            Double delta = null;
            if (latest != null && previous != null && latest.getWeightKg() != null && previous.getWeightKg() != null) {
                delta = latest.getWeightKg() - previous.getWeightKg();
            }
            mav.addObject("latestWeightRecord", latest);
            mav.addObject("weightDelta", delta);
        }

        return mav;
    }

    @PostMapping("/{clientId}/phase")
    public ModelAndView updateCoachingPhase(@PathVariable Long clientId,
                                            @RequestParam(required = false) CoachingPhase phase,
                                            @RequestParam(required = false) String customLabel,
                                            @RequestParam(required = false) String notes,
                                            RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }
        try {
            trainerClientLinkService.changeCoachingPhase(trainer.getId(), clientId, phase, customLabel, notes);
        } catch (TrainerClientLinkException ex) {
            if (ex.getReason() == TrainerClientLinkException.Reason.TRAINER_NOT_VERIFIED) {
                return new ModelAndView("redirect:/trainer/clients?error=trainer-unverified");
            }
            throw ex;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("workspaceError", true);
            redirectAttributes.addFlashAttribute("phaseDraft", phase == null ? "" : phase.name());
            redirectAttributes.addFlashAttribute("phaseLabelDraft", customLabel == null ? "" : customLabel);
            redirectAttributes.addFlashAttribute("phaseNoteDraft", notes == null ? "" : notes);
            return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientPhase");
        }
        redirectAttributes.addFlashAttribute("workspaceOutcome", "phase");
        return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientPhase");
    }

    @PostMapping("/{clientId}/assign-workout")
    public ModelAndView assignWorkout(@PathVariable Long clientId,
                                      @RequestParam(required = false) Long templateId,
                                      @RequestParam(required = false) String trainerNotes,
                                      RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (!trainer.isTrainerVerified() || !trainer.isEnabled()) {
            return new ModelAndView("redirect:/trainer/clients?error=trainer-unverified");
        }
        try {
            trainerAssignmentService.assignWorkout(trainer, clientId, templateId, trainerNotes);
        } catch (IllegalArgumentException ex) {
            retainAssignment(redirectAttributes, "workout", templateId, trainerNotes);
            return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientAssignments");
        }
        redirectAttributes.addFlashAttribute("workspaceOutcome", "workout");
        return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientAssignments");
    }

    @PostMapping("/{clientId}/assign-schedule")
    public ModelAndView assignSchedule(@PathVariable Long clientId,
                                       @RequestParam(required = false) Long scheduleId,
                                       @RequestParam(required = false) String trainerNotes,
                                       RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }
        if (!trainer.isTrainerVerified() || !trainer.isEnabled()) {
            return new ModelAndView("redirect:/trainer/clients?error=trainer-unverified");
        }
        try {
            trainerAssignmentService.assignSchedule(trainer, clientId, scheduleId, trainerNotes);
        } catch (IllegalArgumentException ex) {
            retainAssignment(redirectAttributes, "schedule", scheduleId, trainerNotes);
            return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientAssignments");
        }
        redirectAttributes.addFlashAttribute("workspaceOutcome", "schedule");
        return new ModelAndView("redirect:/trainer/clients/" + clientId + "#clientAssignments");
    }

    private void retainAssignment(RedirectAttributes redirectAttributes, String kind, Long id, String notes) {
        redirectAttributes.addFlashAttribute("workspaceError", true);
        if (id != null) redirectAttributes.addFlashAttribute(kind + "DraftId", id);
        redirectAttributes.addFlashAttribute(kind + "DraftNotes", notes == null ? "" : notes);
    }
}
