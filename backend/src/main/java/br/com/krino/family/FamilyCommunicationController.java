package br.com.krino.family;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family-communication")
public class FamilyCommunicationController {

    private final FamilyCommunicationService service;

    public FamilyCommunicationController(FamilyCommunicationService service) { this.service = service; }

    @GetMapping("/schools")
    public List<FamilyCommunicationService.SchoolOption> schools(Authentication authentication) { return service.schools(authentication); }

    @GetMapping("/schools/{schoolId}/classes")
    public List<FamilyCommunicationService.ClassOption> classes(@PathVariable long schoolId, Authentication authentication) { return service.classes(schoolId, authentication); }

    @GetMapping("/schools/{schoolId}/students")
    public List<FamilyCommunicationService.StudentOption> students(@PathVariable long schoolId, @RequestParam(required = false) Long classId, Authentication authentication) { return service.students(schoolId, classId, authentication); }

    @GetMapping("/students/{studentId}/guardians")
    public List<FamilyCommunicationService.GuardianOption> guardians(@PathVariable long studentId, Authentication authentication) { return service.guardians(studentId, authentication); }

    @GetMapping("/conversations")
    public List<FamilyCommunicationService.ConversationView> conversations(@RequestParam long schoolId, @RequestParam(required = false) String search, Authentication authentication) { return service.conversations(schoolId, search, authentication); }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<FamilyCommunicationService.MessageView> messages(@PathVariable long conversationId, Authentication authentication) { return service.messages(conversationId, authentication); }

    @PostMapping("/conversations")
    public FamilyCommunicationService.ConversationView createConversation(@Valid @RequestBody FamilyCommunicationService.NewStaffConversationRequest request, Authentication authentication) { return service.createConversation(request, authentication); }

    @PostMapping("/conversations/{conversationId}/messages")
    public void reply(@PathVariable long conversationId, @Valid @RequestBody FamilyCommunicationService.MessageRequest request, Authentication authentication) { service.reply(conversationId, request, authentication); }

    @GetMapping("/announcements")
    public List<FamilyCommunicationService.AnnouncementView> announcements(@RequestParam long schoolId, Authentication authentication) { return service.announcements(schoolId, authentication); }

    @PostMapping("/announcements")
    public FamilyCommunicationService.AnnouncementView createAnnouncement(@Valid @RequestBody FamilyCommunicationService.AnnouncementRequest request, Authentication authentication) { return service.createAnnouncement(request, authentication); }

    @DeleteMapping("/announcements/{id}")
    public void deactivateAnnouncement(@PathVariable long id, Authentication authentication) { service.deactivateAnnouncement(id, authentication); }
}
