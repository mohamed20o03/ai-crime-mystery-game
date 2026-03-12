package com.thecrime.dto;

import lombok.Data;

@Data
public class ShareClueRequest {
    private int clueIndex; // Index into privateClues array, -1 for pass
}
