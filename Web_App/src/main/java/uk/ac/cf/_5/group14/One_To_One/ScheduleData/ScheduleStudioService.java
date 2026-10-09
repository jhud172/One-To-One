package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Native schedule drafts. Existing calendar occurrences are independent snapshots. */
@Service
public class ScheduleStudioService {
    private final ScheduleRepository schedules;
    private final ScheduleEntryRepository entries;
    private final ExerciseRepository exercises;
    private final CustomExerciseRepository customs;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ScheduleStudioService(ScheduleRepository schedules,ScheduleEntryRepository entries,
                                 ExerciseRepository exercises,CustomExerciseRepository customs) {
        this.schedules=schedules; this.entries=entries; this.exercises=exercises; this.customs=customs;
    }

    public record Movement(Long entryId,int day,String key) {}
    public record Draft(String name,String description,ScheduleType type,RotationMode rotation,int dayCount,
                        String revision,List<Movement> movements) {}

    public Draft load(Schedule schedule) {
        List<Movement> rows=entries.findBySchedule(schedule).stream()
                .sorted(Comparator.comparingInt(ScheduleEntry::getDayOfWeek).thenComparingInt(ScheduleEntry::getOrderNumber)
                        .thenComparing(ScheduleEntry::getId))
                .map(row->new Movement(row.getId(),row.getDayOfWeek(),row.getCustomExercise()!=null
                        ? "c:"+row.getCustomExercise().getId() : row.getExercise()!=null ? "e:"+row.getExercise().getId() : ""))
                .toList();
        return new Draft(schedule.getName(),schedule.getDescription()==null ? "" : schedule.getDescription(),
                schedule.getScheduleType(),schedule.getRotationMode(),cycleDays(schedule),revision(schedule),rows);
    }

    public static int cycleDays(Schedule schedule) {
        return schedule.getScheduleType()==ScheduleType.DAILY ? 1 : schedule.getScheduleType()==ScheduleType.CUSTOM
                ? schedule.getCustomDayCount()==null ? 7 : schedule.getCustomDayCount() : 7;
    }

    public Draft read(MultiValueMap<String,String> fields,Schedule schedule) {
        ScheduleType type=ScheduleType.valueOf(value(fields,"scheduleType",schedule.getScheduleType().name()));
        RotationMode rotation=RotationMode.valueOf(value(fields,"rotationMode",schedule.getRotationMode().name()));
        int requested=Integer.parseInt(value(fields,"customDayCount",String.valueOf(cycleDays(schedule))));
        if(requested<1 || requested>14) throw new IllegalArgumentException("cycle");
        int days=type==ScheduleType.DAILY ? 1 : type==ScheduleType.WEEKLY ? 7 : requested;
        List<String> ids=fields.getOrDefault("entryIds",List.of());
        List<String> dayFields=fields.getOrDefault("days",List.of());
        List<String> keys=fields.getOrDefault("movementKeys",List.of());
        if(ids.size()!=keys.size() || dayFields.size()!=keys.size() || keys.size()>500) throw new IllegalArgumentException("rows");
        List<Movement> rows=new ArrayList<>();
        for(int index=0;index<keys.size();index++) {
            int day=Integer.parseInt(dayFields.get(index));
            if(day<1 || day>14) throw new IllegalArgumentException("day");
            String rawId=ids.get(index);
            rows.add(new Movement(rawId.isBlank() ? null : positiveId(rawId),day,keys.get(index)));
        }
        return new Draft(value(fields,"name",schedule.getName()),value(fields,"description",""),type,rotation,days,
                value(fields,"revision",""),List.copyOf(rows));
    }

