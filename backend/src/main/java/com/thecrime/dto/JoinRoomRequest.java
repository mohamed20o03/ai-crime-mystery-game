package com.thecrime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinRoomRequest {
    @NotBlank(message = "Room code is required")
    @Size(min = 6, max = 6, message = "Room code must be 6 characters")
    private String roomCode;
    
    @NotBlank(message = "Player name is required")
    @Size(min = 2, max = 20, message = "Name must be 2-20 characters")
    private String playerName;
}
