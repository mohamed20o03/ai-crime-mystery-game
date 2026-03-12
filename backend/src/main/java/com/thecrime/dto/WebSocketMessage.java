package com.thecrime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type;
    private Object payload;
    private String message;
    
    public static WebSocketMessage of(String type, Object payload) {
        return WebSocketMessage.builder()
                .type(type)
                .payload(payload)
                .build();
    }
    
    public static WebSocketMessage error(String message) {
        return WebSocketMessage.builder()
                .type("ERROR")
                .message(message)
                .build();
    }
    
    public static WebSocketMessage info(String message) {
        return WebSocketMessage.builder()
                .type("INFO")
                .message(message)
                .build();
    }
}
