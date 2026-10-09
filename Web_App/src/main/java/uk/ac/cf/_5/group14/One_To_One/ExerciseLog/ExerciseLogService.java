package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface ExerciseLogService {
    void saveLog(ExerciseLogForm form, User user);
    void updateLog(Long id, ExerciseLogForm form, User user);
    List<ExerciseLog> getAllLogs();
    ExerciseLog getLogById(Long id);
    ExerciseLog getLogByIdForUser(Long id, User user);
    record LogSnapshot(ExerciseLog log, String revision) { }
    java.util.Optional<LogSnapshot> getLogSnapshot(Long id, User user);
    List<ExerciseLog> findTop5RecentExerciseLogs(User user);
    List<ExerciseLog> getLogsForUser(User user);
    List<ExerciseLog> getLogsByUser(User user);
    org.springframework.data.domain.Page<ExerciseLog> searchHistory(User user, String query,
            java.time.LocalDate from, java.time.LocalDate until, boolean oldest, int page);
}
