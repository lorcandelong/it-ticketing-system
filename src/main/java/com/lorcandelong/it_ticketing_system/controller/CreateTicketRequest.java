package com.lorcandelong.it_ticketing_system.controller;

import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
		@NotBlank @Size(max = 150) String title,
		@NotBlank @Size(max = 5000) String description,
		@NotNull TicketPriority priority,
		@Size(max = 100) String assignee
) {
}