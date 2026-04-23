package iped.app.ui.ai;

import java.util.function.Consumer;

import iped.app.ui.ai.backend.AIBackendService;

/**
 * Service layer that encapsulates AI chat workflow orchestration.
 */
public class AIChatService {

    private final AIChatCoordinator coordinator;

    public AIChatService(AIBackendService backendService) {
        this.coordinator = new AIChatCoordinator(backendService);
    }

    public void askQuestion(String question, Consumer<String> onToken, Runnable onComplete, Consumer<String> onError) {
        coordinator.askQuestion(question, onToken, onComplete, onError);
    }
}
