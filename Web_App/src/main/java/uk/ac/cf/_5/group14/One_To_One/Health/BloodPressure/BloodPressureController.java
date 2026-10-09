package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Controller
@RequestMapping("/health/blood-pressure")
public class BloodPressureController {
    private final AuthHelper authHelper;
    private final BloodPressureService service;
    public BloodPressureController(AuthHelper authHelper, BloodPressureService service) { this.authHelper=authHelper; this.service=service; }

    @InitBinder({"quickAdd", "reading"})
    public void bindReading(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("readingDate", "readingTime", "systolic", "diastolic", "pulse", "arm", "position", "notes");
    }
    @ModelAttribute("bpArmLabels") public Map<String,String> armLabels() { return Map.of("LEFT","ui.bp.left","RIGHT","ui.bp.right"); }
    @ModelAttribute("bpPositionLabels") public Map<String,String> positionLabels() { return Map.of("SITTING","ui.bp.sitting","STANDING","ui.bp.standing","LYING","ui.bp.lying"); }

    @GetMapping
    public String hub(@RequestParam(defaultValue="30") int range, @RequestParam(defaultValue="1") int page, Model model, HttpSession session) {
        User user=authHelper.getAuthenticatedUser(session); if(user==null) return "redirect:/login";
        var reading=new BloodPressureReading(); reading.setReadingDate(LocalDate.now()); reading.setReadingTime(LocalTime.now().withSecond(0).withNano(0));
        model.addAttribute("quickAdd",reading); populateModel(model,user,range,page); return "client-views/health/blood-pressure";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("quickAdd") BloodPressureReading reading, BindingResult errors,
            @RequestParam(defaultValue="30") int range, @RequestParam(defaultValue="1") int page,
            Model model, HttpSession session, RedirectAttributes flash, HttpServletResponse response) {
        User user=authHelper.getAuthenticatedUser(session); if(user==null) return "redirect:/login";
        reading.setUser(user); reading.setSource(BloodPressureReading.ReadingSource.MANUAL);
        if(!errors.hasErrors()) {
            try { service.save(reading); flash.addFlashAttribute("bpSaved",true); return listRedirect(range,1); }
            catch(IllegalStateException | org.springframework.dao.DataIntegrityViolationException duplicate) {
                errors.reject("ui.bp.duplicate"); errors.rejectValue("readingTime","ui.bp.duplicate");
            }
        }
        populateModel(model,user,range,page); rejectedDraft(model,errors); response.setStatus(400); return "client-views/health/blood-pressure";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, @RequestParam(defaultValue="30") int range,
            @RequestParam(defaultValue="1") int page, Model model, HttpSession session) {
        User user=authHelper.getAuthenticatedUser(session); if(user==null) return "redirect:/login";
        var reading=owned(id,user); model.addAttribute("reading",reading); model.addAttribute("bpRevision",service.revision(reading));
        editContext(model,range,page); return "client-views/health/blood-pressure-edit";
    }

    @PostMapping("/edit/{id}")
    public String editSave(@PathVariable Long id, @Valid @ModelAttribute("reading") BloodPressureReading updated, BindingResult errors,
            @RequestParam(defaultValue="30") int range, @RequestParam(defaultValue="1") int page,
            @RequestParam(required=false) String expectedRevision, Model model, HttpSession session,
            RedirectAttributes flash, HttpServletResponse response) {
        User user=authHelper.getAuthenticatedUser(session); if(user==null) return "redirect:/login";
        owned(id,user); updated.setId(id); int status=400;
        if(!errors.hasErrors()) {
            try { service.update(id,updated,user,expectedRevision); flash.addFlashAttribute("bpSaved",true); return listRedirect(range,page); }
            catch(StaleBloodPressureReadingException changed) {
                var latest=owned(id,user); model.addAttribute("bpLatest",latest); model.addAttribute("bpRevision",service.revision(latest));
                model.addAttribute("bpConflict",true); errors.reject("ui.bp.conflict"); status=409;
            } catch(IllegalStateException | org.springframework.dao.DataIntegrityViolationException duplicate) {
                errors.reject("ui.bp.duplicate"); errors.rejectValue("readingTime","ui.bp.duplicate");
            } catch(IllegalArgumentException unavailable) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            }
        }
        if(!model.containsAttribute("bpRevision")) model.addAttribute("bpRevision",expectedRevision);
        editContext(model,range,page); rejectedDraft(model,errors); response.setStatus(status); return "client-views/health/blood-pressure-edit";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, @RequestParam(defaultValue="30") int range,
            @RequestParam(defaultValue="1") int page, @RequestParam(required=false) String expectedRevision,
            HttpSession session, RedirectAttributes flash) {
        User user=authHelper.getAuthenticatedUser(session); if(user==null) return "redirect:/login";
        owned(id,user);
        try { service.delete(id,user,expectedRevision); flash.addFlashAttribute("bpDeleted",true); }
        catch(StaleBloodPressureReadingException | IllegalArgumentException changed) { flash.addFlashAttribute("bpChanged",true); }
        return listRedirect(range,page);
    }

    private BloodPressureReading owned(Long id,User user) {
        return service.findById(id).filter(reading->reading.getUser().getId().equals(user.getId()))
                .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private void populateModel(Model model,User user,int range,int page) {
        range=normalisedRange(range); LocalDate today=LocalDate.now(); LocalDate from=today.minusDays(range-1L);
        var readings=service.getRange(user,from,today); var history=service.history(user,page<=1?0:Math.min(page-1,9999));
        var revisions=new java.util.HashMap<Long,String>(); for(var reading:history) revisions.put(reading.getId(),service.revision(reading));
        model.addAttribute("recent",history.getContent()); model.addAttribute("rangeReadings",readings);
        model.addAttribute("stats",service.computeStats(readings)); model.addAttribute("streak",service.computeStreak(user));
        model.addAttribute("range",range); model.addAttribute("bpFrom",from); model.addAttribute("today",today);
        model.addAttribute("bpPage",history.getNumber()+1); model.addAttribute("bpPageCount",Math.max(1,history.getTotalPages()));
        model.addAttribute("bpTotal",history.getTotalElements()); model.addAttribute("bpStart",history.getTotalElements()==0?0L:(long)history.getNumber()*6+1);
        model.addAttribute("bpEnd",(long)history.getNumber()*6+history.getNumberOfElements()); model.addAttribute("bpRevisions",revisions);
        choices(model);
    }
    private void editContext(Model model,int range,int page) {
        model.addAttribute("range",normalisedRange(range)); model.addAttribute("bpPage",Math.max(1,Math.min(page,10000))); choices(model);
    }
    private void choices(Model model) { model.addAttribute("arms",BloodPressureReading.Arm.values()); model.addAttribute("positions",BloodPressureReading.Position.values()); }
    private void rejectedDraft(Model model,BindingResult errors) {
        for(String field:new String[]{"readingDate","readingTime","arm","position"}) {
            var error=errors.getFieldError(field); model.addAttribute("bpMalformed"+field,error!=null && error.isBindingFailure());
            model.addAttribute("bpDraft"+field,errors.getFieldValue(field));
        }
    }
    private int normalisedRange(int range) { return range<1?30:Math.min(range,366); }
    private String listRedirect(int range,int page) { return "redirect:/health/blood-pressure?range="+normalisedRange(range)+"&page="+Math.max(1,Math.min(page,10000)); }
}
