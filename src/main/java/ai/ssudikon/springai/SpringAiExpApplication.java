package ai.ssudikon.springai;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class SpringAiExpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiExpApplication.class, args);
    }

    @Component
    static class AlexPopeAiClient {
        private final VectorStore vectorStore;
        private final ChatClient chatClient;

        AlexPopeAiClient(VectorStore vectorStore, ChatClient chatClient) {
            this.vectorStore = vectorStore;
            this.chatClient = chatClient;
        }

        public String chat(String query) {
            var prompt = """
                    You're assisting with questions about alexander pope.
                    Alexander Pope (1688–1744) was an English poet, translator, and satirist known for his significant contributions to 18th-century English literature. 
                    He is renowned for works like "The Rape of the Lock," "The Dunciad," and "An Essay on Criticism." Pope's writing style often featured satire and discursive poetry, and his translations of Homer were also notable.
                                        
                    Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                    If unsure, simply state that you don't know.
                                        
                    DOCUMENTS:
                    {documents}  
                    """;

            var listOfSimilarDocs = vectorStore.similaritySearch(query);
            var docs = listOfSimilarDocs.stream().map(Document::getContent).collect(Collectors.joining(System.lineSeparator()));

            var systemMessage = new SystemPromptTemplate(prompt).createMessage(Map.of("documents", docs));
            var userMessage = new UserMessage(query);

            var promptList = new Prompt(List.of(systemMessage, userMessage));
            var aiResponse = chatClient.call(promptList);

            return aiResponse.getResult().getOutput().getContent();

        }

    }

    @Bean
    ApplicationRunner applicationRunner(VectorStore vectorStore, @Value("file:/Users/sudikonda/Developer/Java/IdeaProjects/spring-ai-exp/src/main/resources/AlexanderPope-Wikipedia.pdf") Resource resource, JdbcTemplate jdbcTemplate, ChatClient chatClient, AlexPopeAiClient alexPopeAiClient) {
        return args -> {
            init(vectorStore, resource, jdbcTemplate);

            String chatResponse = alexPopeAiClient.chat("Who is Alexander Pope");
            System.out.println("chatResponse = " + chatResponse);

        };
    }

    private static void init(VectorStore vectorStore, Resource resource, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM vector_store");

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3).withNumberOfTopPagesToSkipBeforeDelete(1).build()).withPagesPerDocument(1).build();

        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(resource, config);
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> docs = tokenTextSplitter.apply(pagePdfDocumentReader.get());
        vectorStore.accept(docs);
    }

}
