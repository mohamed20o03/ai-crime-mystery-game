package com.thecrime.service.game;

import com.thecrime.domain.enums.GameLanguage;
import com.thecrime.domain.enums.GamePhase;
import com.thecrime.domain.model.GameRoom;
import com.thecrime.domain.model.Player;
import com.thecrime.exception.GameException;
import com.thecrime.exception.RoomNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game rooms in memory.
 * Follows Single Responsibility Principle - only handles room CRUD operations.
 */
@Service
@Slf4j
public class RoomService {
    
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    
    @Value("${game.room-code-length}")
    private int roomCodeLength;
    
    @Value("${game.max-players}")
    private int maxPlayers;
    
    @Value("${game.min-players}")
    private int minPlayers;
    
    public GameRoom createRoom(String hostName, String setting, GameLanguage language, int criminalCount) {
        String roomCode = generateRoomCode();
        String hostId = generatePlayerId();
        
        Player host = Player.builder()
                .id(hostId)
                .name(hostName)
                .isHost(true)
                .isEliminated(false)
                .build();
        
        GameRoom room = GameRoom.builder()
                .roomCode(roomCode)
                .hostPlayerId(hostId)
                .phase(GamePhase.LOBBY)
                .language(language)
                .setting(setting)
                .criminalCount(Math.max(1, criminalCount))
                .build();
        
        room.addPlayer(host);
        rooms.put(roomCode, room);
        
        log.info("Created room {} by host {}", roomCode, hostName);
        return room;
    }
    
    public GameRoom joinRoom(String roomCode, String playerName) {
        GameRoom room = getRoom(roomCode);
        
        if (room.getPhase() != GamePhase.LOBBY) {
            throw new GameException("Game has already started");
        }
        
        if (room.getPlayers().size() >= maxPlayers) {
            throw new GameException("Room is full");
        }
        
        // Check for duplicate names
        boolean nameExists = room.getPlayers().values().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));
        if (nameExists) {
            throw new GameException("Name already taken in this room");
        }
        
        String playerId = generatePlayerId();
        Player player = Player.builder()
                .id(playerId)
                .name(playerName)
                .isHost(false)
                .isEliminated(false)
                .build();
        
        room.addPlayer(player);
        log.info("Player {} joined room {}", playerName, roomCode);
        
        return room;
    }
    
    public GameRoom getRoom(String roomCode) {
        GameRoom room = rooms.get(roomCode.toUpperCase());
        if (room == null) {
            throw new RoomNotFoundException(roomCode);
        }
        return room;
    }
    
    public Optional<GameRoom> findRoom(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode.toUpperCase()));
    }
    
    public void removeRoom(String roomCode) {
        rooms.remove(roomCode.toUpperCase());
        log.info("Removed room {}", roomCode);
    }
    
    public void removePlayerFromRoom(String roomCode, String playerId) {
        GameRoom room = getRoom(roomCode);
        room.removePlayer(playerId);
        
        // If host left and there are still players, assign new host
        if (playerId.equals(room.getHostPlayerId()) && !room.getPlayers().isEmpty()) {
            Player newHost = room.getPlayers().values().iterator().next();
            newHost.setHost(true);
            room.setHostPlayerId(newHost.getId());
            log.info("New host {} assigned in room {}", newHost.getName(), roomCode);
        }
        
        // If room is empty, remove it
        if (room.getPlayers().isEmpty()) {
            removeRoom(roomCode);
        }
    }
    
    public boolean canStartGame(String roomCode) {
        GameRoom room = getRoom(roomCode);
        return room.getPlayers().size() >= minPlayers;
    }
    
    public int getMinPlayers() {
        return minPlayers;
    }
    
    private String generateRoomCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(roomCodeLength);
            for (int i = 0; i < roomCodeLength; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }
    
    private String generatePlayerId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