    public Draft command(Draft draft,String command,MultiValueMap<String,String> fields,User user) {
        if(command==null || command.equals("refresh")) return draft;
        List<Movement> rows=new ArrayList<>(draft.movements());
        if(command.equals("add")) {
            int day=Integer.parseInt(value(fields,"addDay","1"));
            if(day<1 || day>draft.dayCount() || rows.size()>=500) throw new IllegalArgumentException("day");
            String key=value(fields,"addMovement",""); movement(key,user);
            rows.add(new Movement(null,day,key));
        } else if(command.equals("confirmClear")) rows.clear();
        else if(command.equals("forward") || command.equals("backward")) {
            int offset=command.equals("forward") ? 1 : -1;
            rows=rows.stream().map(row->new Movement(row.entryId(),Math.floorMod(row.day()-1+offset,draft.dayCount())+1,row.key())).toList();
        } else if(command.equals("spread")) {
            for(int index=0;index<rows.size();index++) {
                Movement row=rows.get(index); rows.set(index,new Movement(row.entryId(),index%draft.dayCount()+1,row.key()));
            }
        } else {
            String[] parts=command.split(":",-1);
            if(parts.length!=2 || !Set.of("up","down","remove").contains(parts[0])) throw new IllegalArgumentException("command");
            int index=Integer.parseInt(parts[1]);
            if(index<0 || index>=rows.size()) throw new IllegalArgumentException("row");
            if(parts[0].equals("remove")) rows.remove(index);
            else {
                int step=parts[0].equals("up") ? -1 : 1;
                for(int candidate=index+step;candidate>=0 && candidate<rows.size();candidate+=step) {
                    if(rows.get(candidate).day()==rows.get(index).day()) { Collections.swap(rows,index,candidate); break; }
                }
            }
        }
        return new Draft(draft.name(),draft.description(),draft.type(),draft.rotation(),draft.dayCount(),draft.revision(),List.copyOf(rows));
    }

    @Transactional
    public Schedule create(MultiValueMap<String,String> fields,User user) {
        Schedule schedule=new Schedule(); Draft draft=read(fields,schedule);
        validateMetadata(draft);
        if(!draft.movements().isEmpty()) throw new IllegalArgumentException("rows");
        schedule.setUser(user); applyMetadata(schedule,draft); return schedules.save(schedule);
    }

    @Transactional
    public void save(Long id,Draft draft,User user) {
        Schedule schedule=schedules.findOwnedForDeployment(id,user.getId()).orElseThrow(()->new IllegalArgumentException("owner"));
        entityManager.refresh(schedule);
        if(!Objects.equals(draft.revision(),revision(schedule))) throw new IllegalArgumentException("stale");
        validateMetadata(draft);
        Map<Long,ScheduleEntry> saved=new HashMap<>();
        for(ScheduleEntry entry:entries.findBySchedule(schedule)) saved.put(entry.getId(),entry);
        Set<Long> used=new HashSet<>(); List<ScheduleEntry> resolved=new ArrayList<>();
        Map<Integer,Integer> order=new HashMap<>();
        // Resolve every reference before altering any managed record.
        List<Object> references=new ArrayList<>();
        for(Movement row:draft.movements()) {
            if(row.day()<1 || row.day()>draft.dayCount() || (row.entryId()!=null && (!saved.containsKey(row.entryId()) || !used.add(row.entryId()))))
                throw new IllegalArgumentException("row");
            references.add(movement(row.key(),user));
        }
        for(int index=0;index<draft.movements().size();index++) {
            Movement row=draft.movements().get(index); Object reference=references.get(index);
            ScheduleEntry entry=row.entryId()==null ? new ScheduleEntry() : saved.get(row.entryId());
            entry.setSchedule(schedule); entry.setDayOfWeek(row.day()); entry.setOrderNumber(order.getOrDefault(row.day(),0));
            order.merge(row.day(),1,Integer::sum);
            entry.setExercise(reference instanceof Exercise exercise ? exercise : null);
            entry.setCustomExercise(reference instanceof CustomExercise custom ? custom : null); resolved.add(entry);
        }
        applyMetadata(schedule,draft);
        schedules.save(schedule);
        for(ScheduleEntry existing:saved.values()) if(!used.contains(existing.getId())) entries.delete(existing);
        entries.saveAll(resolved);
    }

