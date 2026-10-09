package uk.ac.cf._5.group14.One_To_One.Health;

import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureReading;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureReadingRepository;
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BloodPressureService;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BloodPressureServiceTest {

    private BloodPressureReadingRepository repo = mock(BloodPressureReadingRepository.class);
    private BloodPressureService service = new BloodPressureService(repo);

    private User makeUser(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    @Test
    void saveShouldPersistReading() {
        User user = makeUser(1L);
        BloodPressureReading reading = new BloodPressureReading();
        reading.setUser(user);
        reading.setReadingDate(LocalDate.now());
        reading.setSystolic(120);
        reading.setDiastolic(80);
        reading.setSource(BloodPressureReading.ReadingSource.MANUAL);

        when(repo.findByUserAndReadingDateAndReadingTimeIsNull(any(), any())).thenReturn(Optional.empty());
        when(repo.save(reading)).thenReturn(reading);

        BloodPressureReading result = service.save(reading);
        assertThat(result).isEqualTo(reading);
        verify(repo).save(reading);
    }

    @Test
    void mixedReadingsCannotHideHigherCategoryBehindLowComponent() {
        var category = uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory.CRISIS;
        assertThat(uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory.classify(190, 50)).isEqualTo(category);
        assertThat(uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory.classify(80, 130)).isEqualTo(category);
        assertThat(uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory.classify(145, 50))
                .isEqualTo(uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.BpCategory.HIGH_STAGE2);
    }

    @Test
    void saveShouldThrowWhenDailyReadingAlreadyExistsWithNoTime() {
        User user = makeUser(2L);
        LocalDate date = LocalDate.of(2026, 3, 1);

        BloodPressureReading existing = new BloodPressureReading();
        existing.setId(10L);
        existing.setUser(user);
        existing.setReadingDate(date);

        BloodPressureReading newReading = new BloodPressureReading();
        newReading.setUser(user);
        newReading.setReadingDate(date);
        newReading.setSystolic(120);
        newReading.setDiastolic(80);

        when(repo.findByUserAndReadingDateAndReadingTimeIsNull(user, date)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.save(newReading))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("daily reading already exists");
    }

    @Test
    void deleteShouldThrowWhenOwnershipFails() {
        User owner = makeUser(1L);
        User attacker = makeUser(2L);

        BloodPressureReading reading = new BloodPressureReading();
        reading.setId(5L);
        reading.setUser(owner);

        when(repo.findById(5L)).thenReturn(Optional.of(reading));

        assertThatThrownBy(() -> service.delete(5L, attacker))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");

        verify(repo, never()).delete(any());
    }

    @Test
    void updateShouldThrowWhenOwnershipFails() {
        User owner = makeUser(1L);
        User attacker = makeUser(2L);

        BloodPressureReading reading = new BloodPressureReading();
        reading.setId(7L);
        reading.setUser(owner);

        when(repo.findById(7L)).thenReturn(Optional.of(reading));

        BloodPressureReading update = new BloodPressureReading();
        update.setReadingDate(LocalDate.now());
        update.setSystolic(130);
        update.setDiastolic(85);

        assertThatThrownBy(() -> service.update(7L, update, attacker))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void computeStatsShouldReturnZeroStatsForEmptyList() {
        BloodPressureService.BpStats stats = service.computeStats(List.of());
        assertThat(stats.totalReadings()).isEqualTo(0);
        assertThat(stats.avgSystolic()).isEqualTo(0);
    }

    @Test
    void computeStatsShouldAverageCorrectly() {
        User user = makeUser(1L);
        BloodPressureReading r1 = new BloodPressureReading();
        r1.setUser(user);
        r1.setSystolic(120);
        r1.setDiastolic(80);
        r1.setReadingDate(LocalDate.now());

        BloodPressureReading r2 = new BloodPressureReading();
        r2.setUser(user);
        r2.setSystolic(140);
        r2.setDiastolic(90);
        r2.setReadingDate(LocalDate.now());

        BloodPressureService.BpStats stats = service.computeStats(List.of(r1, r2));
        assertThat(stats.avgSystolic()).isEqualTo(130);
        assertThat(stats.avgDiastolic()).isEqualTo(85);
        assertThat(stats.totalReadings()).isEqualTo(2);
    }
}
