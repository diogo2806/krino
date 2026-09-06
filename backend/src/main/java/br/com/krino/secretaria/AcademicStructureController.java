package br.com.krino.secretaria;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/secretaria")
@PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_READ') or @authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
public class AcademicStructureController {
    private final AcademicStructureService service;
    public AcademicStructureController(AcademicStructureService service) { this.service = service; }

    @GetMapping("/teacher-assignments")
    public List<AcademicStructureService.TeacherAssignmentView> assignments(@RequestParam long classId, Authentication authentication) { return service.assignments(classId, authentication); }

    @PostMapping("/teacher-assignments")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public AcademicStructureService.TeacherAssignmentView assign(@Valid @RequestBody AcademicStructureService.TeacherAssignmentRequest request, Authentication authentication) { return service.assign(request, authentication); }

    @DeleteMapping("/teacher-assignments/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public void removeAssignment(@PathVariable long id, Authentication authentication) { service.removeAssignment(id, authentication); }

    @GetMapping("/calendar")
    public List<AcademicStructureService.CalendarDayView> calendar(@RequestParam long schoolId, @RequestParam int year, Authentication authentication) { return service.calendar(schoolId, year, authentication); }

    @PostMapping("/calendar")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public AcademicStructureService.CalendarDayView saveCalendarDay(@Valid @RequestBody AcademicStructureService.CalendarDayRequest request, Authentication authentication) { return service.saveCalendarDay(request, authentication); }

    @GetMapping("/schedules")
    public List<AcademicStructureService.ScheduleView> schedules(@RequestParam long classId, @RequestParam(required = false) LocalDate referenceDate, Authentication authentication) { return service.schedules(classId, referenceDate, authentication); }

    @PostMapping("/schedules")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public AcademicStructureService.ScheduleView createSchedule(@Valid @RequestBody AcademicStructureService.ScheduleRequest request, Authentication authentication) { return service.createSchedule(request, authentication); }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCHOOL_WRITE')")
    public void deleteSchedule(@PathVariable long id, Authentication authentication) { service.deleteSchedule(id, authentication); }
}
