package com.thecrime.domain.model;

import com.thecrime.domain.enums.GameLanguage;
import com.thecrime.domain.enums.GamePhase;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
public class GameRoom {
    private String roomCode;
    private String hostPlayerId;
    private GamePhase phase;
    private GameLanguage language;
    private String setting;
    
    @Builder.Default
    private Map<String, Player> players = new ConcurrentHashMap<>();
    
    @Builder.Default
    private int currentRound = 0;
    
    // Number of criminals (configurable at game start)
    @Builder.Default
    private int criminalCount = 1;
    
    // Whether the clue for the current round has been revealed
    @Builder.Default
    private boolean roundClueRevealed = false;
    
    // Tied player IDs (for TIE_BREAK phase)
    @Builder.Default
    private List<String> tiedPlayerIds = new ArrayList<>();
    
    // Crime briefing (shared with all players)
    private String crimeBriefing;
    
    // Ground truth (kept secret, only revealed at game end)
    private String groundTruth;
    
    // Master timeline (hidden chain-of-thought — never shown to players, used for validation)
    private String masterTimeline;
    
    // Full narrative story
    private String fullNarrative;
    
    // Game result
    private String winningSide; // "innocents" or "criminal"
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }
    
    public void removePlayer(String playerId) {
        players.remove(playerId);
    }
    
    public Optional<Player> getPlayer(String playerId) {
        return Optional.ofNullable(players.get(playerId));
    }
    
    public Player getHost() {
        return players.get(hostPlayerId);
    }
    
    public List<Player> getActivePlayers() {
        return players.values().stream()
                .filter(p -> !p.isEliminated())
                .toList();
    }
    
    public List<Player> getAllPlayers() {
        return new ArrayList<>(players.values());
    }
    
    public int getActivePlayerCount() {
        return (int) players.values().stream()
                .filter(p -> !p.isEliminated())
                .count();
    }
    
    public List<Player> getCriminals() {
        return players.values().stream()
                .filter(p -> p.getRole() == com.thecrime.domain.enums.PlayerRole.CRIMINAL)
                .toList();
    }
    
    public long getActiveCriminalCount() {
        return players.values().stream()
                .filter(p -> !p.isEliminated())
                .filter(p -> p.getRole() == com.thecrime.domain.enums.PlayerRole.CRIMINAL)
                .count();
    }
    
    public long getActiveInnocentCount() {
        return players.values().stream()
                .filter(p -> !p.isEliminated())
                .filter(p -> p.getRole() == com.thecrime.domain.enums.PlayerRole.INNOCENT)
                .count();
    }
    
    public void startNewRound() {
        currentRound++;
        roundClueRevealed = false;
        tiedPlayerIds.clear();
        // Reset votes
        players.values().forEach(p -> p.setVoteTarget(null));
    }
    
    public int getMaxRounds() {
        return players.size() + 2;
    }
}
