package uk.ac.cf._5.group14.One_To_One.TrainerAssignments;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface TrainerAssignmentService {
    AssignedWorkout assignWorkout(User trainer, Long clientId, Long templateId, String trainerNotes);
    AssignedSchedule assignSchedule(User trainer, Long clientId, Long scheduleId, String trainerNotes);

    List<AssignedWorkout> listWorkoutsForClient(Long clientId);
    List<AssignedSchedule> listSchedulesForClient(Long clientId);
    AssignedSchedule getScheduleForClient(Long clientId, Long assignmentId);
    List<AssignedWorkout> listWorkoutsForTrainerClient(Long trainerId, Long clientId);
    List<AssignedSchedule> listSchedulesForTrainerClient(Long trainerId, Long clientId);

    AssignedWorkout updateClientWorkout(Long clientId, Long assignmentId, String clientNotes, String clientFeedback, boolean completed);

    TrainerAdherenceStats getClientAdherence(Long trainerId, Long clientId);
}
