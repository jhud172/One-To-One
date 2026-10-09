package uk.ac.cf._5.group14.One_To_One.TrainerAssignments;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleService;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplate;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutTemplateRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TrainerAssignmentServiceImpl implements TrainerAssignmentService {

    private final AssignedWorkoutRepository assignedWorkoutRepository;
    private final AssignedScheduleRepository assignedScheduleRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final ScheduleService scheduleService;
    private final AccessGuard accessGuard;
    private final UserRepository userRepository;

    public TrainerAssignmentServiceImpl(AssignedWorkoutRepository assignedWorkoutRepository,
                                        AssignedScheduleRepository assignedScheduleRepository,
                                        WorkoutTemplateRepository workoutTemplateRepository,
                                        ScheduleService scheduleService,
                                        AccessGuard accessGuard,
                                        UserRepository userRepository) {
        this.assignedWorkoutRepository = assignedWorkoutRepository;
        this.assignedScheduleRepository = assignedScheduleRepository;
        this.workoutTemplateRepository = workoutTemplateRepository;
        this.scheduleService = scheduleService;
        this.accessGuard = accessGuard;
        this.userRepository = userRepository;
    }

    @Override
    public AssignedWorkout assignWorkout(User trainer, Long clientId, Long templateId, String trainerNotes) {
        requireTrainer(trainer);
        requireActiveClient(trainer, clientId);
        String notes = trainerNotes(trainerNotes);
        if (templateId == null) throw new IllegalArgumentException("Choose a workout template");

        WorkoutTemplate template = workoutTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Workout template not found"));
        if (template.getOwnerUser() == null || !template.getOwnerUser().getId().equals(trainer.getId())) {
            throw new AccessDeniedException("Workout template not owned by trainer");
        }

        AssignedWorkout assigned = new AssignedWorkout();
        assigned.setTrainerUserId(trainer.getId());
        assigned.setClientUserId(clientId);
        assigned.setWorkoutTemplate(template);
        assigned.setTrainerNotes(notes);
        return assignedWorkoutRepository.save(assigned);
    }

    @Override
    public AssignedSchedule assignSchedule(User trainer, Long clientId, Long scheduleId, String trainerNotes) {
        requireTrainer(trainer);
        requireActiveClient(trainer, clientId);
        String notes = trainerNotes(trainerNotes);
        if (scheduleId == null) throw new IllegalArgumentException("Choose a schedule");

        Schedule schedule = scheduleService.findById(scheduleId);
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule not found");
        }
        if (schedule.getUser() == null || !schedule.getUser().getId().equals(trainer.getId())) {
            throw new AccessDeniedException("Schedule not owned by trainer");
        }

        AssignedSchedule assigned = new AssignedSchedule();
        assigned.setTrainerUserId(trainer.getId());
        assigned.setClientUserId(clientId);
        assigned.setSchedule(schedule);
        assigned.setTrainerNotes(notes);
        return assignedScheduleRepository.save(assigned);
    }

    @Override
    public List<AssignedWorkout> listWorkoutsForClient(Long clientId) {
        return assignedWorkoutRepository.findByClientUserIdOrderByAssignedAtDesc(clientId);
    }

    @Override
    public List<AssignedSchedule> listSchedulesForClient(Long clientId) {
        return assignedScheduleRepository.findByClientUserIdOrderByAssignedAtDesc(clientId);
    }

    @Override
    public AssignedSchedule getScheduleForClient(Long clientId, Long assignmentId) {
        AssignedSchedule assignment = assignedScheduleRepository.findByIdAndClientUserId(assignmentId, clientId)
                .orElseThrow(() -> new AccessDeniedException("Assignment not found"));
        if (assignment.getSchedule() == null || assignment.getSchedule().getUser() == null
                || !assignment.getTrainerUserId().equals(assignment.getSchedule().getUser().getId())) {
            throw new AccessDeniedException("Invalid assigned schedule");
        }
        return assignment;
    }

    @Override
    public List<AssignedWorkout> listWorkoutsForTrainerClient(Long trainerId, Long clientId) {
        return assignedWorkoutRepository.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainerId, clientId);
    }

    @Override
    public List<AssignedSchedule> listSchedulesForTrainerClient(Long trainerId, Long clientId) {
        return assignedScheduleRepository.findByTrainerUserIdAndClientUserIdOrderByAssignedAtDesc(trainerId, clientId);
    }

    @Override
    public AssignedWorkout updateClientWorkout(Long clientId, Long assignmentId, String clientNotes, String clientFeedback, boolean completed) {
        AssignedWorkout assigned = assignedWorkoutRepository.findByIdAndClientUserId(assignmentId, clientId)
                .orElseThrow(() -> new AccessDeniedException("Assignment not found"));
        String notes = trimToNull(clientNotes);
        String feedback = trimToNull(clientFeedback);
        if ((notes != null && notes.length() > 1200) || (feedback != null && feedback.length() > 1200)) {
            throw new IllegalArgumentException("Client notes and feedback must be at most 1200 characters");
        }
        assigned.setClientNotes(notes);
        assigned.setClientFeedback(feedback);
        assigned.setCompleted(completed);
        if (completed && assigned.getCompletedAt() == null) {
            assigned.setCompletedAt(LocalDateTime.now());
        }
        if (!completed) {
            assigned.setCompletedAt(null);
        }
        return assignedWorkoutRepository.save(assigned);
    }

    @Override
    public TrainerAdherenceStats getClientAdherence(Long trainerId, Long clientId) {
        List<AssignedWorkout> workouts = listWorkoutsForTrainerClient(trainerId, clientId);
        int total = workouts.size();
        int completed = (int) workouts.stream().filter(AssignedWorkout::isCompleted).count();
        double rate = total == 0 ? 0.0 : (double) completed / (double) total;
        return new TrainerAdherenceStats(total, completed, rate);
    }

    private void requireTrainer(User trainer) {
        if (trainer == null || trainer.getRole() != Role.TRAINER
                || !trainer.isEnabled() || !trainer.isTrainerVerified()) {
            throw new AccessDeniedException("Trainer role required");
        }
    }

    private void requireActiveClient(User trainer, Long clientId) {
        if (clientId == null) throw new IllegalArgumentException("Client required");
        User client = userRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        if (client.getRole() != Role.CLIENT || !client.isEnabled()) {
            throw new AccessDeniedException("Enabled client required");
        }
        // Serialise assignment with Pause/End so an inactive relationship cannot receive new work.
        accessGuard.requireTrainerAccessClient(trainer.getId(), clientId);
    }

    private String trainerNotes(String value) {
        String notes = trimToNull(value);
        if (notes != null && notes.length() > 800) {
            throw new IllegalArgumentException("Trainer notes must be at most 800 characters");
        }
        return notes;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
