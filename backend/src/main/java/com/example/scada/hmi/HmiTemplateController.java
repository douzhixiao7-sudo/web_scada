package com.example.scada.hmi;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hmi/templates")
public class HmiTemplateController {
    private final HmiService hmiService;

    public HmiTemplateController(HmiService hmiService) { this.hmiService = hmiService; }

    @GetMapping
    public List<HmiTemplateResponse> list() { return hmiService.listTemplates(); }

    @PostMapping
    public HmiTemplateResponse create(@RequestBody HmiTemplateRequest request) { return hmiService.createTemplate(request); }

    @PutMapping("/{id}")
    public HmiTemplateResponse update(@PathVariable Long id, @RequestBody HmiTemplateRequest request) { return hmiService.updateTemplate(id, request); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { hmiService.deleteTemplate(id); }
}
