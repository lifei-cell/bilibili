package com.gary.bilibili.danmu.vo;

public class DanmuWebSocketTicketVO {

    private String ticket;
    private Long expiresIn;

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
