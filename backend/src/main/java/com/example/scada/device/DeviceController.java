package com.example.scada.device;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/areas")
    public List<AreaResponse> listAreas() {
        return deviceService.listAreas();
    }

    @GetMapping("/devices")
    public List<DeviceResponse> listDevices(
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) String status) {
        return deviceService.listDevices(areaId, status);
    }

    @GetMapping("/devices/{id}")
    public DeviceResponse getDevice(@PathVariable Long id) {
        return deviceService.getDevice(id);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse createDevice(@RequestBody DeviceRequest request) {
        return deviceService.createDevice(request);
    }

    @PutMapping("/devices/{id}")
    public DeviceResponse updateDevice(@PathVariable Long id, @RequestBody DeviceRequest request) {
        return deviceService.updateDevice(id, request);
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
    }

    @GetMapping("/points")
    public List<PointResponse> listPoints(@RequestParam Long deviceId) {
        return deviceService.listPoints(deviceId);
    }

    @PostMapping("/devices/{deviceId}/points")
    @ResponseStatus(HttpStatus.CREATED)
    public PointResponse createPoint(@PathVariable Long deviceId, @RequestBody PointRequest request) {
        return deviceService.createPoint(deviceId, request);
    }

    @PutMapping("/devices/{deviceId}/points/{pointId}")
    public PointResponse updatePoint(
            @PathVariable Long deviceId,
            @PathVariable Long pointId,
            @RequestBody PointRequest request) {
        return deviceService.updatePoint(deviceId, pointId, request);
    }

    @DeleteMapping("/devices/{deviceId}/points/{pointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoint(@PathVariable Long deviceId, @PathVariable Long pointId) {
        deviceService.deletePoint(deviceId, pointId);
    }
}
