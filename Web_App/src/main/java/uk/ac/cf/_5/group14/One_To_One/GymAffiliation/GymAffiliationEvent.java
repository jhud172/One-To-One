package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity @Table(name = "trainer_gym_affiliation_events") @Getter
public class GymAffiliationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "affiliation_id", nullable = false) private Long affiliationId;
    @Column(name = "actor_user_id") private Long actorUserId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "affiliation_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private TrainerGymAffiliation affiliation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private uk.ac.cf._5.group14.One_To_One.Users.User actor;
    @Column(nullable = false, length = 20) private String action;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected GymAffiliationEvent() {}
    public GymAffiliationEvent(Long affiliationId, Long actorId, String action) {
        this.affiliationId = affiliationId; this.actorUserId = actorId; this.action = action; this.createdAt = Instant.now();
    }
}
