package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import jakarta.servlet.http.HttpServletResponse;

import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Controller
@RequestMapping("/exercise-log")
public class ExerciseLogController {

    @Autowired
    private ExerciseLogService service;

    @Autowired
    private ScheduleOccurrenceRepository occurrenceRepo;

    @Autowired
    private CalendarTaskRepository calendarTaskRepository;

    @Autowired
    private PdfService pdfService;

    private final uk.ac.cf._5.group14.One_To_One.Level.LevelService levelService;

    @Autowired
    public ExerciseLogController(uk.ac.cf._5.group14.One_To_One.Level.LevelService levelService) {
        this.levelService = levelService;
    }

    @Autowired
    private AuthHelper authHelper;

    // Show form
    @GetMapping
    public String showForm(Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        ExerciseLogForm emptyExerciseLogForm = new ExerciseLogForm();
        emptyExerciseLogForm.setDate(LocalDate.now());
        emptyExerciseLogForm.setMoodBefore(2);
        emptyExerciseLogForm.setMoodAfter(3);
        emptyExerciseLogForm.setConfidence(3);
        model.addAttribute("exerciseLog", emptyExerciseLogForm);
        model.addAttribute("user", user);
        model.addAttribute("formAction", "/exercise-log");
        model.addAttribute("editing", false);
        addLogOrigin(model, (ExerciseLogForm) model.getAttribute("exerciseLog"), user);
        return "shared-views/exercise-log/exercise-log-form";
    }

    // Save form
    @PostMapping
    public String save(@Valid @ModelAttribute("exerciseLog") ExerciseLogForm form, BindingResult errors, Model model,
                       HttpServletResponse response, org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (!errors.hasErrors()) {
            try { service.saveLog(form, user); }
            catch (IllegalArgumentException invalid) { errors.reject("ui.log.invalid"); }
        }
        if (errors.hasErrors()) {
            model.addAttribute("formAction", "/exercise-log"); model.addAttribute("editing", false);
            addLogOrigin(model, form, user);
            addRejectedLogFields(model, errors);
            response.setStatus(400);
            return "shared-views/exercise-log/exercise-log-form";
        }
        // Award points for logging exercise
        levelService.addPoints(user, 10);
        flash.addFlashAttribute("exerciseLogSaved", true);
        return "redirect:/calendar";
    }

