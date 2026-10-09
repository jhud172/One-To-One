package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface HealthRecordService {
    HealthRecordForm createHealthRecordForm(User user);

    List<HealthRecord> getAllHealthRecords(User user);

    org.springframework.data.domain.Page<HealthRecord> searchHistory(User user, String query,
            java.time.LocalDate from, java.time.LocalDate until, String activity, boolean oldest, int page);

    HealthRecord getHealthRecordByIdForUser(Long id, User user);

    void addHealthRecord(HealthRecordForm healthRecordForm, User user);

    HealthRecord getMostRecentHealthRecord(User user);
}
