package com.thecrime.controller;

import com.thecrime.domain.model.GameRoom;
import com.thecrime.dto.*;
import com.thecrime.service.game.GameService;
import com.thecrime.service.game.RoomService;
import com.thecrime.service.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket controller for real-time game actions.
 * 
 * Flow: join → start → briefing/confirm → suspects/confirm
 *       → round/start → vote/start → vote → tiebreak/resolve → elimination/continue → kick/replay
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class GameWebSocketController {
    
    private final GameService gameService;
    private final RoomService roomService;
    private final WebSocketService webSocketService;
    
    @MessageMapping("/room/{roomCode}/join")
    public void joinRoom(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        log.info("Player {} connecting to room {}", playerId, roomCode);
        try {
            GameRoom room = roomService.getRoom(roomCode);
            room.getPlayer(playerId).ifPresent(player -> {
                player.setSessionId(playerId);
            });
            gameService.broadcastGameState(room, null);
        } catch (Exception e) {
            log.error("Error joining room", e);
        }
    }
    
    @MessageMapping("/room/{roomCode}/start")
    public void startGame(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        int criminalCount = 1;
        try {
            criminalCount = Integer.parseInt(payload.getOrDefault("criminalCount", "1"));
        } catch (NumberFormatException ignored) {}
        try {
            gameService.startGame(roomCode, playerId, criminalCount);
        } catch (Exception e) {
            log.error("Error starting game", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/briefing/confirm")
    public void confirmBriefing(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.confirmBriefing(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error confirming briefing", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/suspects/confirm")
    public void confirmSuspects(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.confirmSuspects(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error confirming suspects", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    /**
     * Host starts a new round (reveals clue for each player).
     */
    @MessageMapping("/room/{roomCode}/round/start")
    public void startRound(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.startRound(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error starting round", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    /**
     * Host initiates voting.
     */
    @MessageMapping("/room/{roomCode}/vote/start")
    public void startVoting(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.startVoting(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error starting voting", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/vote")
    public void castVote(
            @DestinationVariable String roomCode,
            @Payload VoteRequestWithPlayer request
    ) {
        try {
            VoteRequest vr = new VoteRequest();
            vr.setTargetPlayerId(request.getTargetPlayerId());
            gameService.castVote(roomCode, request.getPlayerId(), vr);
        } catch (Exception e) {
            log.error("Error casting vote", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    /**
     * Host picks a player among tied candidates.
     */
    @MessageMapping("/room/{roomCode}/tiebreak/resolve")
    public void resolveTieBreak(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        String targetPlayerId = payload.get("targetPlayerId");
        try {
            gameService.resolveTieBreak(roomCode, playerId, targetPlayerId);
        } catch (Exception e) {
            log.error("Error resolving tiebreak", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    /**
     * Host continues after elimination reveal (next round or game over).
     */
    @MessageMapping("/room/{roomCode}/elimination/continue")
    public void continueAfterElimination(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.continueAfterElimination(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error continuing after elimination", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/close")
    public void closeRoom(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.closeRoom(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error closing room", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/kick")
    public void kickPlayer(
            @DestinationVariable String roomCode,
            @Payload KickRequestWithPlayer request
    ) {
        try {
            gameService.kickPlayer(roomCode, request.getPlayerId(), request.getTargetPlayerId());
        } catch (Exception e) {
            log.error("Error kicking player", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    @MessageMapping("/room/{roomCode}/replay")
    public void replayGame(
            @DestinationVariable String roomCode,
            @Payload Map<String, String> payload
    ) {
        String playerId = payload.get("playerId");
        try {
            gameService.resetRoom(roomCode, playerId);
        } catch (Exception e) {
            log.error("Error resetting room for replay", e);
            webSocketService.broadcastToRoom(roomCode, WebSocketMessage.error(e.getMessage()));
        }
    }
    
    // Helper DTOs
    @lombok.Data
    public static class VoteRequestWithPlayer {
        private String playerId;
        private String targetPlayerId;
    }
    
    @lombok.Data
    public static class KickRequestWithPlayer {
        private String playerId;
        private String targetPlayerId;
    }
}