    // List
    @GetMapping("/list")
    public String listLogsForUser(Model model, @ModelAttribute("historyFilter") ExerciseLogHistoryFilter filter,
                                  BindingResult errors, @RequestParam(defaultValue = "1") int page,
                                  HttpServletResponse response) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        String query = filter.getQ() == null ? "" : filter.getQ().strip();
        if (query.length() > 120) query = query.substring(0, 120);
        filter.setQ(query); filter.setSort("oldest".equals(filter.getSort()) ? "oldest" : "newest");
        if (!errors.hasFieldErrors("from") && !errors.hasFieldErrors("until")
                && filter.getFrom() != null && filter.getUntil() != null && filter.getFrom().isAfter(filter.getUntil())) {
            errors.rejectValue("until", "ui.log.dateRangeInvalid");
        }
        var result = errors.hasErrors() ? org.springframework.data.domain.Page.<ExerciseLog>empty()
                : service.searchHistory(user, query, filter.getFrom(), filter.getUntil(), "oldest".equals(filter.getSort()), page <= 1 ? 0 : Math.min(page - 1, 9999));
        if (errors.hasErrors()) response.setStatus(400);
        model.addAttribute("logs", result.getContent()); model.addAttribute("query", query);
        model.addAttribute("logPage", result.getNumber() + 1); model.addAttribute("logPageCount", Math.max(1, result.getTotalPages()));
        model.addAttribute("logTotal", result.getTotalElements()); model.addAttribute("sort", filter.getSort());
        model.addAttribute("logOwnerId", user.getId());
        model.addAttribute("draftFrom", errors.getFieldValue("from")); model.addAttribute("draftUntil", errors.getFieldValue("until"));
        model.addAttribute("historyFromMalformed", errors.getFieldError("from") != null && errors.getFieldError("from").isBindingFailure());
        model.addAttribute("historyUntilMalformed", errors.getFieldError("until") != null && errors.getFieldError("until").isBindingFailure());
        model.addAttribute("hasLogFilters", !query.isBlank() || filter.getFrom() != null || filter.getUntil() != null || errors.hasErrors());
        model.addAttribute("logRangeStart", result.isEmpty() ? 0 : result.getNumber() * 6 + 1);
        model.addAttribute("logRangeEnd", result.getNumber() * 6 + result.getNumberOfElements());
        return "shared-views/exercise-log/exercise-log-list";
    }

    // View single
    @GetMapping("/view/{id}")
    public String viewSingle(@PathVariable Long id, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        ExerciseLog log = service.getLogByIdForUser(id, user);
        if (log == null) {
            return "redirect:/exercise-log/list";
        }
        model.addAttribute("log", log);
        addLogOrigin(model, formForLog(log, user), user);
        return "shared-views/exercise-log/exercise-log-view";
    }

    @GetMapping("/edit/{id}")
    public String editLog(@PathVariable Long id, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        var snapshot = service.getLogSnapshot(id, user).orElse(null);
        if (snapshot == null) return "redirect:/exercise-log/list";
        ExerciseLogForm form = formForLog(snapshot.log(), user);
        form.setExpectedRevision(snapshot.revision());

        model.addAttribute("exerciseLog", form);
        model.addAttribute("formAction", "/exercise-log/edit/" + id);
        model.addAttribute("editing", true);
        addLogOrigin(model, form, user);
        return "shared-views/exercise-log/exercise-log-form";
    }

    @PostMapping("/edit/{id}")
    public String updateLog(@PathVariable Long id, @Valid @ModelAttribute("exerciseLog") ExerciseLogForm form, BindingResult errors,
                            Model model, HttpServletResponse response, org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (service.getLogByIdForUser(id, user) == null) return "redirect:/exercise-log/list";
        if (!errors.hasErrors()) {
            try { service.updateLog(id, form, user); }
            catch (ExerciseLogRevisionConflictException changed) {
                var latest = service.getLogSnapshot(id, user).orElse(null);
                if (latest == null) return "redirect:/exercise-log/list";
                form.setExpectedRevision(latest.revision());
                model.addAttribute("logConflict", true); model.addAttribute("latestLog", latest.log());
                model.addAttribute("formAction", "/exercise-log/edit/" + id); model.addAttribute("editing", true);
                addLogOrigin(model, form, user);
                response.setStatus(409);
                return "shared-views/exercise-log/exercise-log-form";
            }
            catch (IllegalArgumentException invalid) { errors.reject("ui.log.invalid"); }
        }
        if (errors.hasErrors()) {
            model.addAttribute("formAction", "/exercise-log/edit/" + id); model.addAttribute("editing", true);
            addLogOrigin(model, form, user);
            addRejectedLogFields(model, errors);
            response.setStatus(400);
            return "shared-views/exercise-log/exercise-log-form";
        }
        flash.addFlashAttribute("exerciseLogSaved", true);
        return "redirect:/exercise-log/view/" + id;
    }

    @GetMapping("/add-occurrence")
    public String addForOccurrence(@RequestParam Long occId, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        ExerciseLogForm form = new ExerciseLogForm();
        ScheduleOccurrence occ = occurrenceRepo.findByIdAndUserId(occId, user.getId()).orElse(null);
        if (occ == null) return "redirect:/calendar";
        if (occ.getExerciseLog() != null) return "redirect:/exercise-log/edit/" + occ.getExerciseLog().getId();
        form.setOccurrenceId(occId);
        form.setDate(occ.getDate());
        form.setMoodBefore(2); form.setMoodAfter(3); form.setConfidence(3);
        model.addAttribute("exerciseLog", form);
        model.addAttribute("formAction", "/exercise-log");
        model.addAttribute("editing", false);
        addLogOrigin(model, (ExerciseLogForm) model.getAttribute("exerciseLog"), user);
        return "shared-views/exercise-log/exercise-log-form";
    }

    @GetMapping("/add-calendar")
    public String addCalendarExercise(@RequestParam Long taskId, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        ExerciseLogForm form = new ExerciseLogForm();
        CalendarTask task = calendarTaskRepository.findByIdAndUser(taskId, user);
        if (task == null) {
            return "redirect:/calendar";
        }
        if (task.getExerciseLog() != null) return "redirect:/exercise-log/edit/" + task.getExerciseLog().getId();
        form.setCalendarTaskId(taskId);
        form.setDate(task.getDate());
        form.setMoodBefore(2);
        form.setMoodAfter(3);
        form.setConfidence(3);
        model.addAttribute("exerciseLog", form);
        model.addAttribute("formAction", "/exercise-log");
        model.addAttribute("editing", false);
        addLogOrigin(model, (ExerciseLogForm) model.getAttribute("exerciseLog"), user);
        return "shared-views/exercise-log/exercise-log-form";
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ExerciseLog> logs = service.getLogsByUser(user);
        byte[] pdf = pdfService.generateLogsPdf(logs);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=exercise_logs.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private ExerciseLogForm formForLog(ExerciseLog log, User user) {
        var form = new ExerciseLogForm();
        form.setDate(log.getDate()); form.setMoodBefore(log.getMoodBefore()); form.setMoodAfter(log.getMoodAfter());
        form.setConfidence(log.getConfidence()); form.setComments(log.getComments()); form.setDurationMinutes(log.getDurationMinutes());
        if (log.getOccurrence() != null) form.setOccurrenceId(log.getOccurrence().getId());
        if (log.getCalendarTask() != null) form.setCalendarTaskId(log.getCalendarTask().getId());
        if (form.getOccurrenceId() == null) occurrenceRepo.findFirstByExerciseLogIdAndUserId(log.getId(), user.getId())
                .ifPresent(occurrence -> form.setOccurrenceId(occurrence.getId()));
        if (form.getCalendarTaskId() == null) calendarTaskRepository.findFirstByExerciseLogIdAndUserId(log.getId(), user.getId())
                .ifPresent(task -> form.setCalendarTaskId(task.getId()));
        return form;
    }

    private void addRejectedLogFields(Model model, BindingResult errors) {
        model.addAttribute("logDateMalformed", errors.getFieldError("date") != null && errors.getFieldError("date").isBindingFailure());
        model.addAttribute("logDateDraft", errors.getFieldValue("date"));
    }

    private void addLogOrigin(Model model, ExerciseLogForm form, User user) {
        boolean linked = form.getOccurrenceId() != null || form.getCalendarTaskId() != null;
        model.addAttribute("logOriginLinked", linked);
        LocalDate originDate = null;
        String originName = null;
        if (form.getOccurrenceId() != null && form.getCalendarTaskId() == null) {
            var occurrence = occurrenceRepo.findByIdAndUserId(form.getOccurrenceId(), user.getId()).orElse(null);
            if (occurrence != null) {
                originDate = occurrence.getDate();
                if (occurrence.getExercise() != null) originName = occurrence.getExercise().getName();
                else if (occurrence.getCustomExercise() != null && user.getId().equals(occurrence.getCustomExercise().getUserId()))
                    originName = occurrence.getCustomExercise().getName();
            }
        } else if (form.getCalendarTaskId() != null && form.getOccurrenceId() == null) {
            var task = calendarTaskRepository.findByIdAndUser(form.getCalendarTaskId(), user);
            if (task != null) { originDate = task.getDate(); originName = task.getTitle(); }
        }
        model.addAttribute("logOriginUnavailable", linked && originDate == null);
        model.addAttribute("logOriginName", originName); model.addAttribute("logOriginDate", originDate);
        if (originDate != null) model.addAttribute("logOriginUrl", "/calendar/day/" + originDate);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public String deniedLogLink(org.springframework.security.access.AccessDeniedException ex) {
        return "redirect:/access-denied";
    }
}
