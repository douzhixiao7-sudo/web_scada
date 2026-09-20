package com.example.scada.collector;

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
@RequestMapping("/api/collect")
public class CollectChannelController {
    private final CollectChannelService collectChannelService;

    public CollectChannelController(CollectChannelService collectChannelService) {
        this.collectChannelService = collectChannelService;
    }

    @GetMapping("/channels")
    public List<CollectChannelResponse> listChannels(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Boolean enabled) {
        return collectChannelService.listChannels(deviceId, enabled);
    }

    @PutMapping("/channels/{id}")
    public CollectChannelResponse updateChannel(@PathVariable Long id, @RequestBody CollectChannelRequest request) {
        return collectChannelService.updateChannel(id, request);
    }

    @PostMapping("/channels/{id}/poll")
    public CollectChannelResponse markPolled(@PathVariable Long id) {
        return collectChannelService.markPolled(id);
    }

    @GetMapping("/channels/{id}/bindings")
    public List<CollectBindingResponse> listBindings(@PathVariable Long id) {
        return collectChannelService.listBindings(id);
    }

    @PutMapping("/channels/{channelId}/bindings/{bindingId}")
    public CollectBindingResponse updateBinding(
            @PathVariable Long channelId,
            @PathVariable Long bindingId,
            @RequestBody CollectBindingRequest request) {
        return collectChannelService.updateBinding(channelId, bindingId, request);
    }
}
