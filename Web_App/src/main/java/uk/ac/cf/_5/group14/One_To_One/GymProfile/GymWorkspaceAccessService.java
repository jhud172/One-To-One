package uk.ac.cf._5.group14.One_To_One.GymProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymWorkspaceAccessService {
    private final GymProfileRepository gyms;

    public Optional<GymProfile> ownedGym(User admin) {
        if (admin == null || admin.getRole() != Role.GYM_ADMIN || !admin.isEnabled() || admin.getGymId() == null) return Optional.empty();
        return gyms.findById(admin.getGymId()).filter(gym -> Objects.equals(gym.getUserId(), admin.getId()));
    }
}
