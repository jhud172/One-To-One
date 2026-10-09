package uk.ac.cf._5.group14.One_To_One.Verification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.cf._5.group14.One_To_One.Users.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VerificationEvidenceService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DOCUMENTS = 5;
    private final VerificationDocumentRepository documents;
    private final VerificationEventRepository events;
    private final TrainerVerificationRequestRepository requests;
    private final UserRepository users;
    private final TrainerVerificationService verification;
    private final jakarta.persistence.EntityManager entities;

    @Transactional
    public void upload(Long requestId, Long trainerId, List<MultipartFile> files) {
        TrainerVerificationRequest request = editableRequest(requestId, trainerId);
        var selected = files == null ? List.<MultipartFile>of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (selected.isEmpty() || selected.size() + documents.countByRequestId(requestId) > MAX_DOCUMENTS) {
            throw new IllegalArgumentException("Select files within the five-document limit");
        }
        // Validate the entire batch before persisting any document. The database
        // transaction also rolls back metadata, bytes and history together.
        List<PreparedFile> prepared = selected.stream().map(this::prepare).toList();
        verification.preserveLegacySnapshot(request);
        for (PreparedFile file : prepared) {
            documents.save(new VerificationDocument(requestId, trainerId, file.name(), file.type(), file.bytes()));
            events.save(new VerificationEvent(request, trainerId, VerificationEvent.Action.DOCUMENT_ADDED, request.getStatus(), file.name()));
        }
    }

    @Transactional
    public void remove(Long requestId, Long trainerId, Long documentId) {
        TrainerVerificationRequest request = editableRequest(requestId, trainerId);
        var file = documents.findProjectedById(documentId).filter(summary -> Objects.equals(summary.getRequestId(), requestId))
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        verification.preserveLegacySnapshot(request);
        documents.deleteById(documentId);
        events.save(new VerificationEvent(request, trainerId, VerificationEvent.Action.DOCUMENT_REMOVED, request.getStatus(), file.getFileName()));
    }

    private TrainerVerificationRequest editableRequest(Long requestId, Long trainerId) {
        User trainer = users.findByIdForUpdate(trainerId).orElseThrow(() -> new AccessDeniedException("Trainer account required"));
        entities.refresh(trainer);
        if (!trainer.isEnabled() || trainer.getRole() != Role.TRAINER) throw new AccessDeniedException("Trainer account required");
        TrainerVerificationRequest request = requests.findLockedById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        entities.refresh(request);
        if (!Objects.equals(request.getTrainerUserId(), trainerId)) throw new IllegalArgumentException("Request not found");
        if (trainer.isTrainerVerified() || (request.getStatus() != VerificationStatus.PENDING && request.getStatus() != VerificationStatus.NEEDS_INFO)) {
            throw new IllegalStateException("Evidence can only be added to an open review");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<VerificationDocumentRepository.Summary>> summaries(List<TrainerVerificationRequest> ownedRequests) {
        if (ownedRequests.isEmpty()) return Map.of();
        return documents.findByRequestIdInOrderByCreatedAtAscIdAsc(ownedRequests.stream().map(TrainerVerificationRequest::getId).toList())
            .stream().collect(java.util.stream.Collectors.groupingBy(VerificationDocumentRepository.Summary::getRequestId));
    }

    @Transactional(readOnly = true)
    public Download download(Long documentId, String username) {
        User actor = users.findByUsername(username).orElseThrow(() -> new AccessDeniedException("Account required"));
        if (!actor.isEnabled()) throw new AccessDeniedException("Enabled account required");
        var metadata = documents.findProjectedById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        var request = requests.findById(metadata.getRequestId()).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        boolean reviewer = actor.getRole() == Role.PLATFORM_ADMIN || actor.getRole() == Role.SUPER_ADMIN;
        boolean owner = actor.getRole() == Role.TRAINER && Objects.equals(request.getTrainerUserId(), actor.getId());
        if (!reviewer && !owner) throw new IllegalArgumentException("Document not found");
        byte[] content = documents.findContentById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return new Download(metadata.getFileName(), content);
    }

    private PreparedFile prepare(MultipartFile upload) {
        if (upload.getSize() > MAX_BYTES) throw new IllegalArgumentException("Each document must be 2 MiB or smaller");
        try (InputStream input = upload.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid document size");
            String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
            if (header.startsWith("%PDF-1.") || header.startsWith("%PDF-2.")) {
                String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.US_ASCII);
                if (!tail.contains("%%EOF")) throw new IllegalArgumentException("Incomplete PDF document");
                // PDFs are kept intact and always downloaded as attachments;
                // header checks are format validation, not a malware scan.
                return new PreparedFile(safeName(upload.getOriginalFilename(), ".pdf"), "application/pdf", bytes);
            }
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw new IllegalArgumentException("Use a PDF, PNG or JPEG document");
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new IllegalArgumentException("Use PNG or JPEG images");
                    reader.setInput(imageInput, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 12_000_000) {
                        throw new IllegalArgumentException("Image dimensions exceed the supported limit");
                    }
                    BufferedImage decoded = reader.read(0);
                    BufferedImage clean = new BufferedImage(width, height, format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                    var graphics = clean.createGraphics();
                    try { graphics.drawImage(decoded, 0, 0, null); } finally { graphics.dispose(); }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    boolean png = format.equals("png");
                    if (!ImageIO.write(clean, png ? "png" : "jpeg", output) || output.size() > MAX_BYTES) throw new IllegalArgumentException("Invalid image size");
                    return new PreparedFile(safeName(upload.getOriginalFilename(), png ? ".png" : ".jpg"), png ? "image/png" : "image/jpeg", output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Unable to read this document", invalid);
        }
    }

    private String safeName(String original, String extension) {
        String name = original == null ? "qualification" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9 _.-]", "_");
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        name = name.replaceAll("^[ .]+|[ .]+$", "");
        if (name.isBlank()) name = "qualification";
        return name.substring(0, Math.min(name.length(), 120 - extension.length())) + extension;
    }

    private record PreparedFile(String name, String type, byte[] bytes) {}
    public record Download(String name, byte[] bytes) {}
}
