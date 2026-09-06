package br.com.krino.secretaria;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secretaria/enrollments")
@PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_READ') or @authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
public class EnrollmentController {
    private final EnrollmentService service;
    public EnrollmentController(EnrollmentService service) { this.service = service; }

    @GetMapping
    public List<EnrollmentService.EnrollmentView> list(@RequestParam(required = false) Long schoolId, @RequestParam int year, Authentication authentication) { return service.list(schoolId, year, authentication); }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public EnrollmentService.EnrollmentView enroll(@Valid @RequestBody EnrollmentService.EnrollmentRequest request, Authentication authentication) { return service.enroll(request, authentication); }

    @PostMapping("/{id}/movements")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public EnrollmentService.MovementResult move(@PathVariable long id, @Valid @RequestBody EnrollmentService.MovementRequest request, Authentication authentication) { return service.move(id, request, authentication); }

    @GetMapping("/student/{studentId}/movements")
    public List<EnrollmentService.MovementView> movements(@PathVariable long studentId, Authentication authentication) { return service.movements(studentId, authentication); }
}
