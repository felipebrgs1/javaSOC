package com.felipeb.discordclone.webrtc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webrtc")
public class WebRtcConfigController {

    private final List<String> iceServers;

    public WebRtcConfigController(@Value("#{'${webrtc.ice-servers}'.split(',')}") List<String> iceServers) {
        this.iceServers = iceServers;
    }

    /** Returns the STUN/TURN servers clients should use when creating RTCPeerConnections. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        List<Map<String, Object>> servers = iceServers.stream()
                .map(url -> Map.<String, Object>of("urls", List.of(url.trim())))
                .toList();
        return Map.of("iceServers", servers);
    }
}