    @Transactional
    public void appendLegacy(Long id,Map<String,String> fields,User user) {
        Schedule schedule=schedules.findOwnedForDeployment(id,user.getId()).orElseThrow(()->new IllegalArgumentException("owner"));
        entityManager.refresh(schedule);
        // Legacy callers may append a catalogue movement; posted entity IDs and nested entities never bind.
        if(fields.containsKey("id") || fields.containsKey("schedule.id") || fields.containsKey("customExercise.id"))
            throw new IllegalArgumentException("binding");
        int day=Integer.parseInt(fields.getOrDefault("dayOfWeek","0"));
        if(day<1 || day>cycleDays(schedule) || entries.findBySchedule(schedule).size()>=500) throw new IllegalArgumentException("day");
        Exercise exercise=(Exercise)movement("e:"+fields.getOrDefault("exercise.id",""),user);
        ScheduleEntry entry=new ScheduleEntry(); entry.setSchedule(schedule); entry.setExercise(exercise); entry.setDayOfWeek(day);
        entry.setOrderNumber(entries.findBySchedule(schedule).stream().filter(row->row.getDayOfWeek()==day)
                .mapToInt(ScheduleEntry::getOrderNumber).max().orElse(-1)+1); entries.save(entry);
    }

    private Object movement(String key,User user) {
        String[] parts=key.split(":",-1);
        if(parts.length!=2) throw new IllegalArgumentException("movement");
        Long id=positiveId(parts[1]);
        if(parts[0].equals("e")) return exercises.findById(id).orElseThrow(()->new IllegalArgumentException("movement"));
        if(parts[0].equals("c")) {
            CustomExercise movement=customs.findById(id).orElseThrow(()->new IllegalArgumentException("movement"));
            if(Objects.equals(movement.getUserId(),user.getId())) return movement;
        }
        throw new IllegalArgumentException("movement");
    }

    private String revision(Schedule schedule) {
        StringBuilder content=new StringBuilder();
        for(Object value:new Object[]{schedule.getId(),schedule.getName(),schedule.getDescription(),schedule.getScheduleType(),
                schedule.getRotationMode(),schedule.getCustomDayCount(),schedule.getTemplateId()}) {
            String text=Objects.toString(value,""); content.append(text.length()).append('#').append(text).append('|');
        }
        entries.findBySchedule(schedule).stream().sorted(Comparator.comparing(ScheduleEntry::getId)).forEach(row->content.append('|')
                .append(row.getId()).append(':').append(row.getDayOfWeek()).append(':').append(row.getOrderNumber()).append(':')
                .append(row.getExercise()==null ? "" : row.getExercise().getId()).append(':')
                .append(row.getCustomExercise()==null ? "" : row.getCustomExercise().getId()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static Long positiveId(String raw) { long id=Long.parseLong(raw); if(id<1) throw new IllegalArgumentException("id"); return id; }
    private static void validateMetadata(Draft draft) {
        if(draft.name()==null || draft.name().trim().isBlank() || draft.name().trim().length()>200 || draft.description()==null
                || draft.description().trim().length()>500 || draft.dayCount()<1 || draft.dayCount()>14 || draft.movements().size()>500
                || draft.type()==null || draft.rotation()==null || (draft.type()==ScheduleType.DAILY && draft.dayCount()!=1)
                || (draft.type()==ScheduleType.WEEKLY && draft.dayCount()!=7)) throw new IllegalArgumentException("fields");
    }
    private static void applyMetadata(Schedule schedule,Draft draft) {
        schedule.setName(draft.name().trim()); schedule.setDescription(draft.description().trim().isBlank() ? null : draft.description().trim());
        schedule.setScheduleType(draft.type()); schedule.setRotationMode(draft.rotation()); schedule.setCustomDayCount(draft.dayCount());
    }
    private static String value(MultiValueMap<String,String> fields,String key,String fallback) {
        String value=fields.getFirst(key); return value==null ? fallback : value;
    }
}
