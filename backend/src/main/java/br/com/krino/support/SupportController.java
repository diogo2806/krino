package br.com.krino.support;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.krino.support.SupportReportService.SupportReport;
import br.com.krino.support.SupportService.InteractionRequest;
import br.com.krino.support.SupportService.InteractionView;
import br.com.krino.support.SupportService.SlaPolicyRequest;
import br.com.krino.support.SupportService.SlaPolicyView;
import br.com.krino.support.SupportService.SupportContext;
import br.com.krino.support.SupportService.TicketCreateRequest;
import br.com.krino.support.SupportService.TicketDetail;
import br.com.krino.support.SupportService.TicketUpdateRequest;
import br.com.krino.support.SupportService.TicketView;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;
    private final SupportReportService reportService;

    public SupportController(SupportService supportService, SupportReportService reportService) {
        this.supportService = supportService;
        this.reportService = reportService;
    }

    @GetMapping("/context")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_READ')")
    public SupportContext context(Authentication authentication) {
        return supportService.context(authentication);
    }

    @GetMapping("/tickets")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_READ')")
    public List<TicketView> tickets(@RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long schoolId,
            Authentication authentication) {
        return supportService.list(status, severity, search, schoolId, authentication);
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_READ')")
    public TicketDetail ticket(@PathVariable long ticketId, Authentication authentication) {
        return supportService.get(ticketId, authentication);
    }

    @PostMapping("/tickets")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_CREATE')")
    public TicketView createTicket(@Valid @RequestBody TicketCreateRequest request, Authentication authentication) {
        return supportService.create(request, authentication);
    }

    @PostMapping("/tickets/{ticketId}/interactions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_READ')")
    public InteractionView addInteraction(@PathVariable long ticketId,
            @Valid @RequestBody InteractionRequest request, Authentication authentication) {
        return supportService.addInteraction(ticketId, request, authentication);
    }

    @PatchMapping("/tickets/{ticketId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_MANAGE')")
    public TicketView updateTicket(@PathVariable long ticketId,
            @Valid @RequestBody TicketUpdateRequest request, Authentication authentication) {
        return supportService.update(ticketId, request, authentication);
    }

    @GetMapping("/sla-policies")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_TICKET_READ')")
    public List<SlaPolicyView> slaPolicies() {
        return supportService.slaPolicies();
    }

    @PutMapping("/sla-policies/{severity}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_SLA_MANAGE')")
    public SlaPolicyView updateSlaPolicy(@PathVariable String severity,
            @Valid @RequestBody SlaPolicyRequest request, Authentication authentication) {
        return supportService.updatePolicy(severity, request, authentication);
    }

    @GetMapping("/reports")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_REPORT_READ')")
    public SupportReport report(@RequestParam(required = false) Long schoolId, Authentication authentication) {
        return reportService.report(schoolId, authentication);
    }
}
