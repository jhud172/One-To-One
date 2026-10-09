package uk.ac.cf._5.group14.One_To_One.WorkoutTemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutBuilderController;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.ScheduledWorkoutSessionController;

/** Presentation preferences for the two active players, without changing their session data. */
@ControllerAdvice(assignableTypes = {WorkoutBuilderController.class, ScheduledWorkoutSessionController.class})
@RequiredArgsConstructor
public class WorkoutDisplayAdvice {
    private final AuthHelper authHelper;
    private final WorkoutTemplateService templates;
    private final WorkoutDisplayConfig config;

    @ModelAttribute("workoutDisplay")
    public WorkoutDisplayConfig.Settings workoutDisplay() {
        User user = authHelper.getAuthenticatedUser();
        return user == null ? config.defaults(TemplateLayoutType.FLOW)
                : config.forDisplay(templates.getDefaultTemplateForUser(user.getId()));
    }
}
