package uk.ac.cf._5.group14.One_To_One.Membership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymMembershipProductRepository extends JpaRepository<GymMembershipProduct, Long> {
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from GymMembershipProduct p where p.id = :id")
    Optional<GymMembershipProduct> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select p from GymMembershipProduct p where p.gymId = :gymId order by p.createdAt desc, p.id desc")
    List<GymMembershipProduct> findByGymIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("gymId") Long gymId);

    @org.springframework.data.jpa.repository.Query("select p from GymMembershipProduct p where p.gymId = :gymId order by p.createdAt desc, p.id desc")
    Page<GymMembershipProduct> findByGymIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("gymId") Long gymId, Pageable pageable);
    
    List<GymMembershipProduct> findByGymIdAndActiveOrderByCreatedAtDesc(Long gymId, boolean active);
    
    long countByGymIdAndActive(Long gymId, boolean active);

    long countByGymId(Long gymId);

    String CATALOGUE_FILTER = "p.gymId = :gymId and (lower(p.name) like :pattern escape '!' or lower(coalesce(p.description, '')) like :pattern escape '!') and (:state = '' or (:state = 'ACTIVE' and p.active = true) or (:state = 'INACTIVE' and p.active = false))";

    @org.springframework.data.jpa.repository.Query("select count(p) from GymMembershipProduct p where " + CATALOGUE_FILTER)
    long countCatalogue(@org.springframework.data.repository.query.Param("gymId") Long gymId, @org.springframework.data.repository.query.Param("pattern") String pattern, @org.springframework.data.repository.query.Param("state") String state);

    @org.springframework.data.jpa.repository.Query("select p from GymMembershipProduct p where " + CATALOGUE_FILTER + " order by p.createdAt desc, p.id desc")
    List<GymMembershipProduct> findCatalogue(@org.springframework.data.repository.query.Param("gymId") Long gymId, @org.springframework.data.repository.query.Param("pattern") String pattern, @org.springframework.data.repository.query.Param("state") String state, Pageable pageable);

    Optional<GymMembershipProduct> findByIdAndGymId(Long id, Long gymId);
}
