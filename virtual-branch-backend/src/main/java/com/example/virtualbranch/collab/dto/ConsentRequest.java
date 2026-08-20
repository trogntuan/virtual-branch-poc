package com.example.virtualbranch.collab.dto;

import com.example.virtualbranch.collab.ConsentDecision;
import jakarta.validation.constraints.NotNull;

public record ConsentRequest(
        @NotNull String decision
) {
    public ConsentDecision toDecision() {
        return ConsentDecision.from(decision);
    }
}
