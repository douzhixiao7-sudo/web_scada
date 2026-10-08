package com.example.scada.hmi;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hmi/screens")
public class HmiScreenController {
    private final HmiService hmiService;

    public HmiScreenController(HmiService hmiService) { this.hmiService = hmiService; }

    @GetMapping
    public List<HmiScreenResponse> list() { return hmiService.listScreens(); }

    @PostMapping
    public HmiScreenResponse create(@RequestBody HmiScreenRequest request) { return hmiService.createScreen(request); }

    @PutMapping("/{id}")
    public HmiScreenResponse update(@PathVariable Long id, @RequestBody HmiScreenRequest request) { return hmiService.updateScreen(id, request); }

    @PostMapping("/{id}/copy")
    public HmiScreenResponse copy(@PathVariable Long id, @RequestBody HmiScreenRequest request) { return hmiService.copyScreen(id, request); }

    @PostMapping("/{id}/default")
    public HmiScreenResponse setDefault(@PathVariable Long id) { return hmiService.setDefault(id); }

    @PostMapping("/{id}/move")
    public void move(@PathVariable Long id, @RequestParam String direction) { hmiService.moveScreen(id, direction); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { hmiService.deleteScreen(id); }
}
