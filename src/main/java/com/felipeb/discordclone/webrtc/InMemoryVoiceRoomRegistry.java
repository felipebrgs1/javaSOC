package com.felipeb.discordclone.webrtc;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class InMemoryVoiceRoomRegistry implements VoiceRoomRegistry {

    private final Map<String, Set<String>> rooms = new HashMap<>();
    private final Map<String, String> userRoom = new HashMap<>();

    @Override
    public synchronized Set<String> join(String room, String username) {
        leaveAll(username);
        Set<String> peers = rooms.computeIfAbsent(room, r -> new HashSet<>());
        Set<String> existing = new HashSet<>(peers);
        peers.add(username);
        userRoom.put(username, room);
        return existing;
    }

    @Override
    public synchronized Set<String> leave(String room, String username) {
        Set<String> peers = rooms.get(room);
        if (peers == null) {
            return Set.of();
        }
        peers.remove(username);
        if (peers.isEmpty()) {
            rooms.remove(room);
        }
        userRoom.remove(username, room);
        return new HashSet<>(peers);
    }

    @Override
    public synchronized Set<String> leaveAll(String username) {
        String room = userRoom.get(username);
        if (room == null) {
            return Set.of();
        }
        Set<String> peers = rooms.get(room);
        if (peers != null) {
            peers.remove(username);
            if (peers.isEmpty()) {
                rooms.remove(room);
            }
        }
        userRoom.remove(username);
        return room.isEmpty() ? Set.of() : Set.of(room);
    }

    @Override
    public synchronized Set<String> participants(String room) {
        Set<String> peers = rooms.get(room);
        return peers == null ? Set.of() : new HashSet<>(peers);
    }

    @Override
    public synchronized boolean isInRoom(String room, String username) {
        Set<String> peers = rooms.get(room);
        return peers != null && peers.contains(username);
    }
}
