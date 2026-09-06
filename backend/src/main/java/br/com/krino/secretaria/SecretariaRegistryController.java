package br.com.krino.secretaria;

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
@RequestMapping("/api/secretaria")
@PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_READ') or @authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
public class SecretariaRegistryController {

    private final SecretariaRegistryService service;

    public SecretariaRegistryController(SecretariaRegistryService service) { this.service = service; }

    @GetMapping("/schools")
    public List<SecretariaRegistryService.SchoolView> schools(Authentication authentication) { return service.listSchools(authentication); }

    @PostMapping("/schools")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SCHOOL_WRITE')")
    public SecretariaRegistryService.SchoolView createSchool(@Valid @RequestBody SecretariaRegistryService.SchoolRequest request, Authentication authentication) { return service.createSchool(request, authentication); }

    @PutMapping("/schools/{id}")
    public SecretariaRegistryService.SchoolView updateSchool(@PathVariable long id, @Valid @RequestBody SecretariaRegistryService.SchoolRequest request, Authentication authentication) { return service.updateSchool(id, request, authentication); }

    @GetMapping("/students")
    public List<SecretariaRegistryService.StudentView> students(@RequestParam(required = false) Long schoolId, @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "") String search, Authentication authentication) { return service.listStudents(schoolId, year, search, authentication); }

    @PostMapping("/students")
    public SecretariaRegistryService.StudentView createStudent(@Valid @RequestBody SecretariaRegistryService.StudentRequest request, Authentication authentication) { return service.createStudent(request, authentication); }

    @PutMapping("/students/{id}")
    public SecretariaRegistryService.StudentView updateStudent(@PathVariable long id, @Valid @RequestBody SecretariaRegistryService.StudentRequest request, Authentication authentication) { return service.updateStudent(id, request, authentication); }

    @GetMapping("/professionals")
    public List<SecretariaRegistryService.ProfessionalView> professionals(@RequestParam(required = false) Long schoolId, @RequestParam(defaultValue = "") String search, Authentication authentication) { return service.listProfessionals(schoolId, search, authentication); }

    @PostMapping("/professionals")
    public SecretariaRegistryService.ProfessionalView createProfessional(@Valid @RequestBody SecretariaRegistryService.ProfessionalRequest request, Authentication authentication) { return service.createProfessional(request, authentication); }

    @PutMapping("/professionals/{id}")
    public SecretariaRegistryService.ProfessionalView updateProfessional(@PathVariable long id, @Valid @RequestBody SecretariaRegistryService.ProfessionalRequest request, Authentication authentication) { return service.updateProfessional(id, request, authentication); }

    @GetMapping("/classes")
    public List<SecretariaRegistryService.ClassView> classes(@RequestParam(required = false) Long schoolId, @RequestParam int year, Authentication authentication) { return service.listClasses(schoolId, year, authentication); }

    @PostMapping("/classes")
    public SecretariaRegistryService.ClassView createClass(@Valid @RequestBody SecretariaRegistryService.ClassRequest request, Authentication authentication) { return service.createClass(request, authentication); }

    @PutMapping("/classes/{id}")
    public SecretariaRegistryService.ClassView updateClass(@PathVariable long id, @Valid @RequestBody SecretariaRegistryService.ClassRequest request, Authentication authentication) { return service.updateClass(id, request, authentication); }

    @GetMapping("/components")
    public List<SecretariaRegistryService.ComponentView> components(Authentication authentication) { return service.listComponents(authentication); }
}
