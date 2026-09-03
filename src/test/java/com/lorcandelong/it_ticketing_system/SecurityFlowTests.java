package com.lorcandelong.it_ticketing_system;

import com.lorcandelong.it_ticketing_system.model.TicketPriority;
import com.lorcandelong.it_ticketing_system.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityFlowTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketService ticketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void validCredentialsRedirectToDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void technicianCredentialsAlsoRedirectToDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tech")
                        .param("password", "tech123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void technicianCannotReassignTicket() throws Exception {
        var ticket = ticketService.create("Laptop rollback", "Patch failed and device was reverted", TicketPriority.MEDIUM, "tech");

        mockMvc.perform(post("/tickets/{id}/assignee", ticket.getId())
                        .with(user("tech").roles("TECHNICIAN"))
                        .param("assignee", "new-admin")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestsRequireCsrf() throws Exception {
        var ticket = ticketService.create("Protected ticket", "Requires a token", TicketPriority.LOW, "tech");

        mockMvc.perform(post("/tickets/{id}/status", ticket.getId())
                        .with(user("tech").roles("TECHNICIAN"))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotOpenUnassignedTicketById() throws Exception {
        var ticket = ticketService.create("Private ticket", "Assigned elsewhere", TicketPriority.LOW, "admin");

        mockMvc.perform(get("/tickets/{id}", ticket.getId())
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReassignTicket() throws Exception {
        var ticket = ticketService.create("Laptop rollback", "Patch failed and device was reverted", TicketPriority.MEDIUM, "tech");

        mockMvc.perform(post("/tickets/{id}/assignee", ticket.getId())
                        .with(user("admin").roles("ADMIN"))
                        .param("assignee", "new-admin")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void ticketDetailShowsAssigneeDropdown() throws Exception {
        var ticket = ticketService.create("Laptop rollback", "Patch failed and device was reverted", TicketPriority.MEDIUM, "tech");

        mockMvc.perform(get("/tickets/{id}", ticket.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("name=\"assignee\"")))
                .andExpect(content().string(containsString("tech")))
                .andExpect(content().string(containsString("admin")));
    }

    @Test
    void exportTicketCsvReturnsData() throws Exception {
        ticketService.create("VPN outage", "Remote users cannot connect", TicketPriority.HIGH, "tech");

        mockMvc.perform(get("/tickets/export")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString("id,title,description,priority,status,assignee")))
                .andExpect(content().string(containsString("VPN outage")));
    }

    @Test
    void importTicketCsvCreatesTickets() throws Exception {
        mockMvc.perform(multipart("/tickets/import")
                        .file("file", "id,title,description,priority,status,assignee\n1,New import,Imported from csv,HIGH,OPEN,tech\n".getBytes())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void overdueTicketsPageIsAccessible() throws Exception {
        ticketService.create("VPN outage", "Remote users cannot connect", TicketPriority.HIGH, "tech");

        mockMvc.perform(get("/tickets/overdue")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void technicianSeesOnlyAssignedTickets() throws Exception {
        ticketService.create("Laptop rollback", "Patch failed and device was reverted", TicketPriority.MEDIUM, "tech");
        ticketService.create("Server outage", "Core infrastructure failure", TicketPriority.HIGH, "alice@company.com");

        mockMvc.perform(get("/")
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Laptop rollback")))
                .andExpect(content().string(not(containsString("Server outage"))));
    }

    @Test
    void apiReportsMissingTicketAsNotFound() throws Exception {
        mockMvc.perform(get("/api/tickets/999999")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not_found")));
    }

    @Test
    void apiReportsInvalidTransitionAsBadRequest() throws Exception {
        var ticket = ticketService.create("API workflow", "Validate status errors", TicketPriority.LOW, "tech");

        mockMvc.perform(put("/api/tickets/{id}/status", ticket.getId())
                        .with(user("tech").roles("TECHNICIAN"))
                        .contentType("application/json")
                        .content("{\"status\":\"CLOSED\"}")
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("bad_request")));
    }

    @Test
    void apiReturnsFlatTicketResponse() throws Exception {
        var ticket = ticketService.create("API response", "Stable response shape", TicketPriority.LOW, "tech");

        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"title\":\"API response\"")))
                .andExpect(content().string(not(containsString("comments"))))
                .andExpect(content().string(not(containsString("activities"))));
    }
}
