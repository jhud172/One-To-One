package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

/** Bounded owner-only overview; loading the hub never loads resource notes or whole libraries. */
@Service
public class TrainerLibraryOverviewService {
    private static final int PREVIEW_LIMIT = 6;
    private final UserRepository users;
    private final TrainerLibraryExerciseRepository exercises;
    private final TrainerLibraryWorkoutTemplateRepository workouts;
    private final TrainerLibraryProgrammeTemplateRepository programmes;

    public TrainerLibraryOverviewService(UserRepository users, TrainerLibraryExerciseRepository exercises,
                                         TrainerLibraryWorkoutTemplateRepository workouts,
                                         TrainerLibraryProgrammeTemplateRepository programmes) {
        this.users = users; this.exercises = exercises; this.workouts = workouts; this.programmes = programmes;
    }

    @Transactional(readOnly = true)
    public Summary overview(Long trainerId, String rawQuery) {
        if (trainerId == null) throw new AccessDeniedException("Trainer access required");
        var owner = users.findById(trainerId).orElseThrow(() -> new AccessDeniedException("Trainer access required"));
        if (owner.getRole() != Role.TRAINER || !owner.isEnabled() || !owner.isTrainerVerified()) {
            throw new AccessDeniedException("Verified trainer access required");
        }
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() > 120) query = query.substring(0, 120);
        var page = PageRequest.of(0, PREVIEW_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var exercisePage = exercises.findByTrainerIdAndNameContainingIgnoreCase(trainerId, query, page);
        var workoutPage = workouts.findByTrainerIdAndTitleContainingIgnoreCase(trainerId, query, page);
        var programmePage = programmes.findByTrainerIdAndTitleContainingIgnoreCase(trainerId, query, page);
        var items = new ArrayList<Item>();
        exercisePage.forEach(item -> items.add(new Item(item.getId(), item.getName(), "ui.02329", item.getCreatedAt(),
                "/trainer/library/exercises/" + item.getId())));
        workoutPage.forEach(item -> items.add(new Item(item.getId(), item.getTitle(), "ui.00235", item.getCreatedAt(),
                "/trainer/library/workouts/" + item.getId())));
        programmePage.forEach(item -> items.add(new Item(item.getId(), item.getTitle(), "ui.00241", item.getCreatedAt(),
                "/trainer/library/programmes/" + item.getId())));
        var recent = items.stream().sorted(Comparator.comparing(Item::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Item::id, Comparator.reverseOrder()).thenComparing(Item::kindKey))
                .limit(PREVIEW_LIMIT).toList();
        return new Summary(query,
                query.isEmpty() ? exercisePage.getTotalElements() : exercises.countByTrainerId(trainerId),
                query.isEmpty() ? workoutPage.getTotalElements() : workouts.countByTrainerId(trainerId),
                query.isEmpty() ? programmePage.getTotalElements() : programmes.countByTrainerId(trainerId),
                exercisePage.getTotalElements(), workoutPage.getTotalElements(), programmePage.getTotalElements(), recent);
    }

    public record Item(Long id, String name, String kindKey, Instant createdAt, String href) { }
    public record Summary(String query, long exerciseCount, long workoutCount, long programmeCount,
                          long exerciseMatches, long workoutMatches, long programmeMatches, List<Item> items) {
        public long totalMatches() { return exerciseMatches + workoutMatches + programmeMatches; }
        public long totalCount() { return exerciseCount + workoutCount + programmeCount; }
    }
}
