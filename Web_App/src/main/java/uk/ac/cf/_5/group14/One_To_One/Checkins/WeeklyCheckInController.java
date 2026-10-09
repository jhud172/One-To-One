package uk.ac.cf._5.group14.One_To_One.Checkins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Goals.Goal;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalStatus;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplateRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/checkins")
public class WeeklyCheckInController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final TrainerClientLinkService trainerClientLinkService;
    private final TrainerScheduleTemplateRepository templateRepository;
    private final WeeklyCheckInService weeklyCheckInService;
    private final GoalService goalService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public WeeklyCheckInController(AuthHelper authHelper,
                                   UserService userService,
                                   TrainerClientLinkService trainerClientLinkService,
                                   TrainerScheduleTemplateRepository templateRepository,
                                   WeeklyCheckInService weeklyCheckInService,
                                   GoalService goalService,
                                   ObjectMapper objectMapper,
                                   UserRepository userRepository) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.trainerClientLinkService = trainerClientLinkService;
        this.templateRepository = templateRepository;
        this.weeklyCheckInService = weeklyCheckInService;
        this.goalService = goalService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
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

    @GetMapping("/client-submit")
    public ModelAndView clientSubmit(@RequestParam(required = false) Long templateId, Model model) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }

        TrainerClientLink link = trainerClientLinkService.getActiveLinkForClient(client.getId());
        ModelAndView mav = new ModelAndView("client-views/checkins/client-submit");
        if (model != null && model.containsAttribute("checkInSubmitted")) mav.addObject("checkInSubmitted", model.getAttribute("checkInSubmitted"));
        mav.addObject("recentCheckIns", weeklyCheckInService.listRecentForClient(client));
        mav.addObject("pageTitle", "Weekly Check-in");
        mav.addObject("activeLink", link);
        mav.addObject("templates", List.of());
        mav.addObject("questions", List.of());
        mav.addObject("today", LocalDate.now());

        if (link != null) {
            List<TrainerScheduleTemplate> templates = templateRepository.findByTrainerIdOrderByUpdatedAtDesc(link.getTrainerUserId());
            mav.addObject("templates", templates);
            Long selectedId = templateId != null ? templateId : (templates.isEmpty() ? null : templates.get(0).getId());
            if (selectedId != null && templates.stream().noneMatch(template -> selectedId.equals(template.getId()))) {
                throw new AccessDeniedException("Template is not owned by your active trainer");
            }
            mav.addObject("selectedTemplateId", selectedId);
            mav.addObject("selectedTemplateName", templates.stream()
                    .filter(template -> template.getId().equals(selectedId))
                    .map(TrainerScheduleTemplate::getName).findFirst().orElse(""));
            mav.addObject("questions", weeklyCheckInService.listQuestions(selectedId));
        }

        return mav;
    }

    @PostMapping("/client-submit")
    public ModelAndView submitCheckIn(@RequestParam Long templateId,
                                      @RequestParam(required = false) String clientNotes,
                                      @RequestParam(required = false) String weekStart,
                                      @RequestParam Map<String, String> params,
                                      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }

        ModelAndView draftView = clientSubmit(templateId, null);
        if (draftView.getModel().get("activeLink") == null) {
            throw new AccessDeniedException("Active trainer required");
        }
        Map<Long, String> answers = new HashMap<>();
        List<TrainerCheckInQuestion> questions = weeklyCheckInService.listQuestions(templateId);
        for (TrainerCheckInQuestion question : questions) {
            String key = "q_" + question.getId();
            String value = params.get(key);
            if (value != null) {
                answers.put(question.getId(), value);
            }
        }

        try {
            LocalDate weekStartDate = weekStart != null && !weekStart.isBlank() ? LocalDate.parse(weekStart) : null;
            weeklyCheckInService.submitCheckIn(client, templateId, answers, clientNotes, weekStartDate);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            String error = ex instanceof java.time.format.DateTimeParseException ? "invalid-week" : ex.getMessage();
            return clientDraftError(draftView, error, clientNotes, weekStart, params);
        } catch (IllegalStateException ex) {
            if (!"Weekly check-in already submitted".equals(ex.getMessage())) throw ex;
            return clientDraftError(draftView, "duplicate-week", clientNotes, weekStart, params);
        }
        redirectAttributes.addFlashAttribute("checkInSubmitted", true);
        return new ModelAndView("redirect:/checkins/client-submit?templateId=" + templateId);
    }

    private ModelAndView clientDraftError(ModelAndView view, String error, String notes,
                                         String weekStart, Map<String, String> answers) {
        view.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        view.addObject("checkInError", error);
        view.addObject("clientNotesInput", notes);
        view.addObject("weekInput", weekStart);
        view.addObject("draftAnswers", answers);
        return view;
    }

    @GetMapping("/trainer-review/{id}")
    public ModelAndView trainerReview(@PathVariable Long id, Model model) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }

        WeeklyCheckIn checkIn = weeklyCheckInService.getForTrainer(trainer, id);
        var responses = parseResponses(checkIn.getResponsesJson());
        List<Goal> goals = new ArrayList<>(goalService.listGoalsForViewer(trainer, checkIn.getClientId(), GoalStatus.ACTIVE, null, false));
        boolean goalUnavailable = false;
        if (checkIn.getGoalId() != null && goals.stream().noneMatch(goal -> checkIn.getGoalId().equals(goal.getId()))) {
            try {
                Goal attached = goalService.getGoalForViewer(trainer, checkIn.getGoalId());
                if (attached.getOwnerUser() != null && checkIn.getClientId().equals(attached.getOwnerUser().getId())) {
                    goals.add(attached);
                } else goalUnavailable = true;
            } catch (IllegalArgumentException | AccessDeniedException ex) {
                goalUnavailable = true;
            }
        }

        ModelAndView mav = new ModelAndView("trainer-views/checkins/trainer-review");
        mav.addObject("pageTitle", "Weekly Check-in Review");
        mav.addObject("checkIn", checkIn);
        if (model != null && model.containsAttribute("reviewSaved")) mav.addObject("reviewSaved", model.getAttribute("reviewSaved"));
        mav.addObject("responses", responses.answers());
        mav.addObject("answersUnavailable", responses.unavailable());
        mav.addObject("goalUnavailable", goalUnavailable);
        mav.addObject("goals", goals);
        mav.addObject("client", userRepository.findById(checkIn.getClientId()).orElse(null));
        return mav;
    }

    @PostMapping("/trainer-review/{id}")
    public ModelAndView trainerRespond(@PathVariable Long id,
                                       @RequestParam(required = false) String trainerResponse,
                                       @RequestParam(required = false) String nextWeekFocus,
                                       @RequestParam(required = false) Long goalId,
                                       RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER) {
            return new ModelAndView("redirect:/access-denied");
        }
        try {
            weeklyCheckInService.respondToCheckIn(trainer, id, trainerResponse, nextWeekFocus, goalId);
        } catch (IllegalArgumentException ex) {
            ModelAndView review = trainerReview(id, null);
            review.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            review.addObject("checkInError", ex.getMessage());
            review.addObject("responseInput", trainerResponse);
            review.addObject("focusInput", nextWeekFocus);
            review.addObject("goalInput", goalId);
            return review;
        }
        redirectAttributes.addFlashAttribute("reviewSaved", true);
        return new ModelAndView("redirect:/checkins/trainer-review/" + id);
    }

    private record ParsedResponses(List<Map<String, String>> answers, boolean unavailable) {}

    @GetMapping("/client-review/{id}")
    public ModelAndView clientReview(@PathVariable Long id) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) return new ModelAndView("redirect:/access-denied");
        var checkIn = weeklyCheckInService.getForClient(client, id);
        var responses = parseResponses(checkIn.getResponsesJson());
        var view = new ModelAndView("client-views/checkins/client-review");
        view.addObject("pageTitle", "Weekly Check-in");
        view.addObject("checkIn", checkIn);
        view.addObject("responses", responses.answers());
        view.addObject("answersUnavailable", responses.unavailable());
        return view;
    }

    private ParsedResponses parseResponses(String json) {
        if (json == null || json.isBlank()) {
            return new ParsedResponses(List.of(), false);
        }
        try {
            List<Map<String, String>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (parsed == null) return new ParsedResponses(List.of(), true);
            var valid = parsed.stream().filter(answer -> answer != null && answer.get("prompt") != null && answer.get("answer") != null).toList();
            return new ParsedResponses(valid, valid.size() != parsed.size());
        } catch (Exception e) {
            return new ParsedResponses(List.of(), true);
        }
    }
}
