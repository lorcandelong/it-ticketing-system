package com.lorcandelong.it_ticketing_system.controller;

import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(@NotNull TicketStatus status) {
}