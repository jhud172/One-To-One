package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrainerReviewServiceTest {

    @Autowired
    private TrainerReviewService reviewService;

    @Autowired
    private TrainerReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainerClientLinkRepository linkRepository;

    private User trainer;
    private User client;
    private TrainerClientLink activeLink;

    @BeforeEach
    void setUp() {
        // Create trainer
        trainer = new User();
        trainer.setFirstName("John");
        trainer.setLastName("Trainer");
        trainer.setEmail("trainer@test.com");
        trainer.setUsername("trainer123");
        trainer.setPassword("password123");
        trainer.setRole(Role.TRAINER);
        trainer.setTrainerVerified(true);
        trainer = userRepository.save(trainer);

        // Create client
        client = new User();
        client.setFirstName("Jane");
        client.setLastName("Client");
        client.setEmail("client@test.com");
        client.setUsername("client123");
        client.setPassword("password123");
        client.setRole(Role.CLIENT);
        client = userRepository.save(client);

        // Create active link
        activeLink = new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE);
        activeLink.setActivatedAt(java.time.Instant.now());
        activeLink = linkRepository.save(activeLink);
    }

    @Test
    void testClientWithActiveLinkCanReview() {
        // GIVEN: Client has active link with trainer
        boolean canReview = reviewService.canClientReviewTrainer(client.getId(), trainer.getId());

        // THEN: Client can leave a review
        assertThat(canReview).isTrue();
    }

    @Test
    void testClientCanOnlyReviewOncePerLink() {
        // GIVEN: Client has active link with trainer
        // WHEN: Client leaves a review
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 5, "Professional,Knowledgeable", "Great trainer!");

        // THEN: Review is created successfully
        assertThat(review).isNotNull();
        assertThat(review.getStars()).isEqualTo(5);
        assertThat(review.getTrainerId()).isEqualTo(trainer.getId());
        assertThat(review.getClientId()).isEqualTo(client.getId());

        // AND: Client cannot leave another review for the same link
        assertThatThrownBy(() -> {
            reviewService.createReview(client.getId(), trainer.getId(), 4, null, "Another review");
        }).isInstanceOf(TrainerReviewException.class)
          .extracting(ex -> ((TrainerReviewException) ex).getReason())
          .isEqualTo(TrainerReviewException.Reason.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void testClientWithEndedLinkCanReview() {
        // GIVEN: Client has ended link with trainer
        activeLink.setStatus(TrainerClientLinkStatus.ENDED);
        linkRepository.save(activeLink);

        // WHEN: Check if client can review
        boolean canReview = reviewService.canClientReviewTrainer(client.getId(), trainer.getId());

        // THEN: Client can leave a review
        assertThat(canReview).isTrue();

        // AND: Client can create review
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 4, null, "Good experience");
        assertThat(review).isNotNull();
    }

    @Test
    void testClientWithoutLinkCannotReview() {
        // GIVEN: Another client without any link to trainer
        User anotherClient = new User();
        anotherClient.setFirstName("Bob");
        anotherClient.setLastName("NoLink");
        anotherClient.setEmail("nolink@test.com");
        anotherClient.setUsername("nolink123");
        anotherClient.setPassword("password123");
        anotherClient.setRole(Role.CLIENT);
        final User savedClient = userRepository.save(anotherClient);

        // WHEN: Check if client can review
        boolean canReview = reviewService.canClientReviewTrainer(savedClient.getId(), trainer.getId());

        // THEN: Client cannot review
        assertThat(canReview).isFalse();

        // AND: Attempting to create review throws exception
        assertThatThrownBy(() -> {
            reviewService.createReview(savedClient.getId(), trainer.getId(), 5, null, "Review");
        }).isInstanceOf(TrainerReviewException.class)
          .extracting(ex -> ((TrainerReviewException) ex).getReason())
          .isEqualTo(TrainerReviewException.Reason.LINK_NOT_ELIGIBLE);
    }

    @Test
    void testClientWithRequestedLinkCannotReview() {
        // GIVEN: Link is requested (not accepted yet)
        activeLink.setStatus(TrainerClientLinkStatus.REQUESTED);
        linkRepository.save(activeLink);

        // WHEN: Check if client can review
        boolean canReview = reviewService.canClientReviewTrainer(client.getId(), trainer.getId());

        // THEN: Client cannot review requested relationship
        assertThat(canReview).isFalse();
    }

    @Test
    void withdrawnRequestCannotBeReviewedAsPastCoaching() {
        activeLink.setStatus(TrainerClientLinkStatus.ENDED);
        activeLink.setActivatedAt(null);
        linkRepository.saveAndFlush(activeLink);
        assertThat(reviewService.canClientReviewTrainer(client.getId(), trainer.getId())).isFalse();
        assertThatThrownBy(() -> reviewService.createReview(client.getId(), trainer.getId(), 5, null, "Never coached"))
                .isInstanceOf(TrainerReviewException.class);
    }

    @Test
    void testClientWithPausedLinkCannotReview() {
        // GIVEN: Link is paused
        activeLink.setStatus(TrainerClientLinkStatus.PAUSED);
        linkRepository.save(activeLink);

        // WHEN: Check if client can review
        boolean canReview = reviewService.canClientReviewTrainer(client.getId(), trainer.getId());

        // THEN: Client cannot review paused relationship
        assertThat(canReview).isFalse();
    }

    @Test
    void testReviewMustHaveValidStarRating() {
        // WHEN/THEN: Creating review with invalid star rating
        assertThatThrownBy(() -> {
            reviewService.createReview(client.getId(), trainer.getId(), 0, null, "Invalid");
        }).isInstanceOf(Exception.class);

        assertThatThrownBy(() -> {
            reviewService.createReview(client.getId(), trainer.getId(), 6, null, "Invalid");
        }).isInstanceOf(Exception.class);
    }

    @Test
    void testGetAverageRating() {
        // GIVEN: Multiple reviews for trainer
        TrainerReview review1 = reviewService.createReview(client.getId(), trainer.getId(), 5, null, "Excellent");
        
        // Create another client and link
        User client2 = new User();
        client2.setFirstName("Alice");
        client2.setLastName("Client2");
        client2.setEmail("client2@test.com");
        client2.setUsername("client2");
        client2.setPassword("password123");
        client2.setRole(Role.CLIENT);
        client2 = userRepository.save(client2);

        TrainerClientLink link2 = new TrainerClientLink(client2.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE);
        link2 = linkRepository.save(link2);

        TrainerReview review2 = reviewService.createReview(client2.getId(), trainer.getId(), 3, null, "Average");

        // WHEN: Get average rating
        Double avgRating = reviewService.getAverageRating(trainer.getId());

        // THEN: Average should be (5 + 3) / 2 = 4.0
        assertThat(avgRating).isEqualTo(4.0);
    }

    @Test
    void testReviewStatusDefaultsToVisible() {
        // WHEN: Create a review
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 5, null, "Great!");

        // THEN: Status should be VISIBLE by default
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
    }

    @Test
    void testOnlyVisibleReviewsCountInAverage() {
        // GIVEN: Create a review and then hide it
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 5, null, "Great!");
        
        // Create admin user
        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmail("admin@test.com");
        admin.setUsername("admin");
        admin.setPassword("password123");
        admin.setRole(Role.PLATFORM_ADMIN);
        admin = userRepository.save(admin);

        // Hide the review
        reviewService.hideReview(review.getId(), admin.getId());

        // WHEN: Get average rating
        Double avgRating = reviewService.getAverageRating(trainer.getId());

        // THEN: Average should be 0.0 (no visible reviews)
        assertThat(avgRating).isEqualTo(0.0);

        // AND: Review count should be 0
        long count = reviewService.getReviewCount(trainer.getId());
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testOnlyAdminsCanHideReviews() {
        // GIVEN: A review exists
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 5, null, "Great!");

        // WHEN/THEN: Non-admin user tries to hide review
        assertThatThrownBy(() -> {
            reviewService.hideReview(review.getId(), client.getId());
        }).isInstanceOf(AccessDeniedException.class);

        // AND: Trainer (non-admin) tries to hide review
        assertThatThrownBy(() -> {
            reviewService.hideReview(review.getId(), trainer.getId());
        }).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void testTrainerCannotDeleteOwnReviews() {
        // This is enforced by service design - trainers can only report reviews
        // The hideReview method requires admin role, so this test validates 
        // that trainers are prevented from modifying review visibility
        
        // GIVEN: A review exists
        TrainerReview review = reviewService.createReview(client.getId(), trainer.getId(), 3, null, "Average");

        // WHEN/THEN: Trainer tries to hide the review
        assertThatThrownBy(() -> {
            reviewService.hideReview(review.getId(), trainer.getId());
        }).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Only admins can hide reviews");
    }
}
