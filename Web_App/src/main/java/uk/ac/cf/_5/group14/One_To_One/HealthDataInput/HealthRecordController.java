package uk.ac.cf._5.group14.One_To_One.HealthDataInput;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import jakarta.validation.Valid;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.annotation.PathVariable;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.PhysicalCondition.PhysicalCondition;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.PhysicalCondition.PhysicalConditionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

@Controller
public class HealthRecordController {

    private final HealthRecordService healthRecordService;
    private final PhysicalConditionService physicalConditionService;

    @Autowired
    private AuthHelper authHelper;

    public HealthRecordController(HealthRecordService healthRecordService, PhysicalConditionService physicalConditionService) {
        this.healthRecordService = healthRecordService;
        this.physicalConditionService = physicalConditionService;
    }

    @org.springframework.web.bind.annotation.InitBinder("healthRecordForm")
    public void bindMeasurements(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("baselineDate", "systolicBloodPressure", "diastolicBloodPressure", "cholesterol", "weightKg", "heightCm", "waistCm", "activityLevel", "physicalConditions", "physicalConditions[*]");
    }

    @org.springframework.web.bind.annotation.ModelAttribute("healthActivityLabels")
    public java.util.Map<String, String> activityLabels() {
        return java.util.Map.of("Sedentary", "ui.00651", "Lightly Active", "ui.00652", "Moderately Active", "ui.00653", "Very Active", "ui.00654");
    }

    @GetMapping("/health-record")
    public ModelAndView getHealthDataForm() {
        ModelAndView modelAndView = new ModelAndView("client-views/health-record/health-record-form");
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");

        HealthRecordForm emptyHealthRecordForm = healthRecordService.createHealthRecordForm(user);
        List<PhysicalCondition> allPhysicalConditions = physicalConditionService.getAllPhysicalConditions();

        modelAndView.addObject("healthRecordForm", emptyHealthRecordForm);
        modelAndView.addObject("allPhysicalConditions", allPhysicalConditions);
        return modelAndView;
    }

    @PostMapping("/health-record")
    public ModelAndView postHealthDataForm(
            @Valid @ModelAttribute("healthRecordForm") HealthRecordForm healthRecordForm,
            BindingResult bindingResult,
            Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {

        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");
        if (!bindingResult.hasErrors()) {
            try { healthRecordService.addHealthRecord(healthRecordForm, user); }
            catch (InvalidHealthConditionException invalid) { bindingResult.rejectValue("physicalConditions", "ui.health.invalid"); }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("allPhysicalConditions", physicalConditionService.getAllPhysicalConditions());
            model.addAttribute("healthDateMalformed", bindingResult.getFieldError("baselineDate") != null && bindingResult.getFieldError("baselineDate").isBindingFailure());
            model.addAttribute("healthDateDraft", bindingResult.getFieldValue("baselineDate"));
            model.addAttribute("healthRecordForm", healthRecordForm);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "healthRecordForm", bindingResult);
            ModelAndView invalid = new ModelAndView("client-views/health-record/health-record-form", model.asMap());
            invalid.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return invalid;
        }

        flash.addFlashAttribute("healthRecordSaved", true);
        return new ModelAndView("redirect:/health-record/list?success");
    }


    @GetMapping("/health-record/list")
    public ModelAndView listHealthRecords(@ModelAttribute("healthHistoryFilter") HealthRecordHistoryFilter filter,
            BindingResult errors, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");
        String query = filter.getQ() == null ? "" : filter.getQ().strip();
        if (query.length() > 120) query = query.substring(0, 120);
        filter.setQ(query); filter.setSort("oldest".equals(filter.getSort()) ? "oldest" : "newest");
        if (filter.getActivity() == null) filter.setActivity("");
        if (!filter.getActivity().isEmpty() && !activityLabels().containsKey(filter.getActivity())) errors.rejectValue("activity", "ui.health.invalid");
        if (!errors.hasFieldErrors("from") && !errors.hasFieldErrors("until") && filter.getFrom() != null
                && filter.getUntil() != null && filter.getFrom().isAfter(filter.getUntil())) errors.rejectValue("until", "ui.log.dateRangeInvalid");
        var result = errors.hasErrors() ? org.springframework.data.domain.Page.<HealthRecord>empty()
                : healthRecordService.searchHistory(user, query, filter.getFrom(), filter.getUntil(), filter.getActivity(),
                    "oldest".equals(filter.getSort()), page <= 1 ? 0 : Math.min(page - 1, 9999));
        ModelAndView mav = new ModelAndView("client-views/health-record/health-record-list");
        mav.addObject("healthHistoryFilter", filter); mav.addObject(BindingResult.MODEL_KEY_PREFIX + "healthHistoryFilter", errors);
        mav.addObject("records", result.getContent()); mav.addObject("query", query);
        mav.addObject("healthPage", result.getNumber() + 1); mav.addObject("healthPageCount", Math.max(1, result.getTotalPages()));
        mav.addObject("healthTotal", result.getTotalElements());
        mav.addObject("healthRangeStart", result.getTotalElements() == 0 ? 0L : (long) result.getNumber() * 6 + 1);
        mav.addObject("healthRangeEnd", (long) result.getNumber() * 6 + result.getNumberOfElements());
        mav.addObject("healthFromMalformed", errors.getFieldError("from") != null && errors.getFieldError("from").isBindingFailure());
        mav.addObject("healthUntilMalformed", errors.getFieldError("until") != null && errors.getFieldError("until").isBindingFailure());
        mav.addObject("healthDraftFrom", errors.getFieldValue("from")); mav.addObject("healthDraftUntil", errors.getFieldValue("until"));
        mav.addObject("healthFiltered", !query.isEmpty() || filter.getFrom() != null || filter.getUntil() != null || !filter.getActivity().isEmpty());
        if (errors.hasErrors()) mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        return mav;
    }

    @GetMapping("/health-record/{id}")
    public ModelAndView viewHealthRecord(@PathVariable Long id) {
        ModelAndView mav = new ModelAndView("client-views/health-record/health-record-view");
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");
        mav.addObject("record", healthRecordService.getHealthRecordByIdForUser(id, user));
        return mav;
    }





}
