package com.lorcandelong.it_ticketing_system.controller;

import com.lorcandelong.it_ticketing_system.model.Ticket;
import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.model.TicketStatus;
import com.lorcandelong.it_ticketing_system.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.BindingResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final TicketService ticketService;

    public DashboardController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/")
    @SuppressWarnings("null")
    public String dashboard(@RequestParam(required = false) TicketStatus status,
                           @RequestParam(required = false) String assignee,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           HttpServletRequest request,
                           Model model) {
        var principal = request.getUserPrincipal();
        boolean isTechnician = request.isUserInRole("TECHNICIAN");
        String currentUsername = principal != null ? principal.getName() : null;

        var visibleTickets = isTechnician && currentUsername != null
                ? ticketService.findByAssignee(currentUsername)
                : ticketService.findAll();
        var tickets = visibleTickets;

        if (search != null && !search.isBlank()) {
            tickets = tickets.stream()
                    .filter(ticket -> ticket.getTitle().toLowerCase().contains(search.toLowerCase())
                            || ticket.getDescription().toLowerCase().contains(search.toLowerCase())
                            || (ticket.getAssignee() != null && ticket.getAssignee().toLowerCase().contains(search.toLowerCase())))
                    .toList();
        }
        if (status != null) {
            tickets = tickets.stream()
                    .filter(ticket -> ticket.getStatus() == status)
                    .toList();
        }
        if (assignee != null && !assignee.isBlank()) {
            final String effectiveAssignee = isTechnician && currentUsername != null
                    ? currentUsername
                    : assignee.trim();
            tickets = tickets.stream()
                    .filter(ticket -> effectiveAssignee.equalsIgnoreCase(ticket.getAssignee()))
                    .toList();
        }

                tickets = tickets.stream()
                    .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            int pageSize = 10;
            int totalPages = Math.max(1, (tickets.size() + pageSize - 1) / pageSize);
            int pageNumber = Math.max(0, Math.min(page, totalPages - 1));
            int pageStart = pageNumber * pageSize;
            int pageEnd = Math.min(pageStart + pageSize, tickets.size());
            var pageTickets = tickets.subList(pageStart, pageEnd);
            Map<Long, String> slaByTicket = new LinkedHashMap<>();
            for (Ticket ticket : tickets) {
                slaByTicket.put(ticket.getId(), ticketService.calculateSlaStatus(ticket.getId()));
            }

            model.addAttribute("tickets", pageTickets);
            model.addAttribute("filteredTicketCount", tickets.size());
            model.addAttribute("pageNumber", pageNumber);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasPrevious", pageNumber > 0);
            model.addAttribute("hasNext", pageNumber < totalPages - 1);
            model.addAttribute("slaByTicket", slaByTicket);
            model.addAttribute("totalTickets", visibleTickets.size());
            model.addAttribute("openTickets", visibleTickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.OPEN).count());
            model.addAttribute("inProgressTickets", visibleTickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.IN_PROGRESS).count());
            model.addAttribute("closedTickets", visibleTickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.CLOSED).count());
        model.addAttribute("slaCounts", Map.of(
                "On track", tickets.stream().filter(ticket -> "On track".equals(ticketService.calculateSlaStatus(ticket.getId()))).count(),
                "At risk", tickets.stream().filter(ticket -> "At risk".equals(ticketService.calculateSlaStatus(ticket.getId()))).count(),
                "Breached", tickets.stream().filter(ticket -> "Breached".equals(ticketService.calculateSlaStatus(ticket.getId()))).count()));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("assignees", visibleTickets.stream()
                .map(ticket -> ticket.getAssignee())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedAssignee", assignee);
        model.addAttribute("selectedSearch", search);
        model.addAttribute("currentUsername", currentUsername);
        model.addAttribute("isTechnician", isTechnician);
        model.addAttribute("currentPage", "dashboard");
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/reports")
    @SuppressWarnings("null")
    public String reports(HttpServletRequest request, Model model) {
        List<Ticket> tickets = visibleTickets(request);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            statusCounts.put(status.name(), tickets.stream().filter(ticket -> ticket.getStatus() == status).count());
        }

        Map<String, Long> assigneeCounts = tickets.stream()
                .map(Ticket::getAssignee)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank() && !"Unassigned".equalsIgnoreCase(value))
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> priorityCounts = new LinkedHashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            priorityCounts.put(priority.name(), tickets.stream().filter(ticket -> ticket.getPriority() == priority).count());
        }

        model.addAttribute("totalTickets", tickets.size());
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("assigneeCounts", assigneeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList());
        model.addAttribute("priorityCounts", priorityCounts);
        model.addAttribute("mostActiveAssignee", assigneeCounts.isEmpty() ? "Unassigned" :
                assigneeCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("Unassigned"));
        model.addAttribute("currentPage", "reports");
        return "reports";
    }

    @GetMapping("/tickets/overdue")
    public String overdueTickets(HttpServletRequest request, Model model) {
        List<Ticket> allTickets = visibleTickets(request);
        var overdueTickets = allTickets.stream()
                .filter(ticket -> {
                    String slaStatus = ticketService.calculateSlaStatus(ticket.getId());
                    return "At risk".equals(slaStatus) || "Breached".equals(slaStatus);
                })
                .toList();

        long breachedCount = overdueTickets.stream()
                .filter(ticket -> "Breached".equals(ticketService.calculateSlaStatus(ticket.getId())))
                .count();
        long atRiskCount = overdueTickets.stream()
                .filter(ticket -> "At risk".equals(ticketService.calculateSlaStatus(ticket.getId())))
                .count();

        model.addAttribute("tickets", overdueTickets);
        model.addAttribute("breachedCount", breachedCount);
        model.addAttribute("atRiskCount", atRiskCount);
        model.addAttribute("currentPage", "overdue");
        return "overdue";
    }

    @GetMapping("/tickets/export")
    public ResponseEntity<String> exportTickets() {
        String csv = ticketService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tickets.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/tickets/new")
    public String newTicketForm(Model model) {
        model.addAttribute("ticket", new TicketForm("", "", TicketPriority.MEDIUM, ""));
        model.addAttribute("currentPage", "new");
        return "ticket-form";
    }

    @PostMapping("/tickets/import")
    public String importTickets(@RequestParam("file") MultipartFile file, HttpServletRequest request,
                                RedirectAttributes redirectAttributes) throws Exception {
        if (!request.isUserInRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can import tickets");
        }
        if (file != null && !file.isEmpty()) {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            int importedCount = ticketService.importCsv(csv, currentActor(request));
            redirectAttributes.addFlashAttribute("importedCount", importedCount);
        } else {
            redirectAttributes.addFlashAttribute("importedCount", 0);
        }
        return "redirect:/";
    }

    @PostMapping("/tickets/new")
    public String createTicket(@Valid @ModelAttribute("ticket") TicketForm form, BindingResult bindingResult,
                               HttpServletRequest request, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentPage", "new");
            return "ticket-form";
        }
        String assignee = request.isUserInRole("TECHNICIAN") ? currentActor(request) : form.assignee();
        ticketService.create(form.title().trim(), form.description().trim(), form.priority(), assignee, currentActor(request));
        return "redirect:/";
    }

    @GetMapping("/tickets/{id}")
    public String ticketDetail(@PathVariable Long id, HttpServletRequest request, Model model) {
        var ticket = ticketService.findById(id);
        if (request.isUserInRole("TECHNICIAN") && !currentActor(request).equalsIgnoreCase(ticket.getAssignee())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Technicians can only view assigned tickets");
        }
        var assigneeOptions = java.util.stream.Stream.concat(
                java.util.stream.Stream.of("admin", "tech", "Unassigned"),
                ticketService.findAll().stream()
                        .map(ticketItem -> ticketItem.getAssignee())
                        .filter(name -> name != null && !name.isBlank())
                        .distinct())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", ticketService.findCommentsByTicketId(id));
        model.addAttribute("activities", ticketService.findActivitiesByTicketId(id));
        model.addAttribute("isAdmin", request.isUserInRole("ADMIN"));
        model.addAttribute("assigneeOptions", assigneeOptions);
        model.addAttribute("currentPage", "ticket");
        return "ticket-detail";
    }

    @PostMapping("/tickets/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam("status") TicketStatus status,
                               HttpServletRequest request) {
        requireTicketAccess(id, request);
        ticketService.updateStatus(id, status, currentActor(request));
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/escalate")
    public String escalateTicket(@PathVariable Long id, HttpServletRequest request) {
        requireTicketAccess(id, request);
        ticketService.escalate(id, currentActor(request));
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/assignee")
    public String updateAssignee(@PathVariable Long id,
                                @RequestParam("assignee") String assignee,
                                HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can reassign tickets");
        }
        if (assignee != null && !assignee.isBlank()) {
            ticketService.updateAssignee(id, assignee.trim(), currentActor(request));
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/delete")
    public String deleteTicket(@PathVariable Long id, HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can delete tickets");
        }
        ticketService.delete(id, currentActor(request));
        return "redirect:/";
    }

    @PostMapping("/tickets/{id}/comment")
    public String addComment(@PathVariable Long id, @RequestParam("message") String message,
                             HttpServletRequest request) {
        requireTicketAccess(id, request);
        if (message != null && !message.isBlank()) {
            ticketService.addComment(id, message.trim(), currentActor(request));
        }
        return "redirect:/tickets/" + id;
    }

    public record TicketForm(@NotBlank @Size(max = 150) String title,
                             @NotBlank @Size(max = 5000) String description,
                             @NotNull TicketPriority priority,
                             @Size(max = 100) String assignee) {
        public TicketForm {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            assignee = assignee == null ? "" : assignee;
        }
    }

    private List<Ticket> visibleTickets(HttpServletRequest request) {
        if (!request.isUserInRole("TECHNICIAN")) {
            return ticketService.findAll();
        }
        return ticketService.findByAssignee(currentActor(request));
    }

    private void requireTicketAccess(Long id, HttpServletRequest request) {
        if (request.isUserInRole("TECHNICIAN")
                && !currentActor(request).equalsIgnoreCase(ticketService.findById(id).getAssignee())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Technicians can only modify assigned tickets");
        }
    }

    private String currentActor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "System" : request.getUserPrincipal().getName();
    }
}
