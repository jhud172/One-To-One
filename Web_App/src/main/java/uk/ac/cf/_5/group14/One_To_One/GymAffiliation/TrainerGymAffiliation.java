package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "trainer_gym_affiliations", uniqueConstraints = @UniqueConstraint(columnNames = {"trainer_user_id", "gym_id"}))
@Getter @Setter
public class TrainerGymAffiliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "trainer_user_id", nullable = false) private Long trainerUserId;
    @Column(name = "gym_id", nullable = false) private Long gymId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trainer_user_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private uk.ac.cf._5.group14.One_To_One.Users.User trainer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "gym_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile gym;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private GymAffiliationStatus status;
    @Column(name = "initiated_by", nullable = false, length = 20) private String initiatedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public TrainerGymAffiliation() {}
    public TrainerGymAffiliation(Long trainerId, Long gymId, GymAffiliationStatus status, String initiatedBy) {
        this.trainerUserId = trainerId; this.gymId = gymId; this.status = status; this.initiatedBy = initiatedBy;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
}
