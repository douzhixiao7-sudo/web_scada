package com.example.scada.hmi;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hmi/config")
public class HmiController {
    private final HmiService hmiService;

    public HmiController(HmiService hmiService) {
        this.hmiService = hmiService;
    }

    @GetMapping("/draft")
    public HmiConfigResponse draft() { return hmiService.getDraft(); }

    @PutMapping("/draft")
    public HmiConfigResponse saveDraft(@RequestBody HmiDocumentRequest request) { return hmiService.saveDraft(request); }

    @GetMapping("/published")
    public HmiRevisionResponse published() { return hmiService.getPublished(); }

    @PostMapping("/publish")
    public HmiRevisionResponse publish(@RequestBody HmiDocumentRequest request) { return hmiService.publish(request); }

    @GetMapping("/versions")
    public List<HmiRevisionResponse> versions() { return hmiService.listVersions(); }

    @PostMapping("/versions/{id}/restore")
    public HmiConfigResponse restore(@PathVariable Long id) { return hmiService.restore(id); }
}
