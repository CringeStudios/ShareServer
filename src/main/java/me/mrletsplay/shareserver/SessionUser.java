package me.mrletsplay.shareserver;

public record SessionUser(Session session, String username, int siteID) {}
