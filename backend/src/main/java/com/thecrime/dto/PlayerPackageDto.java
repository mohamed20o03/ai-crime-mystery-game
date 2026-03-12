package com.thecrime.dto;

import com.thecrime.domain.enums.PlayerRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The private package sent to a specific player.
 * This is delivered via private WebSocket message.
 */
@Data
@Builder
public class PlayerPackageDto {
    private PlayerRole role;
    private String characterDescription;
    private String mustSayTestimony;
    
    // Only the revealed clues (gradual reveal)
    // For criminals: contains misleading messages instead of real clues
    private List<String> privateClues;
    
    // Total clues available (so frontend can show "X of Y")
    private int totalClueCount;
    
    // For criminals: list of other criminal player names (so they know each other)
    private List<String> fellowCriminals;
}
