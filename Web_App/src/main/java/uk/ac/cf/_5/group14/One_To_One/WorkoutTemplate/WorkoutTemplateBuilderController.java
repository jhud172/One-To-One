package uk.ac.cf._5.group14.One_To_One.WorkoutTemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.*;

@Controller
@RequestMapping("/workout-templates")
@RequiredArgsConstructor
public class WorkoutTemplateBuilderController {
    private final WorkoutTemplateService workoutTemplateService;
    private final UserSettingsService userSettingsService;
    private final AuthHelper authHelper;
    private final WorkoutDisplayConfig config;

    @GetMapping("")
    public String listPage(Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        List<WorkoutTemplate> own = workoutTemplateService.findByUser(user);
        List<WorkoutTemplate> builtIn = workoutTemplateService.findGlobalTemplates();
        Map<Long, WorkoutDisplayConfig.Settings> previews = new LinkedHashMap<>();
        own.forEach(t -> previews.put(t.getId(), config.forDisplay(t)));
        builtIn.forEach(t -> previews.put(t.getId(), config.forDisplay(t)));
        model.addAttribute("userTemplates", own); model.addAttribute("globalTemplates", builtIn);
        model.addAttribute("preferredTemplate", workoutTemplateService.getDefaultTemplateForUser(user.getId()));
        model.addAttribute("templatePreviews", previews);
        return "trainer-views/workout-templates/index";
    }

    @GetMapping("/builder")
    public String builderNew(@RequestParam(required = false) Long copy, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        WorkoutTemplate draft = new WorkoutTemplate();
        if (copy != null) {
            WorkoutTemplate original = visible(copy, user);
            draft.setName(original.getName()); draft.setLayoutType(original.getLayoutType()); draft.setConfigJson(original.getConfigJson());
        }
        return builder(draft, config.forDisplay(draft), model);
    }

