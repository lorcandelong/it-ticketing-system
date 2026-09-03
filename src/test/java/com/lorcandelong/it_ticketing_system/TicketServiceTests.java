package com.lorcandelong.it_ticketing_system;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import com.lorcandelong.it_ticketing_system.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class TicketServiceTests {

    @Autowired
    private TicketService ticketService;

    @Test
    @SuppressWarnings("null")
    void canFindTicketsByStatusAndAssignee() {
        Ticket openTicket = ticketService.create("Wi-Fi outage", "Reception has no internet", TicketPriority.HIGH, "alice");
        ticketService.create("Access card request", "New starter access", TicketPriority.LOW, "bob");
        openTicket.setStatus(TicketStatus.IN_PROGRESS);
        ticketService.updateStatus(openTicket.getId(), TicketStatus.IN_PROGRESS);

        List<Ticket> byStatus = ticketService.findByStatus(TicketStatus.IN_PROGRESS);
        List<Ticket> byAssignee = ticketService.findByAssignee("alice");

        assertThat(byStatus).extracting(Ticket::getTitle).contains("Wi-Fi outage");
        assertThat(byAssignee).extracting(Ticket::getTitle).contains("Wi-Fi outage");
    }

    @Test
    @SuppressWarnings("null")
    void canSearchTicketsByKeyword() {
        ticketService.create("VPN outage", "Remote users cannot connect", TicketPriority.HIGH, "alice");
        ticketService.create("Laptop replacement", "Need a new battery for DL-12", TicketPriority.LOW, "bob");

        List<Ticket> results = ticketService.search("VPN");

        assertThat(results).extracting(Ticket::getTitle).contains("VPN outage");
    }

    @Test
    @SuppressWarnings("null")
    void canUpdateTicketAssignee() {
        Ticket ticket = ticketService.create("Printer queue jam", "Office printer is stuck", TicketPriority.MEDIUM, "saul");

        Ticket updated = ticketService.updateAssignee(ticket.getId(), "tech");

        assertThat(updated.getAssignee()).isEqualTo("tech");
    }

    @Test
    @SuppressWarnings("null")
    void canDeleteTicket() {
        Ticket ticket = ticketService.create("Archive request", "Need old files restored", TicketPriority.LOW, "admin");

        ticketService.delete(ticket.getId());

        assertThat(ticketService.findAll()).extracting(Ticket::getId).doesNotContain(ticket.getId());
    }

    @Test
    void canEscalateTicketStatus() {
        Ticket ticket = ticketService.create("Mail queue spike", "Too many messages stuck in exchange", TicketPriority.HIGH, "tech");

        Ticket escalated = ticketService.escalate(ticket.getId());

        assertThat(escalated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void canTrackSlaStatus() {
        Ticket ticket = ticketService.create("Mail queue spike", "Too many messages stuck in exchange", TicketPriority.URGENT, "tech");

        var slaStatus = ticketService.calculateSlaStatus(ticket.getId());

        assertThat(slaStatus).isNotNull();
    }

    @Test
    void recordsTicketActivity() {
        Ticket ticket = ticketService.create("Monitor failure", "Display is blank", TicketPriority.HIGH, "tech", "admin");

        ticketService.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, "tech");
        ticketService.addComment(ticket.getId(), "Investigating the display cable", "tech");

        assertThat(ticketService.findActivitiesByTicketId(ticket.getId()))
                .extracting("type")
                .containsExactly("COMMENTED", "STATUS_CHANGED", "CREATED");
    }

    @Test
    void importsQuotedCsvFields() {
        int imported = ticketService.importCsv(
                "id,title,description,priority,status,assignee\n"
                        + "1,\"Printer, finance\",\"Line one\nLine two\",HIGH,OPEN,tech\n");

        assertThat(imported).isEqualTo(1);
        assertThat(ticketService.search("Printer, finance")).anyMatch(ticket ->
                ticket.getDescription().equals("Line one\nLine two"));
    }

    @Test
    void closedTicketsCannotBeReopened() {
        Ticket ticket = ticketService.create("Completed request", "The work is complete", TicketPriority.LOW, "tech");
        ticketService.escalate(ticket.getId());
        ticketService.escalate(ticket.getId());

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.getId(), TicketStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Closed tickets cannot be reopened");
    }

    @Test
    void rejectsInvalidTicketInput() {
        assertThatThrownBy(() -> ticketService.create(" ", "Description", TicketPriority.LOW, "tech"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Title must contain between 1 and 150 characters");

        assertThatThrownBy(() -> ticketService.create("Title", "Description", null, "tech"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Priority is required");
    }

    @Test
    void rejectsInvalidCommentInput() {
        Ticket ticket = ticketService.create("Comment validation", "Validate notes", TicketPriority.LOW, "tech");

        assertThatThrownBy(() -> ticketService.addComment(ticket.getId(), " ", "tech"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Comment must contain between 1 and 5000 characters");
    }

    @Test
    void rejectsSkippedStatusTransitions() {
        Ticket ticket = ticketService.create("Workflow check", "Status must progress in order", TicketPriority.MEDIUM, "tech");

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.getId(), TicketStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid status transition from OPEN to CLOSED");
    }

    @Test
    void recordsCsvImportActivity() {
        ticketService.importCsv("id,title,description,priority,status,assignee\n"
                + "1,Imported ticket,Imported description,LOW,OPEN,tech\n", "admin");

        Ticket imported = ticketService.search("Imported ticket").get(0);

        assertThat(ticketService.findActivitiesByTicketId(imported.getId()))
                .extracting("actor", "type")
                .containsExactly(tuple("admin", "IMPORTED"));
    }
}
