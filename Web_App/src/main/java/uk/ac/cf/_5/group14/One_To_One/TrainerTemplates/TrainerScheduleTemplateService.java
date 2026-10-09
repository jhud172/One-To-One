package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;

public interface TrainerScheduleTemplateService {

    TrainerScheduleTemplate createTemplate(User trainer, String name, String description, String tags);

    TrainerScheduleTemplate updateTemplate(User trainer, Long templateId, String name, String description, String tags, boolean archived);

    TrainerScheduleTemplate saveMetadata(User trainer, Long templateId, TrainerScheduleMetadataForm form);

    MetadataSnapshot getMetadataSnapshot(User trainer, Long templateId);

    record MetadataSnapshot(TrainerScheduleTemplate template, String revision) { }

    TrainerScheduleTemplateEntry addEntry(User trainer, Long templateId, TrainerScheduleTemplateEntry entry);

    TrainerScheduleTemplateEntry updateEntry(User trainer, Long templateId, Long entryId, TrainerScheduleTemplateEntry entry);

    void deleteEntry(User trainer, Long templateId, Long entryId);

    boolean moveEntry(User trainer, Long templateId, Long entryId, String direction);

    TrainerScheduleTemplate cloneTemplate(User trainer, Long templateId);

    List<TrainerScheduleTemplatePreviewItem> previewApply(User trainer,
                                                         Long templateId,
                                                         Long clientId,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         boolean idempotent);

    ApplicationPreview previewApplication(User trainer, Long templateId, Long clientId,
                                           LocalDate startDate, LocalDate endDate, boolean idempotent);

    record ApplicationPreview(List<TrainerScheduleTemplatePreviewItem> items, String revision) { }

    int applyReviewedTemplate(User trainer, Long templateId, Long clientId, LocalDate startDate,
                              LocalDate endDate, boolean idempotent, String expectedRevision);

    int applyTemplate(User trainer,
                      Long templateId,
                      Long clientId,
                      LocalDate startDate,
                      LocalDate endDate,
                      boolean idempotent);

    List<TrainerScheduleTemplate> listForTrainer(User trainer);

    TemplateCatalogue searchForTrainer(User trainer, String query, int page);

    record TemplateCatalogue(String query, org.springframework.data.domain.Page<TrainerScheduleTemplate> page,
                             long ownedCount, java.util.Map<Long, Long> entryCounts,
                             java.util.Map<Long, Long> questionCounts,
                             java.util.Map<Long, java.util.Map<Integer, Long>> weekdayCounts) { }

    TrainerScheduleTemplate getForTrainer(User trainer, Long templateId);
}
