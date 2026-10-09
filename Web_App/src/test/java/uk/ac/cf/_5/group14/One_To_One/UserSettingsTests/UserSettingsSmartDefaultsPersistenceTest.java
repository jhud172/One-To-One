package uk.ac.cf._5.group14.One_To_One.UserSettingsTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.ThemePreference;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsRepository;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsServiceImpl;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(UserSettingsServiceImpl.class)
class UserSettingsSmartDefaultsPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void demoAppearanceChoiceSurvivesSubsequentSettingsReads() {
        User user = userRepository.findByUsername("demo").orElseThrow();
        userSettingsService.update(user, "en", ThemePreference.DARK, false);
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findByUsername("demo").orElseThrow();
        assertThat(userSettingsService.getOrCreate(reloaded).getTheme()).isEqualTo(ThemePreference.DARK);
        entityManager.flush();
        entityManager.clear();
        assertThat(userSettingsRepository.findById(reloaded.getId()).orElseThrow().getTheme())
                .isEqualTo(ThemePreference.DARK);
    }

    @Test
    void updateSmartDefaultsPersistsValues() {
        User user = userRepository.findByUsername("demo").orElseThrow();

        userSettingsService.updateSmartDefaults(
                user,
                4,
                6,
                10,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                2200,
                160,
                200,
                70,
                Set.of()
        );

        UserSettings reloaded = userSettingsRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getDefaultSets()).isEqualTo(4);
        assertThat(reloaded.getDefaultRepMin()).isEqualTo(6);
        assertThat(reloaded.getDefaultRepMax()).isEqualTo(10);
        assertThat(reloaded.isPreferredEquipmentBodyweight()).isTrue();
        assertThat(reloaded.isPreferredEquipmentDumbbell()).isTrue();
        assertThat(reloaded.isPreferredEquipmentBarbell()).isFalse();
        assertThat(reloaded.isPreferredEquipmentMachine()).isFalse();
        assertThat(reloaded.isPreferredEquipmentBands()).isFalse();
        assertThat(reloaded.isPreferredEquipmentKettlebell()).isTrue();
        assertThat(reloaded.getMacroTargetCalories()).isEqualTo(2200);
        assertThat(reloaded.getMacroTargetProtein()).isEqualTo(160);
        assertThat(reloaded.getMacroTargetCarbs()).isEqualTo(200);
        assertThat(reloaded.getMacroTargetFat()).isEqualTo(70);
    }
}
