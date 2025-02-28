package com.hypergryph.arknights.core.pojo;

public class Account {
    private long uid;
    private String phone;
    private String password;
    private String secret;
    private String user;
    private String mails;
    private String assistCharList;
    private String friend;
    private long ban;

    public Account() {
    }

    public long getUid() {return this.uid;}
    public void setUid(long uid) {this.uid = uid;}
    public String getPhone() {return this.phone;}
    public void setPhone(String phone) {this.phone = phone;}
    public String getPassword() {return this.password;}
    public void setPassword(String password) {this.password = password;}
    public String getSecret() {return this.secret;}
    public void setSecret(String secret) {this.secret = secret;}
    public String getUser() {return this.user;}
    public void setUser(String user) {this.user = user;}
    public String getMails() {return this.mails;}
    public void setMails(String mails) {this.mails = mails;}
    public String getAssistCharList() {return this.assistCharList;}
    public void setAssistCharList(String assistCharList) {this.assistCharList = assistCharList;}
    public String getFriend() {return this.friend;}
    public void setFriend(String friend) {this.friend = friend;}
    public long getBan() {return this.ban;}
    public void setBan(long ban) {this.ban = ban;}
}

