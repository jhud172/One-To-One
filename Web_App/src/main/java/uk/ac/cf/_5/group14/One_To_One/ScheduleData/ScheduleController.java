package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Security.CurrentUser;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryService;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Workout.Workout;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/schedules")
public class ScheduleController {

    @Autowired
    private final ScheduleEntryService scheduleEntryService;

    public ScheduleController(ScheduleService scheduleService,
                              ScheduleEntryService scheduleEntryService) {
        this.scheduleService = scheduleService;
        this.scheduleEntryService = scheduleEntryService;
    }

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private ScheduleOccurrenceService scheduleOccurrenceService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDeploymentPlanner deploymentPlanner;

    @Autowired
    private ScheduleAppliedRepository scheduleAppliedRepository;

    @Autowired
    private TrainerClientLinkRepository trainerClientLinkRepository;

    @Autowired
    private uk.ac.cf._5.group14.One_To_One.Security.AccessGuard accessGuard;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleTemplateService scheduleTemplateService;

    @Autowired
    private ScheduleCopyService scheduleCopyService;

    @Autowired
    private ScheduleComposerService scheduleComposerService;

    @Autowired
    private ScheduleStudioService scheduleStudioService;

    @Autowired
    private uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExerciseRepository;

    @Autowired
    private ScheduleApplicationService scheduleApplicationService;

