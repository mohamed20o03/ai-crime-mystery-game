package com.thecrime.service.game;

import com.thecrime.domain.enums.GamePhase;
import com.thecrime.domain.enums.PlayerRole;
import com.thecrime.domain.model.GameRoom;
import com.thecrime.domain.model.Player;
import com.thecrime.dto.*;
import com.thecrime.exception.GameException;
import com.thecrime.service.ai.ScenarioGeneratorService;
import com.thecrime.service.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core game orchestration service.
 * 
 * Flow:
 * LOBBY → GENERATING → CRIME_BRIEFING → SUSPECTS → GAME_ROUND
 *   → (host starts round: reveal clue) → (host starts vote) → VOTING
 *   → (all votes in) → ELIMINATION_REVEAL (or TIE_BREAK → host picks → ELIMINATION_REVEAL)
 *   → (host continues) → GAME_ROUND (next round) or GAME_OVER
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {
    
    private final RoomService roomService;
    private final ScenarioGeneratorService scenarioGenerator;
    private final WebSocketService webSocketService;
    
    // ==================== ROOM MANAGEMENT ====================
    
    public void closeRoom(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        log.info("Host {} is closing room {}", requestingPlayerId, roomCode);
        
        // Broadcast to all players that the room is closed
        WebSocketMessage closeMessage = WebSocketMessage.builder()
                .type("ROOM_CLOSED")
                .message("The host has closed the room.")
                .build();
        webSocketService.broadcastToRoom(roomCode, closeMessage);
        
        // Remove room entirely from memory
        roomService.removeRoom(roomCode);
    }
    
    // ==================== GAME START ====================
    
    public void startGame(String roomCode, String requestingPlayerId, int criminalCount) {
        GameRoom room = roomService.getRoom(roomCode);
        
        if (!room.getHostPlayerId().equals(requestingPlayerId)) {
            throw new GameException("Only the host can start the game");
        }
        if (!roomService.canStartGame(roomCode)) {
            throw new GameException("Need at least " + roomService.getMinPlayers() + " players to start");
        }
        if (room.getPhase() != GamePhase.LOBBY) {
            throw new GameException("Game has already started");
        }
        
        // Validate criminal count: must be >= 1 and <= half the total players
        int maxCriminals = room.getAllPlayers().size() / 2;
        if (criminalCount < 1) {
            throw new GameException("عدد المجرمين لازم يكون ١ على الأقل");
        }
        if (criminalCount > maxCriminals) {
            throw new GameException("عدد المجرمين لازم يكون أقل من أو يساوي " + maxCriminals);
        }
        
        room.setCriminalCount(criminalCount);
        room.setPhase(GamePhase.GENERATING_SCENARIO);
        broadcastGameState(room, "جاري إنشاء السيناريو...");
        
        // All players participate in the game
        List<String> playerNames = room.getAllPlayers().stream()
                .map(Player::getName)
                .toList();
        
        try {
            scenarioGenerator.generateScenario(room, playerNames, (success) -> {
                if (success) {
                    startCrimeBriefingPhase(room);
                } else {
                    room.setPhase(GamePhase.LOBBY);
                    broadcastGameState(room, "فشل في إنشاء السيناريو. حاول مرة أخرى.");
                }
            });
        } catch (Exception e) {
            log.error("Error starting game", e);
            room.setPhase(GamePhase.LOBBY);
            broadcastGameState(room, "حدث خطأ. حاول مرة أخرى.");
        }
    }
    
    // ==================== PHASE 1: CRIME BRIEFING ====================
    
    private void startCrimeBriefingPhase(GameRoom room) {
        room.setPhase(GamePhase.CRIME_BRIEFING);
        broadcastGameState(room, null);
    }
    
    public void confirmBriefing(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.CRIME_BRIEFING) {
            throw new GameException("Not in briefing phase");
        }
        
        startSuspectsPhase(room);
    }
    
    // ==================== PHASE 2: SUSPECTS ====================
    
    private void startSuspectsPhase(GameRoom room) {
        room.setPhase(GamePhase.SUSPECTS);
        broadcastGameState(room, null);
    }
    
    public void confirmSuspects(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.SUSPECTS) {
            throw new GameException("Not in suspects phase");
        }
        
        // Send private packages to all players
        for (Player player : room.getAllPlayers()) {
            sendPlayerPackage(room.getRoomCode(), player, room);
        }
        
        startGameRound(room);
    }
    
    // ==================== PHASE 3: GAME ROUND (persistent page) ====================
    
    /**
     * Enter GAME_ROUND phase. currentRound=0 means no clues yet.
     * Host will press "Start Round" to reveal clues.
     */
    private void startGameRound(GameRoom room) {
        room.setPhase(GamePhase.GAME_ROUND);
        room.setRoundClueRevealed(false);
        broadcastGameState(room, null);
    }
    
    /**
     * Host presses "Start Round N" → reveal one clue per active player.
     */
    public void startRound(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.GAME_ROUND) {
            throw new GameException("Not in game round phase");
        }
        if (room.isRoundClueRevealed()) {
            throw new GameException("Clue already revealed this round");
        }
        
        room.startNewRound(); // increments currentRound, resets votes, sets roundClueRevealed=false
        room.setRoundClueRevealed(true);
        
        // Reveal one more clue per player — including eliminated "ghosts"
        // so no pre-generated clues are lost from the deduction chain
        for (Player player : room.getAllPlayers()) {
            player.revealNextClue();
        }
        
        // Send updated packages to ALL players (ghosts still need their clues)
        for (Player player : room.getAllPlayers()) {
            sendPlayerPackage(room.getRoomCode(), player, room);
        }
        
        broadcastGameState(room, "الجولة " + room.getCurrentRound() + " — تم كشف دليل جديد");
    }
    
    // ==================== PHASE 4: VOTING ====================
    
    /**
     * Host initiates voting from the game round page.
     */
    public void startVoting(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.GAME_ROUND) {
            throw new GameException("Not in game round phase");
        }
        if (!room.isRoundClueRevealed()) {
            throw new GameException("Must reveal clue before voting");
        }
        
        room.setPhase(GamePhase.VOTING);
        room.getActivePlayers().forEach(p -> p.setVoteTarget(null));
        broadcastGameState(room, "مرحلة التصويت — صوّت لمن تعتقد أنه المجرم");
    }
    
    public void castVote(String roomCode, String playerId, VoteRequest request) {
        GameRoom room = roomService.getRoom(roomCode);
        
        if (room.getPhase() != GamePhase.VOTING) {
            throw new GameException("Not in voting phase");
        }
        
        Player voter = room.getPlayer(playerId)
                .orElseThrow(() -> new GameException("Player not found"));
        
        if (voter.isEliminated()) {
            throw new GameException("Eliminated players cannot vote");
        }
        
        Player target = room.getPlayer(request.getTargetPlayerId())
                .orElseThrow(() -> new GameException("Target player not found"));
        
        if (target.isEliminated()) {
            throw new GameException("Cannot vote for eliminated player");
        }
        
        voter.setVoteTarget(request.getTargetPlayerId());
        
        long votedCount = room.getActivePlayers().stream()
                .filter(p -> p.getVoteTarget() != null)
                .count();
        
        webSocketService.broadcastToRoom(roomCode, WebSocketMessage.of("VOTE_UPDATE", Map.of(
                "votedCount", votedCount,
                "totalPlayers", room.getActivePlayerCount()
        )));
        
        if (votedCount == room.getActivePlayerCount()) {
            resolveVotes(room);
        }
    }
    
    // ==================== VOTE RESOLUTION ====================
    
    private void resolveVotes(GameRoom room) {
        Map<String, Long> voteCounts = room.getActivePlayers().stream()
                .filter(p -> p.getVoteTarget() != null)
                .collect(Collectors.groupingBy(Player::getVoteTarget, Collectors.counting()));
        
        long maxVotes = voteCounts.values().stream()
                .mapToLong(Long::longValue).max().orElse(0);
        
        List<String> topVoted = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey).toList();
        
        // Broadcast vote results to everyone
        webSocketService.broadcastToRoom(room.getRoomCode(), WebSocketMessage.of("VOTE_RESULT", 
                buildVoteResultMap(room, voteCounts)));
        
        if (topVoted.size() > 1) {
            // Tie → TIE_BREAK phase, host picks
            room.setPhase(GamePhase.TIE_BREAK);
            room.setTiedPlayerIds(new ArrayList<>(topVoted));
            broadcastGameState(room, "تعادل في التصويت! المضيف يختار من بين المتعادلين");
        } else {
            // Clear winner → eliminate
            eliminatePlayer(room, topVoted.get(0));
        }
    }
    
    // ==================== PHASE 5: TIE BREAK ====================
    
    /**
     * Host picks one of the tied players to eliminate.
     */
    public void resolveTieBreak(String roomCode, String requestingPlayerId, String targetPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.TIE_BREAK) {
            throw new GameException("Not in tie break phase");
        }
        
        if (!room.getTiedPlayerIds().contains(targetPlayerId)) {
            throw new GameException("Player is not among tied candidates");
        }
        
        eliminatePlayer(room, targetPlayerId);
    }
    
    // ==================== PHASE 6: ELIMINATION REVEAL ====================
    
    private void eliminatePlayer(GameRoom room, String eliminatedId) {
        Player eliminated = room.getPlayer(eliminatedId).orElseThrow();
        eliminated.setEliminated(true);
        
        boolean wasCriminal = eliminated.getRole() == PlayerRole.CRIMINAL;
        
        room.setPhase(GamePhase.ELIMINATION_REVEAL);
        room.setTiedPlayerIds(new ArrayList<>());
        
        // Store elimination info for the game state broadcast
        broadcastGameState(room, null);
        
        // Send detailed elimination info
        Map<String, Object> revealData = new HashMap<>();
        revealData.put("eliminatedPlayer", eliminated.getName());
        revealData.put("eliminatedPlayerId", eliminated.getId());
        revealData.put("wasCriminal", wasCriminal);
        revealData.put("characterDescription", 
                eliminated.getPlayerPackage() != null ? eliminated.getPlayerPackage().getCharacterDescription() : "");
        
        webSocketService.broadcastToRoom(room.getRoomCode(), 
                WebSocketMessage.of("ELIMINATION_REVEAL", revealData));
    }
    
    /**
     * Host presses "Next" after elimination reveal.
     * Check win/loss conditions, then continue to next GAME_ROUND or GAME_OVER.
     */
    public void continueAfterElimination(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.ELIMINATION_REVEAL) {
            throw new GameException("Not in elimination reveal phase");
        }
        
        // Check win conditions
        if (room.getActiveCriminalCount() == 0) {
            // All criminals caught → innocents win
            room.setPhase(GamePhase.GAME_OVER);
            room.setWinningSide("innocents");
            broadcastGameState(room, null);
            webSocketService.broadcastToRoom(room.getRoomCode(), WebSocketMessage.of("GAME_OVER", Map.of(
                    "winner", "innocents",
                    "groundTruth", room.getGroundTruth() != null ? room.getGroundTruth() : ""
            )));
            return;
        }
        
        if (room.getActiveInnocentCount() <= room.getActiveCriminalCount()) {
            // Criminals outnumber or equal innocents → criminals win
            room.setPhase(GamePhase.GAME_OVER);
            room.setWinningSide("criminal");
            broadcastGameState(room, null);
            webSocketService.broadcastToRoom(room.getRoomCode(), WebSocketMessage.of("GAME_OVER", Map.of(
                    "winner", "criminal",
                    "groundTruth", room.getGroundTruth() != null ? room.getGroundTruth() : ""
            )));
            return;
        }
        
        if (room.getCurrentRound() >= room.getMaxRounds()) {
            // Max rounds reached → criminals win
            room.setPhase(GamePhase.GAME_OVER);
            room.setWinningSide("criminal");
            broadcastGameState(room, null);
            webSocketService.broadcastToRoom(room.getRoomCode(), WebSocketMessage.of("GAME_OVER", Map.of(
                    "winner", "criminal",
                    "reason", "انتهت الجولات المتاحة",
                    "groundTruth", room.getGroundTruth() != null ? room.getGroundTruth() : ""
            )));
            return;
        }
        
        // Continue to next game round
        startGameRound(room);
    }
    
    // ==================== HELPERS ====================
    
    private void validateHost(GameRoom room, String playerId) {
        if (!room.getHostPlayerId().equals(playerId)) {
            throw new GameException("Only the host can perform this action");
        }
    }
    
    /**
     * Send private package to a player.
     */
    private void sendPlayerPackage(String roomCode, Player player, GameRoom room) {
        if (player.getPlayerPackage() == null) {
            log.warn("sendPlayerPackage: Player '{}' has NULL package, skipping", player.getName());
            return;
        }
        
        var pkg = player.getPlayerPackage();
        int totalClues = pkg.getPrivateClues() != null ? pkg.getPrivateClues().size() : 0;
        
        List<String> clues = player.getRevealedClues().stream()
                .map(com.thecrime.domain.model.PlayerClue::getClue)
                .toList();
        
        List<String> fellowCriminals = null;
        if (player.getRole() == PlayerRole.CRIMINAL) {
            // Criminals know each other
            fellowCriminals = room.getCriminals().stream()
                    .filter(c -> !c.getId().equals(player.getId()))
                    .map(Player::getName)
                    .toList();
        }
        
        log.info("sendPlayerPackage to '{}': role={}, revealedClueCount={}, totalClues={}, cluesSending={}",
                player.getName(), player.getRole(), player.getRevealedClueCount(), totalClues, clues.size());
        
        PlayerPackageDto dto = PlayerPackageDto.builder()
                .role(pkg.getRole())
                .characterDescription(pkg.getCharacterDescription())
                .mustSayTestimony(pkg.getMustSayTestimony())
                .privateClues(clues)
                .totalClueCount(totalClues)
                .fellowCriminals(fellowCriminals)
                .build();
        
        webSocketService.sendToPlayer(roomCode, player.getId(), 
                WebSocketMessage.of("YOUR_PACKAGE", dto));
    }
    
    private Map<String, Long> buildVoteResultMap(GameRoom room, Map<String, Long> voteCounts) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : voteCounts.entrySet()) {
            room.getPlayer(entry.getKey())
                    .ifPresent(p -> result.put(p.getName(), entry.getValue()));
        }
        return result;
    }
    
    // ==================== KICK & RESET ====================
    
    public void kickPlayer(String roomCode, String requestingPlayerId, String targetPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (requestingPlayerId.equals(targetPlayerId)) {
            throw new GameException("Cannot kick yourself");
        }
        
        Player target = room.getPlayer(targetPlayerId)
                .orElseThrow(() -> new GameException("Player not found"));
        
        String targetName = target.getName();
        
        webSocketService.sendToPlayer(roomCode, targetPlayerId, 
                WebSocketMessage.of("KICKED", Map.of("message", "تم إزالتك من الغرفة")));
        
        roomService.removePlayerFromRoom(roomCode, targetPlayerId);
        
        log.info("Host kicked player {} from room {}", targetName, roomCode);
        broadcastGameState(room, "تم إزالة " + targetName + " من الغرفة");
    }
    
    public void resetRoom(String roomCode, String requestingPlayerId) {
        GameRoom room = roomService.getRoom(roomCode);
        validateHost(room, requestingPlayerId);
        
        if (room.getPhase() != GamePhase.GAME_OVER) {
            throw new GameException("Game must be over to replay");
        }
        
        room.getAllPlayers().forEach(Player::reset);
        
        room.setPhase(GamePhase.LOBBY);
        room.setCurrentRound(0);
        room.setRoundClueRevealed(false);
        room.setTiedPlayerIds(new ArrayList<>());
        room.setCrimeBriefing(null);
        room.setGroundTruth(null);
        room.setMasterTimeline(null);
        room.setWinningSide(null);
        
        log.info("Room {} reset for new game", roomCode);
        
        webSocketService.broadcastToRoom(roomCode, 
                WebSocketMessage.of("ROOM_RESET", Map.of("message", "تم إعادة تهيئة الغرفة")));
        broadcastGameState(room, "تم إعادة تهيئة الغرفة — جاهزون للعب مجدداً!");
    }
    
    // ==================== BROADCAST ====================
    
    public void broadcastGameState(GameRoom room, String message) {
        List<PlayerDto> playerDtos = room.getAllPlayers().stream()
                .map(p -> PlayerDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .characterDescription(
                            p.getPlayerPackage() != null ? p.getPlayerPackage().getCharacterDescription() : null
                        )
                        .suspicionReason(
                            p.getPlayerPackage() != null ? p.getPlayerPackage().getSuspicionReason() : null
                        )
                        .isHost(p.isHost())
                        .isEliminated(p.isEliminated())
                        .hasVoted(p.getVoteTarget() != null)
                        .build())
                .toList();
        
        // Find last eliminated player info for ELIMINATION_REVEAL phase
        String eliminatedPlayerName = null;
        String eliminatedPlayerRole = null;
        if (room.getPhase() == GamePhase.ELIMINATION_REVEAL) {
            Optional<Player> lastEliminated = room.getAllPlayers().stream()
                    .filter(Player::isEliminated)
                    .reduce((a, b) -> b); // last eliminated
            if (lastEliminated.isPresent()) {
                eliminatedPlayerName = lastEliminated.get().getName();
                eliminatedPlayerRole = lastEliminated.get().getRole() != null 
                        ? lastEliminated.get().getRole().name() : null;
            }
        }
        
        GameStateDto state = GameStateDto.builder()
                .roomCode(room.getRoomCode())
                .phase(room.getPhase())
                .currentRound(room.getCurrentRound())
                .players(playerDtos)
                .setting(room.getSetting())
                .crimeBriefing(room.getCrimeBriefing())
                .roundClueRevealed(room.isRoundClueRevealed())
                .tiedPlayerIds(room.getTiedPlayerIds())
                .eliminatedPlayerName(eliminatedPlayerName)
                .eliminatedPlayerRole(eliminatedPlayerRole)
                .message(message)
                .build();
        
        webSocketService.broadcastToRoom(room.getRoomCode(), WebSocketMessage.of("GAME_STATE", state));
    }
}
