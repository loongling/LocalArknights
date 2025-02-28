package com.hypergryph.arknights.core.pojo;

public class UserInfo {
    private long uid;
    private String status;
    private String chars;
    private String socialAssistCharList;
    private String assistCharList;
    private String friend;

    public UserInfo() {
    }

    public long getUid() {return this.uid;}
    public void setUid(long uid) {this.uid = uid;}
    public String getStatus() {return this.status;}
    public void setStatus(String status) {this.status = status;}
    public String getChars() {return this.chars;}
    public void setChars(String chars) {this.chars = chars;}
    public String getSocialAssistCharList() {return this.socialAssistCharList;}
    public void setSocialAssistCharList(String socialAssistCharList) {this.socialAssistCharList = socialAssistCharList;
    }
    public String getAssistCharList() {return this.assistCharList;}
    public void setAssistCharList(String assistCharList) {this.assistCharList = assistCharList;}
    public String getFriend() {return this.friend;}
    public void setFriend(String friend) {this.friend = friend;}
}