package com.hypergryph.arknights.core.pojo;

public class Mail {
    private int id;
    private String name;
    private String from;
    private String subject;
    private String content;
    private String items;

    public Mail() {
    }

    public int getId() {return this.id;}
    public void setId(int id) {this.id = id;}
    public String getName() {return this.name;}
    public void setName(String name) {this.name = name;}
    public String getFrom() {return this.from;}
    public void setFrom(String from) {this.from = from;}
    public String getSubject() {return this.subject;}
    public void setSubject(String subject) {this.subject = subject;}
    public String getContent() {return this.content;}
    public void setContent(String content) {this.content = content;}
    public String getItems() {return this.items;}
    public void setItems(String items) {this.items = items;}
}