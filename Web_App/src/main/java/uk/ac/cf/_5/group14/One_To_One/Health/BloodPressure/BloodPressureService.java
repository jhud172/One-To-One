package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BloodPressureService {

    private final BloodPressureReadingRepository repo;

    @org.springframework.beans.factory.annotation.Autowired
    private uk.ac.cf._5.group14.One_To_One.Users.UserRepository users;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public BloodPressureService(BloodPressureReadingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public BloodPressureReading save(BloodPressureReading reading) {
        if (reading.getUser() == null || reading.getUser().getId() == null) throw new SecurityException("Reading owner required");
        validateReading(reading);
        lockOwner(reading.getUser());
        reading.setId(null);
        if (reading.getReadingTime() == null && reading.getId() == null) {
            Optional<BloodPressureReading> existing = repo.findByUserAndReadingDateAndReadingTimeIsNull(
                    reading.getUser(), reading.getReadingDate());
            if (existing.isPresent()) {
                throw new IllegalStateException("A daily reading already exists for " + reading.getReadingDate() +
                        ". Add a time to log multiple readings in a day.");
            }
        }
        return repo.save(reading);
    }

    @Transactional
    public BloodPressureReading update(Long id, BloodPressureReading updated, User currentUser) {
        return updateReading(id, updated, currentUser, null, false);
    }

    @Transactional
    public BloodPressureReading update(Long id, BloodPressureReading updated, User currentUser, String revision) {
        return updateReading(id, updated, currentUser, revision, true);
    }

    private BloodPressureReading updateReading(Long id, BloodPressureReading updated, User currentUser, String revision, boolean checkRevision) {
        lockOwner(currentUser);
        BloodPressureReading existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reading not found"));
        if (!existing.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Access denied");
        }
        refreshReading(existing);
        if (checkRevision && !revision(existing).equals(revision)) throw new StaleBloodPressureReadingException();
        validateReading(updated);
        if (updated.getReadingTime() == null) {
            repo.findByUserAndReadingDateAndReadingTimeIsNull(currentUser, updated.getReadingDate())
                    .filter(reading -> !reading.getId().equals(id))
                    .ifPresent(reading -> { throw new IllegalStateException("Daily reading already exists"); });
        }
        existing.setReadingDate(updated.getReadingDate());
        existing.setReadingTime(updated.getReadingTime());
        existing.setSystolic(updated.getSystolic());
        existing.setDiastolic(updated.getDiastolic());
        existing.setPulse(updated.getPulse());
        existing.setArm(updated.getArm());
        existing.setPosition(updated.getPosition());
        existing.setNotes(updated.getNotes());
        return repo.save(existing);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        deleteReading(id, currentUser, null, false);
    }

    @Transactional
    public void delete(Long id, User currentUser, String revision) {
        deleteReading(id, currentUser, revision, true);
    }

    private void deleteReading(Long id, User currentUser, String revision, boolean checkRevision) {
        lockOwner(currentUser);
        BloodPressureReading reading = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reading not found"));
        if (!reading.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Access denied");
        }
        refreshReading(reading);
        if (checkRevision && !revision(reading).equals(revision)) throw new StaleBloodPressureReadingException();
        repo.delete(reading);
    }

    private void lockOwner(User user) {
        if (user == null || user.getId() == null) throw new SecurityException("Reading owner required");
        // Serialise untimed-reading checks, including API writes, on the existing owner row.
        if (users != null) users.findByIdForUpdate(user.getId()).orElseThrow(() -> new SecurityException("Reading owner unavailable"));
    }

    private void refreshReading(BloodPressureReading reading) {
        if (entityManager == null) return;
        try { entityManager.refresh(reading, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
        catch (jakarta.persistence.EntityNotFoundException removed) { throw new IllegalArgumentException("Reading no longer exists", removed); }
    }

    public String revision(BloodPressureReading reading) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var data = new java.io.DataOutputStream(bytes)) {
                for (Object value : new Object[]{reading.getId(), reading.getReadingDate(), reading.getReadingTime(),
                        reading.getSystolic(), reading.getDiastolic(), reading.getPulse(), reading.getArm(), reading.getPosition(), reading.getNotes()}) {
                    var text = value == null ? new byte[0] : value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    data.writeBoolean(value != null); data.writeInt(text.length); data.write(text);
                }
            }
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public org.springframework.data.domain.Page<BloodPressureReading> history(User user, int page) {
        if (user == null || user.getId() == null) throw new SecurityException("Reading owner required");
        var result = repo.findHistory(user, org.springframework.data.domain.PageRequest.of(Math.max(0, Math.min(page, 9999)), 6));
        if (result.getTotalElements() == 0) return org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0,6));
        return result.getNumber() >= result.getTotalPages() ? repo.findHistory(user, org.springframework.data.domain.PageRequest.of(result.getTotalPages()-1,6)) : result;
    }

    public List<BloodPressureReading> getRecent(User user) {
        return repo.findTop14ByUserOrderByReadingDateDescReadingTimeDesc(user);
    }

    public List<BloodPressureReading> getRange(User user, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Invalid reading date range");
        return repo.findForRange(user, from, to);
    }

    private void validateReading(BloodPressureReading reading) {
        if (reading.getReadingDate() == null || reading.getSystolic() == null || reading.getDiastolic() == null
                || reading.getSystolic() < 60 || reading.getSystolic() > 250 || reading.getDiastolic() < 40 || reading.getDiastolic() > 150
                || (reading.getPulse() != null && (reading.getPulse() < 30 || reading.getPulse() > 220))
                || (reading.getNotes() != null && reading.getNotes().length() > 500)) throw new IllegalArgumentException("Invalid reading values");
    }

    public Optional<BloodPressureReading> findById(Long id) {
        return repo.findById(id);
    }

    public BpStats computeStats(List<BloodPressureReading> readings) {
        if (readings.isEmpty()) return new BpStats(0, 0, 0, 0, 0, 0, 0, 0);
        int sysSum = 0, diasSum = 0;
        int sysMin = Integer.MAX_VALUE, sysMax = Integer.MIN_VALUE;
        int diasMin = Integer.MAX_VALUE, diasMax = Integer.MIN_VALUE;
        for (BloodPressureReading r : readings) {
            sysSum += r.getSystolic();
            diasSum += r.getDiastolic();
            if (r.getSystolic() < sysMin) sysMin = r.getSystolic();
            if (r.getSystolic() > sysMax) sysMax = r.getSystolic();
            if (r.getDiastolic() < diasMin) diasMin = r.getDiastolic();
            if (r.getDiastolic() > diasMax) diasMax = r.getDiastolic();
        }
        int count = readings.size();
        long daysLogged = readings.stream().map(BloodPressureReading::getReadingDate).distinct().count();
        return new BpStats(sysSum / count, diasSum / count, sysMin, sysMax, diasMin, diasMax, count, (int) daysLogged);
    }

    /** Consecutive days streak ending today (or most recent logged day). */
    public int computeStreak(User user) {
        List<BloodPressureReading> all = repo.findByUserOrderByReadingDateDescReadingTimeDesc(user);
        if (all.isEmpty()) return 0;
        List<LocalDate> dates = all.stream()
                .map(BloodPressureReading::getReadingDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        LocalDate expected = LocalDate.now();
        if (!dates.get(0).equals(expected) && !dates.get(0).equals(expected.minusDays(1))) return 0;
        expected = dates.get(0);
        int streak = 0;
        for (LocalDate d : dates) {
            if (d.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    public record BpStats(int avgSystolic, int avgDiastolic,
                          int minSystolic, int maxSystolic,
                          int minDiastolic, int maxDiastolic,
                          int totalReadings, int daysLogged) {}
}
