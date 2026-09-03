package com.lorcandelong.it_ticketing_system.repository;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(TicketStatus status);
    List<Ticket> findByAssignee(String assignee);

    @Query("SELECT t FROM Ticket t WHERE " +
            "LOWER(t.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(t.assignee) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Ticket> search(@Param("term") String term);
}