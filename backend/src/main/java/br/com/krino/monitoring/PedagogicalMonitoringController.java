package br.com.krino.monitoring;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedagogical-monitoring")
public class PedagogicalMonitoringController {

    private final PedagogicalMonitoringService service;

    public PedagogicalMonitoringController(PedagogicalMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/context")
    public PedagogicalMonitoringService.MonitoringContext context(@RequestParam int year, Authentication authentication) {
        return service.context(year, authentication);
    }

    @GetMapping("/classes")
    public List<PedagogicalMonitoringService.ClassOption> classes(@RequestParam long schoolId, @RequestParam int year, Authentication authentication) {
        return service.classes(schoolId, year, authentication);
    }

    @GetMapping("/summary")
    public PedagogicalMonitoringService.MonitoringSummary summary(@RequestParam int year,
            @RequestParam(required = false) Integer period, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, Authentication authentication) {
        return service.summary(year, period, schoolId, classId, authentication);
    }

    @GetMapping("/trend")
    public List<PedagogicalMonitoringService.TrendPoint> trend(@RequestParam int year,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        return service.trend(year, schoolId, classId, authentication);
    }

    @GetMapping("/breakdown")
    public List<PedagogicalMonitoringService.BreakdownItem> breakdown(@RequestParam int year,
            @RequestParam(required = false) Integer period, @RequestParam(required = false) Long schoolId,
            Authentication authentication) {
        return service.breakdown(year, period, schoolId, authentication);
    }

    @GetMapping("/indicator-records")
    public List<PedagogicalMonitoringService.IndicatorRecordView> indicatorRecords(@RequestParam int year,
            @RequestParam(required = false) Long schoolId, Authentication authentication) {
        return service.indicatorRecords(year, schoolId, authentication);
    }

    @PostMapping("/indicator-records")
    public PedagogicalMonitoringService.IndicatorRecordView createIndicatorRecord(
            @Valid @RequestBody PedagogicalMonitoringService.IndicatorRecordRequest request, Authentication authentication) {
        return service.createIndicatorRecord(request, authentication);
    }
}
