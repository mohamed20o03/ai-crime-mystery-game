package com.thecrime.controller;

import com.thecrime.domain.enums.GameLanguage;
import com.thecrime.domain.model.GameRoom;
import com.thecrime.domain.model.Player;
import com.thecrime.dto.CreateRoomRequest;
import com.thecrime.dto.JoinRoomRequest;
import com.thecrime.service.game.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for room management (create/join).
 * WebSocket handles in-game actions.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    
    private final RoomService roomService;
    
    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        GameLanguage language = "English".equalsIgnoreCase(request.getLanguage()) 
                ? GameLanguage.ENGLISH 
                : GameLanguage.ARABIC;
        
        GameRoom room = roomService.createRoom(
                request.getHostName(),
                request.getSetting(),
                language,
                request.getCriminalCount()
        );
        
        Player host = room.getHost();
        
        return ResponseEntity.ok(Map.of(
                "roomCode", room.getRoomCode(),
                "playerId", host.getId(),
                "playerName", host.getName(),
                "isHost", true
        ));
    }
    
    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        GameRoom room = roomService.joinRoom(
                request.getRoomCode().toUpperCase(),
                request.getPlayerName()
        );
        
        // Find the newly added player
        Player player = room.getAllPlayers().stream()
                .filter(p -> p.getName().equals(request.getPlayerName()))
                .findFirst()
                .orElseThrow();
        
        return ResponseEntity.ok(Map.of(
                "roomCode", room.getRoomCode(),
                "playerId", player.getId(),
                "playerName", player.getName(),
                "isHost", false
        ));
    }
    
    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getRoomInfo(@PathVariable String roomCode) {
        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        
        return ResponseEntity.ok(Map.of(
                "roomCode", room.getRoomCode(),
                "playerCount", room.getPlayers().size(),
                "phase", room.getPhase(),
                "players", room.getAllPlayers().stream()
                        .map(p -> Map.of(
                                "id", p.getId(),
                                "name", p.getName(),
                                "isHost", p.isHost()
                        ))
                        .toList()
        ));
    }
}
