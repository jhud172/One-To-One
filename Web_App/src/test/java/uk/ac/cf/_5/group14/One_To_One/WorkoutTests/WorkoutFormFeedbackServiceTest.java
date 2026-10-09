package uk.ac.cf._5.group14.One_To_One.WorkoutTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import uk.ac.cf._5.group14.One_To_One.Config.DatabaseTableAvailability;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Workouts.AiFormFeedbackRepository;
import uk.ac.cf._5.group14.One_To_One.Workouts.VideoProcessingStatus;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutBuilderService;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutFormFeedbackService;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutSetLog;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutSetLogRepository;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutSetVideo;
import uk.ac.cf._5.group14.One_To_One.Workouts.WorkoutSetVideoRepository;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkoutFormFeedbackServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeVideo_rejectsFilesWithoutKnownVideoSignature() throws Exception {
        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                mock(WorkoutBuilderService.class),
                mock(WorkoutSetLogRepository.class),
                mock(WorkoutSetVideoRepository.class),
                mock(AiFormFeedbackRepository.class),
                tempDir.toString());

        User user = new User();
        user.setId(9L);

        MockMultipartFile upload = new MockMultipartFile("video", "clip.mp4", "video/mp4", "not-a-video".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.storeVideo(user, 1L, 2L, upload));
    }

    @Test
    void storeVideo_acceptsMp4SignatureAndPersistsStoredVideo() throws Exception {
        WorkoutBuilderService builderService = mock(WorkoutBuilderService.class);
        WorkoutSetLogRepository setLogRepository = mock(WorkoutSetLogRepository.class);
        WorkoutSetVideoRepository videoRepository = mock(WorkoutSetVideoRepository.class);

        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                builderService,
                setLogRepository,
                videoRepository,
                mock(AiFormFeedbackRepository.class),
                tempDir.toString());

        User user = new User();
        user.setId(9L);

        WorkoutSession session = new WorkoutSession();
        WorkoutSetLog setLog = new WorkoutSetLog();

        when(builderService.getSession(user, 11L)).thenReturn(session);
        when(setLogRepository.findByIdAndSession(7L, session)).thenReturn(Optional.of(setLog));
        when(videoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile upload = new MockMultipartFile("video", "clip.mp4", "video/mp4", mp4HeaderBytes());

        WorkoutSetVideo saved = service.storeVideo(user, 11L, 7L, upload);

        assertThat(saved.getStatus()).isEqualTo(VideoProcessingStatus.STORED);
        assertThat(saved.getPath()).contains("/uploads/workout-videos/user-9/session-11/");
        assertThat(tempDir.resolve("user-9/session-11")
                .resolve(Path.of(saved.getPath()).getFileName())).exists();
    }

    @Test
    void storeVideo_acceptsWebmSignatureAndUsesWebmExtension() throws Exception {
        WorkoutBuilderService builderService = mock(WorkoutBuilderService.class);
        WorkoutSetLogRepository setLogRepository = mock(WorkoutSetLogRepository.class);
        WorkoutSetVideoRepository videoRepository = mock(WorkoutSetVideoRepository.class);
        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                builderService,
                setLogRepository,
                videoRepository,
                mock(AiFormFeedbackRepository.class),
                tempDir.toString());
        User user = new User();
        user.setId(9L);
        WorkoutSession session = new WorkoutSession();
        WorkoutSetLog setLog = new WorkoutSetLog();
        when(builderService.getSession(user, 11L)).thenReturn(session);
        when(setLogRepository.findByIdAndSession(7L, session)).thenReturn(Optional.of(setLog));
        when(videoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkoutSetVideo saved = service.storeVideo(
                user,
                11L,
                7L,
                new MockMultipartFile("video", "clip.webm", "video/webm", webmHeaderBytes()));

        assertThat(saved.getPath()).endsWith(".webm");
        assertThat(tempDir.resolve("user-9/session-11")
                .resolve(Path.of(saved.getPath()).getFileName())).exists();
    }

    @Test
    void storeVideo_rejectsFilesLargerThanEightMiBBeforeWriting() {
        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                mock(WorkoutBuilderService.class),
                mock(WorkoutSetLogRepository.class),
                mock(WorkoutSetVideoRepository.class),
                mock(AiFormFeedbackRepository.class),
                tempDir.toString());

        User user = new User();
        user.setId(9L);
        byte[] oversized = new byte[(8 * 1024 * 1024) + 1];
        System.arraycopy(mp4HeaderBytes(), 0, oversized, 0, mp4HeaderBytes().length);
        MockMultipartFile upload = new MockMultipartFile("video", "large.mp4", "video/mp4", oversized);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.storeVideo(user, 11L, 7L, upload));

        assertThat(error.getMessage()).contains("8MB");
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void deleteVideo_removesOwnedDatabaseRecordAndFile() throws Exception {
        WorkoutBuilderService builderService = mock(WorkoutBuilderService.class);
        WorkoutSetLogRepository setLogRepository = mock(WorkoutSetLogRepository.class);
        WorkoutSetVideoRepository videoRepository = mock(WorkoutSetVideoRepository.class);
        AiFormFeedbackRepository feedbackRepository = mock(AiFormFeedbackRepository.class);
        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                builderService,
                setLogRepository,
                videoRepository,
                feedbackRepository,
                tempDir.toString());
        User user = new User();
        user.setId(9L);
        WorkoutSession session = new WorkoutSession();
        WorkoutSetLog setLog = new WorkoutSetLog();
        WorkoutSetVideo video = new WorkoutSetVideo();
        video.setId(33L);
        video.setSetLog(setLog);
        video.setPath("/uploads/workout-videos/user-9/session-11/set-7-test.mp4");
        Path stored = tempDir.resolve("user-9/session-11/set-7-test.mp4");
        Files.createDirectories(stored.getParent());
        Files.write(stored, mp4HeaderBytes());
        when(builderService.getSession(user, 11L)).thenReturn(session);
        when(setLogRepository.findByIdAndSession(7L, session)).thenReturn(Optional.of(setLog));
        when(videoRepository.findByIdAndSetLog(33L, setLog)).thenReturn(Optional.of(video));

        service.deleteVideo(user, 11L, 7L, 33L);

        assertThat(stored).doesNotExist();
        verify(feedbackRepository).deleteByVideo(video);
        verify(videoRepository).delete(video);
    }

    @Test
    void deleteVideo_doesNotTouchAFileWhenTheSessionIsNotOwnedByTheUser() {
        WorkoutBuilderService builderService = mock(WorkoutBuilderService.class);
        WorkoutSetVideoRepository videoRepository = mock(WorkoutSetVideoRepository.class);
        WorkoutFormFeedbackService service = new WorkoutFormFeedbackService(
                builderService,
                mock(WorkoutSetLogRepository.class),
                videoRepository,
                mock(AiFormFeedbackRepository.class),
                tempDir.toString());
        User user = new User();
        user.setId(9L);
        when(builderService.getSession(user, 11L)).thenThrow(new IllegalArgumentException("Session not found"));

        assertThrows(IllegalArgumentException.class, () -> service.deleteVideo(user, 11L, 7L, 33L));

        verify(videoRepository, never()).delete(any());
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void historicalPlaceholderResultsAreNeverPresentedAsAnalysis() {
        var service = new WorkoutFormFeedbackService(mock(WorkoutBuilderService.class), mock(WorkoutSetLogRepository.class),
                mock(WorkoutSetVideoRepository.class), mock(AiFormFeedbackRepository.class), tempDir.toString());
        var video = new WorkoutSetVideo(); video.setId(22L); video.setStatus(VideoProcessingStatus.COMPLETE);
        video.setPath("/uploads/workout-videos/user-9/session-11/set-7-test.mp4");
        assertThat(service.buildFeedbackPayload(video)).containsEntry("status", "STORED").containsEntry("analysisAvailable", false)
                .doesNotContainKeys("feedback", "repCount", "confidence", "tempo", "flags");
        assertThat(service.buildFeedbackPayload(null)).containsEntry("status", "NONE").containsEntry("analysisAvailable", false);
    }

    private byte[] mp4HeaderBytes() {
        return new byte[] {
                0x00, 0x00, 0x00, 0x18,
                'f', 't', 'y', 'p',
                'i', 's', 'o', 'm',
                0x00, 0x00, 0x02, 0x00
        };
    }

    private byte[] webmHeaderBytes() {
        return new byte[] {
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
                0x01, 0x00, 0x00, 0x00
        };
    }
}
