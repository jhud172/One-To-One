package uk.ac.cf._5.group14.One_To_One.Workouts;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface WorkoutBuilderService {
    List<WorkoutTemplate> listTemplates(User user);
    WorkoutTemplate createTemplate(User user, WorkoutTemplateForm form);
    WorkoutTemplate updateTemplate(User user, Long templateId, WorkoutTemplateForm form);
    WorkoutTemplate getTemplate(User user, Long templateId);
    void deleteTemplate(User user, Long templateId);

    java.util.Optional<WorkoutSession> findOpenSession(User user, Long templateId);
    WorkoutSession startSession(User user, Long templateId);
    WorkoutSession getSession(User user, Long sessionId);
    WorkoutSetLog updateSet(User user, Long sessionId, Long setId, WorkoutSetUpdateRequest request);
}
