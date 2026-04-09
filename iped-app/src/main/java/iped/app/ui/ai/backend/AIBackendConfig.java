package iped.app.ui.ai.backend;

/**
 * An immutable configuration object that holds the necessary connection details 
 * for communicating with the AI Large Language Model (LLM) backend.
 * <p>
 * This class encapsulates the endpoint URL and authentication credentials. 
 * By passing this object to the backend services rather than individual string 
 * arguments, the system remains flexible and easier to maintain.
 * </p>
 */
public class AIBackendConfig {
    
    // The root URL of the AI API endpoint 
    private final String baseUrl;
    
    /** * The authentication key required to access the AI service. 
     * This may be an empty string or null if connecting to an unsecured local model.
     */
    private final String apiKey;

    public AIBackendConfig(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }
    
    // Note: Future integration point for loading these values directly from 
    // IPED's configuration property files (e.g., using iped.engine.config.ConfigurationManager)
}