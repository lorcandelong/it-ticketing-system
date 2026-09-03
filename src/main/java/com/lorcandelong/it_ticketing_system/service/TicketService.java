package com.lorcandelong.it_ticketing_system.service;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketActivity;
import com.lorcandelong.it_ticketing_system.model.TicketComment;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import com.lorcandelong.it_ticketing_system.repository.TicketCommentRepository;
import com.lorcandelong.it_ticketing_system.repository.TicketActivityRepository;
import com.lorcandelong.it_ticketing_system.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TicketService {

	private final TicketRepository ticketRepository;
	private final TicketCommentRepository ticketCommentRepository;
	private final TicketActivityRepository ticketActivityRepository;

	public TicketService(TicketRepository ticketRepository, TicketCommentRepository ticketCommentRepository,
			TicketActivityRepository ticketActivityRepository) {
		this.ticketRepository = ticketRepository;
		this.ticketCommentRepository = ticketCommentRepository;
		this.ticketActivityRepository = ticketActivityRepository;
	}

	@Transactional(readOnly = true)
	public List<Ticket> findAll() {
		return ticketRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Ticket findById(Long id) {
		return ticketRepository.findById(id)
				.orElseThrow(() -> new TicketNotFoundException(id));
	}

	public Ticket create(String title, String description, TicketPriority priority) {
		return create(title, description, priority, "Unassigned");
	}

	public Ticket create(String title, String description, TicketPriority priority, String assignee) {
		return create(title, description, priority, assignee, "System");
	}

	public Ticket create(String title, String description, TicketPriority priority, String assignee, String actor) {
		validateTicketInput(title, description, priority, assignee);
		Ticket ticket = ticketRepository.save(new Ticket(title, description, priority, assignee));
		recordActivity(ticket, actor, "CREATED", "Ticket created");
		return ticket;
	}

	@Transactional(readOnly = true)
	public List<Ticket> findByStatus(TicketStatus status) {
		return ticketRepository.findByStatus(status);
	}

	@Transactional(readOnly = true)
	public List<Ticket> findByAssignee(String assignee) {
		return ticketRepository.findByAssignee(assignee);
	}

	@Transactional(readOnly = true)
	public List<Ticket> search(String term) {
		if (term == null || term.isBlank()) {
			return findAll();
		}
		return ticketRepository.search(term.trim());
	}

	public Ticket updateStatus(Long id, TicketStatus status) {
		return updateStatus(id, status, "System");
	}

	public Ticket updateStatus(Long id, TicketStatus status, String actor) {
		Ticket ticket = findById(id);
		if (status == null) {
			throw new IllegalArgumentException("Status is required");
		}
		if (ticket.getStatus() == TicketStatus.CLOSED && status != TicketStatus.CLOSED) {
			throw new IllegalArgumentException("Closed tickets cannot be reopened");
		}
		TicketStatus previousStatus = ticket.getStatus();
		if (previousStatus != status && !isAllowedTransition(previousStatus, status)) {
			throw new IllegalArgumentException("Invalid status transition from " + previousStatus + " to " + status);
		}
		ticket.setStatus(status);
		Ticket saved = ticketRepository.save(ticket);
		if (previousStatus != status) {
			recordActivity(saved, actor, "STATUS_CHANGED", "Status changed from " + previousStatus + " to " + status);
		}
		return saved;
	}

	public Ticket escalate(Long id) {
		return escalate(id, "System");
	}

	public Ticket escalate(Long id, String actor) {
		Ticket ticket = findById(id);
		TicketStatus previousStatus = ticket.getStatus();
		TicketStatus nextStatus = switch (ticket.getStatus()) {
			case OPEN -> TicketStatus.IN_PROGRESS;
			case IN_PROGRESS -> TicketStatus.CLOSED;
			case CLOSED -> TicketStatus.CLOSED;
		};
		ticket.setStatus(nextStatus);
		Ticket saved = ticketRepository.save(ticket);
		if (previousStatus != nextStatus) {
			recordActivity(saved, actor, "ESCALATED", "Status advanced from " + previousStatus + " to " + nextStatus);
		}
		return saved;
	}

	public Ticket updateAssignee(Long id, String assignee) {
		return updateAssignee(id, assignee, "System");
	}

	public Ticket updateAssignee(Long id, String assignee, String actor) {
		Ticket ticket = findById(id);
		String previousAssignee = ticket.getAssignee();
		ticket.setAssignee(assignee);
		Ticket saved = ticketRepository.save(ticket);
		if (!previousAssignee.equals(saved.getAssignee())) {
			recordActivity(saved, actor, "ASSIGNED", "Assigned to " + saved.getAssignee());
		}
		return saved;
	}

	public String calculateSlaStatus(Long id) {
		Ticket ticket = findById(id);
		Duration age = Duration.between(ticket.getCreatedAt(), LocalDateTime.now());
		long hours = age.toHours();

		if (ticket.getStatus() == TicketStatus.CLOSED) {
			return "Closed";
		}
		if (ticket.getPriority() == TicketPriority.URGENT && hours > 6) {
			return "Breached";
		}
		if (ticket.getPriority() == TicketPriority.HIGH && hours > 12) {
			return "Breached";
		}
		if (ticket.getPriority() == TicketPriority.MEDIUM && hours > 24) {
			return "At risk";
		}
		if (ticket.getPriority() == TicketPriority.LOW && hours > 48) {
			return "At risk";
		}
		return "On track";
	}

	public void delete(Long id) {
		delete(id, "System");
	}

	public void delete(Long id, String actor) {
		ticketRepository.deleteById(id);
	}

	public TicketComment addComment(Long ticketId, String message) {
		return addComment(ticketId, message, "System");
	}

	public TicketComment addComment(Long ticketId, String message, String actor) {
		if (message == null || message.isBlank() || message.length() > 5000) {
			throw new IllegalArgumentException("Comment must contain between 1 and 5000 characters");
		}
		Ticket ticket = findById(ticketId);
		TicketComment comment = new TicketComment(message);
		ticket.addComment(comment);
		TicketComment saved = ticketCommentRepository.save(comment);
		recordActivity(ticket, actor, "COMMENTED", "Comment added");
		return saved;
	}

	@Transactional(readOnly = true)
	public List<TicketComment> findCommentsByTicketId(Long ticketId) {
		return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
	}

	@Transactional(readOnly = true)
	public List<TicketActivity> findActivitiesByTicketId(Long ticketId) {
		return ticketActivityRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
	}

	private void recordActivity(Ticket ticket, String actor, String type, String details) {
		String safeActor = actor == null || actor.isBlank() ? "System" : actor;
		ticketActivityRepository.save(new TicketActivity(ticket, safeActor, type, details));
	}

	private void validateTicketInput(String title, String description, TicketPriority priority, String assignee) {
		if (title == null || title.isBlank() || title.length() > 150) {
			throw new IllegalArgumentException("Title must contain between 1 and 150 characters");
		}
		if (description == null || description.isBlank() || description.length() > 5000) {
			throw new IllegalArgumentException("Description must contain between 1 and 5000 characters");
		}
		if (priority == null) {
			throw new IllegalArgumentException("Priority is required");
		}
		if (assignee != null && assignee.length() > 100) {
			throw new IllegalArgumentException("Assignee cannot exceed 100 characters");
		}
	}

	private boolean isAllowedTransition(TicketStatus previousStatus, TicketStatus nextStatus) {
		return switch (previousStatus) {
			case OPEN -> nextStatus == TicketStatus.IN_PROGRESS;
			case IN_PROGRESS -> nextStatus == TicketStatus.CLOSED;
			case CLOSED -> false;
		};
	}

	@Transactional(readOnly = true)
	public String exportCsv() {
		String header = "id,title,description,priority,status,assignee";
		String rows = ticketRepository.findAll().stream()
				.map(ticket -> String.format("%s,%s,%s,%s,%s,%s",
					ticket.getId(),
					sanitize(ticket.getTitle()),
					sanitize(ticket.getDescription()),
					ticket.getPriority(),
					ticket.getStatus(),
					sanitize(ticket.getAssignee())))
				.collect(Collectors.joining(System.lineSeparator()));
		return header + System.lineSeparator() + rows;
	}

	public int importCsv(String csv) {
		return importCsv(csv, "System");
	}

	public int importCsv(String csv, String actor) {
		if (csv == null || csv.isBlank()) {
			return 0;
		}

		List<String> lines = parseCsvRecords(csv);
		if (lines.size() <= 1) {
			return 0;
		}

		List<Ticket> imported = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isBlank() || line.startsWith("id,")) {
				continue;
			}

			List<String> parts = parseCsvLine(line);
			if (parts.size() < 6) {
				continue;
			}

			try {
				String title = parts.get(1).trim();
				String description = parts.get(2).trim();
				TicketPriority priority = TicketPriority.valueOf(parts.get(3).trim().toUpperCase());
				TicketStatus status = TicketStatus.valueOf(parts.get(4).trim().toUpperCase());
				String assignee = parts.get(5).trim();

				if (title.isBlank() || description.isBlank()) {
					continue;
				}
				Ticket ticket = new Ticket(title, description, priority, assignee);
				ticket.setStatus(status);
				Ticket saved = ticketRepository.save(ticket);
				recordActivity(saved, actor, "IMPORTED", "Ticket imported from CSV");
				imported.add(saved);
			} catch (IllegalArgumentException exception) {
			}
		}
		return imported.size();
	}

	private List<String> parseCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (character == ',' && !quoted) {
				fields.add(field.toString());
				field.setLength(0);
			} else {
				field.append(character);
			}
		}
		fields.add(field.toString());
		return fields;
	}

	private List<String> parseCsvRecords(String csv) {
		List<String> records = new ArrayList<>();
		StringBuilder record = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < csv.length(); i++) {
			char character = csv.charAt(i);
			if (character == '"') {
				quoted = !quoted;
			}
			if ((character == '\n' || character == '\r') && !quoted) {
				if (record.length() > 0) {
					records.add(record.toString());
					record.setLength(0);
				}
				if (character == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
					i++;
				}
			} else {
				record.append(character);
			}
		}
		if (record.length() > 0) {
			records.add(record.toString());
		}
		return records;
	}

	private String sanitize(String value) {
		String safe = value == null ? "" : value.replace("\"", "\"\"");
		if (safe.contains(",") || safe.contains("\n") || safe.contains("\"")) {
			return "\"" + safe + "\"";
		}
		return safe;
	}
}