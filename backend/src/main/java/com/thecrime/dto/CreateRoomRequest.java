package com.thecrime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomRequest {
    @NotBlank(message = "Host name is required")
    @Size(min = 2, max = 20, message = "Name must be 2-20 characters")
    private String hostName;
    
    private String setting;
    
    private String language = "Arabic";
    
    // Number of criminals (default 1)
    private int criminalCount = 1;
}
