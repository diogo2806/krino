package br.com.krino.transport;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport/admin")
@PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_READ') or @authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_WRITE')")
public class UniversityTransportAdminController {

    private final UniversityTransportService service;

    public UniversityTransportAdminController(UniversityTransportService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public List<UniversityTransportService.RequestView> requests(@RequestParam(required = false) String status, Authentication authentication) {
        return service.reviewQueue(status, authentication);
    }

    @GetMapping("/requests/{requestId}")
    public UniversityTransportService.RequestView request(@PathVariable long requestId, Authentication authentication) {
        return service.reviewRequest(requestId, authentication);
    }

    @GetMapping("/requests/{requestId}/documents/{type}")
    public ResponseEntity<byte[]> document(@PathVariable long requestId, @PathVariable String type, Authentication authentication) {
        return UniversityTransportController.response(service.reviewDocument(requestId, type, authentication));
    }

    @PostMapping("/requests/{requestId}/start-review")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_WRITE')")
    public UniversityTransportService.RequestView startReview(@PathVariable long requestId, Authentication authentication) {
        return service.startReview(requestId, authentication);
    }

    @PostMapping("/requests/{requestId}/request-adjustment")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_WRITE')")
    public UniversityTransportService.RequestView requestAdjustment(@PathVariable long requestId, @RequestBody UniversityTransportService.ReasonInput input, Authentication authentication) {
        requireUnderReview(requestId, authentication);
        return service.requestAdjustment(requestId, input, authentication);
    }

    @PostMapping("/requests/{requestId}/deny")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_WRITE')")
    public UniversityTransportService.RequestView deny(@PathVariable long requestId, @RequestBody UniversityTransportService.ReasonInput input, Authentication authentication) {
        requireUnderReview(requestId, authentication);
        return service.deny(requestId, input, authentication);
    }

    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_REVIEW_WRITE')")
    public UniversityTransportService.RequestView approve(@PathVariable long requestId, @RequestBody UniversityTransportService.ApprovalInput input, Authentication authentication) {
        requireUnderReview(requestId, authentication);
        return service.approve(requestId, input, authentication);
    }

    @GetMapping("/requests/{requestId}/card")
    public UniversityTransportService.CardView card(@PathVariable long requestId, Authentication authentication) {
        return service.cardForReview(requestId, authentication);
    }

    @GetMapping("/card-art")
    public UniversityTransportService.CardArtView cardArt(Authentication authentication) {
        return service.cardArt(authentication);
    }

    @PutMapping("/card-art")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'TRANSPORT_CARD_ART_WRITE')")
    public UniversityTransportService.CardArtView updateCardArt(@RequestBody UniversityTransportService.CardArtInput input, Authentication authentication) {
        return service.updateCardArt(input, authentication);
    }

    private void requireUnderReview(long requestId, Authentication authentication) {
        if (!"UNDER_REVIEW".equals(service.reviewRequest(requestId, authentication).status())) {
            throw new IllegalArgumentException("Inicie a análise antes de registrar uma decisão.");
        }
    }
}
