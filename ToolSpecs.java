
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class ToolSpecs {

    public static List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();

        specs.add(ToolSpecification.builder()
                .name("getWeather")
                .description("Get current live weather for a given city. Always return structured JSON output only.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "The city name")
                        .required("arg0")
                        .build())
                .build());

        specs.add(ToolSpecification.builder()
                .name("sendEmail")
                .description("")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "Recipient email address")
                        .addStringProperty("arg1", "Email Subject")
                        .addStringProperty("arg2", "Email body in HTML format")
                        .required("arg0", "arg1", "arg2")
                        .build())
                .build());

        specs.add(ToolSpecification.builder()
                .name("convertCurrency")
                .description("")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "Amount to convert")
                        .addStringProperty("arg1", "From currency")
                        .addStringProperty("arg2", "To currency")
                        .required("arg0", "arg1", "arg2")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name("webSearch")
                .description("")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "Search query")
                        .required("arg0")
                        .build())
                .build());

        return specs;
    }

}
