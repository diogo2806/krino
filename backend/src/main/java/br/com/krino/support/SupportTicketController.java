package br.com.krino.support;

import java.util.List;

import jakarta.validation.Valid;
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
@RequestMapping("/api/support")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping("/tickets")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_CREATE')")
    public List<SupportTicketService.TicketView> myTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        return supportTicketService.listOwn(authentication, status, severity, search);
    }

    @PostMapping("/tickets")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_CREATE')")
    public SupportTicketService.TicketDetail create(
            @Valid @RequestBody SupportTicketService.CreateTicketRequest request,
            Authentication authentication) {
        return supportTicketService.create(request, authentication);
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_CREATE') or @authorizationService.hasNetworkPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public SupportTicketService.TicketDetail detail(@PathVariable long ticketId, Authentication authentication) {
        return supportTicketService.detail(ticketId, authentication);
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_CREATE') or @authorizationService.hasNetworkPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public SupportTicketService.TicketDetail addMessage(
            @PathVariable long ticketId,
            @Valid @RequestBody SupportTicketService.MessageRequest request,
            Authentication authentication) {
        return supportTicketService.addMessage(ticketId, request, authentication);
    }

    @GetMapping("/admin/tickets")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public List<SupportTicketService.TicketView> allTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {
        return supportTicketService.listAll(status, severity, search);
    }

    @PutMapping("/admin/tickets/{ticketId}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public SupportTicketService.TicketDetail manage(
            @PathVariable long ticketId,
            @Valid @RequestBody SupportTicketService.ManageTicketRequest request,
            Authentication authentication) {
        return supportTicketService.manage(ticketId, request, authentication);
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public SupportTicketService.SupportSummary summary() {
        return supportTicketService.summary();
    }
}
