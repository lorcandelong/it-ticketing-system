package com.lorcandelong.it_ticketing_system.config;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import com.lorcandelong.it_ticketing_system.repository.TicketRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final TicketRepository ticketRepository;

    public DataSeeder(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public void run(String... args) {
        if (ticketRepository.count() > 0) {
            return;
        }

        ticketRepository.save(new Ticket("VPN not connecting", "Remote user cannot authenticate to the company VPN after password reset.", TicketPriority.HIGH, "alice@company.com"));
        ticketRepository.save(new Ticket("Printer queue jam", "Finance department printer is stuck and showing offline status.", TicketPriority.MEDIUM, "mark@company.com"));
        ticketRepository.save(new Ticket("Laptop battery replacement", "Employee requested battery replacement for an aging workstation.", TicketPriority.LOW, "sam@company.com"));

        Ticket highPriorityTicket = new Ticket("Email downtime", "Users are reporting office 365 latency and failed sends.", TicketPriority.URGENT, "dana@company.com");
        highPriorityTicket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(highPriorityTicket);

        Ticket closedTicket = new Ticket("Access card reissue", "New starter access card was requested after move to a new office.", TicketPriority.MEDIUM, "nina@company.com");
        closedTicket.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(closedTicket);
    }
}
