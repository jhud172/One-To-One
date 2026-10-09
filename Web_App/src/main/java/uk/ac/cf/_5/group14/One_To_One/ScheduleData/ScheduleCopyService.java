package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Service
public class ScheduleCopyService {
    private final ScheduleService schedules;
    private final ScheduleEntryService entries;

    public ScheduleCopyService(ScheduleService schedules, ScheduleEntryService entries) {
        this.schedules = schedules;
        this.entries = entries;
    }

    @Transactional
    public Schedule copy(Schedule original, User owner) {
        Schedule duplicate = new Schedule();
        String name = original.getName();
        duplicate.setName(name.substring(0, Math.min(name.length(), 193)) + " (Copy)");
        duplicate.setDescription(original.getDescription());
        duplicate.setUser(owner);
        duplicate.setScheduleType(original.getScheduleType());
        duplicate.setRotationMode(original.getRotationMode());
        duplicate.setCustomDayCount(original.getCustomDayCount());
        duplicate.setTemplateId(original.getTemplateId());
        schedules.save(duplicate);
        for (ScheduleEntry source : entries.getEntriesBySchedule(original)) {
            ScheduleEntry entry = new ScheduleEntry();
            entry.setSchedule(duplicate);
            entry.setExercise(source.getExercise());
            entry.setCustomExercise(source.getCustomExercise());
            entry.setDayOfWeek(source.getDayOfWeek());
            entry.setOrderNumber(source.getOrderNumber());
            entries.save(entry);
        }
        return duplicate;
    }
}
