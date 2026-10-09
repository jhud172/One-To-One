package uk.ac.cf._5.group14.One_To_One.StrengthLog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.ScheduledWorkoutSessionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplateRepository;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class ScheduledWorkoutSessionController {

    private final ScheduledWorkoutSessionService scheduledWorkoutSessionService;
    private final AuthHelper authHelper;
    private final TrainerClientLinkService trainerClientLinkService;
    private final TrainerScheduleTemplateRepository checkInTemplates;
    private final Clock clock;

    public ScheduledWorkoutSessionController(
            ScheduledWorkoutSessionService scheduledWorkoutSessionService,
            AuthHelper authHelper,
            TrainerClientLinkService trainerClientLinkService,
            TrainerScheduleTemplateRepository checkInTemplates,
            Clock clock
    ) {
        this.scheduledWorkoutSessionService = scheduledWorkoutSessionService;
        this.authHelper = authHelper;
        this.trainerClientLinkService = trainerClientLinkService;
        this.checkInTemplates = checkInTemplates;
        this.clock = clock;
    }

    @GetMapping("/workout-session/launch/session/{sessionId}")
    public String launchSession(@PathVariable Long sessionId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        WorkoutSession session = scheduledWorkoutSessionService.launchFromSession(user, sessionId);
        return "redirect:" + scheduledWorkoutSessionService.sessionDestinationPath(session);
    }

    @GetMapping("/workout-session/launch/occurrence/{occurrenceId}")
    public String launchOccurrence(@PathVariable Long occurrenceId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        WorkoutSession session = scheduledWorkoutSessionService.launchFromOccurrence(user, occurrenceId);
        return "redirect:" + scheduledWorkoutSessionService.sessionDestinationPath(session);
    }

    @GetMapping("/workout-session/{sessionId}")
    public String sessionPage(@PathVariable Long sessionId, Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        WorkoutSession session = scheduledWorkoutSessionService.requireOwnedSession(user, sessionId);
        if (session.isCompleted()) {
            return "redirect:/workout-session/" + sessionId + "/complete";
        }

        model.addAttribute("view", scheduledWorkoutSessionService.buildViewModel(user, sessionId));
        return "shared-views/workout-session/session";
    }

    @GetMapping("/workout-session/{sessionId}/complete")
    public String completePage(@PathVariable Long sessionId, Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        WorkoutSession session = scheduledWorkoutSessionService.requireOwnedSession(user, sessionId);
        if (!session.isCompleted()) {
            return "redirect:/workout-session/" + sessionId;
        }

        model.addAttribute("view", scheduledWorkoutSessionService.buildViewModel(user, sessionId));
        model.addAttribute("nextWorkout", scheduledWorkoutSessionService.nextOpenLaunchItem(user, LocalDate.now(clock)));
        var activeCoach = user.getRole() == Role.CLIENT
                ? trainerClientLinkService.getActiveLinkForClient(user.getId()) : null;
        model.addAttribute("canCoachFeedback", activeCoach != null);
        model.addAttribute("coachId", activeCoach == null ? null : activeCoach.getTrainerUserId());
        model.addAttribute("canCheckIn", activeCoach != null
                && checkInTemplates.existsByTrainerId(activeCoach.getTrainerUserId()));
        return "shared-views/workout-session/complete";
    }

    @PostMapping("/workout-session/{sessionId}/exercises/{exerciseSessionId}/sets")
    public String addSet(@PathVariable Long sessionId, @PathVariable Long exerciseSessionId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (scheduledWorkoutSessionService.requireOwnedSession(user, sessionId).isCompleted()) {
            return "redirect:/workout-session/" + sessionId + "/complete";
        }

        scheduledWorkoutSessionService.addSet(user, sessionId, exerciseSessionId);
        return "redirect:/workout-session/" + sessionId;
    }

    @PostMapping("/workout-session/{sessionId}/sets/{setId}")
    public String updateSet(
            @PathVariable Long sessionId,
            @PathVariable Long setId,
            @RequestParam(required = false) Double weight,
            @RequestParam(required = false) Integer reps,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Boolean completed
    ) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (scheduledWorkoutSessionService.requireOwnedSession(user, sessionId).isCompleted()) {
            return "redirect:/workout-session/" + sessionId + "/complete";
        }

        scheduledWorkoutSessionService.updateSet(user, sessionId, setId, weight, reps, notes, completed);
        return "redirect:/workout-session/" + sessionId + "#set-" + setId;
    }

    @PostMapping(value = "/workout-session/{sessionId}/sets/{setId}", headers = "X-Workout-Async=1", produces = "application/json")
    @ResponseBody
    public ScheduledWorkoutSessionViewModel saveSetWithoutNavigation(
            @PathVariable Long sessionId, @PathVariable Long setId,
            @RequestParam(required = false) Double weight,
            @RequestParam(required = false) Integer reps,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Boolean completed
    ) {
        User user = currentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (scheduledWorkoutSessionService.requireOwnedSession(user, sessionId).isCompleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This workout is already complete");
        }
        scheduledWorkoutSessionService.updateSet(user, sessionId, setId, weight, reps, notes, completed);
        return scheduledWorkoutSessionService.buildViewModel(user, sessionId);
    }

    @PostMapping("/workout-session/{sessionId}/sets/{setId}/toggle")
    public String toggleSet(@PathVariable Long sessionId, @PathVariable Long setId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (scheduledWorkoutSessionService.requireOwnedSession(user, sessionId).isCompleted()) {
            return "redirect:/workout-session/" + sessionId + "/complete";
        }

        scheduledWorkoutSessionService.toggleSet(user, sessionId, setId);
        return "redirect:/workout-session/" + sessionId;
    }

    @PostMapping("/workout-session/{sessionId}/sets/{setId}/delete")
    public String deleteSet(@PathVariable Long sessionId, @PathVariable Long setId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (scheduledWorkoutSessionService.requireOwnedSession(user, sessionId).isCompleted()) {
            return "redirect:/workout-session/" + sessionId + "/complete";
        }

        scheduledWorkoutSessionService.deleteSet(user, sessionId, setId);
        return "redirect:/workout-session/" + sessionId;
    }

    @PostMapping("/workout-session/{sessionId}/complete")
    public String finishSession(@PathVariable Long sessionId) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        scheduledWorkoutSessionService.completeSession(user, sessionId);
        return "redirect:/workout-session/" + sessionId + "/complete";
    }

    private User currentUser() {
        return authHelper.getAuthenticatedUser();
    }
}
