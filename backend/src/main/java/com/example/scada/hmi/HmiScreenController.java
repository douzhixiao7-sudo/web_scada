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
}
