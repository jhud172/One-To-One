package uk.ac.cf._5.group14.One_To_One.Reviews;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "trainer_reviews", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"trainer_user_id", "client_user_id", "link_id"}))
public class TrainerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trainer_user_id", nullable = false)
    private Long trainerId;

    @Column(name = "client_user_id", nullable = false)
    private Long clientId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Min(1)
    @Max(5)
    @Column(name = "stars", nullable = false)
    private Integer stars;

    @Column(name = "tags", length = 500)
    private String tags; // CSV format: "Professional,Responsive,Knowledgeable"

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    public TrainerReview() {
    }

    public TrainerReview(Long trainerId, Long clientId, Long linkId, Integer stars, String tags, String comment) {
        this.trainerId = trainerId;
        this.clientId = clientId;
        this.linkId = linkId;
        this.stars = stars;
        this.tags = tags;
        this.comment = comment;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ReviewStatus.VISIBLE;
        }
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public Long getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(Long trainerId) {
        this.trainerId = trainerId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public String getTags() {
        return tags;
    }

    @Transient
    public List<DisplayTag> getDisplayTags() {
        return tags == null || tags.isBlank() ? List.of() : Arrays.stream(tags.split(","))
                .map(String::trim).filter(tag -> !tag.isEmpty()).distinct()
                .map(tag -> new DisplayTag(tag, TrainerReviewTag.labelKey(tag))).toList();
    }

    public record DisplayTag(String value, String labelKey) { }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }
}
