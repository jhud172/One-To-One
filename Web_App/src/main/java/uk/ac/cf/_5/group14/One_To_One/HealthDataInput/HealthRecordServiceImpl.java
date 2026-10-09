package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.PhysicalCondition.PhysicalCondition;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.PhysicalCondition.PhysicalConditionService;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class HealthRecordServiceImpl implements HealthRecordService {

    private final HealthRecordRepository healthRecordRepository;
    private final PhysicalConditionService physicalConditionService;
    private final jakarta.validation.Validator validator;

    public HealthRecordServiceImpl(HealthRecordRepository healthRecordRepository, PhysicalConditionService physicalConditionService, jakarta.validation.Validator validator) {
        this.healthRecordRepository = healthRecordRepository;
        this.physicalConditionService = physicalConditionService;
        this.validator = validator;
    }

    @Override
    public HealthRecordForm createHealthRecordForm(User user) {
        HealthRecordForm emptyHealthRecordForm = new HealthRecordForm();
        emptyHealthRecordForm.setUser(user);
        emptyHealthRecordForm.setBaselineDate(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));

        return emptyHealthRecordForm;
    }

    public HealthRecord formToHealthRecordMapper(HealthRecordForm healthRecordForm) {
        return new HealthRecord(healthRecordForm.getId(), healthRecordForm.getUser(), healthRecordForm.getBaselineDate(), healthRecordForm.getSystolicBloodPressure(), healthRecordForm.getDiastolicBloodPressure(), healthRecordForm.getCholesterol(), healthRecordForm.getWeightKg(), healthRecordForm.getHeightCm(), healthRecordForm.getBmi(), healthRecordForm.getWaistCm(), healthRecordForm.getWaistHeightRatio(),  healthRecordForm.getActivityLevel(), idToPhysicalCondition(healthRecordForm));
    }

    @org.springframework.transaction.annotation.Transactional
    public void addHealthRecord(HealthRecordForm healthRecordForm, User user) {
        if (user == null || user.getId() == null) throw new org.springframework.security.access.AccessDeniedException("Record owner required");
        if (!validator.validate(healthRecordForm).isEmpty()) throw new IllegalArgumentException("Invalid health measurements");
        HealthRecord healthRecord = formToHealthRecordMapper(healthRecordForm);
        healthRecord.setId(null);
        healthRecord.setUser(user);
        healthRecord.setBmi(calculateBmi(healthRecordForm));
        healthRecord.setWaistHeightRatio(calculateWaistHeightRatio(healthRecordForm));

        healthRecordRepository.save(healthRecord);
    }

    private double calculateWaistHeightRatio(HealthRecordForm healthRecordForm) {
        double waistCm = healthRecordForm.getWaistCm();
        double heightCm = healthRecordForm.getHeightCm();
        double waistHeightRatio = waistCm / heightCm;
        return Math.round(waistHeightRatio * 100.0) / 100.0;
    }

    private double calculateBmi(HealthRecordForm healthRecordForm) {
        double weightKg = healthRecordForm.getWeightKg();
        double heightM = healthRecordForm.getHeightCm() / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 100.0) / 100.0;
    }

    List<PhysicalCondition> idToPhysicalCondition(HealthRecordForm healthRecordForm) {
        List<Long> physicalConditionIds = healthRecordForm.getPhysicalConditions() == null ? List.of() : healthRecordForm.getPhysicalConditions();
        if (physicalConditionIds.stream().anyMatch(java.util.Objects::isNull)) throw new InvalidHealthConditionException();
        var distinctIds = new java.util.LinkedHashSet<>(physicalConditionIds);
        var conditions = physicalConditionService.getPhysicalConditionsById(List.copyOf(distinctIds));
        if (conditions.size() != distinctIds.size()) throw new InvalidHealthConditionException();
        return conditions;
    }

    @Override
    public List<HealthRecord> getAllHealthRecords(User user) {
        return healthRecordRepository.findAllByUser((user));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<HealthRecord> searchHistory(User user, String query,
            java.time.LocalDate from, java.time.LocalDate until, String activity, boolean oldest, int page) {
        if (user == null || user.getId() == null) throw new org.springframework.security.access.AccessDeniedException("Record owner required");
        if (from != null && until != null && from.isAfter(until)) throw new IllegalArgumentException("Invalid date range");
        if (activity == null) activity = "";
        if (!activity.isEmpty() && !java.util.Set.of("Sedentary", "Lightly Active", "Moderately Active", "Very Active").contains(activity))
            throw new IllegalArgumentException("Invalid activity filter");
        String literal = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        if (literal.length() > 120) literal = literal.substring(0, 120);
        String pattern = "%" + literal.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var direction = oldest ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        var sort = org.springframework.data.domain.Sort.by(direction, "baselineDate", "id");
        var fromDate = from == null ? null : from.atStartOfDay();
        var untilDate = until == null ? null : until.atTime(java.time.LocalTime.MAX);
        var result = healthRecordRepository.searchHistory(user, pattern, fromDate, untilDate, activity,
                org.springframework.data.domain.PageRequest.of(Math.clamp(page, 0, 9999), 6, sort));
        if (result.getTotalElements() == 0) return new org.springframework.data.domain.PageImpl<>(List.of(),
                org.springframework.data.domain.PageRequest.of(0, 6, sort), 0);
        if (result.getNumber() >= result.getTotalPages()) result = healthRecordRepository.searchHistory(user, pattern, fromDate, untilDate, activity,
                org.springframework.data.domain.PageRequest.of(result.getTotalPages() - 1, 6, sort));
        return result;
    }

    @Override
    public HealthRecord getHealthRecordByIdForUser(Long id, User user) {
        if (user == null || user.getId() == null) throw new org.springframework.security.access.AccessDeniedException("Record owner required");
        return healthRecordRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Record unavailable"));
    }

    public HealthRecord getMostRecentHealthRecord(User user) {
        return healthRecordRepository.findTopByUserOrderByBaselineDateDescIdDesc(user)
                .orElse(null);
    }

}
