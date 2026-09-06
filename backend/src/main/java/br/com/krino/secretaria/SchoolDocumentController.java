package br.com.krino.secretaria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secretaria/documents")
@PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_DOCUMENT_READ')")
public class SchoolDocumentController {
    private final SchoolDocumentService service;
    public SchoolDocumentController(SchoolDocumentService service) { this.service = service; }

    @GetMapping("/{type}")
    public SchoolDocumentService.DocumentView generate(
            @PathVariable String type,
            @RequestParam Long schoolId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Integer period,
            Authentication authentication) {
        return service.generate(type, schoolId, year, classId, studentId, period, authentication);
    }
}
