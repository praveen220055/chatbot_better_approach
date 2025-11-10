private final WeatherService weatherService = new WeatherService();
private final CurrencyExchangeTool currencyExchangeTool = new CurrencyExchangeTool();
private final WebSearch webSearch = new WebSearch();
private final EmailService emailService = new EmailService();


@POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response bot(com.zoho.chatbot.dto.ChatRequest request, @Context HttpServletRequest httpRequest) {
        try {
            String input = request.getInputStr();
            String convId = request.getConversationId();
            Long userId = getUserIdFromSession(httpRequest);
            if (convId == null || convId.trim().isEmpty()) {
            return Response.status(400).entity("{\"error\":\"conversation_id is required\"}").build();
        }
        if (!DataBaseUtils.isConversationOwnedByUser(convId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("{\"error\":\"You do not have permission to chat in this conversation.\"}")
                           .build();
        }

            List<ChatMessage> chatHistory = new ArrayList<>(db.loadMessages(convId, 50));

            chatHistory.add(SystemMessage.systemMessage(SYSTEM_MESSAGE));
            UserMessage userMsg = UserMessage.userMessage(input);
            chatHistory.add(userMsg);

            String userMessageJson = gson.toJson(serializeMessage(userMsg));

            List<ToolSpecification> toolSpecs = ToolSpecs.getToolSpecifications();

            JsonObject root = new JsonObject();

            JsonObject userJsonObject = new JsonObject();
            userJsonObject.addProperty("text", userMessageJson);
            root.add("user_request", userJsonObject);

            db.insertMessage(convId, "user", userMessageJson);
            JsonArray executionSteps = new JsonArray();

            int stepNumber = 1;
            int x = 0;
            int toolcallNum = 1;
            for (int i = 0; i < 10; i++) {
                System.out.println("ITERATION NUMBER" + x++);
                dev.langchain4j.model.chat.request.ChatRequest chatRequest = dev.langchain4j.model.chat.request.ChatRequest
                        .builder()
                        .toolSpecifications(toolSpecs)
                        .messages(chatHistory)
                        .build();

                ChatResponse chatResponse = model.chat(chatRequest);
                AiMessage aiMessage = chatResponse.aiMessage();
                String tempTitle = DataBaseUtils.getTitle(convId);
                boolean needsTitleUpdate = (tempTitle == null || tempTitle.trim().equals("New Chat")
                        || tempTitle.trim().isEmpty());

                if (needsTitleUpdate) {
                    MyAssistant assistant = AiServices.builder(MyAssistant.class)
                            .chatModel(model)
                            .build();

                    String getTitle = assistant.title(input);

                    DataBaseUtils.updateTitle(convId, getTitle);
                }

                if (aiMessage.hasToolExecutionRequests()) {

                    String aiMessageToolJson = gson.toJson(serializeMessage(aiMessage));
                    db.insertMessage(convId, "assistant", aiMessageToolJson);
                    System.out.println("NOTE THIS NOTE THIS ==========================");
                    System.out.println(aiMessageToolJson);
                    System.out.println("NOTE THIS NOTE THIS ==========================");
                    JsonObject stepObj = new JsonObject();

                    stepObj.addProperty("step_number", stepNumber);
                    stepNumber++;
                    stepObj.addProperty("step_type", "tool_execution");
                    JsonArray toolExecutions = new JsonArray();

                    for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {

                        JsonObject toolExecution = new JsonObject();
                        toolExecution.addProperty("tool_name", toolRequest.name());
                        toolExecution.addProperty("tool_id", toolRequest.id());
                        toolExecution.addProperty("tool_arguments", toolRequest.arguments());

                        String toolResult = executeTool(toolRequest);

                        toolExecution.addProperty("tool_result", toolResult);
                        toolExecutions.add(toolExecution);

                        ToolExecutionResultMessage toolMsg = ToolExecutionResultMessage.from(toolRequest, toolResult);

                        chatHistory.add(toolMsg);

                        String toolResultJson = gson.toJson(serializeMessage(toolMsg));
                        db.insertMessage(convId, "function", toolResultJson);
                    }

                    stepObj.add("tool_execution", toolExecutions);
                    executionSteps.add(stepObj);
                    chatHistory.add(aiMessage);
                    String aiMessageJson = gson.toJson(serializeMessage(aiMessage));
                    // db.insertMessage(convId, "assistant", "[Tool execution requested]", null,
                    // aiMessageJson);

                } else {
                    JsonObject stepObj = new JsonObject();
                    stepObj.addProperty("step_number", stepNumber);
                    stepObj.addProperty("step_type", "final_response");

                    String finalResponse = aiMessage.text().trim();
                    stepObj.addProperty("content", finalResponse);
                    executionSteps.add(stepObj);
                    String aiMessageJson = gson.toJson(serializeMessage(aiMessage));
                    root.add("execution_steps", executionSteps);
                    chatHistory.add(aiMessage);
                    db.insertMessage(convId, "assistant", aiMessageJson);
                    JsonObject jsonResponse = new JsonObject();

                    jsonResponse.addProperty("text", finalResponse);
                    root.add("response", jsonResponse);
                    return Response.ok(gson.toJson(root)).build();
                }
            }

            JsonObject error = new JsonObject();
            error.addProperty("error", "Max iterations reached without final answer");
            return Response.status(500).entity(gson.toJson(error)).build();

        } catch (Exception e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return Response.status(500).entity(gson.toJson(error)).build();
        }
    }

    public String executeTool(ToolExecutionRequest request) {
        try {
            String toolName = request.name();
            String argsString = request.arguments();
            JsonObject args = JsonParser.parseString(argsString).getAsJsonObject();

            switch (toolName) {
                case "getWeather":
                    return weatherService.getWeather(args.get("arg0").getAsString());
                case "sendEmail":
                    return emailService.sendEmail(
                            args.get("arg0").getAsString(),
                            args.get("arg1").getAsString(),
                            args.get("arg2").getAsString());
                case "convertCurrency":
                    return currencyExchangeTool.convertCurrency(
                            args.get("arg0").getAsDouble(),
                            args.get("arg1").getAsString(),
                            args.get("arg2").getAsString());
                case "webSearch":
                    return webSearch.search(args.get("arg0").getAsString());

                default:
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "unknown tool: " + toolName);
                    return gson.toJson(error);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject errorObject = new JsonObject();
            errorObject.addProperty("error", e.getMessage());
            return gson.toJson(errorObject);
        }
    }

    private JsonObject serializeMessage(ChatMessage message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", message.type().toString());
        if (message instanceof UserMessage) {
            UserMessage um = (UserMessage) message;
            json.addProperty("role", "user");
            json.addProperty("content", um.singleText());
        } else if (message instanceof AiMessage) {
            AiMessage am = (AiMessage) message;
            json.addProperty("role", "assistant");

            if (am.text() != null) {
                json.addProperty("content", am.text());
            }

            if (am.hasToolExecutionRequests()) {
                JsonArray toolReqs = new JsonArray();

                for (ToolExecutionRequest req : am.toolExecutionRequests()) {
                    JsonObject toolReq = new JsonObject();
                    toolReq.addProperty("id", req.id());
                    toolReq.addProperty("name", req.name());
                    toolReq.addProperty("arguments", req.arguments());
                    toolReqs.add(toolReq);
                }

                json.add("tool_requests", toolReqs);
            }
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage term = (ToolExecutionResultMessage) message;
            json.addProperty("role", "function_message");
            json.addProperty("id", term.id());
            json.addProperty("name", term.toolName());
            json.addProperty("content", term.text());
        }

        return json;
    }
