package org.example.dto.v2;

public class TaskCreateRequest {
    private String prompt;
    private String intent;
    private String operator;
    private boolean autoExecute = true;

    public TaskCreateRequest() {}

    public TaskCreateRequest(String prompt, String intent, String operator) {
        this.prompt = prompt;
        this.intent = intent;
        this.operator = operator;
        this.autoExecute = true;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public boolean isAutoExecute() {
        return autoExecute;
    }

    public void setAutoExecute(boolean autoExecute) {
        this.autoExecute = autoExecute;
    }
}
