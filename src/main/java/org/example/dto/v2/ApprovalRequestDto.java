package org.example.dto.v2;

public class ApprovalRequestDto {
    private String approvalId;
    private String action; // APPROVE or REJECT
    private String operator;
    private String comment;

    public ApprovalRequestDto() {}

    public ApprovalRequestDto(String approvalId, String action, String operator, String comment) {
        this.approvalId = approvalId;
        this.action = action;
        this.operator = operator;
        this.comment = comment;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
