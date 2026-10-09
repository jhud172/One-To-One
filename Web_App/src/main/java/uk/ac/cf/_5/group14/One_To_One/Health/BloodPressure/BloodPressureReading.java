package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "blood_pressure_readings")
@Getter
@Setter
public class BloodPressureReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "reading_date", nullable = false)
    @NotNull
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate readingDate;

    @Column(name = "reading_time")
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.TIME)
    private LocalTime readingTime;

    @Column(nullable = false)
    @NotNull
    @Min(60)
    @Max(250)
    private Integer systolic;

    @Column(nullable = false)
    @NotNull
    @Min(40)
    @Max(150)
    private Integer diastolic;

    @Min(30)
    @Max(220)
    private Integer pulse;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Arm arm;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Position position;

    @Size(max = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private ReadingSource source = ReadingSource.MANUAL;

    @Column(name = "created_at", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Arm { LEFT, RIGHT }
    public enum Position { SITTING, STANDING, LYING }
    public enum ReadingSource { MANUAL, IMPORTED }
}
