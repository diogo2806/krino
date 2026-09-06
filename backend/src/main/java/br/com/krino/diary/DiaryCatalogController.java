package br.com.krino.diary;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diaries/catalog")
public class DiaryCatalogController {

    private final DiaryCatalogService service;

    public DiaryCatalogController(DiaryCatalogService service) {
        this.service = service;
    }

    @GetMapping("/schools")
    public List<DiaryCatalogService.SchoolView> schools(Authentication authentication) {
        return service.schools(authentication);
    }

    @GetMapping("/classes")
    public List<DiaryCatalogService.ClassView> classes(@RequestParam long schoolId, @RequestParam int year, Authentication authentication) {
        return service.classes(schoolId, year, authentication);
    }

    @GetMapping("/professionals")
    public List<DiaryCatalogService.ProfessionalView> professionals(@RequestParam long schoolId, Authentication authentication) {
        return service.professionals(schoolId, authentication);
    }

    @GetMapping("/components")
    public List<DiaryCatalogService.ComponentView> components(Authentication authentication) {
        return service.components(authentication);
    }
}
