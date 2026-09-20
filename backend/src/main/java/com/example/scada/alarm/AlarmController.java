package com.example.scada.alarm;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {
    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping("/active")
    public List<AlarmEventResponse> listActiveAlarms() {
        return alarmService.syncAndListActiveAlarms();
    }

    @GetMapping("/events")
    public List<AlarmEventResponse> listEvents(@RequestParam(required = false) String status) {
        return alarmService.listEvents(status);
    }

    @PostMapping("/events/{id}/ack")
    public AlarmEventResponse acknowledge(@PathVariable Long id, @RequestBody(required = false) AlarmAckRequest request) {
        return alarmService.acknowledge(id, request);
    }
}
