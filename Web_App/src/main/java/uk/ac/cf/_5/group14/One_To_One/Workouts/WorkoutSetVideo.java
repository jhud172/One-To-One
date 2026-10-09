package uk.ac.cf._5.group14.One_To_One.Workouts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workout_set_videos")
@Getter
@Setter
public class WorkoutSetVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "set_log_id")
    private WorkoutSetLog setLog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoProcessingStatus status = VideoProcessingStatus.STORED;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
