package com.thecrime.dto;

import com.thecrime.domain.enums.GamePhase;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GameStateDto {
    private String roomCode;
    private GamePhase phase;
    private int currentRound;
    private List<PlayerDto> players;
    private String setting;
    private String crimeBriefing;
    
    // Whether the host has revealed the clue for the current round
    private boolean roundClueRevealed;
    
    // Tied player IDs (for TIE_BREAK phase)
    private List<String> tiedPlayerIds;
    
    // Last eliminated player info (for ELIMINATION_REVEAL phase)
    private String eliminatedPlayerName;
    private String eliminatedPlayerRole; // "CRIMINAL" or "INNOCENT"
    
    private String message;
}