    @GetMapping("")
    public String listSchedules(@CurrentUser User user, Model model) {
        List<Schedule> all = scheduleService.findByUser(user);
        List<ScheduleApplied> active = scheduleAppliedRepository.findByUser(user);
        User trainer = getActiveTrainer(user);
        List<Schedule> shared = trainer != null ? scheduleService.findByUser(trainer) : List.of();

        model.addAttribute("schedules", all);
        model.addAttribute("activeSchedules", active);
        model.addAttribute("sharedSchedules", shared);
        model.addAttribute("currentUserId", user.getId());
        Map<Long, List<ScheduleEntry>> previewEntries = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.concat(all.stream(), shared.stream()).forEach(schedule ->
                previewEntries.put(schedule.getId(), scheduleEntryService.getEntriesBySchedule(schedule).stream()
                        .sorted(java.util.Comparator.comparingInt(ScheduleEntry::getDayOfWeek)
                                .thenComparingInt(ScheduleEntry::getOrderNumber)).toList()));
        Map<Long, String> libraryStatus = new java.util.HashMap<>();
        for (Schedule schedule : all) {
            List<ScheduleApplied> deployments = active.stream()
                    .filter(applied -> applied.getSchedule().getId().equals(schedule.getId())).toList();
            libraryStatus.put(schedule.getId(), deployments.isEmpty() ? "ui.scheduleCentre.notApplied"
                    : deployments.stream().anyMatch(ScheduleApplied::isShownOnCalendar)
                        ? "ui.scheduleCentre.applied" : "ui.scheduleCentre.hidden");
        }
        model.addAttribute("schedulePreviewEntries", previewEntries);
        model.addAttribute("scheduleLibraryStatus", libraryStatus);
        model.addAttribute("shownDeploymentCount", active.stream().filter(ScheduleApplied::isShownOnCalendar).count());
        model.addAttribute("hiddenDeploymentCount", active.stream().filter(applied -> !applied.isShownOnCalendar()).count());
        model.addAttribute("today", LocalDate.now());

        return "trainer-views/schedule/list";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String deleteSchedule(
            @PathVariable Long id,
            @CurrentUser User user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Schedule schedule = scheduleRepository.findOwnedForDeployment(id, user.getId()).orElse(null);
        if (!isOwner(user, schedule)) {
            return "redirect:/schedules?error";
        }
        if (scheduleRepository.hasSavedTrainingReferences(id)) {
            redirectAttributes.addFlashAttribute("scheduleCentreError", "ui.scheduleCentre.deleteBlocked");
            return "redirect:/schedules";
        }
        scheduleEntryRepository.deleteByScheduleId(id);
        scheduleRepository.delete(schedule);
        return "redirect:/schedules?deleted";
    }

    @Transactional
    @PostMapping("/{id}/deactivate")
    public String deactivateSchedule(
            @PathVariable Long id,
            @CurrentUser User user
    ) {
        Schedule schedule = scheduleService.findById(id);
        if (!isOwner(user, schedule)) {
            return "redirect:/schedules?error";
        }
        for (ScheduleApplied applied : scheduleAppliedRepository.findByUserAndSchedule(user, schedule)) {
            applied.setShownOnCalendar(false);
            scheduleAppliedRepository.save(applied);
        }

        return "redirect:/schedules?deactivated";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        return "redirect:/schedules/builder";
    }

    @PostMapping("/create")
    public String createSubmit(
            @RequestParam org.springframework.util.MultiValueMap<String,String> fields,
            @CurrentUser User user
    ) {
        try {
            Schedule schedule=scheduleStudioService.create(fields,user);
            return "redirect:/schedules/"+schedule.getId()+"/entries";
        } catch(IllegalArgumentException exception) { return "redirect:/schedules?error"; }
    }

    @Transactional
    @PostMapping("/applied/{appliedId}/remove")
    public String removeApplied(@PathVariable Long appliedId,
                                @CurrentUser User user) {
        ScheduleApplied applied = scheduleAppliedRepository.findById(appliedId).orElse(null);
        if (applied == null || !applied.getUser().getId().equals(user.getId())) {
            return "redirect:/schedules?error";
        }
        applied.setShownOnCalendar(false);
        scheduleAppliedRepository.save(applied);
        return "redirect:/schedules?hidden";
    }


    @GetMapping("/{id:\\d+}/entries")
    public String entryForm(@PathVariable Long id,
                            @CurrentUser User user,
                            Model model) {

        Schedule schedule = scheduleService.findById(id);
        if (!isOwner(user, schedule)) {
            return "redirect:/schedules?notfound";
        }

        return studioPage(schedule,scheduleStudioService.load(schedule),user,model);
    }

    @PostMapping("/{id}/entries")
    public String entrySubmit(
            @PathVariable Long id,
            @CurrentUser User user,
            @RequestParam Map<String,String> fields
    ) {
        Schedule schedule = scheduleService.findById(id);
        if (!isOwner(user, schedule)) {
            return "redirect:/schedules?error";
        }
        try { scheduleStudioService.appendLegacy(id,fields,user); }
        catch(IllegalArgumentException exception) { return "redirect:/schedules/"+id+"/entries?error"; }
        return "redirect:/schedules/" + id + "/entries";
    }

    @PostMapping("/{id}/entries/save")
    public String saveStudio(@PathVariable Long id,@RequestParam org.springframework.util.MultiValueMap<String,String> fields,
                             @CurrentUser User user,Model model) {
        Schedule schedule=scheduleService.findById(id);
        if(!isOwner(user,schedule)) return "redirect:/schedules?error";
        ScheduleStudioService.Draft draft=null;
        try {
            String command=fields.getFirst("editAction");
            if("confirmReset".equals(command)) return studioPage(schedule,scheduleStudioService.load(schedule),user,model);
            draft=scheduleStudioService.read(fields,schedule);
            if(command==null || command.equals("save")) {
                scheduleStudioService.save(id,draft,user);
                return "redirect:/schedules/"+id+"/entries?saved";
            }
            if(command.equals("clear")) model.addAttribute("studioConfirmClear",true);
            else if(command.equals("reset")) model.addAttribute("studioConfirmReset",true);
            else draft=scheduleStudioService.command(draft,command,fields,user);
            model.addAttribute("studioChanged",true);
        } catch(IllegalArgumentException exception) {
            model.addAttribute("studioError","stale".equals(exception.getMessage()) ? "ui.scheduleDetail.stale" : "ui.scheduleDetail.invalid");
            if(draft==null) draft=scheduleStudioService.load(schedule);
        }
        return studioPage(schedule,draft,user,model);
    }

    private String studioPage(Schedule schedule,ScheduleStudioService.Draft draft,User user,Model model) {
        Map<String,String> movements=new java.util.LinkedHashMap<>();
        exerciseRepository.findAll().stream().sorted(java.util.Comparator.comparing(Exercise::getName)).forEach(movement->
                movements.put("e:"+movement.getId(),movement.getName()));
        customExerciseRepository.findByUserIdOrderByNameAsc(user.getId()).forEach(movement->movements.put("c:"+movement.getId(),movement.getName()));
        List<ScheduleApplied> deployments=scheduleAppliedRepository.findByUserAndSchedule(user,schedule);
        model.addAttribute("schedule",schedule); model.addAttribute("studioDraft",draft); model.addAttribute("studioMovements",movements);
        model.addAttribute("studioStatus",deployments.isEmpty() ? "ui.scheduleCentre.notApplied"
                : deployments.stream().anyMatch(ScheduleApplied::isShownOnCalendar) ? "ui.scheduleCentre.applied" : "ui.scheduleCentre.hidden");
        Map<Integer,Long> counts=draft.movements().stream().collect(java.util.stream.Collectors.groupingBy(ScheduleStudioService.Movement::day,
                java.util.stream.Collectors.counting()));
        model.addAttribute("studioDayCounts",counts);
        java.util.Set<Integer> canMoveUp=new java.util.HashSet<>(),canMoveDown=new java.util.HashSet<>();
        Map<Integer,Integer> previous=new java.util.HashMap<>();
        for(int index=0;index<draft.movements().size();index++) {
            Integer earlier=previous.put(draft.movements().get(index).day(),index);
            if(earlier!=null) { canMoveUp.add(index); canMoveDown.add(earlier); }
        }
        model.addAttribute("studioCanMoveUp",canMoveUp); model.addAttribute("studioCanMoveDown",canMoveDown);
        model.addAttribute("studioDays",java.util.stream.IntStream.rangeClosed(1,Math.min(14,Math.max(1,draft.dayCount()))).boxed().toList());
        return "trainer-views/schedule/add-entry";
    }

    @PostMapping("/{id}/apply")
    public String applyScheduleToCalendar(@PathVariable Long id,
            @RequestParam String startDate, @RequestParam String weeks, @CurrentUser User user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Schedule schedule = findAccessibleSchedule(user, id);
        if (schedule == null) return "redirect:/schedules?error";
        try {
            int added = scheduleApplicationService.apply(schedule,user,startDate,weeks);
            redirectAttributes.addFlashAttribute("scheduleApplicationAdded",added);
            return "redirect:/calendar";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("applyError",true);
            redirectAttributes.addFlashAttribute("applyNotice","empty".equals(exception.getMessage())
                    ? "ui.application.empty" : "plan".equals(exception.getMessage())
                    ? "ui.application.invalidPlan" : "ui.deploy.invalidWindow");
            redirectAttributes.addFlashAttribute("applyStartDate",startDate);
            redirectAttributes.addFlashAttribute("applyWeeks",weeks);
            return "redirect:/schedules/"+id+"/apply";
        }
    }

    @GetMapping("/{id}/apply")
    public String showApplyForm(@PathVariable Long id, @CurrentUser User user, Model model) {
        Schedule schedule = findAccessibleSchedule(user,id);
        if (schedule == null) return "redirect:/schedules?error";
        model.addAttribute("schedule",schedule);
        if (!model.containsAttribute("applyStartDate")) model.addAttribute("applyStartDate",LocalDate.now().toString());
        if (!model.containsAttribute("applyWeeks")) model.addAttribute("applyWeeks","4");
        return "trainer-views/schedule/apply";
    }

    @PostMapping("/{id}/apply/preview")
    public String previewApplication(@PathVariable Long id, @RequestParam String startDate,
            @RequestParam String weeks, @CurrentUser User user, Model model) {
        Schedule schedule = findAccessibleSchedule(user,id);
        if (schedule == null) return "redirect:/schedules?error";
        model.addAttribute("schedule",schedule);
        model.addAttribute("applyStartDate",startDate); model.addAttribute("applyWeeks",weeks);
        try {
            model.addAttribute("applicationPreview",scheduleApplicationService.preview(schedule,user,startDate,weeks));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("applyError",true);
            model.addAttribute("applyNotice","plan".equals(exception.getMessage())
                    ? "ui.application.invalidPlan" : "ui.deploy.invalidWindow");
        }
        return "trainer-views/schedule/apply";
    }

    @Transactional
    @PostMapping("/applied/{appliedId}/settings")
    public String updateAppliedSettings(
            @PathVariable Long appliedId,
            @RequestParam(defaultValue = "false") boolean shownOnCalendar,
            @RequestParam(defaultValue = "false") boolean requiresLogging,
            @CurrentUser User user
    ) {
        ScheduleApplied applied = scheduleAppliedRepository.findById(appliedId).orElse(null);
        if (applied == null || !applied.getUser().getId().equals(user.getId())) {
            return "redirect:/schedules?error";
        }

        applied.setShownOnCalendar(shownOnCalendar);
        applied.setRequiresLogging(requiresLogging);
        if (applied.getDurationWeeks() < 1) {
            applied.setDurationWeeks(4);
        }
        scheduleAppliedRepository.save(applied);

        return "redirect:/schedules?saved";
    }

    @PostMapping("/{id}/duplicate")
    public String duplicateSchedule(@PathVariable Long id, @CurrentUser User user) {
        Schedule original = scheduleService.findById(id);
        if (!isOwner(user, original)) return "redirect:/schedules?error";
        Schedule duplicate = scheduleCopyService.copy(original, user);
        return "redirect:/schedules/" + duplicate.getId() + "/entries";
    }

    @PostMapping("/{id}/update")
    public String updateSchedule(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @CurrentUser User user
    ) {
        Schedule schedule = scheduleService.findById(id);
        if (!isOwner(user, schedule)) {
            return "redirect:/schedules?error";
        }
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank() || trimmedName.length()>200 || (description!=null && description.trim().length()>500)) {
            return "redirect:/schedules/" + id + "/entries?error";
        }
        schedule.setName(trimmedName);
        String cleanedDescription = description != null ? description.trim() : null;
        schedule.setDescription(cleanedDescription == null || cleanedDescription.isBlank() ? null : cleanedDescription);
        scheduleService.save(schedule);
        return "redirect:/schedules/" + id + "/entries?updated";
    }

