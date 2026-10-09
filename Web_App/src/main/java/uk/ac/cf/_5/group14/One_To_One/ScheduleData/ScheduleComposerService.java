package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Workout.Workout;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Typed draft commands and atomic workout-to-movement import for schedule authoring. */
@Service
public class ScheduleComposerService {
    private static final List<String> WEEKDAYS = List.of("Mon","Tue","Wed","Thu","Fri","Sat","Sun");
    private final ObjectMapper mapper;
    private final WorkoutRepository workouts;
    private final ScheduleRepository schedules;
    private final ScheduleEntryRepository entries;
    private final ScheduleTemplateService templates;

    public ScheduleComposerService(ObjectMapper mapper, WorkoutRepository workouts, ScheduleRepository schedules,
                                   ScheduleEntryRepository entries, ScheduleTemplateService templates) {
        this.mapper=mapper; this.workouts=workouts; this.schedules=schedules; this.entries=entries; this.templates=templates;
    }

    public record Placement(int day, Long workoutId) {}
    public record Draft(String name, ScheduleType type, RotationMode rotation, int dayCount,
                        String templateId, List<Placement> placements) {}

    public Draft empty() { return new Draft("",ScheduleType.WEEKLY,RotationMode.WEEKLY_REPEAT,7,"",List.of()); }

    public Draft read(MultiValueMap<String,String> fields) {
        ScheduleType type=ScheduleType.valueOf(value(fields,"scheduleType","WEEKLY").toUpperCase(Locale.ROOT));
        RotationMode rotation=RotationMode.valueOf(value(fields,"rotationMode","WEEKLY_REPEAT").toUpperCase(Locale.ROOT));
        int requested=positive(value(fields,"customDayCount","7"));
        if(requested>14) throw new IllegalArgumentException("Cycle exceeds fourteen days");
        int days=type==ScheduleType.WEEKLY ? 7 : type==ScheduleType.DAILY ? 1 : requested;
        List<Placement> rows=new ArrayList<>();
        if("true".equals(fields.getFirst("composerForm"))) {
            List<String> dayFields=fields.getOrDefault("days",List.of());
            List<String> workoutFields=fields.getOrDefault("workoutIds",List.of());
            if(dayFields.size()!=workoutFields.size() || dayFields.size()>100) throw new IllegalArgumentException("Invalid placements");
            for(int index=0;index<dayFields.size();index++) {
                int day=positive(dayFields.get(index));
                if(day>14) throw new IllegalArgumentException("Invalid day");
                rows.add(new Placement(day,identifier(workoutFields.get(index))));
            }
        } else {
            String payload=fields.getFirst("payload");
            if(payload==null || payload.length()>16384) throw new IllegalArgumentException("Invalid payload");
            try {
                JsonNode root=mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(payload);
                if(root==null || !root.isObject() || root.size()>14) throw new IllegalArgumentException("Invalid payload");
                var iterator=root.properties().iterator();
                while(iterator.hasNext()) {
                    var field=iterator.next(); int day=dayIndex(field.getKey());
                    if(day>14 || !field.getValue().isArray()) throw new IllegalArgumentException("Invalid day");
                    for(JsonNode id:field.getValue()) {
                        if(!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong()<1 || rows.size()>=100)
                            throw new IllegalArgumentException("Invalid workout");
                        rows.add(new Placement(day,id.asLong()));
                    }
                }
            } catch(java.io.IOException exception) { throw new IllegalArgumentException("Invalid payload",exception); }
        }
        String template=value(fields,"templateId","");
        if(!template.isBlank() && templates.getTemplateById(template)==null) throw new IllegalArgumentException("Unknown template");
        return new Draft(value(fields,"name",""),type,rotation,days,template,List.copyOf(rows));
    }