    @GetMapping("/builder/{id}")
    public String builderEdit(@PathVariable Long id, Model model) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        WorkoutTemplate draft = owned(id, user);
        return builder(draft, config.forDisplay(draft), model);
    }

    @PostMapping("")
    public String create(@RequestParam MultiValueMap<String,String> values, Model model, RedirectAttributes redirect) {
        return save(null, values, model, redirect);
    }
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam MultiValueMap<String,String> values, Model model, RedirectAttributes redirect) {
        return save(id, values, model, redirect);
    }

    private String save(Long id, MultiValueMap<String,String> values, Model model, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        WorkoutTemplate existing = id == null ? null : owned(id, user);
        WorkoutTemplate draft = new WorkoutTemplate(); draft.setId(id); draft.setUser(user); draft.setName(value(values,"name",""));
        WorkoutDisplayConfig.Settings settings = config.defaults(TemplateLayoutType.FLOW);
        try {
            TemplateLayoutType layout = TemplateLayoutType.valueOf(value(values,"layoutType","FLOW").toUpperCase(Locale.ROOT));
            draft.setLayoutType(layout);
            settings = values.containsKey("displayForm")
                    ? config.validate(layout, value(values,"theme","default"), value(values,"transition","none"),
                    value(values,"density","comfortable"), values.containsKey("progress"), values.containsKey("restTimer"),
                    values.getOrDefault("components", List.of()))
                    : config.parse(layout, values.getFirst("configJson"));
            String command = value(values,"editAction","save");
            if (!"save".equals(command)) {
                settings = command(settings, command); model.addAttribute("draftChanged", true);
                return builder(draft, settings, model);
            }
            String name = draft.getName().trim();
            if (name.isEmpty() || name.length() > 200) throw new IllegalArgumentException("Invalid name");
            // Complete validation before mutating an existing managed entity.
            String source = config.serialise(settings);
            WorkoutTemplate target = existing == null ? new WorkoutTemplate() : existing;
            target.setUser(user); target.setName(name); target.setLayoutType(layout); target.setConfigJson(source);
            if (existing == null) workoutTemplateService.create(target); else workoutTemplateService.update(target);
            redirect.addFlashAttribute("displayFeedback", "ui.display.saved");
            return "redirect:/workout-templates";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("displayError", "ui.display.invalid"); model.addAttribute("draftChanged", true);
            model.addAttribute("invalidConfigJson", values.getFirst("configJson"));
            return builder(draft, settings, model);
        }
    }

    private WorkoutDisplayConfig.Settings command(WorkoutDisplayConfig.Settings settings, String command) {
        List<String> items = new ArrayList<>(settings.components()); String[] parts = command.split(":", -1);
        if (parts.length != 2 || !WorkoutDisplayConfig.COMPONENTS.contains(parts[1])) throw new IllegalArgumentException("Unknown component");
        int index = items.indexOf(parts[1]);
        switch (parts[0]) {
            case "add" -> { if (index < 0) items.add(parts[1]); }
            case "remove" -> items.remove(parts[1]);
            case "up", "down" -> {
                if (index < 0) throw new IllegalArgumentException("Missing component");
                int next = index + ("up".equals(parts[0]) ? -1 : 1);
                if (next >= 0 && next < items.size()) Collections.swap(items,index,next);
            }
            default -> throw new IllegalArgumentException("Unknown command");
        }
        return config.validate(TemplateLayoutType.valueOf(settings.layout().toUpperCase(Locale.ROOT)), settings.theme(),
                settings.transition(), settings.density(), settings.progress(), settings.restTimer(), items);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        owned(id, user);
        try { workoutTemplateService.delete(id); redirect.addFlashAttribute("displayFeedback","ui.scheduleWorkout.deleted"); }
        catch (DataIntegrityViolationException ex) { redirect.addFlashAttribute("displayError","ui.display.deleteBlocked"); }
        return "redirect:/workout-templates";
    }

    @PostMapping("/{id}/set-preferred")
    @ResponseBody
    public ResponseEntity<?> setPreferred(@PathVariable Long id) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();
        visible(id, user); userSettingsService.updatePreferredWorkoutTemplate(user,id);
        return ResponseEntity.ok(Map.of("preferredTemplateId", id));
    }

    @PostMapping("/{id}/prefer")
    public String prefer(@PathVariable Long id, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        visible(id,user); userSettingsService.updatePreferredWorkoutTemplate(user,id);
        redirect.addFlashAttribute("displayFeedback","ui.display.preferred");
        return "redirect:/workout-templates";
    }

    private WorkoutTemplate visible(Long id, User user) {
        WorkoutTemplate template = workoutTemplateService.findById(id).orElseThrow(this::missing);
        if (template.getUser() != null && !user.getId().equals(template.getUser().getId())) throw missing();
        return template;
    }
    private WorkoutTemplate owned(Long id, User user) {
        WorkoutTemplate template = visible(id,user); if (template.getUser() == null) throw missing(); return template;
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private String value(MultiValueMap<String,String> values, String key, String fallback) {
        String value = values.getFirst(key); return value == null ? fallback : value;
    }
    private String builder(WorkoutTemplate draft, WorkoutDisplayConfig.Settings settings, Model model) {
        if (draft.getConfigJson() != null) {
            try { config.parse(draft.getLayoutType(),draft.getConfigJson()); }
            catch (IllegalArgumentException ex) { model.addAttribute("displayError","ui.display.invalid"); }
        }
        model.addAttribute("editTemplate",draft); model.addAttribute("isEdit",draft.getId()!=null);
        model.addAttribute("displayConfig",settings); model.addAttribute("displayComponents",WorkoutDisplayConfig.COMPONENTS);
        model.addAttribute("componentLabels",Map.of("progress","ui.01656","timer","ui.02820","exerciseCard","ui.02821", "notes","ui.00175","setEntry","ui.02822","summary","ui.02189","restTimer","ui.02823"));
        return "trainer-views/workout-templates/builder";
    }
}
