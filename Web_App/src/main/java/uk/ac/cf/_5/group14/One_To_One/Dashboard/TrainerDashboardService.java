package uk.ac.cf._5.group14.One_To_One.Dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInRepository;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInStatus;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadStateRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

/** Read-only coaching priorities from current relationships; opening the dashboard marks nothing read. */
@Service
@RequiredArgsConstructor
public class TrainerDashboardService {
    private final TrainerClientLinkRepository links;
    private final WeeklyCheckInRepository checkIns;
    private final MessageReadStateRepository readStates;
    private final UserRepository users;
    private final Clock clock;

    public record Review(Long id, Long clientId, String clientName, LocalDate weekStart,
                         LocalDate submittedDate, long waitingDays) { }
    public record Summary(int activeClients, int pendingRequests, long awaitingReview,
                          long waitingSevenDays, long unreadMessages, List<Review> reviews) { }

    @Transactional(readOnly = true)
    public Summary forTrainer(User trainer) {
        var activeIds = links.findByTrainerUserIdAndStatusOrderByUpdatedAtDesc(trainer.getId(), TrainerClientLinkStatus.ACTIVE)
                .stream().map(TrainerClientLink::getClientUserId).distinct().toList();
        int pending = links.findPendingByTrainerId(trainer.getId()).size();
        long unread = readStates.countUnreadForActiveTrainer(trainer.getId());
        if (activeIds.isEmpty()) return new Summary(0, pending, 0, 0, unread, List.of());
        var oldest = checkIns.findTop5ByTrainerIdAndStatusAndClientIdInOrderBySubmittedAtAscIdAsc(
                trainer.getId(), WeeklyCheckInStatus.SUBMITTED, activeIds);
        Map<Long,User> clients = users.findAllById(oldest.stream().map(row -> row.getClientId()).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        var now = clock.instant();
        var reviews = oldest.stream().map(row -> new Review(row.getId(), row.getClientId(),
                displayName(clients.get(row.getClientId())), row.getWeekStartDate(),
                row.getSubmittedAt().atZone(clock.getZone()).toLocalDate(),
                Math.max(0, ChronoUnit.DAYS.between(row.getSubmittedAt(), now)))).toList();
        return new Summary(activeIds.size(), pending,
                checkIns.countByTrainerIdAndStatusAndClientIdIn(trainer.getId(), WeeklyCheckInStatus.SUBMITTED, activeIds),
                checkIns.countByTrainerIdAndStatusAndClientIdInAndSubmittedAtLessThanEqual(
                        trainer.getId(), WeeklyCheckInStatus.SUBMITTED, activeIds, now.minus(7, ChronoUnit.DAYS)),
                unread, reviews);
    }

    private String displayName(User user) {
        if (user == null) return "—";
        String name = java.util.stream.Stream.of(user.getFirstName(), user.getLastName())
                .filter(value -> value != null && !value.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        return name.isBlank() ? user.getUsername() : name;
    }
}
