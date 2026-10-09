package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Service
public class WorkoutFormFeedbackService {

    private static final long MAX_VIDEO_BYTES = 8L * 1024L * 1024L;

    private final WorkoutBuilderService workoutBuilderService;
    private final WorkoutSetLogRepository setLogRepository;
    private final WorkoutSetVideoRepository videoRepository;
    private final AiFormFeedbackRepository feedbackRepository;
    private final Path uploadRoot;

    public WorkoutFormFeedbackService(WorkoutBuilderService workoutBuilderService,
                                      WorkoutSetLogRepository setLogRepository,
                                      WorkoutSetVideoRepository videoRepository,
                                      AiFormFeedbackRepository feedbackRepository,
                                      @Value("${app.storage.workout-video-dir:uploads/workout-videos}") String uploadRoot) {
        this.workoutBuilderService = workoutBuilderService;
        this.setLogRepository = setLogRepository;
        this.videoRepository = videoRepository;
        this.feedbackRepository = feedbackRepository;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public WorkoutSetVideo storeVideo(User user, Long sessionId, Long setId, MultipartFile file) throws IOException {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("Workout video must be 8MB or smaller.");
        }

        VideoFormat videoFormat = detectVideoFormat(file);
        if (videoFormat == null) {
            throw new IllegalArgumentException("Unsupported video type");
        }

        WorkoutSession session = workoutBuilderService.getSession(user, sessionId);
        WorkoutSetLog setLog = setLogRepository.findByIdAndSession(setId, session)
                .orElseThrow(() -> new IllegalArgumentException("Set not found"));

        Path sessionUploadRoot = uploadRoot.resolve("user-" + user.getId()).resolve("session-" + sessionId).normalize();
        Files.createDirectories(sessionUploadRoot);

        String filename = "set-" + setId + "-" + System.currentTimeMillis() + videoFormat.extension();
        Path target = sessionUploadRoot.resolve(filename).normalize();
        if (!target.startsWith(sessionUploadRoot)) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        WorkoutSetVideo video = new WorkoutSetVideo();
        video.setSetLog(setLog);
        video.setStatus(VideoProcessingStatus.STORED);
        video.setPath("/uploads/workout-videos/user-" + user.getId() + "/session-" + sessionId + "/" + filename);
        try {
            return videoRepository.save(video);
        } catch (RuntimeException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public WorkoutSetVideo getLatestVideo(User user, Long sessionId, Long setId) {
        if (user == null || user.getId() == null) {
            return null;
        }
        WorkoutSession session = workoutBuilderService.getSession(user, sessionId);
        WorkoutSetLog setLog = setLogRepository.findByIdAndSession(setId, session).orElse(null);
        if (setLog == null) {
            return null;
        }
        return videoRepository.findTopBySetLogOrderByCreatedAtDesc(setLog).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, WorkoutSetVideo> listLatestVideos(User user, Long sessionId) {
        var session = workoutBuilderService.getSession(user, sessionId);
        var latest = new java.util.LinkedHashMap<Long, WorkoutSetVideo>();
        for (var video : videoRepository.findBySetLogSessionOrderByCreatedAtDescIdDesc(session)) {
            latest.putIfAbsent(video.getSetLog().getId(), video);
        }
        return latest;
    }

    @Transactional(rollbackFor = IOException.class)
    public void deleteVideo(User user, Long sessionId, Long setId, Long videoId) throws IOException {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        WorkoutSession session = workoutBuilderService.getSession(user, sessionId);
        WorkoutSetLog setLog = setLogRepository.findByIdAndSession(setId, session)
                .orElseThrow(() -> new IllegalArgumentException("Set not found"));
        WorkoutSetVideo video = videoRepository.findByIdAndSetLog(videoId, setLog)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));

        Path storedFile = resolveOwnedVideoPath(video.getPath(), user.getId(), sessionId);
        feedbackRepository.deleteByVideo(video);
        videoRepository.delete(video);
        Files.deleteIfExists(storedFile);
    }

    @Transactional(readOnly = true)
    public Path resolveOwnedVideo(User user, Long ownerUserId, Long sessionId, String filename) {
        if (user == null || user.getId() == null || !user.getId().equals(ownerUserId)) {
            throw new IllegalArgumentException("Video not found");
        }
        workoutBuilderService.getSession(user, sessionId);
        String videoUrl = "/uploads/workout-videos/user-" + ownerUserId
                + "/session-" + sessionId + "/" + (filename == null ? "" : filename);
        return resolveOwnedVideoPath(videoUrl, ownerUserId, sessionId);
    }

    public Map<String, Object> buildFeedbackPayload(WorkoutSetVideo video) {
        // No analysis provider exists. Historical placeholder scores must not be presented as results.
        if (video == null) return Map.of("status", "NONE", "analysisAvailable", false);
        return Map.of("status", "STORED", "videoId", video.getId(), "videoUrl", video.getPath(), "analysisAvailable", false);
    }

    private Path resolveOwnedVideoPath(String videoUrl, Long userId, Long sessionId) {
        String prefix = "/uploads/workout-videos/user-" + userId + "/session-" + sessionId + "/";
        if (videoUrl == null || !videoUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Invalid video path");
        }
        String filename = videoUrl.substring(prefix.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid video path");
        }
        Path sessionUploadRoot = uploadRoot.resolve("user-" + userId).resolve("session-" + sessionId).normalize();
        Path storedFile = sessionUploadRoot.resolve(filename).normalize();
        if (!storedFile.startsWith(sessionUploadRoot)) {
            throw new IllegalArgumentException("Invalid video path");
        }
        return storedFile;
    }

    private VideoFormat detectVideoFormat(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(16);
            if (header.length >= 12
                    && header[4] == 'f'
                    && header[5] == 't'
                    && header[6] == 'y'
                    && header[7] == 'p') {
                return VideoFormat.MP4;
            }
            if (header.length >= 4
                    && header[0] == (byte) 0x1A
                    && header[1] == (byte) 0x45
                    && header[2] == (byte) 0xDF
                    && header[3] == (byte) 0xA3) {
                return VideoFormat.WEBM;
            }
        }
        return null;
    }

    private enum VideoFormat {
        MP4(".mp4"),
        WEBM(".webm");

        private final String extension;

        VideoFormat(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }
}
