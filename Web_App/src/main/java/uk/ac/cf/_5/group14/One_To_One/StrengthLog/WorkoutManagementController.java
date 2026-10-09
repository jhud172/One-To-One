package uk.ac.cf._5.group14.One_To_One.StrengthLog;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogService;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.ScheduledWorkoutSessionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class WorkoutManagementController {

    private final AuthHelper authHelper;
    private final ScheduledWorkoutSessionService scheduledWorkoutSessionService;
    private final ExerciseLogService exerciseLogService;
    private final Clock clock;

    public WorkoutManagementController(
            AuthHelper authHelper,
            ScheduledWorkoutSessionService scheduledWorkoutSessionService,
            ExerciseLogService exerciseLogService,
            Clock clock
    ) {
        this.authHelper = authHelper;
        this.scheduledWorkoutSessionService = scheduledWorkoutSessionService;
        this.exerciseLogService = exerciseLogService;
        this.clock = clock;
    }

    @GetMapping("/workout-management")
    public String workoutManagement(Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate lookaheadEnd = today.plusDays(21);

        model.addAttribute("pageTitle", "Workout Management");
        model.addAttribute("today", today);
        var activeWorkouts = scheduledWorkoutSessionService.listOpenLaunchItems(user, today);
        model.addAttribute("activeWorkouts", activeWorkouts);
        model.addAttribute("nextWorkout", activeWorkouts.isEmpty() ? null : activeWorkouts.getFirst());
        model.addAttribute("otherWorkouts", activeWorkouts.stream().skip(1).toList());
        model.addAttribute("upcomingWorkouts",
                scheduledWorkoutSessionService.listUpcomingLaunchItems(user, today.plusDays(1), lookaheadEnd).stream()
                        .filter(item -> !item.completed())
                        .toList());
        model.addAttribute("completedSessions", scheduledWorkoutSessionService.listRecentCompletedSessions(user, 12));
        model.addAttribute("exerciseLogs", exerciseLogService.getLogsByUser(user).stream().limit(12).toList());

        return "shared-views/workout-management/index";
    }
}
