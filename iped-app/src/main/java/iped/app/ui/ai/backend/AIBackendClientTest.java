package iped.app.ui.ai.backend;

// This is a temporary class used for testing the llm integration with the 
// backend server. It sends the POST to the backend server which runs locally
// through docker. 
public class AIBackendClientTest {

    public static void main(String[] args) {
        System.out.println("Backend Integration Test\n");

        // Initialize Client 
        // config will throw an error as there is no valid keycloakToken yet
        AIBackendConfig config = new AIBackendConfig("http://localhost:8000", "change-me");
        AIBackendClient client = new AIBackendClient(config);

        // Note: Mock WhatsApp HTML must be formatted EXACTLY how the Python parser expects it
        String mockHtml = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body>" +
                "<div class=\"linha\">" +
                "  <div class=\"incoming\">" +
                "    <span class=\"time\">21/03/2025 14:30:00</span>" + 
                "    <span class=\"name\">João Silva</span>" +
                "    Bom dia, qual o preço do pão hoje?" + 
                "  </div>" +
                "</div>" +
                "<div class=\"linha\">" + 
                "  <div class=\"outgoing\">" +
                "    <span class=\"time\">21/03/2025 14:32:00</span>" +
                "    <span class=\"name\">Carlos</span>" +
                "    Está caro..." +
                "  </div>" +
                "</div>" +
                "</body></html>";

        try {
            // Test init Chat
            System.out.println("Testing /api/init_chat...");
            String chatHash = client.initChat(mockHtml);
            System.out.println("Server returned Chat Hash: " + chatHash + "\n");

            // test stream chat
            System.out.println("Phase 2: Testing /api/chat/stream...");
            String question = "Qual o nome de quem respondeu a pergunta de João? Quantos anos ele tem?";
            System.out.println("Question: " + question);
            System.out.print("Assistant Response: ");

            // Call the stream method. The lambda prints tokens exactly as they arrive.
            client.streamChatResponse(chatHash, question, token -> {
                System.out.print(token);
                System.out.flush(); // Force print to console immediately
            });

            System.out.println("\n\nStream completed successfully");

        } catch (AIBackendException e) {
            System.err.println("\nTest Failed: AI Backend Error");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("\nTest Failed: Unexpected Java Error");
            e.printStackTrace();
        }
    }
}
