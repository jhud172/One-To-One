package uk.ac.cf._5.group14.One_To_One.Verification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class VerificationDocumentController {
    private final VerificationEvidenceService evidence;

    @GetMapping("/verification/documents/{id}")
    @PreAuthorize("hasAnyRole('TRAINER', 'PLATFORM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            var file = evidence.download(id, principal.getUsername());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.bytes().length).body(file.bytes());
        } catch (IllegalArgumentException missing) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }
    }
}
