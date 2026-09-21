package com.example.scada.control;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control/commands")
public class ControlCommandController {
    private final ControlCommandService controlCommandService;

    public ControlCommandController(ControlCommandService controlCommandService) {
        this.controlCommandService = controlCommandService;
    }

    @GetMapping
    public List<ControlCommandResponse> list(@RequestParam(required = false) Long deviceId) {
        return controlCommandService.list(deviceId);
    }

    @PostMapping
    public ControlCommandResponse submit(@RequestBody ControlCommandRequest request) {
        return controlCommandService.submit(request);
    }
}
