package me.mrletsplay.shareserver;

import java.util.HashMap;
import java.util.Map;

public class ShareServer {

	private static final Map<String, Session> SESSIONS = new HashMap<>();

	public static void main(String[] args) {
		new ShareWSServer().run();
	}

	public static Session getOrCreateSession(String sessionID) {
		return SESSIONS.computeIfAbsent(sessionID, Session::new);
	}

	public static void deleteSession(String sessionID) {
		SESSIONS.remove(sessionID);
	}

}
