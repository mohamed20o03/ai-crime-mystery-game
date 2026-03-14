package com.thecrime.domain.model;

import com.thecrime.domain.enums.PlayerRole;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class Player {
    private String id;
    private String name;
    private String sessionId;
    private boolean isHost;
    private boolean isEliminated;
    
    // Assigned after game starts
    private PlayerRole role;
    private PlayerPackage playerPackage;
    
    // Track what has been shared
    @Builder.Default
    private List<String> sharedClues = new ArrayList<>();
    
    // How many private clues have been revealed to this player so far
    @Builder.Default
    private int revealedClueCount = 0;
    
    // Current vote target (player id)
    private String voteTarget;
    
    public void reset() {
        this.role = null;
        this.playerPackage = null;
        this.sharedClues = new ArrayList<>();
        this.revealedClueCount = 0;
        this.voteTarget = null;
        this.isEliminated = false;
    }
    
    /**
     * Get only the clues revealed so far (gradual reveal)
     */
    public List<PlayerClue> getRevealedClues() {
        if (playerPackage == null || playerPackage.getPrivateClues() == null) {
            return new ArrayList<>();
        }
        List<PlayerClue> allClues = playerPackage.getPrivateClues();
        return allClues.subList(0, Math.min(revealedClueCount, allClues.size()));
    }
    
    /**
     * Reveal one more clue. Returns true if a new clue was actually revealed.
     */
    public boolean revealNextClue() {
        if (playerPackage == null || playerPackage.getPrivateClues() == null) {
            return false;
        }
        if (revealedClueCount < playerPackage.getPrivateClues().size()) {
            revealedClueCount++;
            return true;
        }
        return false;
    }
}
