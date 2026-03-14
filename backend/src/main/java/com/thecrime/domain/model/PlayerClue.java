package com.thecrime.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * A structured clue given to a player, with hook and chain metadata
 * used for validation and progressive reveal.
 */
@Data
@Builder
public class PlayerClue {
    // The clue text shown to the player
    private String clue;
    
    private String type;
    
    private String targets;
    
    // Which player's testimony contains the hook sentence
    private String hookPlayer;
    
    // The exact sentence in hookPlayer's testimony that activates this clue
    private String hookSentence;
    
    // Name of another clue this one chains to (for narrowing suspicion)
    private String chainConnectsTo;
    
    private String narrativeSource;
}
