package com.thecrime.service.websocket;

import com.thecrime.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Handles WebSocket message distribution.
 * Separates messaging concerns from game logic (SRP).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Broadcast a message to all players in a room
     */
    public void broadcastToRoom(String roomCode, WebSocketMessage message) {
        log.debug("Broadcasting to room {}: {}", roomCode, message.getType());
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, message);
    }
    
    /**
     * Send a private message to a specific player
     */
    public void sendToPlayer(String roomCode, String playerId, WebSocketMessage message) {
        log.debug("Sending to player {} in room {}: {}", playerId, roomCode, message.getType());
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/player/" + playerId, message);
    }
    
    /**
     * Send an error to a specific session
     */
    public void sendError(String sessionId, String errorMessage) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", 
                WebSocketMessage.error(errorMessage));
    }
}
