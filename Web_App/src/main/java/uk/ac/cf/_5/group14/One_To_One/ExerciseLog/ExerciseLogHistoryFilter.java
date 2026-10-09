package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class ExerciseLogHistoryFilter {
    private String q = "";
    private String sort = "newest";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate until;
}
