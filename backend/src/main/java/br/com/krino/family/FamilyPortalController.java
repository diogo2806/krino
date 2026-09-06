package br.com.krino.family;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family-portal")
public class FamilyPortalController {

    private final FamilyPortalService service;

    public FamilyPortalController(FamilyPortalService service) {
        this.service = service;
    }

    @GetMapping("/students")
    public List<FamilyPortalService.LinkedStudentView> students(Authentication authentication) {
        return service.students(authentication);
    }

    @GetMapping("/students/{studentId}/report-card")
    public FamilyPortalService.ReportCardView reportCard(@PathVariable long studentId, @RequestParam int year,
            @RequestParam int period, Authentication authentication) {
        return service.reportCard(studentId, year, period, authentication);
    }

    @GetMapping("/students/{studentId}/notifications")
    public List<FamilyPortalService.AccessNotificationView> notifications(@PathVariable long studentId, Authentication authentication) {
        return service.notifications(studentId, authentication);
    }

    @GetMapping("/students/{studentId}/announcements")
    public List<FamilyPortalService.AnnouncementView> announcements(@PathVariable long studentId, Authentication authentication) {
        return service.announcements(studentId, authentication);
    }

    @GetMapping("/students/{studentId}/conversations")
    public List<FamilyPortalService.ConversationView> conversations(@PathVariable long studentId, Authentication authentication) {
        return service.conversations(studentId, authentication);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<FamilyPortalService.MessageView> messages(@PathVariable long conversationId, Authentication authentication) {
        return service.messages(conversationId, authentication);
    }

    @PostMapping("/conversations")
    public FamilyPortalService.ConversationView createConversation(@Valid @RequestBody FamilyPortalService.NewConversationRequest request,
            Authentication authentication) {
        return service.createConversation(request, authentication);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public void reply(@PathVariable long conversationId, @Valid @RequestBody FamilyPortalService.MessageRequest request,
            Authentication authentication) {
        service.reply(conversationId, request, authentication);
    }
}
