package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class HealthRecordHistoryFilter {
    private String q = "";
    private String sort = "newest";
    private String activity = "";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate until;
}
