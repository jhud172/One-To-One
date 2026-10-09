package uk.ac.cf._5.group14.One_To_One.GymProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.Verification.*;
import uk.ac.cf._5.group14.One_To_One.Membership.*;

@Service
@RequiredArgsConstructor
public class GymOperationsService {
    private final UserRepository users;
    private final TrainerVerificationRequestRepository requests;
    private final GymMembershipProductRepository products;
    private final GymMemberSubscriptionRepository subscriptions;
    private final GymTrainerRosterService roster;

    @Transactional(readOnly = true)
    public Snapshot forAdmin(User admin) {
        var gym = roster.ownedGym(admin);
        if (gym.isEmpty()) return new Snapshot(false, 0, 0, 0, 0, 0, 0, null);
        Long gymId = gym.get().getId();
        return new Snapshot(true, users.countCurrentGymTrainers(gymId, false), users.countCurrentGymTrainers(gymId, true),
            requests.countLatestForCurrentGymRoster(gymId, VerificationStatus.PENDING),
            requests.countLatestForCurrentGymRoster(gymId, VerificationStatus.NEEDS_INFO),
            products.countByGymIdAndActive(gymId, true), subscriptions.countByGymIdAndStatus(gymId, SubscriptionStatus.ACTIVE), gym.get().getGymName());
    }

    public record Snapshot(boolean hasGym, long trainers, long verified, long pending, long needsInfo, long activeProducts, long subscribers, String gymName) {}
}
