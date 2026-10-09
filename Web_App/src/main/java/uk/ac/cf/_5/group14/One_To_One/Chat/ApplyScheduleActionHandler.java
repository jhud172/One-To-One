package uk.ac.cf._5.group14.One_To_One.Chat;

import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleApplicationService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ApplyScheduleActionHandler implements CoachActionHandler<ApplyScheduleActionPayload> {

    private static final int MAX_WEEKS = 12;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleApplicationService scheduleApplicationService;
    private final TrainerClientLinkRepository trainerClientLinkRepository;

    public ApplyScheduleActionHandler(ScheduleRepository scheduleRepository,
                                      ScheduleApplicationService scheduleApplicationService,
                                      TrainerClientLinkRepository trainerClientLinkRepository) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleApplicationService = scheduleApplicationService;
        this.trainerClientLinkRepository = trainerClientLinkRepository;
    }

    @Override
    public CoachActionType type() {
        return CoachActionType.APPLY_SCHEDULE;
    }

    @Override
    public List<String> validate(ApplyScheduleActionPayload payload, User user) {
        List<String> errors = new ArrayList<>();
        if (user == null || user.getId() == null) {
            errors.add("User is required.");
        }
        if (payload == null) {
            errors.add("Missing schedule details.");
            return errors;
        }
        if (payload.scheduleName() == null || payload.scheduleName().isBlank()) {
            errors.add("Schedule name is required.");
        }
        if (payload.startDate() == null) {
            errors.add("Start date is required.");
        }
        if (payload.durationWeeks() < 1 || payload.durationWeeks() > MAX_WEEKS) {
            errors.add("Duration must be between 1 and " + MAX_WEEKS + " weeks.");
        }
        return errors;
    }

    @Override
    public CoachActionExecution execute(ApplyScheduleActionPayload payload, User user) {
        List<String> errors = validate(payload, user);
        if (!errors.isEmpty()) {
            return new CoachActionExecution(false, "", String.join(" ", errors));
        }
        String name = payload.scheduleName().trim();
        Schedule schedule = findAccessibleSchedule(user, name);
        if (schedule == null) {
            return new CoachActionExecution(false, "", "Schedule not found or not accessible.");
        }

        // Let the shared service own its transaction so a rejected plan can return useful feedback.
        try {
            int added = scheduleApplicationService.apply(schedule, user, payload.startDate().toString(),
                    Integer.toString(payload.durationWeeks()));
            if (added == 0) {
                return new CoachActionExecution(true,
                        "Matching movements are already scheduled. Your existing calendar settings are retained.", null);
            }
            String reply = "Added " + added + " movement" + (added == 1 ? "" : "s")
                    + " to your calendar starting " + payload.startDate().format(DATE_FMT) + " for "
                    + payload.durationWeeks() + " week" + (payload.durationWeeks() == 1 ? "" : "s") + ".";
            return new CoachActionExecution(true, reply, null);
        } catch (IllegalArgumentException exception) {
            String error = switch (exception.getMessage() == null ? "" : exception.getMessage()) {
                case "empty" -> "This schedule has no movements in the selected date range. Review it before applying.";
                case "plan" -> "This schedule contains invalid movements. Review it before applying.";
                case "window" -> "The selected date range is invalid.";
                default -> "Schedule not found or not accessible.";
            };
            return new CoachActionExecution(false, "", error);
        }
    }

    private Schedule findAccessibleSchedule(User user, String name) {
        if (user == null || name == null || name.isBlank()) {
            return null;
        }
        Schedule own = scheduleRepository.findByUserAndNameIgnoreCase(user, name).orElse(null);
        if (own != null) {
            return own;
        }
        Optional<TrainerClientLink> activeLink = trainerClientLinkRepository
                .findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(user.getId(), TrainerClientLinkStatus.ACTIVE);
        if (activeLink.isEmpty()) {
            return null;
        }
        Long trainerId = activeLink.get().getTrainerUserId();
        User trainer = new User();
        trainer.setId(trainerId);
        return scheduleRepository.findByUserAndNameIgnoreCase(trainer, name).orElse(null);
    }
}
