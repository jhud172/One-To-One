package uk.ac.cf._5.group14.One_To_One.Checkins;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface WeeklyCheckInService {

    WeeklyCheckIn submitCheckIn(User client,
                                Long templateId,
                                Map<Long, String> answers,
                                String clientNotes,
                                LocalDate weekStartDate);

    WeeklyCheckIn respondToCheckIn(User trainer,
                                   Long checkInId,
                                   String trainerResponse,
                                   String nextWeekFocus,
                                   Long goalId);

    List<WeeklyCheckIn> listForTrainer(User trainer);

    List<WeeklyCheckIn> listForClient(User client);
    List<WeeklyCheckIn> listRecentForClient(User client);

    WeeklyCheckIn getForTrainer(User trainer, Long checkInId);

    WeeklyCheckIn getForClient(User client, Long checkInId);

    List<TrainerCheckInQuestion> listQuestions(Long templateId);

    TrainerCheckInQuestion addQuestion(User trainer, Long templateId, String prompt, boolean required);

    void deleteQuestion(User trainer, Long templateId, Long questionId);

    boolean moveQuestion(User trainer, Long templateId, Long questionId, String direction);
}
