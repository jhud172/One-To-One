package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrainerMetadataFreshnessIntegrationTest {
    @Autowired UserRepository users;
    @Autowired TrainerLibraryService library;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User trainer;

    @BeforeEach void setup() {
        String name = "metadata-freshness-" + UUID.randomUUID();
        trainer = new User(name + "@example.invalid", "Local", "Owner", name, "test-password");
        trainer.setRole(Role.TRAINER); trainer.setTrainerVerified(true);
        trainer = users.saveAndFlush(trainer);
    }

    @Test void workoutRevisionUsesLatestDatabaseMetadataEvenWhenControllerAlreadyLoadedEntity() {
        var form = new TrainerLibraryWorkoutTemplateForm(); form.setTitle("Original workout");
        var saved = library.createWorkout(trainer.getId(), form); entityManager.flush();
        String revision = library.getWorkoutRevision(trainer.getId(), saved.getId());
        library.getWorkoutOwned(trainer.getId(), saved.getId());
        // Bypass the persistence context to reproduce a row changed after the initial controller read.
        jdbc.update("update trainer_library_workout_templates set title = ? where id = ?", "Latest workout", saved.getId());
        form.setTitle("Older open draft"); form.setExpectedRevision(revision);
        assertThatThrownBy(() -> library.updateWorkout(trainer.getId(), saved.getId(), form))
                .isInstanceOf(TrainerLibraryRevisionConflictException.class);
        assertThat(jdbc.queryForObject("select title from trainer_library_workout_templates where id = ?", String.class, saved.getId()))
                .isEqualTo("Latest workout");
        assertThat(library.getWorkoutMetadataSnapshot(trainer.getId(), saved.getId()).workout().getTitle()).isEqualTo("Latest workout");
    }

    @Test void programmeRevisionUsesLatestDatabaseMetadataEvenWhenControllerAlreadyLoadedEntity() {
        var form = new TrainerLibraryProgrammeTemplateForm(); form.setTitle("Original programme"); form.setWeeks(4);
        var saved = library.createProgramme(trainer.getId(), form); entityManager.flush();
        String revision = library.getProgrammeRevision(trainer.getId(), saved.getId());
        library.getProgrammeOwned(trainer.getId(), saved.getId());
        jdbc.update("update trainer_library_programme_templates set title = ?, weeks = ? where id = ?", "Latest programme", 8, saved.getId());
        form.setTitle("Older open draft"); form.setExpectedRevision(revision);
        assertThatThrownBy(() -> library.updateProgramme(trainer.getId(), saved.getId(), form))
                .isInstanceOf(TrainerLibraryRevisionConflictException.class);
        assertThat(jdbc.queryForObject("select weeks from trainer_library_programme_templates where id = ?", Integer.class, saved.getId())).isEqualTo(8);
        assertThat(library.getProgrammeMetadataSnapshot(trainer.getId(), saved.getId()).programme().getTitle()).isEqualTo("Latest programme");
    }
}
