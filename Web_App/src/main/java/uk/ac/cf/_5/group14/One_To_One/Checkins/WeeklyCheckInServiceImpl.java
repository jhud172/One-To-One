package uk.ac.cf._5.group14.One_To_One.Checkins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationType;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplate;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplateRepository;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNote;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class WeeklyCheckInServiceImpl implements WeeklyCheckInService {

    private final WeeklyCheckInRepository weeklyCheckInRepository;
    private final TrainerCheckInQuestionRepository questionRepository;
    private final TrainerScheduleTemplateRepository templateRepository;
    private final TrainerClientLinkService trainerClientLinkService;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;
    private final GoalService goalService;
    private final VaultNoteRepository vaultNoteRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public WeeklyCheckInServiceImpl(WeeklyCheckInRepository weeklyCheckInRepository,
                                    TrainerCheckInQuestionRepository questionRepository,
                                    TrainerScheduleTemplateRepository templateRepository,
                                    TrainerClientLinkService trainerClientLinkService,
                                    AccessGuard accessGuard,
                                    NotificationService notificationService,
                                    GoalService goalService,
                                    VaultNoteRepository vaultNoteRepository,
                                    UserRepository userRepository,
                                    ObjectMapper objectMapper) {
        this.weeklyCheckInRepository = weeklyCheckInRepository;
        this.questionRepository = questionRepository;
        this.templateRepository = templateRepository;
        this.trainerClientLinkService = trainerClientLinkService;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
        this.goalService = goalService;
        this.vaultNoteRepository = vaultNoteRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeeklyCheckIn submitCheckIn(User client,
                                       Long templateId,
                                       Map<Long, String> answers,
                                       String clientNotes,
                                       LocalDate weekStartDate) {
        requireClient(client);
        userRepository.findByIdForUpdate(client.getId())
                .orElseThrow(() -> new AccessDeniedException("Client not found"));
        TrainerClientLink link = trainerClientLinkService.getActiveLinkForClient(client.getId());
        if (link == null) {
            throw new AccessDeniedException("Active trainer required");
        }

        Long trainerId = link.getTrainerUserId();
        requireTrainer(userRepository.findById(trainerId)
                .orElseThrow(() -> new AccessDeniedException("Trainer not found")));
        TrainerScheduleTemplate template = null;
        if (templateId != null) {
            template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
            if (!trainerId.equals(template.getTrainerId())) {
                throw new AccessDeniedException("Template not owned by trainer");
            }
        }
        LocalDate weekStart = normalizeWeekStart(weekStartDate != null ? weekStartDate : LocalDate.now());
        if (weekStart.isAfter(normalizeWeekStart(LocalDate.now()))) {
            throw new IllegalArgumentException("invalid-week");
        }

        weeklyCheckInRepository.findByTrainerIdAndClientIdAndWeekStartDate(trainerId, client.getId(), weekStart)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Weekly check-in already submitted");
                });

        String responsesJson = toResponsesJson(templateId, answers);

        WeeklyCheckIn checkIn = new WeeklyCheckIn();
        checkIn.setTrainerId(trainerId);
        checkIn.setClientId(client.getId());
        checkIn.setTemplateId(templateId);
        checkIn.setWeekStartDate(weekStart);
        checkIn.setResponsesJson(responsesJson);
        checkIn.setClientNotes(trimToNull(clientNotes));
        checkIn.setStatus(WeeklyCheckInStatus.SUBMITTED);
        checkIn.setSubmittedAt(Instant.now());
        WeeklyCheckIn saved = weeklyCheckInRepository.save(checkIn);

        VaultNote note = new VaultNote();
        note.setUserId(client.getId());
        note.setNoteType(VaultNoteType.CHECKIN);
        note.setTitle("Weekly check-in" + (template != null ? ": " + template.getName() : ""));
        note.setContent(buildNoteBody(saved, template));
        note.setLinkedDate(weekStart);
        vaultNoteRepository.save(note);

        userRepository.findById(trainerId).ifPresent(trainerUser ->
            notificationService.createIfNotRecentlySent(
            trainerUser,
                NotificationType.SYSTEM,
                "Weekly check-in",
                client.getFullName() + " submitted a weekly check-in.",
            15
        ));

        return saved;
    }

    @Override
    public WeeklyCheckIn respondToCheckIn(User trainer, Long checkInId, String trainerResponse, String nextWeekFocus, Long goalId) {
        requireTrainer(trainer);
        WeeklyCheckIn checkIn = weeklyCheckInRepository.findByIdForUpdate(checkInId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));

        if (!trainer.getId().equals(checkIn.getTrainerId())) {
            throw new AccessDeniedException("Not your check-in");
        }
        accessGuard.requireTrainerAccessClient(trainer.getId(), checkIn.getClientId());

        String response = trimToNull(trainerResponse);
        String focus = trimToNull(nextWeekFocus);
        if (response == null && focus == null) {
            throw new IllegalArgumentException("empty-response");
        }
        if (focus != null && focus.length() > 600) {
            throw new IllegalArgumentException("focus-length");
        }
        if (goalId != null) {
            var goal = goalService.getGoalForViewer(trainer, goalId);
            if (goal.getOwnerUser() == null || !checkIn.getClientId().equals(goal.getOwnerUser().getId())) {
                throw new AccessDeniedException("Goal does not belong to this check-in's client");
            }
        }

        if (checkIn.getStatus() == WeeklyCheckInStatus.RESPONDED
                && Objects.equals(checkIn.getTrainerResponse(), response)
                && Objects.equals(checkIn.getNextWeekFocus(), focus)
                && Objects.equals(checkIn.getGoalId(), goalId)) {
            return checkIn;
        }
        checkIn.setGoalId(goalId);
        checkIn.setTrainerResponse(response);
        checkIn.setNextWeekFocus(focus);
        checkIn.setRespondedAt(Instant.now());
        checkIn.setStatus(WeeklyCheckInStatus.RESPONDED);
        WeeklyCheckIn saved = weeklyCheckInRepository.save(checkIn);

        userRepository.findById(checkIn.getClientId()).ifPresent(clientUser ->
            notificationService.create(
            clientUser,
            NotificationType.SYSTEM,
            "Coach response",
            trainer.getFullName() + " responded to your weekly check-in.",
            "/checkins/client-review/" + saved.getId()
        ));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyCheckIn> listForTrainer(User trainer) {
        requireTrainer(trainer);
        return weeklyCheckInRepository.findByTrainerIdOrderBySubmittedAtDesc(trainer.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyCheckIn> listForClient(User client) {
        requireClient(client);
        return weeklyCheckInRepository.findByClientIdOrderBySubmittedAtDesc(client.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyCheckIn> listRecentForClient(User client) {
        requireClient(client);
        return weeklyCheckInRepository.findTop5ByClientIdOrderByWeekStartDateDescIdDesc(client.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyCheckIn getForTrainer(User trainer, Long checkInId) {
        requireTrainer(trainer);
        WeeklyCheckIn checkIn = weeklyCheckInRepository.findById(checkInId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));
        if (!trainer.getId().equals(checkIn.getTrainerId())) {
            throw new AccessDeniedException("Not your check-in");
        }
        accessGuard.requireTrainerAccessClient(trainer.getId(), checkIn.getClientId());
        return checkIn;
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyCheckIn getForClient(User client, Long checkInId) {
        requireClient(client);
        WeeklyCheckIn checkIn = weeklyCheckInRepository.findById(checkInId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));
        if (!client.getId().equals(checkIn.getClientId())) {
            throw new AccessDeniedException("Not your check-in");
        }
        return checkIn;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerCheckInQuestion> listQuestions(Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        return questionRepository.findByTemplateIdOrderByOrderIndexAsc(templateId);
    }

    @Override
    public TrainerCheckInQuestion addQuestion(User trainer, Long templateId, String prompt, boolean required) {
        TrainerScheduleTemplate template = getTemplateForTrainer(trainer, templateId);
        templateRepository.findOwnedForUpdate(templateId, trainer.getId()).orElseThrow(() -> new AccessDeniedException("Template not found"));
        if (prompt == null || prompt.isBlank() || prompt.length() > 300) throw new IllegalArgumentException("Question must contain 1 to 300 characters");
        TrainerCheckInQuestion question = new TrainerCheckInQuestion();
        question.setTemplateId(template.getId());
        question.setPrompt(prompt == null ? "" : prompt.trim());
        question.setRequired(required);
        question.setOrderIndex(nextQuestionOrder(templateId));
        return questionRepository.save(question);
    }

    @Override
    public void deleteQuestion(User trainer, Long templateId, Long questionId) {
        getTemplateForTrainer(trainer, templateId);
        templateRepository.findOwnedForUpdate(templateId, trainer.getId()).orElseThrow(() -> new AccessDeniedException("Template not found"));
        TrainerCheckInQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new AccessDeniedException("Question not found"));
        if (!question.getTemplateId().equals(templateId)) {
            throw new AccessDeniedException("Question not linked to template");
        }
        questionRepository.delete(question);
    }

    @Override
    public boolean moveQuestion(User trainer, Long templateId, Long questionId, String direction) {
        requireTrainer(trainer);
        templateRepository.findOwnedForUpdate(templateId, trainer.getId()).orElseThrow(() -> new AccessDeniedException("Template not found"));
        int step = "UP".equals(direction) ? -1 : "DOWN".equals(direction) ? 1 : 0;
        if (step == 0) throw new IllegalArgumentException("Invalid movement");
        var questions = questionRepository.findByTemplateIdOrderByOrderIndexAsc(templateId);
        int index = -1;
        for (int position = 0; position < questions.size(); position++) {
            if (questions.get(position).getId().equals(questionId)) index = position;
        }
        if (index < 0) throw new AccessDeniedException("Question not found");
        if (index + step < 0 || index + step >= questions.size()) return false;
        java.util.Collections.swap(questions, index, index + step);
        for (int position = 0; position < questions.size(); position++) questions.get(position).setOrderIndex(position + 1);
        questionRepository.saveAll(questions);
        return true;
    }

    private TrainerScheduleTemplate getTemplateForTrainer(User trainer, Long templateId) {
        requireTrainer(trainer);
        if (templateId == null) {
            throw new IllegalArgumentException("Template required");
        }
        return templateRepository.findByIdAndTrainerId(templateId, trainer.getId())
                .orElseThrow(() -> new AccessDeniedException("Template not found"));
    }

    private void requireTrainer(User trainer) {
        if (trainer == null || trainer.getRole() != Role.TRAINER || !trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new AccessDeniedException("Verified active trainer required");
        }
    }

    private void requireClient(User client) {
        if (client == null || client.getRole() != Role.CLIENT) {
            throw new AccessDeniedException("Client role required");
        }
    }

    private int nextQuestionOrder(Long templateId) {
        int last = questionRepository.findByTemplateIdOrderByOrderIndexAsc(templateId).stream()
                .mapToInt(TrainerCheckInQuestion::getOrderIndex).max().orElse(0);
        if (last == Integer.MAX_VALUE) throw new IllegalArgumentException("No available question position");
        return last + 1;
    }

    private String toResponsesJson(Long templateId, Map<Long, String> answers) {
        List<TrainerCheckInQuestion> questions = listQuestions(templateId);
        List<Map<String, String>> payload = new ArrayList<>();
        for (TrainerCheckInQuestion question : questions) {
            Map<String, String> item = new HashMap<>();
            item.put("id", String.valueOf(question.getId()));
            item.put("prompt", question.getPrompt());
            String answer = answers.getOrDefault(question.getId(), "");
            String trimmed = answer == null ? "" : answer.trim();
            if (question.isRequired() && trimmed.isBlank()) {
                throw new IllegalArgumentException("Missing required check-in response");
            }
            item.put("answer", trimmed);
            payload.add(item);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize responses", e);
        }
    }

    private String buildNoteBody(WeeklyCheckIn checkIn, TrainerScheduleTemplate template) {
        StringBuilder builder = new StringBuilder();
        builder.append("Week starting ").append(checkIn.getWeekStartDate()).append("\n");
        if (template != null) {
            builder.append("Template: ").append(template.getName()).append("\n");
        }
        if (checkIn.getClientNotes() != null) {
            builder.append("Notes: ").append(checkIn.getClientNotes());
        }
        return builder.toString();
    }

    private LocalDate normalizeWeekStart(LocalDate date) {
        LocalDate cursor = date;
        while (cursor.getDayOfWeek().getValue() != 1) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
