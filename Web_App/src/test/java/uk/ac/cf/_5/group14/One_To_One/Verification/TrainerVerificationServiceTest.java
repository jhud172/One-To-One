package uk.ac.cf._5.group14.One_To_One.Verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainerVerificationServiceTest {
    
    @Mock
    private TrainerVerificationRequestRepository verificationRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @Mock private jakarta.persistence.EntityManager entities;
    @Mock private VerificationEventRepository events;

    @InjectMocks
    private TrainerVerificationService verificationService;
    
    private User testTrainer;
    private User testAdmin;
    
    @BeforeEach
    void setUp() {
        testTrainer = new User();
        testTrainer.setEmail("trainer@example.com");
        testTrainer.setFirstName("John");
        testTrainer.setLastName("Trainer");
        testTrainer.setTrainerVerified(false);
        testTrainer.setGymId(100L);
        lenient().when(userRepository.isTrainerAffiliatedWithGym(1L, 100L)).thenReturn(true);
        testTrainer.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER);
        
        testAdmin = new User();
        testAdmin.setEmail("admin@example.com");
        testAdmin.setFirstName("Admin");
        testAdmin.setLastName("User");
        testAdmin.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.PLATFORM_ADMIN);
        testAdmin.setId(100L);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(testAdmin));
    }
    
    @Test
    void testCreateVerificationRequest_Success() {
        Long trainerId = 1L;
        Long gymId = 100L;
        String notes = "Experienced trainer with certifications";
        
        when(userRepository.findByIdForUpdate(trainerId)).thenReturn(Optional.of(testTrainer));
        when(verificationRepository.existsByTrainerUserIdAndStatusIn(eq(trainerId), anyCollection()))
            .thenReturn(false);
        when(verificationRepository.save(any(TrainerVerificationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        TrainerVerificationRequest result = verificationService.createVerificationRequest(
            trainerId, gymId, notes
        );
        
        assertNotNull(result);
        assertEquals(trainerId, result.getTrainerUserId());
        assertEquals(gymId, result.getGymId());
        assertEquals(notes, result.getNotes());
        assertEquals(VerificationStatus.PENDING, result.getStatus());
        
        verify(verificationRepository, times(1)).save(any(TrainerVerificationRequest.class));
    }
    
    @Test
    void testCreateVerificationRequest_TrainerNotFound() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            verificationService.createVerificationRequest(1L, 100L, "Notes");
        });
        
        assertEquals("Trainer not found", exception.getMessage());
    }
    
    @Test
    void testCreateVerificationRequest_DuplicatePendingRequest() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTrainer));
        when(verificationRepository.existsByTrainerUserIdAndStatusIn(eq(1L), anyCollection()))
            .thenReturn(true);
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            verificationService.createVerificationRequest(1L, 100L, "Notes");
        });
        
        assertEquals("An open verification request already exists for this trainer",
            exception.getMessage());
    }
    
    @Test
    void testApproveTrainer_Success() {
        Long requestId = 1L;
        Long adminId = 100L;
        String adminNotes = "All credentials verified";
        
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setTrainerUserId(1L);
        request.setStatus(VerificationStatus.PENDING);
        
        when(verificationRepository.findLockedById(requestId)).thenReturn(Optional.of(request));
        when(verificationRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTrainer));
        when(verificationRepository.save(any(TrainerVerificationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        TrainerVerificationRequest result = verificationService.approveTrainer(
            requestId, adminId, adminNotes
        );
        
        assertNotNull(result);
        assertEquals(VerificationStatus.APPROVED, result.getStatus());
        assertEquals(adminId, result.getReviewedByUserId());
        assertEquals(adminNotes, result.getAdminNotes());
        assertNotNull(result.getReviewedAt());
        
        // Verify trainer was marked as verified
        assertTrue(testTrainer.isTrainerVerified());
        verify(userRepository, times(1)).save(testTrainer);
        
        // Verify email was sent
        verify(emailService, times(1)).sendTrainerVerificationUpdate(
            eq(testTrainer),
            eq("APPROVED"),
            eq(adminNotes)
        );
    }
    
    @Test
    void testApproveTrainer_AlreadyApproved() {
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setTrainerUserId(1L);
        request.setStatus(VerificationStatus.APPROVED);
        
        when(verificationRepository.findLockedById(1L)).thenReturn(Optional.of(request));
        when(verificationRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTrainer));
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            verificationService.approveTrainer(1L, 100L, "Notes");
        });
        
        assertEquals("Can only approve pending or needs-info requests", exception.getMessage());
    }
    
    @Test
    void testRejectTrainer_Success() {
        Long requestId = 1L;
        Long adminId = 100L;
        String adminNotes = "Missing required certifications";
        
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setTrainerUserId(1L);
        request.setStatus(VerificationStatus.PENDING);
        
        when(verificationRepository.findLockedById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testTrainer));
        when(verificationRepository.save(any(TrainerVerificationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        TrainerVerificationRequest result = verificationService.rejectTrainer(
            requestId, adminId, adminNotes
        );
        
        assertNotNull(result);
        assertEquals(VerificationStatus.REJECTED, result.getStatus());
        assertEquals(adminId, result.getReviewedByUserId());
        assertEquals(adminNotes, result.getAdminNotes());
        assertNotNull(result.getReviewedAt());
        
        // Verify trainer remains unverified
        assertFalse(testTrainer.isTrainerVerified());
        
        // Verify email was sent
        verify(emailService, times(1)).sendTrainerVerificationUpdate(
            eq(testTrainer),
            eq("REJECTED"),
            eq(adminNotes)
        );
    }
    
    @Test
    void testRequestMoreInfo_Success() {
        Long requestId = 1L;
        Long adminId = 100L;
        String adminNotes = "Please provide proof of certification";
        
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setTrainerUserId(1L);
        request.setStatus(VerificationStatus.PENDING);
        
        when(verificationRepository.findLockedById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testTrainer));
        when(verificationRepository.save(any(TrainerVerificationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        TrainerVerificationRequest result = verificationService.requestMoreInfo(
            requestId, adminId, adminNotes
        );
        
        assertNotNull(result);
        assertEquals(VerificationStatus.NEEDS_INFO, result.getStatus());
        assertEquals(adminId, result.getReviewedByUserId());
        assertEquals(adminNotes, result.getAdminNotes());
        assertNotNull(result.getReviewedAt());
        
        // Verify email was sent
        verify(emailService, times(1)).sendTrainerVerificationUpdate(
            eq(testTrainer),
            eq("NEEDS_INFO"),
            eq(adminNotes)
        );
    }
    
    @Test
    void testRequestMoreInfo_MissingAdminNotes() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            verificationService.requestMoreInfo(1L, 100L, null);
        });
        
        assertEquals("Admin notes are required when requesting more info", exception.getMessage());
    }
    
    @Test
    void testUpdateTrainerNotes_Success() {
        Long requestId = 1L;
        String newNotes = "Updated certification proof attached";
        
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setStatus(VerificationStatus.NEEDS_INFO);
        request.setReviewedByUserId(100L);
        
        when(verificationRepository.findLockedById(requestId)).thenReturn(Optional.of(request));
        when(verificationRepository.save(any(TrainerVerificationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        TrainerVerificationRequest result = verificationService.updateTrainerNotes(
            requestId, newNotes
        );
        
        assertNotNull(result);
        assertEquals(newNotes, result.getNotes());
        assertEquals(VerificationStatus.PENDING, result.getStatus());
        assertNull(result.getReviewedAt());
        assertNull(result.getReviewedByUserId());
    }
    
    @Test
    void testUpdateTrainerNotes_InvalidStatus() {
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setStatus(VerificationStatus.APPROVED);
        
        when(verificationRepository.findLockedById(1L)).thenReturn(Optional.of(request));
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            verificationService.updateTrainerNotes(1L, "New notes");
        });
        
        assertEquals("Can only update notes for needs-info requests", exception.getMessage());
    }
    
    @Test
    void testGetPendingRequests() {
        TrainerVerificationRequest req1 = new TrainerVerificationRequest();
        req1.setStatus(VerificationStatus.PENDING);
        
        TrainerVerificationRequest req2 = new TrainerVerificationRequest();
        req2.setStatus(VerificationStatus.PENDING);
        
        List<TrainerVerificationRequest> pendingRequests = Arrays.asList(req1, req2);
        
        when(verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING))
            .thenReturn(pendingRequests);
        
        List<TrainerVerificationRequest> result = verificationService.getPendingRequests();
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }
    
    @Test
    void testIsTrainerVerified_True() {
        testTrainer.setTrainerVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testTrainer));
        
        boolean result = verificationService.isTrainerVerified(1L);
        
        assertTrue(result);
    }
    
    @Test
    void testIsTrainerVerified_False() {
        testTrainer.setTrainerVerified(false);
        testTrainer.setRole(uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testTrainer));
        
        boolean result = verificationService.isTrainerVerified(1L);
        
        assertFalse(result);
    }
    
    @Test
    void testIsTrainerVerified_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        
        boolean result = verificationService.isTrainerVerified(1L);
        
        assertFalse(result);
    }

    @Test
    void queueAcceptsImmutableRepositoryListsAndSortsWithoutMutatingThem() {
        TrainerVerificationRequest older = new TrainerVerificationRequest(); older.setSubmittedAt(java.time.Instant.parse("2026-09-01T10:00:00Z"));
        TrainerVerificationRequest newer = new TrainerVerificationRequest(); newer.setSubmittedAt(java.time.Instant.parse("2026-09-02T10:00:00Z"));
        when(verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING)).thenReturn(List.of(newer));
        when(verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.NEEDS_INFO)).thenReturn(List.of(older));
        assertEquals(List.of(older, newer), verificationService.getQueueRequests());
    }
}
