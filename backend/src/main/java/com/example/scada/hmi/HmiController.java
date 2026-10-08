package com.example.scada.hmi;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hmi/config")
public class HmiController {
    private final HmiService hmiService;

    public HmiController(HmiService hmiService) {
        this.hmiService = hmiService;
    }

    @GetMapping("/draft")
    public HmiConfigResponse draft(@RequestParam(defaultValue = "1") Long screenId) { return hmiService.getDraft(screenId); }

    @PutMapping("/draft")
    public HmiConfigResponse saveDraft(@RequestParam(defaultValue = "1") Long screenId, @RequestBody HmiDocumentRequest request) { return hmiService.saveDraft(screenId, request); }

    @GetMapping("/published")
    public HmiRevisionResponse published(@RequestParam(defaultValue = "1") Long screenId) { return hmiService.getPublished(screenId); }

    @PostMapping("/publish")
    public HmiRevisionResponse publish(@RequestParam(defaultValue = "1") Long screenId, @RequestBody HmiDocumentRequest request) { return hmiService.publish(screenId, request); }

    @GetMapping("/versions")
    public List<HmiRevisionResponse> versions(@RequestParam(defaultValue = "1") Long screenId) { return hmiService.listVersions(screenId); }

    @PostMapping("/versions/{id}/restore")
    public HmiConfigResponse restore(@PathVariable Long id, @RequestParam(defaultValue = "1") Long screenId) { return hmiService.restore(screenId, id); }
}