    @GetMapping("/builder")
    public String builderPage(@CurrentUser User user, Model model) {
        return composerPage(scheduleComposerService.empty(),user,model);
    }

    @PostMapping("/builder/save")
    public String saveBuilder(@RequestParam org.springframework.util.MultiValueMap<String,String> fields,
                              @CurrentUser User user, Model model) {
        ScheduleComposerService.Draft draft=null;
        try {
            draft=scheduleComposerService.read(fields);
            String command=fields.getFirst("editAction");
            if(command==null || command.equals("save")) {
                Schedule saved=scheduleComposerService.save(draft,user);
                return "redirect:/schedules/"+saved.getId()+"/entries";
            }
            draft=scheduleComposerService.command(draft,command,fields,user);
        } catch(IllegalArgumentException exception) {
            model.addAttribute("composerError","ui.composer.invalid");
            if(draft==null) {
                var empty=scheduleComposerService.empty();
                draft=new ScheduleComposerService.Draft(fields.getFirst("name")==null ? "" : fields.getFirst("name"),
                        empty.type(),empty.rotation(),empty.dayCount(),empty.templateId(),empty.placements());
            }
        }
        return composerPage(draft,user,model);
    }

    private String composerPage(ScheduleComposerService.Draft draft,User user,Model model) {
        List<Workout> workouts=workoutRepository.findByUserId(user.getId());
        Map<Long,String> names=new java.util.LinkedHashMap<>();
        Map<Long,List<String>> movements=new java.util.LinkedHashMap<>();
        for(Workout workout:workouts) {
            names.put(workout.getId(),workout.getName());
            List<String> visible=new java.util.ArrayList<>();
            if(workout.getExercises()!=null) workout.getExercises().forEach(movement->visible.add(movement.getName()));
            if(workout.getCustomExercises()!=null) workout.getCustomExercises().stream()
                    .filter(movement->java.util.Objects.equals(movement.getUserId(),user.getId()))
                    .forEach(movement->visible.add(movement.getName()));
            movements.put(workout.getId(),List.copyOf(visible));
        }
        model.addAttribute("workouts",workouts);
        model.addAttribute("workoutNames",names);
        model.addAttribute("workoutMovements",movements);
        model.addAttribute("composerDraft",draft);
        model.addAttribute("templates",scheduleTemplateService.getAllTemplates());
        model.addAttribute("schedule",new Schedule());
        return "trainer-views/schedule/builder";
    }

    private boolean isOwner(User user, Schedule schedule) {
        return schedule != null
                && schedule.getUser() != null
                && user != null
                && schedule.getUser().getId().equals(user.getId());
    }

    private User getActiveTrainer(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return trainerClientLinkRepository
                .findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(user.getId(), TrainerClientLinkStatus.ACTIVE)
                .map(TrainerClientLink::getTrainerUserId)
                .flatMap(userRepository::findById)
                .orElse(null);
    }

    private boolean isTrainerShared(User user, Schedule schedule) {
        if (schedule == null || schedule.getUser() == null || user == null) {
            return false;
        }
        return accessGuard.canClientAccessTrainer(user.getId(), schedule.getUser().getId());
    }

    private Schedule findAccessibleSchedule(User user, Long id) {
        Schedule schedule = scheduleService.findById(id);
        if (schedule == null) {
            return null;
        }
        if (isOwner(user, schedule) || isTrainerShared(user, schedule)) {
            return schedule;
        }
        return null;
    }


}
