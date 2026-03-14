package com.thecrime.domain.model;

import com.thecrime.domain.enums.PlayerRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The private information package given to each player.
 * This is what the AI generates for each player.
 */
@Data
@Builder
public class PlayerPackage {
    private PlayerRole role;
    
    // Character description (shown on suspects page)
    private String characterDescription;
    
    // Why this suspect is suspicious (shown publicly)
    private String suspicionReason;
    
    private String personalSecret;
    
    private String alibi;
    
    // The mandatory testimony that must be read aloud
    private String mustSayTestimony;
    
    // For innocents: location description
    private String location;
    
    // Private clues that can be shared voluntarily
    private List<PlayerClue> privateClues;
    
    // For innocents: what they missed and why
    private String blindSpot;
    
    // For criminals: cover story, tactical notes
    private String coverStory;
    private String tacticalNote;
    private String alibiCrack;
    
    // What you know about others
    private String knowledgeAboutOthers;
}
