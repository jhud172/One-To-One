package uk.ac.cf._5.group14.One_To_One.WorkoutTemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutTemplateServiceImpl implements WorkoutTemplateService {

    private final WorkoutUiTemplateRepository templateRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    @Override
    public WorkoutTemplate create(WorkoutTemplate template) {
        return templateRepository.save(template);
    }

    @Override
    public WorkoutTemplate update(WorkoutTemplate template) {
        return templateRepository.save(template);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        if (templateRepository.isReferencedBySession(id)) {
            throw new org.springframework.dao.DataIntegrityViolationException("Layout is referenced by a saved session");
        }
        userSettingsRepository.clearPreferredWorkoutTemplate(id);
        templateRepository.deleteById(id);
    }

    @Override
    public Optional<WorkoutTemplate> findById(Long id) {
        return templateRepository.findById(id);
    }

    @Override
    public List<WorkoutTemplate> findByUser(User user) {
        return templateRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    @Override
    public List<WorkoutTemplate> findGlobalTemplates() {
        return templateRepository.findByUserIsNullOrderByName();
    }

    @Override
    public WorkoutTemplate getDefaultTemplateForUser(Long userId) {
        // 1. Check user's preferred template from settings
        Optional<UserSettings> settingsOpt = userSettingsRepository.findById(userId);
        if (settingsOpt.isPresent()) {
            Long preferredId = settingsOpt.get().getPreferredWorkoutTemplateId();
            if (preferredId != null) {
                Optional<WorkoutTemplate> preferred = templateRepository.findById(preferredId);
                if (preferred.isPresent() && (preferred.get().getUser() == null
                        || userId.equals(preferred.get().getUser().getId()))) {
                    return preferred.get();
                }
            }
        }

        // 2. Check user's own default template
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            Optional<WorkoutTemplate> userDefault = templateRepository.findFirstByUserAndIsDefaultTrue(userOpt.get());
            if (userDefault.isPresent()) {
                return userDefault.get();
            }
        }

        // 3. Fall back to global default template
        return templateRepository.findFirstByUserIsNullAndIsDefaultTrue()
                .orElseGet(() -> {
                    List<WorkoutTemplate> globals = templateRepository.findByUserIsNullOrderByName();
                    if (!globals.isEmpty()) {
                        return globals.get(0);
                    }
                    // Ultimate fallback: create an in-memory default (not persisted)
                    WorkoutTemplate fallback = new WorkoutTemplate();
                    fallback.setName("Flow (Default)");
                    fallback.setLayoutType(TemplateLayoutType.FLOW);
                    fallback.setDefault(true);
                    return fallback;
                });
    }
}
