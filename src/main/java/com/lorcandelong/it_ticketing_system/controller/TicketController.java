package com.lorcandelong.it_ticketing_system.controller;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import com.lorcandelong.it_ticketing_system.service.TicketNotFoundException;
import com.lorcandelong.it_ticketing_system.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketResponse> findAll(HttpServletRequest request) {
		return visibleTickets(request).stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public TicketResponse findById(@PathVariable Long id, HttpServletRequest request) {
		Ticket ticket = ticketService.findById(id);
		requireAccess(ticket, request);
		return toResponse(ticket);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@Valid @RequestBody CreateTicketRequest request, HttpServletRequest servletRequest) {
		String assignee = servletRequest.isUserInRole("TECHNICIAN")
				? servletRequest.getUserPrincipal().getName() : request.assignee();
		return toResponse(ticketService.create(request.title().trim(), request.description().trim(), request.priority(), assignee,
				servletRequest.getUserPrincipal().getName()));
	}

	@PutMapping("/{id}/status")
	public TicketResponse updateStatus(
			@PathVariable Long id,
			@Valid @RequestBody UpdateTicketStatusRequest request,
			HttpServletRequest servletRequest
	) {
		Ticket ticket = ticketService.findById(id);
		requireAccess(ticket, servletRequest);
		return toResponse(ticketService.updateStatus(id, request.status(), servletRequest.getUserPrincipal().getName()));
	}

	private List<Ticket> visibleTickets(HttpServletRequest request) {
		if (!request.isUserInRole("TECHNICIAN")) {
			return ticketService.findAll();
		}
		return ticketService.findByAssignee(request.getUserPrincipal().getName());
	}

	private void requireAccess(Ticket ticket, HttpServletRequest request) {
		if (request.isUserInRole("TECHNICIAN")
				&& !request.getUserPrincipal().getName().equalsIgnoreCase(ticket.getAssignee())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Technicians can only access assigned tickets");
		}
	}

	private TicketResponse toResponse(Ticket ticket) {
		return new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getAssignee(),
				ticket.getStatus(), ticket.getPriority(), ticket.getCreatedAt(), ticket.getUpdatedAt());
	}

	public record TicketResponse(Long id, String title, String description, String assignee,
			TicketStatus status, TicketPriority priority, java.time.LocalDateTime createdAt,
			java.time.LocalDateTime updatedAt) {
	}

	@ExceptionHandler(TicketNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> handleNotFound(TicketNotFoundException exception) {
		return Map.of("error", "not_found", "message", exception.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> handleBadRequest(Exception exception) {
		String message = exception instanceof MethodArgumentNotValidException
				? "Request validation failed"
				: exception.getMessage();
		return Map.of("error", "bad_request", "message", message == null ? "Invalid request" : message);
	}
}