package com.example.scada.history;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryQueryService historyQueryService;

    public HistoryController(HistoryQueryService historyQueryService) {
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/values")
    public List<HistoryValueResponse> values(
            @RequestParam Long pointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return historyQueryService.listValues(pointId, start, end);
    }

    @GetMapping("/latest")
    public List<HistoryLatestResponse> latest(@RequestParam Long deviceId) {
        return historyQueryService.latest(deviceId);
    }
}
