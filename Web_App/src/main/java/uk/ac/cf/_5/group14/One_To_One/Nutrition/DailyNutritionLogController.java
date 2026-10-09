package uk.ac.cf._5.group14.One_To_One.Nutrition;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogService.DailyNutritionSummary;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogService.UpsertRequest;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;

@Controller
@RequestMapping("/nutrition")
public class DailyNutritionLogController {

    private final DailyNutritionLogService service;
    private final AuthHelper authHelper;
    private final UserSettingsService userSettingsService;

    public DailyNutritionLogController(DailyNutritionLogService service,
                                       AuthHelper authHelper,
                                       UserSettingsService userSettingsService) {
        this.service = service;
        this.authHelper = authHelper;
        this.userSettingsService = userSettingsService;
    }

    @InitBinder("nutritionForm")
    void editableFields(WebDataBinder binder) {
        binder.setAllowedFields("date", "calories", "proteinGrams", "carbsGrams", "fatGrams", "fibreGrams", "waterMl", "notes");
    }

    @InitBinder("nutritionDateFilter")
    void dateFields(WebDataBinder binder) {
        binder.setAllowedFields("date");
    }

    @GetMapping
    public String view(@ModelAttribute("nutritionDateFilter") NutritionDateFilter filter,
                       BindingResult dates, Model model, HttpServletResponse response) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        if (dates.hasErrors()) {
            model.addAttribute("nutritionPickerInvalid", true);
            model.addAttribute("nutritionPickerDraft", dates.getFieldValue("date"));
            model.addAttribute("hasEntry", false);
            addDateAndTargets(model, user, null);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "shared-views/nutrition/daily-log";
        }
        LocalDate targetDate = filter.getDate() == null ? LocalDate.now() : filter.getDate();
        filter.setDate(targetDate);
        DailyNutritionLog log = service.getOrCreateForDate(user, targetDate);
        var form = new DailyNutritionLogForm();
        form.setDate(targetDate);
        if (log.getId() != null) {
            form.setCalories(log.getCalories()); form.setProteinGrams(log.getProteinGrams());
            form.setCarbsGrams(log.getCarbsGrams()); form.setFatGrams(log.getFatGrams());
            form.setFibreGrams(log.getFibreGrams()); form.setWaterMl(log.getWaterMl()); form.setNotes(log.getNotes());
        }
        model.addAttribute("nutritionForm", form);
        addEntry(model, log);
        addDateAndTargets(model, user, targetDate);
        return "shared-views/nutrition/daily-log";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("nutritionForm") DailyNutritionLogForm form,
                       BindingResult errors, @RequestParam(required = false) String expectedRevision,
                       Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        if (errors.hasErrors()) {
            if (errors.hasFieldErrors("date")) model.addAttribute("nutritionDateDraft", errors.getFieldValue("date"));
            model.addAttribute("nutritionRejected", true);
            // Keep the original revision on a rejected draft; a later valid save still checks intervening changes.
            model.addAttribute("nutritionRevision", expectedRevision);
            return retainedDraft(user, form, model, response, HttpServletResponse.SC_BAD_REQUEST, false);
        }
        try {
            service.upsert(user, form.getDate(), new UpsertRequest(form.getCalories(), form.getProteinGrams(),
                    form.getCarbsGrams(), form.getFatGrams(), form.getFibreGrams(), form.getWaterMl(), form.getNotes()), expectedRevision);
        } catch (StaleNutritionLogException changed) {
            model.addAttribute("nutritionConflict", true);
            model.addAttribute("nutritionRejected", true);
            return retainedDraft(user, form, model, response, HttpServletResponse.SC_CONFLICT, true);
        }
        redirect.addFlashAttribute("nutritionSaved", true);
        return "redirect:/nutrition?date=" + form.getDate();
    }

    private String retainedDraft(User user, DailyNutritionLogForm form, Model model,
                                 HttpServletResponse response, int status, boolean refreshRevision) {
        var picker = new NutritionDateFilter(); picker.setDate(form.getDate());
        model.addAttribute("nutritionDateFilter", picker);
        if (form.getDate() != null) {
            DailyNutritionLog log = service.getOrCreateForDate(user, form.getDate());
            model.addAttribute("summary", log.getId() == null ? null : service.summarize(log));
            model.addAttribute("hasEntry", log.getId() != null);
            if (refreshRevision) model.addAttribute("nutritionRevision", service.revision(log));
        } else model.addAttribute("hasEntry", false);
        addDateAndTargets(model, user, form.getDate());
        response.setStatus(status);
        return "shared-views/nutrition/daily-log";
    }

    private void addEntry(Model model, DailyNutritionLog log) {
        model.addAttribute("summary", log.getId() == null ? null : service.summarize(log));
        model.addAttribute("hasEntry", log.getId() != null);
        model.addAttribute("nutritionRevision", service.revision(log));
    }

    private void addDateAndTargets(Model model, User user, LocalDate date) {
        model.addAttribute("selectedDate", date);
        model.addAttribute("previousDate", date == null || date.equals(LocalDate.MIN) ? null : date.minusDays(1));
        model.addAttribute("nextDate", date == null || date.equals(LocalDate.MAX) ? null : date.plusDays(1));
        UserSettings settings = userSettingsService.getOrCreate(user);
        model.addAttribute("nutritionTargets", settings == null ? null : new NutritionTargets(
                settings.getMacroTargetCalories(), settings.getMacroTargetProtein(),
                settings.getMacroTargetCarbs(), settings.getMacroTargetFat()));
    }

    public record NutritionTargets(Integer calories, Integer protein, Integer carbs, Integer fat) {}
}
