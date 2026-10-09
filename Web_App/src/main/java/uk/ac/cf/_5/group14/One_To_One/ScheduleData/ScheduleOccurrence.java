package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLog;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "schedule_occurrences")
@Getter
@Setter
public class ScheduleOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // Custom-only calendar movements have no catalogue exercise.
    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "custom_exercise_id")
    private CustomExercise customExercise;

    @Column(name = "schedule_name", nullable = false, length = 200)
    private String scheduleName;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @OneToOne
    @JoinColumn(name = "exercise_log_id")
    private ExerciseLog exerciseLog;

    @Column(name = "completed")
    private boolean completed = false;

    @Column(nullable = false)
    private boolean missed = false;

    @Column(name = "missed_at")
    private Instant missedAt;

    @Column(name = "trainer_template_id")
    private Long trainerTemplateId;

    @Column(name = "trainer_template_entry_id")
    private Long trainerTemplateEntryId;

    @Transient
    private boolean loggingRequested;

}
