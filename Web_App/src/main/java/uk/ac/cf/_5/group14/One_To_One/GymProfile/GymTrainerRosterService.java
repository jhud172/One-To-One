package uk.ac.cf._5.group14.One_To_One.GymProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.Verification.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymTrainerRosterService {
    private final UserRepository users;
    private final GymWorkspaceAccessService workspace;
    private final TrainerVerificationRequestRepository requests;
    public static final int PAGE_SIZE = 20;
    public static final Set<String> REVIEW_FILTERS = Set.of("", "PENDING", "NEEDS_INFO", "APPROVED", "REJECTED", "NOT_SUBMITTED");

    public Optional<GymProfile> ownedGym(User admin) {
        return workspace.ownedGym(admin);
    }

    public Page<User> search(User admin, String search, String review, boolean verifiedOnly, int page) {
        var gym = ownedGym(admin).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Owned gym required"));
        if (search == null || search.length() > 120 || review == null || !REVIEW_FILTERS.contains(review) || page < 1) throw new IllegalArgumentException("Invalid trainer filters");
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = users.countGymRoster(gym.getId(), pattern, review, verifiedOnly);
        int selected = (int) Math.min(page - 1L, Math.max(0L, (total - 1) / PAGE_SIZE));
        var pageable = PageRequest.of(selected, PAGE_SIZE);
        return new PageImpl<>(total == 0 ? List.of() : users.findGymRoster(gym.getId(), pattern, review, verifiedOnly, pageable), pageable, total);
    }

    public Map<Long, TrainerVerificationRequest> latestReviews(User admin, Collection<User> trainers) {
        var gym = ownedGym(admin).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Owned gym required"));
        if (trainers.isEmpty()) return Map.of();
        if (trainers.size() > PAGE_SIZE) throw new IllegalArgumentException("Bounded roster required");
        // The final document query rechecks current affiliation as well as the owning gym.
        var ids = trainers.stream().map(User::getId).toList();
        Map<Long, TrainerVerificationRequest> latest = new HashMap<>();
        requests.findLatestForGymRoster(gym.getId(), ids).forEach(request -> latest.put(request.getTrainerUserId(), request));
        return Map.copyOf(latest);
    }
}
