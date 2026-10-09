package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;

@Entity
@Table(name = "schedule_entries")
@Getter
@Setter
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    // A custom movement has no catalogue exercise; services validate exactly one reference.
    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "custom_exercise_id")
    private CustomExercise customExercise;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "order_number", nullable = false)
    private int orderNumber;
}
