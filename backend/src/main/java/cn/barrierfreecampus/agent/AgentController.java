package cn.barrierfreecampus.agent;

import cn.barrierfreecampus.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class AgentController {
    private final AgentService service;

    public AgentController(AgentService service) { this.service = service; }

    @GetMapping("/status")
    public ApiResponse<AgentDtos.AssistantStatus> status() { return ApiResponse.ok(service.status()); }

    @PostMapping("/conversations")
    public ApiResponse<AgentDtos.ConversationView> create(
            @Valid @RequestBody AgentDtos.CreateConversationRequest request, Authentication authentication) {
        return ApiResponse.ok(service.createConversation(authentication.getName(), request.title()));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AgentDtos.ConversationView>> conversations(Authentication authentication) {
        return ApiResponse.ok(service.conversations(authentication.getName()));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<AgentDtos.ConversationDetail> conversation(
            @PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(service.conversation(authentication.getName(), id));
    }

    @PostMapping(value = "/conversations/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@PathVariable UUID id,
                              @Valid @RequestBody AgentDtos.SendMessageRequest request,
                              Authentication authentication) {
        return service.stream(authentication.getName(), id, request);
    }

    @PutMapping("/drafts/{id}/confirmed")
    public ApiResponse<Void> confirmDraft(@PathVariable UUID id, Authentication authentication) {
        service.confirmDraft(id, authentication.getName());
        return ApiResponse.ok(null);
    }
}
