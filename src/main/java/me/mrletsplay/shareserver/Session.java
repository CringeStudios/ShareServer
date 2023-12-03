package me.mrletsplay.shareserver;

public class Session {

	private String id;
	private int nextSiteID = 0;

	public Session(String id) {
		this.id = id;
	}

	public String getID() {
		return id;
	}

	public int getNewSiteID() {
		return nextSiteID++;
	}

}
