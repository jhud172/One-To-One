package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class HealthRecordForm {
    private Long id;
    private User user;
    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime baselineDate;

    @NotNull(message = "{validation.healthRecord.systolicBloodPressure}")
    @Digits(integer = 3, fraction = 0, message = "{typeMismatch.healthRecord.systolicBloodPressure}")
    @Min(value = 70, message = "{validation.healthRecord.minSystolicBloodPressure}")
    @Max(value = 300, message = "{validation.healthRecord.maxSystolicBloodPressure}")
    private Integer systolicBloodPressure;

    @NotNull(message = "{validation.healthRecord.diastolicBloodPressure}")
    @Digits(integer = 3, fraction = 0, message = "{typeMismatch.healthRecord.diastolicBloodPressure}")
    @Min(value = 40, message = "{validation.healthRecord.minDiastolicBloodPressure}")
    @Max(value = 200, message = "{validation.healthRecord.maxDiastolicBloodPressure}")
    private Integer diastolicBloodPressure;

    @NotNull(message = "{validation.healthRecord.cholesterol}")
    @Digits(integer = 3, fraction = 2, message = "{typeMismatch.healthRecord.cholesterol}")
    @Min(value = 1, message = "{validation.healthRecord.minCholesterol}")
    @Max(value = 20, message = "{validation.healthRecord.maxCholesterol}")
    private Double cholesterol;

    @NotNull(message = "{validation.healthRecord.weightKg}")
    @Positive(message = "{validation.healthRecord.weightKgPositive}")
    @Digits(integer = 3, fraction = 2, message = "{typeMismatch.healthRecord.weightKg}")
    private Double weightKg;

    @NotNull(message = "{validation.healthRecord.heightCm}")
    @Positive(message = "{validation.healthRecord.heightCmPositive}")
    @Digits(integer = 3, fraction = 2, message = "{typeMismatch.healthRecord.heightCm}")
    @Min(value = 50, message = "{validation.healthRecord.minHeightCm}")
    @Max(value = 300, message = "{validation.healthRecord.maxHeightCm}")
    private Double heightCm;

    private Double bmi;

    @NotNull(message = "{validation.healthRecord.waistCm}")
    @Positive(message = "{validation.healthRecord.waistCmPositive}")
    @Digits(integer = 3, fraction = 2, message = "{typeMismatch.healthRecord.waistCm}")
    private Double waistCm;

    private Double waistHeightRatio;

    @NotBlank(message = "{validation.healthRecord.activityLevel}")
    @Size(max = 255)
    @Pattern(regexp = "Sedentary|Lightly Active|Moderately Active|Very Active", message = "{validation.healthRecord.activityLevel}")
    private String activityLevel;
    @Size(max = 50)
    private List<Long> physicalConditions = new ArrayList<>();
}
