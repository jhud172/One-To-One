package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainerScheduleTemplateEntryRepository extends JpaRepository<TrainerScheduleTemplateEntry, Long> {

    @org.springframework.data.jpa.repository.Query("select entry from TrainerScheduleTemplateEntry entry where entry.template.id = :templateId order by entry.orderIndex, entry.id")
    List<TrainerScheduleTemplateEntry> findByTemplateIdOrderByOrderIndexAsc(@org.springframework.data.repository.query.Param("templateId") Long templateId);

    @org.springframework.data.jpa.repository.Query("select entry.template.id, entry.dayOfWeek, count(entry) from TrainerScheduleTemplateEntry entry where entry.template.trainerId = :trainerId and entry.template.id in :templateIds group by entry.template.id, entry.dayOfWeek order by entry.dayOfWeek")
    List<Object[]> countOwnedWeekdaysOnPage(@org.springframework.data.repository.query.Param("trainerId") Long trainerId,
                                           @org.springframework.data.repository.query.Param("templateIds") List<Long> templateIds);
}
