package uk.ac.cf._5.group14.One_To_One.ExerciseData;

import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.ScheduledWorkoutSessionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Security.SafeHttpUrl;

@Controller
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ScheduledWorkoutSessionService sessions;
    private final AuthHelper authHelper;

    public ExerciseController(ExerciseService exerciseService,
            ScheduledWorkoutSessionService sessions, AuthHelper authHelper) {
        this.exerciseService = exerciseService;
        this.sessions = sessions;
        this.authHelper = authHelper;
    }

    @GetMapping("/exercise/{id}")
    public ModelAndView getExercise(@PathVariable Long id, @RequestParam(required = false) Long sessionId,
                                    @RequestParam(required = false) Long entryId) {
        ModelAndView modelAndView = new ModelAndView("shared-views/exercise-log/ExerciseTutorial");
        Exercise exercise = exerciseService.getExerciseById(id);
        if (exercise == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise unavailable");
        if (sessionId != null) {
            var user = authHelper.getAuthenticatedUser();
            if (user == null) return new ModelAndView("redirect:/login");
            var session = sessions.buildViewModel(user, sessionId);
            var entry = session.exercises().stream().filter(item -> id.equals(item.catalogueExerciseId())
                    && (entryId == null || entryId.equals(item.exerciseSessionId()))).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise unavailable in this session"));
            modelAndView.addObject("instructionSessionName", session.workoutName());
            modelAndView.addObject("instructionSessionDate", session.date());
            modelAndView.addObject("instructionReturnUrl", "/workout-session/" + sessionId
                    + (session.completed() ? "/complete" : "#exercise-title-" + entry.exerciseSessionId()));
        }
        modelAndView.addObject("exercise", exercise);
        String video = exercise.getVideoUrl();
        modelAndView.addObject("safeVideoUrl", SafeHttpUrl.isSafe(video)
                && video != null && !video.isBlank() ? video.strip() : null);
        return modelAndView;
    }
}
