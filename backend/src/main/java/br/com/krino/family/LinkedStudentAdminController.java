package br.com.krino.family;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users/{userId}/linked-students")
@PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SCOPE_ASSIGN')")
public class LinkedStudentAdminController {

    private final LinkedStudentAdminService service;

    public LinkedStudentAdminController(LinkedStudentAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<LinkedStudentAdminService.StudentOption> linked(@PathVariable long userId) {
        return service.linkedStudents(userId);
    }

    @GetMapping("/catalog")
    public List<LinkedStudentAdminService.StudentOption> catalog(@PathVariable long userId,
            @RequestParam(required = false) String search) {
        return service.catalog(userId, search);
    }

    @PostMapping("/{studentId}")
    public void link(@PathVariable long userId, @PathVariable long studentId, Authentication authentication) {
        service.link(userId, studentId, authentication.getName());
    }

    @DeleteMapping("/{studentId}")
    public void unlink(@PathVariable long userId, @PathVariable long studentId, Authentication authentication) {
        service.unlink(userId, studentId, authentication.getName());
    }
}
