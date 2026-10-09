package uk.ac.cf._5.group14.One_To_One.UserSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update UserSettings s set s.preferredWorkoutTemplateId = null where s.preferredWorkoutTemplateId = :id")
    void clearPreferredWorkoutTemplate(@org.springframework.data.repository.query.Param("id") Long id);
}
