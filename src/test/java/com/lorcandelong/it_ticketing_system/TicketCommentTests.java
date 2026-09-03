package com.lorcandelong.it_ticketing_system;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketCommentTests {

    @Autowired
    private TicketService ticketService;

    @Test
    void canCreateCommentsAgainstTicket() {
        Ticket ticket = ticketService.create("VPN issue", "Users can’t connect", TicketPriority.HIGH, "alice@company.com");

        ticketService.addComment(ticket.getId(), "I’ve started investigating the VPN logs.");

        assertThat(ticketService.findCommentsByTicketId(ticket.getId()))
                .extracting(comment -> comment.getMessage())
                .contains("I’ve started investigating the VPN logs.");
    }
}