    public Draft command(Draft draft,String command,MultiValueMap<String,String> fields,User user) {
        if(command==null || command.equals("refresh")) return draft;
        List<Placement> rows=new ArrayList<>(draft.placements());
        if(command.equals("add")) {
            int day=positive(value(fields,"addDay","1")); Long id=identifier(fields.getFirst("addWorkout"));
            if(day>draft.dayCount() || rows.size()>=100) throw new IllegalArgumentException("Invalid placement");
            ownedWorkout(id,user);
            rows.add(new Placement(day,id));
        } else if(command.equals("clear")) rows.clear();
        else if(command.equals("template")) {
            ScheduleTemplate template=templates.getTemplateById(draft.templateId());
            if(template==null) throw new IllegalArgumentException("Choose template");
            return new Draft(draft.name(),template.getScheduleType(),RotationMode.WEEKLY_REPEAT,
                    template.getDayCount(),template.getId(),draft.placements());
        } else {
            String[] parts=command.split(":",-1);
            if(parts.length!=2 || !List.of("remove","up","down").contains(parts[0])) throw new IllegalArgumentException("Unknown command");
            int index=Integer.parseInt(parts[1]);
            if(index<0 || index>=rows.size()) throw new IllegalArgumentException("Invalid row");
            if(parts[0].equals("remove")) rows.remove(index);
            else {
                int destination=index+(parts[0].equals("up") ? -1 : 1);
                if(destination>=0 && destination<rows.size()) java.util.Collections.swap(rows,index,destination);
            }
        }
        return new Draft(draft.name(),draft.type(),draft.rotation(),draft.dayCount(),draft.templateId(),List.copyOf(rows));
    }

    @Transactional
    public Schedule save(Draft draft,User user) {
        String name=draft.name().trim();
        if(name.isBlank() || name.length()>200 || draft.placements().isEmpty() || draft.placements().size()>100
                || draft.type()==null || draft.rotation()==null || draft.dayCount()<1 || draft.dayCount()>14
                || (draft.type()==ScheduleType.WEEKLY && draft.dayCount()!=7)
                || (draft.type()==ScheduleType.DAILY && draft.dayCount()!=1)
                || (!draft.templateId().isBlank() && templates.getTemplateById(draft.templateId())==null))
            throw new IllegalArgumentException("Invalid schedule");
        List<ScheduleEntry> imported=new ArrayList<>();
        Map<Integer,Integer> orderByDay=new java.util.HashMap<>();
        for(Placement row:draft.placements()) {
            if(row.day()<1 || row.day()>draft.dayCount()) throw new IllegalArgumentException("Move rows into cycle");
            Workout workout=ownedWorkout(row.workoutId(),user);
            int before=imported.size();
            if(workout.getExercises()!=null) for(var exercise:workout.getExercises()) {
                ScheduleEntry entry=new ScheduleEntry(); entry.setExercise(exercise);
                append(imported,entry,row.day(),orderByDay);
            }
            if(workout.getCustomExercises()!=null) for(var exercise:workout.getCustomExercises()) {
                if(!Objects.equals(exercise.getUserId(),user.getId())) throw new IllegalArgumentException("Unavailable custom movement");
                ScheduleEntry entry=new ScheduleEntry(); entry.setCustomExercise(exercise);
                append(imported,entry,row.day(),orderByDay);
            }
            if(before==imported.size()) throw new IllegalArgumentException("Workout has no movements");
        }
        Schedule schedule=new Schedule(); schedule.setUser(user); schedule.setName(name);
        schedule.setScheduleType(draft.type()); schedule.setRotationMode(draft.rotation());
        schedule.setCustomDayCount(draft.dayCount()); schedule.setTemplateId(draft.templateId().isBlank() ? null : draft.templateId());
        schedules.save(schedule);
        for(ScheduleEntry entry:imported) { entry.setSchedule(schedule); entries.save(entry); }
        return schedule;
    }

    private void append(List<ScheduleEntry> imported,ScheduleEntry entry,int day,Map<Integer,Integer> order) {
        if(imported.size()>=500) throw new IllegalArgumentException("Too many movements");
        entry.setDayOfWeek(day); entry.setOrderNumber(order.getOrDefault(day,0)); order.merge(day,1,Integer::sum); imported.add(entry);
    }
    private Workout ownedWorkout(Long id,User user) {
        Workout workout=workouts.findById(id).orElseThrow(()->new IllegalArgumentException("Unavailable workout"));
        if(user==null || !Objects.equals(workout.getUserId(),user.getId())) throw new IllegalArgumentException("Unavailable workout");
        return workout;
    }
    private static String value(MultiValueMap<String,String> fields,String key,String fallback) {
        String value=fields.getFirst(key); return value==null ? fallback : value;
    }
    private static int positive(String value) { int number=Integer.parseInt(value); if(number<1) throw new IllegalArgumentException("Invalid number"); return number; }
    private static Long identifier(String value) { long number=Long.parseLong(value); if(number<1) throw new IllegalArgumentException("Invalid identifier"); return number; }
    private static int dayIndex(String value) {
        int weekday=WEEKDAYS.indexOf(value); if(weekday>=0) return weekday+1;
        if(value.equals("Daily Routine")) return 1;
        return positive(value.startsWith("Day ") ? value.substring(4) : value);
    }
}
