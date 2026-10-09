package uk.ac.cf._5.group14.One_To_One.TrainerAssignments;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.i18n.LocaleContextHolder;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntry;
import java.util.List;
import java.util.stream.IntStream;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.TreeSet;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleType;

@Controller
@RequestMapping("/client/plan")
public class ClientPlanController {

    private final TrainerAssignmentService trainerAssignmentService;
    private final AuthHelper authHelper;
    private final UserService userService;
    private final ScheduleEntryService scheduleEntryService;

    public ClientPlanController(TrainerAssignmentService trainerAssignmentService,
                                AuthHelper authHelper,
                                UserService userService, ScheduleEntryService scheduleEntryService) {
        this.trainerAssignmentService = trainerAssignmentService;
        this.authHelper = authHelper;
        this.userService = userService;
        this.scheduleEntryService = scheduleEntryService;
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
    public ModelAndView plan(Model model) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }

        ModelAndView mav = new ModelAndView("client-views/client/plan");
        mav.addObject("pageTitle", "Assigned Plan");
        mav.addObject("assignedWorkouts", trainerAssignmentService.listWorkoutsForClient(client.getId()));
        mav.addObject("assignedSchedules", trainerAssignmentService.listSchedulesForClient(client.getId()));
        for (String key : new String[]{"planSaved", "planDraft"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        return mav;
    }

    @GetMapping("/schedules/{assignmentId}")
    public ModelAndView schedule(@PathVariable Long assignmentId) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) throw new AccessDeniedException("Client access required");
        AssignedSchedule assignment = trainerAssignmentService.getScheduleForClient(client.getId(), assignmentId);
        List<ScheduleEntry> entries = scheduleEntryService.getEntries(assignment.getSchedule().getId());
        var schedule = assignment.getSchedule();
        boolean weekly = schedule.getScheduleType() == ScheduleType.WEEKLY;
        int dayCount = weekly ? 7 : (schedule.getScheduleType() == ScheduleType.DAILY ? 1
                : Math.max(1, Math.min(365, schedule.getCustomDayCount() == null ? 1 : schedule.getCustomDayCount())));
        TreeSet<Integer> dayNumbers = new TreeSet<>();
        IntStream.rangeClosed(1, dayCount).forEach(dayNumbers::add);
        entries.forEach(entry -> dayNumbers.add(entry.getDayOfWeek()));
        List<AssignedScheduleDay> days = dayNumbers.stream().map(day -> new AssignedScheduleDay(day,
                weekly && day >= 1 && day <= 7 ? DayOfWeek.of(day).getDisplayName(TextStyle.FULL, LocaleContextHolder.getLocale()) : null,
                entries.stream().filter(entry -> entry.getDayOfWeek() == day).toList())).toList();
        ModelAndView mav = new ModelAndView("client-views/client/assigned-schedule");
        mav.addObject("assignment", assignment);
        mav.addObject("days", days);
        return mav;
    }

    public record AssignedScheduleDay(int number, String label, List<ScheduleEntry> entries) {}

    @PostMapping("/workouts/{id}")
    public ModelAndView updateWorkout(@PathVariable Long id,
                                      @RequestParam(required = false) String clientNotes,
                                      @RequestParam(required = false) String clientFeedback,
                                      @RequestParam(name = "completed", defaultValue = "false") boolean completed,
                                      RedirectAttributes redirectAttributes) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }
        try {
            trainerAssignmentService.updateClientWorkout(client.getId(), id, clientNotes, clientFeedback, completed);
            redirectAttributes.addFlashAttribute("planSaved", true);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("planDraft", new ClientPlanDraft(id, boundedDraft(clientNotes), boundedDraft(clientFeedback), completed));
        }
        return new ModelAndView("redirect:/client/plan");
    }

    public record ClientPlanDraft(Long assignmentId, String notes, String feedback, boolean completed) implements java.io.Serializable {}

    private String boundedDraft(String text) {
        return text == null ? "" : text.substring(0, Math.min(4000, text.length()));
    }
}
