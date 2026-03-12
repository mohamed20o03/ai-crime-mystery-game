package com.thecrime.dto;

import lombok.Data;

@Data
public class QuestionRequest {
    private String questionType; // "location", "evidence", "timeline"
    private String question;
}
