package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

@Controller
@RequestMapping("/client/assigned-plan")
public class ClientAssignedPlanController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final TrainerLibraryService trainerLibraryService;

    public ClientAssignedPlanController(AuthHelper authHelper,
                                      UserService userService,
                                      TrainerLibraryService trainerLibraryService) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.trainerLibraryService = trainerLibraryService;
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

    @GetMapping
    public ModelAndView assignedPlan() {
        User user = currentUserOrThrow();
        if (user.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }

        ModelAndView mav = new ModelAndView("client-views/client/assigned-plan");
        mav.addObject("pageTitle", "Assigned Plan");
        mav.addObject("assignedExercises", trainerLibraryService.getAssignedExercisesForClient(user.getId()));
        mav.addObject("assignedWorkouts", trainerLibraryService.getAssignedWorkoutsForClient(user.getId()));
        mav.addObject("assignedProgrammes", trainerLibraryService.getAssignedProgrammesForClient(user.getId()));
        return mav;
    }
}
