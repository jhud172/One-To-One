package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInService;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Security.TrainerAccessException;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.Users.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequestMapping("/trainer/templates")
public class TrainerScheduleTemplateController {
    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainerScheduleTemplateService templateService;
    private final TrainerClientLinkService trainerClientLinkService;
    private final ExerciseRepository exerciseRepository;
    private final CustomExerciseRepository customExerciseRepository;
    private final WeeklyCheckInService weeklyCheckInService;
    private final uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestionRepository questionRepository;

    public TrainerScheduleTemplateController(AuthHelper authHelper, UserService userService, UserRepository userRepository,
            TrainerScheduleTemplateService templateService, TrainerClientLinkService trainerClientLinkService,
            ExerciseRepository exerciseRepository, CustomExerciseRepository customExerciseRepository,
            WeeklyCheckInService weeklyCheckInService,
            uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestionRepository questionRepository) {
        this.authHelper = authHelper; this.userService = userService; this.userRepository = userRepository;
        this.templateService = templateService; this.trainerClientLinkService = trainerClientLinkService;
        this.exerciseRepository = exerciseRepository; this.customExerciseRepository = customExerciseRepository;
        this.weeklyCheckInService = weeklyCheckInService;
        this.questionRepository = questionRepository;
    }

    private User currentUserOrThrow() {
        User user = authHelper.getAuthenticatedUser();
        if (user != null) return user;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) user = userService.findByUsername(auth.getName());
        if (user == null) throw new AccessDeniedException("Not authenticated");
        return user;
    }

    private User currentTrainerOrThrow() {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER || !trainer.isEnabled()) throw new AccessDeniedException("Trainer access required");
        if (!trainer.isTrainerVerified()) throw new TrainerAccessException(TrainerAccessException.Reason.TRAINER_NOT_VERIFIED);
        return trainer;
    }

    @ModelAttribute
    public void catalogueContext(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "0") int page, Model model) {
        String query = q.strip();
        if (query.length() > 120) query = query.substring(0, 120);
        model.addAttribute("query", query);
        model.addAttribute("libraryPage", Math.clamp(page, 0, 9999));
    }

    private ModelAndView templateRedirect(String path, Model model) {
        String query = (String) model.getAttribute("query");
        int page = (Integer) model.getAttribute("libraryPage");
        var destination = org.springframework.web.util.UriComponentsBuilder.fromUriString(path);
        if (!query.isEmpty() || page != 0) {
            destination.queryParam("q", "{catalogueQuery}").queryParam("page", page);
            return new ModelAndView("redirect:" + destination.encode().buildAndExpand(Map.of("catalogueQuery", query)).toUriString());
        }
        return new ModelAndView("redirect:" + destination.build().encode().toUriString());
    }

    @GetMapping
    public ModelAndView index(@RequestParam(required = false) String error, @RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "0") int page) {
        User trainer = currentUserOrThrow();
        if (trainer.getRole() != Role.TRAINER || !trainer.isEnabled()) throw new AccessDeniedException("Trainer access required");
        var mav = new ModelAndView("trainer-views/trainer/templates/index");
        String query = q.strip(); if (query.length() > 120) query = query.substring(0, 120);
        mav.addObject("query", query);
        if (!trainer.isTrainerVerified()) {
            mav.addObject("templates", List.of()); mav.addObject("error", "trainer-unverified"); return mav;
        }
        var catalogue = templateService.searchForTrainer(trainer, query, page);
        mav.addObject("query", catalogue.query());
        mav.addObject("templates", catalogue.page().getContent());
        mav.addObject("templatePage", catalogue.page());
        mav.addObject("libraryPage", catalogue.page().getNumber());
        mav.addObject("templateCount", catalogue.ownedCount());
        mav.addObject("entryCounts", catalogue.entryCounts());
        mav.addObject("questionCounts", catalogue.questionCounts());
        mav.addObject("weekdayCounts", catalogue.weekdayCounts());
        mav.addObject("weekdays", weekdays());
        return mav;
    }

    @GetMapping("/create")
    public ModelAndView createForm(Model model) {
        return editor(currentTrainerOrThrow(), new TrainerScheduleTemplate(), model);
    }

    @PostMapping("/create")
    public ModelAndView create(@Valid @ModelAttribute("metadataForm") TrainerScheduleMetadataForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        User trainer = currentTrainerOrThrow();
        if (errors.hasErrors()) return invalid(editor(trainer, new TrainerScheduleTemplate(), model), "metadataForm", form, errors);
        var template = templateService.createTemplate(trainer, form.getName(), form.getDescription(), form.getTags());
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + template.getId() + "/edit", model);
    }

    @GetMapping("/{id}/edit")
    public ModelAndView edit(@PathVariable Long id, Model model) {
        User trainer = currentTrainerOrThrow();
        return editor(trainer, templateService.getForTrainer(trainer, id), model);
    }

    @PostMapping("/{id}/edit")
    public ModelAndView update(@PathVariable Long id, @Valid @ModelAttribute("metadataForm") TrainerScheduleMetadataForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        User trainer = currentTrainerOrThrow();
        var template = templateService.getForTrainer(trainer, id);
        if (errors.hasErrors()) return invalid(editor(trainer, template, model), "metadataForm", form, errors);
        try {
            templateService.saveMetadata(trainer, id, form);
        } catch (TrainerScheduleMetadataConflictException conflict) {
            var latest = templateService.getMetadataSnapshot(trainer, id);
            form.setExpectedRevision(latest.revision());
            var mav = editor(trainer, latest.template(), model);
            mav.addObject("scheduleConflict", true);
            mav.addObject("latestTemplate", latest.template());
            mav.setStatus(HttpStatus.CONFLICT);
            return mav;
        }
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + id + "/edit", model);
    }

    @PostMapping("/{id}/clone")
    public ModelAndView cloneTemplate(@PathVariable Long id, RedirectAttributes flash, Model model) {
        var clone = templateService.cloneTemplate(currentTrainerOrThrow(), id);
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + clone.getId() + "/edit", model);
    }

    @PostMapping("/{id}/entries")
    public ModelAndView addEntry(@PathVariable Long id, @Valid @ModelAttribute("entryForm") TrainerScheduleEntryForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        User trainer = currentTrainerOrThrow();
        var template = templateService.getForTrainer(trainer, id);
        if (!errors.hasErrors()) {
            try {
                var entry = new TrainerScheduleTemplateEntry();
                entry.setDayOfWeek(form.getDayOfWeek()); entry.setType(form.getType()); entry.setTitle(form.getTitle().strip());
                entry.setTimeWindowStart(parseTime(form.getTimeWindowStart())); entry.setTimeWindowEnd(parseTime(form.getTimeWindowEnd()));
                entry.setDefaultsJson(trimToNull(form.getDefaultsJson())); entry.setIntensityLabel(trimToNull(form.getIntensityLabel()));
                entry.setIntensityLevel(form.getIntensityLevel());
                if (form.getExerciseId() != null) entry.setExercise(exerciseRepository.findById(form.getExerciseId())
                        .orElseThrow(() -> new IllegalArgumentException("Exercise unavailable")));
                if (form.getCustomExerciseId() != null) entry.setCustomExercise(customExerciseRepository.findByIdAndUserId(form.getCustomExerciseId(), trainer.getId())
                        .orElseThrow(() -> new AccessDeniedException("Custom exercise unavailable")));
                templateService.addEntry(trainer, id, entry);
                flash.addFlashAttribute("templateSaved", true);
                return templateRedirect("/trainer/templates/" + id + "/edit", model);
            } catch (IllegalArgumentException | java.time.DateTimeException ex) {
                errors.reject("ui.schedule.entryInvalid");
            }
        }
        return invalid(editor(trainer, template, model), "entryForm", form, errors);
    }

    @PostMapping("/{id}/entries/{entryId}/delete")
    public ModelAndView deleteEntry(@PathVariable Long id, @PathVariable Long entryId, RedirectAttributes flash, Model model) {
        templateService.deleteEntry(currentTrainerOrThrow(), id, entryId);
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + id + "/edit", model);
    }

    @PostMapping("/{id}/entries/{entryId}/move")
    public ModelAndView moveEntry(@PathVariable Long id, @PathVariable Long entryId, @RequestParam String direction,
                                  RedirectAttributes flash, Model model) {
        try {
            if (templateService.moveEntry(currentTrainerOrThrow(), id, entryId, direction)) flash.addFlashAttribute("scheduleOrderSaved", true);
        } catch (IllegalArgumentException invalid) { flash.addFlashAttribute("scheduleOrderError", true); }
        return anchoredEditor(id, "schedule-entry-" + entryId, model);
    }

    @PostMapping("/{id}/questions")
    public ModelAndView addQuestion(@PathVariable Long id, @Valid @ModelAttribute("questionForm") TrainerScheduleQuestionForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        User trainer = currentTrainerOrThrow();
        var template = templateService.getForTrainer(trainer, id);
        if (errors.hasErrors()) return invalid(editor(trainer, template, model), "questionForm", form, errors);
        weeklyCheckInService.addQuestion(trainer, id, form.getPrompt(), form.isRequired());
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + id + "/edit", model);
    }

    @PostMapping("/{id}/questions/{questionId}/delete")
    public ModelAndView deleteQuestion(@PathVariable Long id, @PathVariable Long questionId, RedirectAttributes flash, Model model) {
        weeklyCheckInService.deleteQuestion(currentTrainerOrThrow(), id, questionId);
        flash.addFlashAttribute("templateSaved", true);
        return templateRedirect("/trainer/templates/" + id + "/edit", model);
    }

    @PostMapping("/{id}/questions/{questionId}/move")
    public ModelAndView moveQuestion(@PathVariable Long id, @PathVariable Long questionId, @RequestParam String direction,
                                     RedirectAttributes flash, Model model) {
        try {
            if (weeklyCheckInService.moveQuestion(currentTrainerOrThrow(), id, questionId, direction)) flash.addFlashAttribute("scheduleOrderSaved", true);
        } catch (IllegalArgumentException invalid) { flash.addFlashAttribute("scheduleOrderError", true); }
        return anchoredEditor(id, "schedule-question-" + questionId, model);
    }

    private ModelAndView anchoredEditor(Long id, String anchor, Model model) {
        var redirect = templateRedirect("/trainer/templates/" + id + "/edit", model);
        redirect.setViewName(redirect.getViewName() + "#" + anchor);
        return redirect;
    }

    @GetMapping("/{id}/apply")
    public ModelAndView applyForm(@PathVariable Long id, @ModelAttribute("applyForm") TrainerScheduleApplyForm form,
            BindingResult errors, Model model) {
        User trainer = currentTrainerOrThrow();
        var mav = application(trainer, id, form, model);
        boolean requested = form.getClientId() != null || form.getStart() != null || form.getEnd() != null || errors.hasErrors();
        if (requested) validateApplicationFields(form, errors);
        if (requested && !errors.hasErrors() && !((TrainerScheduleTemplate) mav.getModel().get("template")).isArchived()) {
            try { preview(trainer, id, form, mav); }
            catch (IllegalArgumentException | java.time.DateTimeException ex) { errors.reject("ui.schedule.applyInvalid"); }
        }
        return errors.hasErrors() ? invalid(mav, "applyForm", form, errors) : mav;
    }

    @PostMapping("/{id}/apply")
    public ModelAndView applyTemplate(@PathVariable Long id, @Valid @ModelAttribute("applyForm") TrainerScheduleApplyForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        User trainer = currentTrainerOrThrow();
        var mav = application(trainer, id, form, model);
        validateApplicationFields(form, errors);
        if (!errors.hasErrors()) {
            try {
                int created = templateService.applyReviewedTemplate(trainer, id, form.getClientId(), LocalDate.parse(form.getStart()), LocalDate.parse(form.getEnd()), form.isIdempotent(), form.getExpectedApplyRevision());
                flash.addFlashAttribute("appliedCount", created);
                return templateRedirect("/trainer/templates/" + id + "/apply?clientId=" + form.getClientId()
                        + "&start=" + LocalDate.parse(form.getStart()) + "&end=" + LocalDate.parse(form.getEnd()) + "&idempotent=" + form.isIdempotent(), model);
            } catch (TrainerScheduleApplicationConflictException conflict) {
                if (!((TrainerScheduleTemplate) mav.getModel().get("template")).isArchived()) preview(trainer, id, form, mav);
                mav.addObject("applicationConflict", true);
                mav.setStatus(HttpStatus.CONFLICT);
                return mav;
            } catch (IllegalArgumentException | java.time.DateTimeException ex) { errors.reject("ui.schedule.applyInvalid"); }
        }
        return invalid(mav, "applyForm", form, errors);
    }

    private ModelAndView editor(User trainer, TrainerScheduleTemplate template, Model model) {
        var mav = new ModelAndView("trainer-views/trainer/templates/edit"); mav.addAllObjects(model.asMap());
        mav.addObject("template", template); mav.addObject("entries", template.getEntries());
        mav.addObject("questions", weeklyCheckInService.listQuestions(template.getId()));
        mav.addObject("exercises", exerciseRepository.findAll());
        mav.addObject("customExercises", customExerciseRepository.findByUserIdOrderByNameAsc(trainer.getId()));
        mav.addObject("weekdays", weekdays());
        if (!mav.getModel().containsKey("metadataForm")) {
            var snapshot = template.getId() == null ? null : templateService.getMetadataSnapshot(trainer, template.getId());
            if (snapshot != null) { template = snapshot.template(); mav.addObject("template", template); mav.addObject("entries", template.getEntries()); }
            var form = new TrainerScheduleMetadataForm(); form.setName(template.getName()); form.setTags(template.getTags());
            form.setDescription(template.getDescription()); form.setArchived(template.isArchived());
            if (snapshot != null) form.setExpectedRevision(snapshot.revision());
            mav.addObject("metadataForm", form);
        }
        if (!mav.getModel().containsKey("entryForm")) mav.addObject("entryForm", new TrainerScheduleEntryForm());
        if (!mav.getModel().containsKey("questionForm")) mav.addObject("questionForm", new TrainerScheduleQuestionForm());
        return mav;
    }

    private ModelAndView application(User trainer, Long id, TrainerScheduleApplyForm form, Model model) {
        var mav = new ModelAndView("trainer-views/trainer/templates/apply"); mav.addAllObjects(model.asMap());
        mav.addObject("template", templateService.getForTrainer(trainer, id)); mav.addObject("applyForm", form);
        var ids = trainerClientLinkService.getActiveClientsForTrainer(trainer.getId()).stream().map(link -> link.getClientUserId()).toList();
        mav.addObject("activeClients", userRepository.findAllById(ids).stream().filter(client -> client.getRole() == Role.CLIENT && client.isEnabled()).toList());
        return mav;
    }

    private void preview(User trainer, Long id, TrainerScheduleApplyForm form, ModelAndView mav) {
        if (form.getClientId() == null || form.getStart() == null || form.getEnd() == null) throw new IllegalArgumentException("Select a client and date range");
        var snapshot = templateService.previewApplication(trainer, id, form.getClientId(), LocalDate.parse(form.getStart()), LocalDate.parse(form.getEnd()), form.isIdempotent());
        var items = snapshot.items();
        form.setExpectedApplyRevision(snapshot.revision());
        mav.addObject("preview", items);
        mav.addObject("newCount", items.stream().filter(item -> !item.isDuplicate()).count());
        mav.addObject("duplicateCount", items.stream().filter(TrainerScheduleTemplatePreviewItem::isDuplicate).count());
        mav.addObject("selectedClient", userRepository.findById(form.getClientId()).orElseThrow(() -> new AccessDeniedException("Client unavailable")));
    }

    private void validateApplicationFields(TrainerScheduleApplyForm form, BindingResult errors) {
        if (form.getClientId() == null && !errors.hasFieldErrors("clientId")) errors.rejectValue("clientId", "ui.schedule.applyInvalid");
        LocalDate start = applicationDate(form.getStart(), "start", errors);
        LocalDate end = applicationDate(form.getEnd(), "end", errors);
        if (start != null && end != null && (start.isAfter(end)
                || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365 || end.equals(LocalDate.MAX))) {
            errors.rejectValue("end", "ui.schedule.applyInvalid");
        }
    }

    private LocalDate applicationDate(String value, String field, BindingResult errors) {
        if (errors.hasFieldErrors(field)) return null;
        try {
            if (value == null || value.isBlank() || value.length() != 10) throw new IllegalArgumentException("Invalid date");
            return LocalDate.parse(value);
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            errors.rejectValue(field, "ui.schedule.applyInvalid");
            return null;
        }
    }

    private ModelAndView invalid(ModelAndView mav, String name, Object form, BindingResult errors) {
        mav.addObject(name, form); mav.addObject(BindingResult.MODEL_KEY_PREFIX + name, errors); mav.setStatus(HttpStatus.BAD_REQUEST); return mav;
    }

    private Map<Integer, String> weekdays() {
        Map<Integer, String> labels = new LinkedHashMap<>();
        for (var day : java.time.DayOfWeek.values()) {
            labels.put(day.getValue(), day.getDisplayName(java.time.format.TextStyle.FULL,
                    org.springframework.context.i18n.LocaleContextHolder.getLocale()));
        }
        return labels;
    }

    @ExceptionHandler(TrainerAccessException.class)
    public ModelAndView handleTrainerAccess(TrainerAccessException ex) { return new ModelAndView("redirect:/trainer/templates?error=trainer-unverified"); }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleDenied(AccessDeniedException ex) { return new ModelAndView("redirect:/access-denied"); }

    private LocalTime parseTime(String value) { return value == null || value.isBlank() ? null : LocalTime.parse(value); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
