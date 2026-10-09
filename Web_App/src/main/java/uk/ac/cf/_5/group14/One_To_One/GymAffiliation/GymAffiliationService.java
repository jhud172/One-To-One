package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.GymProfile.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class GymAffiliationService {
    private final UserRepository users;
    private final GymProfileRepository gyms;
    private final GymAffiliationRepository affiliations;
    private final GymAffiliationEventRepository events;
    private final EntityManager entities;

    @Transactional
    public void invite(Long adminId, String trainerUsername) {
        User admin = actor(adminId, Role.GYM_ADMIN);
        if (trainerUsername == null || !trainerUsername.matches("[A-Za-z0-9_]{3,20}")) throw new IllegalArgumentException("Use the trainer's registered username.");
        User trainer = users.findByUsernameIgnoreCase(trainerUsername)
            .filter(user -> user.getRole() == Role.TRAINER && user.isEnabled())
            .orElseThrow(() -> new IllegalArgumentException("A trainer account with this username could not be found."));
        request(admin, lockTrainer(trainer.getId()), gymFor(admin), "GYM");
    }

    @Transactional
    public void requestToJoin(Long trainerId, String gymUsername) {
        User trainer = lockTrainer(trainerId);
        if (gymUsername == null || !gymUsername.matches("[A-Za-z0-9_]{3,20}")) throw new IllegalArgumentException("Use the gym's registered username.");
        User admin = users.findByUsernameIgnoreCase(gymUsername)
            .filter(user -> user.getRole() == Role.GYM_ADMIN && user.isEnabled())
            .orElseThrow(() -> new IllegalArgumentException("A gym account with this username could not be found."));
        request(trainer, trainer, gymFor(admin), "TRAINER");
    }

    private void request(User actor, User trainer, GymProfile gym, String initiator) {
        TrainerGymAffiliation row = affiliations.findByTrainerUserIdAndGymId(trainer.getId(), gym.getId()).orElse(null);
        if (row == null && Objects.equals(trainer.getGymId(), gym.getId())) {
            throw new IllegalStateException("This gym connection already exists.");
        }
        if (row != null && (row.getStatus() == GymAffiliationStatus.ACTIVE || row.getStatus() == GymAffiliationStatus.PENDING)) {
            throw new IllegalStateException("An active connection or pending request already exists.");
        }
        if (row == null) row = new TrainerGymAffiliation(trainer.getId(), gym.getId(), GymAffiliationStatus.PENDING, initiator);
        row.setStatus(GymAffiliationStatus.PENDING); row.setInitiatedBy(initiator); row.setUpdatedAt(Instant.now());
        row = affiliations.saveAndFlush(row);
        events.save(new GymAffiliationEvent(row.getId(), actor.getId(), "REQUESTED"));
    }

    @Transactional
    public void decide(Long actorId, Long trainerId, Long gymId, String action) {
        User actor = users.findById(actorId).filter(User::isEnabled).orElseThrow(() -> new IllegalArgumentException("Access denied."));
        boolean trainerSide = actor.getRole() == Role.TRAINER && actor.getId().equals(trainerId);
        boolean gymSide = actor.getRole() == Role.GYM_ADMIN && Objects.equals(actor.getGymId(), gymId) && gymFor(actor).getId().equals(gymId);
        if (!trainerSide && !gymSide) throw new IllegalArgumentException("Access denied.");
        if (action == null || !Set.of("accept", "decline", "cancel", "end").contains(action)) throw new IllegalArgumentException("Choose a valid connection action.");
        User trainer = lockTrainer(trainerId);
        TrainerGymAffiliation row = affiliations.findByTrainerUserIdAndGymId(trainerId, gymId).orElse(null);
        if (row == null && Objects.equals(trainer.getGymId(), gymId)) row = materialiseLegacy(trainer, gymId);
        if (row == null) throw new IllegalArgumentException("Connection not found.");
        entities.refresh(row);
        boolean incoming = trainerSide ? row.getInitiatedBy().equals("GYM") : row.getInitiatedBy().equals("TRAINER");
        GymAffiliationStatus next;
        String event;
        if (row.getStatus() == GymAffiliationStatus.PENDING && incoming && action.equals("accept")) {
            next = GymAffiliationStatus.ACTIVE; event = "ACCEPTED";
        } else if (row.getStatus() == GymAffiliationStatus.PENDING && incoming && action.equals("decline")) {
            next = GymAffiliationStatus.DECLINED; event = "DECLINED";
        } else if (row.getStatus() == GymAffiliationStatus.PENDING && !incoming && action.equals("cancel")) {
            next = GymAffiliationStatus.ENDED; event = "CANCELLED";
        } else if (row.getStatus() == GymAffiliationStatus.ACTIVE && action.equals("end")) {
            next = GymAffiliationStatus.ENDED; event = "ENDED";
        } else {
            throw new IllegalStateException("This action is unavailable for the connection's current state.");
        }
        row.setStatus(next); row.setUpdatedAt(Instant.now());
        affiliations.save(row); events.save(new GymAffiliationEvent(row.getId(), actorId, event));
        // The primary legacy gym field is retained; canonical reads honour an explicit ended record.
    }

    @Transactional(readOnly = true)
    public List<Connection> connections(User viewer) {
        boolean trainerSide = viewer.getRole() == Role.TRAINER;
        if (!trainerSide && viewer.getRole() != Role.GYM_ADMIN) throw new IllegalArgumentException("Access denied.");
        Long gymId = trainerSide ? null : gymFor(viewer).getId();
        List<TrainerGymAffiliation> rows = trainerSide ? affiliations.findByTrainerUserIdOrderByUpdatedAtDesc(viewer.getId()) : affiliations.findByGymIdOrderByUpdatedAtDesc(gymId);
        List<Connection> result = new ArrayList<>();
        Map<Long, User> trainerNames = new HashMap<>();
        users.findAllById(rows.stream().map(TrainerGymAffiliation::getTrainerUserId).distinct().toList()).forEach(user -> trainerNames.put(user.getId(), user));
        Map<Long, GymProfile> gymNames = new HashMap<>();
        gyms.findAllById(rows.stream().map(TrainerGymAffiliation::getGymId).distinct().toList()).forEach(gym -> gymNames.put(gym.getId(), gym));
        for (TrainerGymAffiliation row : rows) {
            User trainer = trainerNames.get(row.getTrainerUserId()); GymProfile gym = gymNames.get(row.getGymId());
            if (trainer == null || gym == null) continue;
            result.add(connection(row.getTrainerUserId(), row.getGymId(), row.getStatus() == GymAffiliationStatus.ACTIVE ? trainer.getFullName() : trainer.getUsername(), gym.getGymName(), row.getStatus().name(),
                trainerSide ? row.getInitiatedBy().equals("GYM") : row.getInitiatedBy().equals("TRAINER"), false, row.getUpdatedAt()));
        }
        // Local/H2 installs and older accounts retain their existing link before production backfill.
        List<User> legacy = trainerSide ? List.of(viewer) : users.findByRoleAndGymId(Role.TRAINER, gymId);
        for (User trainer : legacy) {
            Long legacyGymId = trainer.getGymId();
            if (legacyGymId == null || (!trainerSide && !legacyGymId.equals(gymId)) || affiliations.findByTrainerUserIdAndGymId(trainer.getId(), legacyGymId).isPresent()) continue;
            gyms.findById(legacyGymId).ifPresent(gym -> result.add(connection(trainer.getId(), gym.getId(), trainer.getFullName(), gym.getGymName(), "ACTIVE", false, true, null)));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<GymAffiliationEvent> history(User viewer, Long trainerId, Long gymId) {
        boolean own = viewer.getRole() == Role.TRAINER && viewer.getId().equals(trainerId);
        boolean ownGym = viewer.getRole() == Role.GYM_ADMIN && Objects.equals(viewer.getGymId(), gymId) && gymFor(viewer).getId().equals(gymId);
        if (!own && !ownGym) throw new IllegalArgumentException("Access denied.");
        var connection = affiliations.findByTrainerUserIdAndGymId(trainerId, gymId);
        if (connection.isPresent()) return events.findByAffiliationIdOrderByCreatedAtDescIdDesc(connection.get().getId());
        if (users.findById(trainerId).filter(user -> user.getRole() == Role.TRAINER && Objects.equals(user.getGymId(), gymId)).isPresent()
                && gyms.existsById(gymId)) return List.of();
        throw new IllegalArgumentException("Connection not found.");
    }

    private Connection connection(Long trainerId, Long gymId, String trainerName, String gymName, String status, boolean incoming, boolean legacy, Instant updated) {
        return new Connection(trainerId, gymId, trainerName, gymName, status, incoming, legacy, updated);
    }
    private TrainerGymAffiliation materialiseLegacy(User trainer, Long gymId) {
        TrainerGymAffiliation row = affiliations.saveAndFlush(new TrainerGymAffiliation(trainer.getId(), gymId, GymAffiliationStatus.ACTIVE, "LEGACY"));
        events.save(new GymAffiliationEvent(row.getId(), null, "LEGACY_IMPORTED"));
        return row;
    }
    private User actor(Long id, Role role) {
        return users.findById(id).filter(user -> user.getRole() == role && user.isEnabled()).orElseThrow(() -> new IllegalArgumentException("Access denied."));
    }
    private User lockTrainer(Long id) {
        User trainer = users.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Trainer not found."));
        entities.refresh(trainer);
        if (trainer.getRole() != Role.TRAINER || !trainer.isEnabled()) throw new IllegalArgumentException("Trainer unavailable.");
        return trainer;
    }
    private GymProfile gymFor(User admin) {
        if (admin.getGymId() == null) throw new IllegalArgumentException("Access denied.");
        return gyms.findById(admin.getGymId()).filter(gym -> gym.getUserId().equals(admin.getId())).orElseThrow(() -> new IllegalArgumentException("Access denied."));
    }
    public record Connection(Long trainerId, Long gymId, String trainerName, String gymName, String status, boolean incoming, boolean legacy, Instant updatedAt) {}
}
