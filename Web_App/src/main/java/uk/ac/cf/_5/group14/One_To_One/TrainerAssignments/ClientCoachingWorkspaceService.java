package uk.ac.cf._5.group14.One_To_One.TrainerAssignments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Checkins.*;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Workouts.*;

@Service
@Transactional(readOnly = true)
public class ClientCoachingWorkspaceService {
    public record Movement(String name, int sets, int reps, int restSeconds) {}
    public record Workout(Long id, String name, String description, List<Movement> movements) {}
    public record Placement(int day, String name) {}
    public record Programme(Long id, String name, List<Placement> movements) {}
    public record Review(Long id, LocalDate weekStartDate, WeeklyCheckInStatus status) {}
    public record Phase(CoachingPhase phase, String label, String notes, Instant changedAt) {}
    public record Summary(List<Workout> workouts, List<Programme> schedules, List<Review> reviews,
                          Long conversationId, List<Phase> phaseHistory) {}

    private final AccessGuard accessGuard;
    private final WorkoutTemplateRepository workouts;
    private final ScheduleService schedules;
    private final ScheduleEntryService entries;
    private final WeeklyCheckInRepository checkIns;
    private final MessageThreadRepository threads;
    private final TrainerClientLinkService relationships;
    private final CoachingPhaseChangeRepository phases;

    public ClientCoachingWorkspaceService(AccessGuard accessGuard, WorkoutTemplateRepository workouts,
            ScheduleService schedules, ScheduleEntryService entries, WeeklyCheckInRepository checkIns,
            MessageThreadRepository threads, TrainerClientLinkService relationships,
            CoachingPhaseChangeRepository phases) {
        this.accessGuard = accessGuard; this.workouts = workouts; this.schedules = schedules;
        this.entries = entries; this.checkIns = checkIns; this.threads = threads;
        this.relationships = relationships; this.phases = phases;
    }

    public Summary summary(User trainer, Long clientId) {
        if (trainer == null || trainer.getRole() != Role.TRAINER || !trainer.isEnabled() || !trainer.isTrainerVerified()) {
            throw new AccessDeniedException("Verified trainer access required");
        }
        accessGuard.requireTrainerAccessClient(trainer.getId(), clientId);
        var link = relationships.getActiveLinkForTrainerClient(trainer.getId(), clientId);
        var workoutPreviews = workouts.findByOwnerUserOrderByUpdatedAtDesc(trainer).stream().map(workout ->
                new Workout(workout.getId(), workout.getName(), workout.getDescription(), workout.getExercises().stream()
                        .sorted(Comparator.comparingInt(WorkoutExercise::getOrderIndex))
                        .map(movement -> new Movement(movement.getExerciseName(), movement.getSets(), movement.getReps(), movement.getRestSeconds()))
                        .toList())).toList();
        var schedulePreviews = schedules.findByUser(trainer).stream().map(schedule ->
                new Programme(schedule.getId(), schedule.getName(), entries.getEntriesBySchedule(schedule).stream()
                        .sorted(Comparator.comparingInt(ScheduleEntry::getDayOfWeek).thenComparingInt(ScheduleEntry::getOrderNumber))
                        .map(entry -> new Placement(entry.getDayOfWeek(), entry.getCustomExercise() != null
                                ? entry.getCustomExercise().getName() : entry.getExercise().getName())).toList())).toList();
        var reviews = checkIns.findTop5ByTrainerIdAndClientIdOrderByWeekStartDateDescIdDesc(trainer.getId(), clientId)
                .stream().map(review -> new Review(review.getId(), review.getWeekStartDate(), review.getStatus())).toList();
        Long conversationId = link == null ? null : threads.findByLinkId(link.getId())
                .filter(thread -> trainer.getId().equals(thread.getTrainerId()) && clientId.equals(thread.getClientId()))
                .map(thread -> thread.getId()).orElse(null);
        var history = link == null ? List.<Phase>of() : phases.findTop5ByLinkIdOrderByChangedAtDescIdDesc(link.getId())
                .stream().filter(change -> trainer.getId().equals(change.getTrainerId()))
                .map(change -> new Phase(change.getNewPhase(), change.getNewLabel(), change.getNotes(), change.getChangedAt())).toList();
        return new Summary(workoutPreviews, schedulePreviews, reviews, conversationId, history);
    }
}
