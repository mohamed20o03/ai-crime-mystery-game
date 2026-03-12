package com.thecrime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerDto {
    private String id;
    private String name;
    private String characterDescription;
    private String suspicionReason;
    // @JsonProperty needed: Lombok generates isHost()/isEliminated() getters which
    // Jackson would serialize as "host"/"eliminated" (strips "is" prefix) without this.
    @JsonProperty("isHost")
    private boolean isHost;
    @JsonProperty("isEliminated")
    private boolean isEliminated;
    private boolean hasVoted;
}
