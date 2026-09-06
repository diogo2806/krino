package br.com.krino.transport;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transport")
public class UniversityTransportController {

    private final UniversityTransportService service;

    public UniversityTransportController(UniversityTransportService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public List<UniversityTransportService.RequestView> myRequests(Authentication authentication) {
        return service.myRequests(authentication);
    }

    @GetMapping("/requests/{requestId}")
    public UniversityTransportService.RequestView myRequest(@PathVariable long requestId, Authentication authentication) {
        return service.myRequest(requestId, authentication);
    }

    @PostMapping("/requests")
    public UniversityTransportService.RequestView create(@RequestBody UniversityTransportService.RequestInput input, Authentication authentication) {
        return service.create(input, authentication);
    }

    @PutMapping("/requests/{requestId}")
    public UniversityTransportService.RequestView update(@PathVariable long requestId, @RequestBody UniversityTransportService.RequestInput input, Authentication authentication) {
        return service.update(requestId, input, authentication);
    }

    @PostMapping(value = "/requests/{requestId}/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UniversityTransportService.DocumentInfo uploadDocument(@PathVariable long requestId, @PathVariable String type, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return service.uploadDocument(requestId, type, file, authentication);
    }

    @GetMapping("/requests/{requestId}/documents/{type}")
    public ResponseEntity<byte[]> document(@PathVariable long requestId, @PathVariable String type, Authentication authentication) {
        return response(service.myDocument(requestId, type, authentication));
    }

    @PostMapping("/requests/{requestId}/submit")
    public UniversityTransportService.RequestView submit(@PathVariable long requestId, Authentication authentication) {
        return service.submit(requestId, authentication);
    }

    @GetMapping("/card")
    public UniversityTransportService.CardView card(Authentication authentication) {
        return service.myCard(authentication);
    }

    static ResponseEntity<byte[]> response(UniversityTransportService.StoredDocument document) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(document.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.inline().filename(document.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document.content());
    }
}
