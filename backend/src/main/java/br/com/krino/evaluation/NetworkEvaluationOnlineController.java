package br.com.krino.evaluation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/network-evaluations/online")
public class NetworkEvaluationOnlineController {

    private final NetworkEvaluationService service;

    public NetworkEvaluationOnlineController(NetworkEvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public NetworkEvaluationService.OnlineEvaluationView evaluation(@RequestParam String token) {
        return service.online(token);
    }

    @PostMapping("/submit")
    public NetworkEvaluationService.BatchView submit(@RequestParam String token, @RequestBody NetworkEvaluationService.OnlineSubmission input) {
        return service.submitOnline(token, input);
    }
}
