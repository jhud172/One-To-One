package uk.ac.cf._5.group14.One_To_One.Users;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    String CURRENT_GYM_TRAINER = """
        u.role = uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER and (
          (u.gymId = :gymId and not exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId))
          or exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId and a.status = uk.ac.cf._5.group14.One_To_One.GymAffiliation.GymAffiliationStatus.ACTIVE)
        )
        """;
    String GYM_ROSTER_FILTER = CURRENT_GYM_TRAINER + """
        and (:verifiedOnly = false or u.trainerVerified = true)
        and (lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) like :pattern escape '!'
             or lower(u.username) like :pattern escape '!' or lower(u.email) like :pattern escape '!')
        and (:review = ''
          or (:review = 'NOT_SUBMITTED' and not exists (select r.id from TrainerVerificationRequest r where r.gymId = :gymId and r.trainerUserId = u.id))
          or exists (select r.id from TrainerVerificationRequest r where r.gymId = :gymId and r.trainerUserId = u.id and str(r.status) = :review
            and not exists (select newer.id from TrainerVerificationRequest newer where newer.gymId = :gymId and newer.trainerUserId = u.id
              and (newer.submittedAt > r.submittedAt or (newer.submittedAt = r.submittedAt and newer.id > r.id)))))
        """;

    @Query("select count(u) from User u where " + CURRENT_GYM_TRAINER + " and (:verifiedOnly = false or u.trainerVerified = true)")
    long countCurrentGymTrainers(@Param("gymId") Long gymId, @Param("verifiedOnly") boolean verifiedOnly);

    @Query("select count(u) from User u where " + GYM_ROSTER_FILTER)
    long countGymRoster(@Param("gymId") Long gymId, @Param("pattern") String pattern, @Param("review") String review, @Param("verifiedOnly") boolean verifiedOnly);

    @Query("select u from User u where " + GYM_ROSTER_FILTER + " order by u.firstName, u.lastName, u.id")
    List<User> findGymRoster(@Param("gymId") Long gymId, @Param("pattern") String pattern, @Param("review") String review,
                           @Param("verifiedOnly") boolean verifiedOnly, org.springframework.data.domain.Pageable pageable);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    List<User> findByRole(Role role);

    /** Accepted additional affiliations and explicit endings take precedence over the legacy primary gym. */
    @Query("""
        select u from User u where u.role = :role and (
          (u.gymId = :gymId and not exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId))
          or exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId and a.status = uk.ac.cf._5.group14.One_To_One.GymAffiliation.GymAffiliationStatus.ACTIVE)
        ) order by u.firstName, u.id
        """)
    List<User> findByRoleAndGymId(@Param("role") Role role, @Param("gymId") Long gymId);

    @Query("""
        select (count(u) > 0) from User u where u.id = :trainerId and u.role = uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER and (
          (u.gymId = :gymId and not exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId))
          or exists (select a.id from TrainerGymAffiliation a where a.trainerUserId = u.id and a.gymId = :gymId and a.status = uk.ac.cf._5.group14.One_To_One.GymAffiliation.GymAffiliationStatus.ACTIVE)
        )
        """)
    boolean isTrainerAffiliatedWithGym(@Param("trainerId") Long trainerId, @Param("gymId") Long gymId);

    List<User> findByRoleAndTrainerVerifiedTrue(Role role);

    List<User> findByRoleAndTrainerVerifiedTrueAndEnabledTrue(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
