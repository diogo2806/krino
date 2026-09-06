package br.com.krino.accesscontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/access-control")
public class StudentAccessControlController {

    private final StudentAccessCredentialService credentialService;
    private final StudentAccessEventService eventService;

    public StudentAccessControlController(StudentAccessCredentialService credentialService, StudentAccessEventService eventService) {
        this.credentialService = credentialService;
        this.eventService = eventService;
    }

    @PostMapping("/identify")
    public StudentAccessCredentialService.StudentIdentity identify(@Valid @RequestBody IdentifyRequest request, Authentication authentication) {
        return credentialService.identify(request.code(), authentication);
    }

    @PutMapping("/students/{studentId}/card")
    public StudentAccessCredentialService.CardView card(@PathVariable long studentId, Authentication authentication) {
        return credentialService.issueCard(studentId, authentication);
    }

    @PostMapping("/events")
    public StudentAccessEventService.EventView record(@Valid @RequestBody StudentAccessEventService.EventRequest request, Authentication authentication) {
        return eventService.record(request, authentication);
    }

    @PostMapping("/sync")
    public List<SyncResult> sync(@RequestBody List<@Valid StudentAccessEventService.EventRequest> requests, Authentication authentication) {
        if (requests == null || requests.isEmpty()) return List.of();
        if (requests.size() > 500) throw new IllegalArgumentException("Sincronize no máximo 500 eventos por lote.");
        List<SyncResult> results = new ArrayList<>();
        for (StudentAccessEventService.EventRequest request : requests) {
            try {
                StudentAccessEventService.EventView event = eventService.record(request, authentication);
                results.add(new SyncResult(request.clientEventId(), true, event.duplicate(), null, event));
            } catch (IllegalArgumentException exception) {
                results.add(new SyncResult(request.clientEventId(), false, false, exception.getMessage(), null));
            }
        }
        return results;
    }

    @GetMapping("/events")
    public List<StudentAccessEventService.EventView> history(@RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long studentId, @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return eventService.history(schoolId, studentId, limit, authentication);
    }

    public record IdentifyRequest(@NotBlank(message = "Leia o QR Code ou informe a matrícula.") String code) {}
    public record SyncResult(UUID clientEventId, boolean synchronizedEvent, boolean duplicate, String error, StudentAccessEventService.EventView event) {}
}
