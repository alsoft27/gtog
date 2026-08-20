package com.gtog.event.infrastructure.out.persistence;

// Documento embebido en EventDocument.onlineAccess, sin coleccion ni @Document propios.
public class OnlineAccessDocument {

	private final String platform;
	private final String url;
	private final String roomId;
	private final String password;
	private final String instructions;
	private final String linkVisibility;
	private final Integer hoursBefore;

	public OnlineAccessDocument(String platform, String url, String roomId, String password, String instructions,
			String linkVisibility, Integer hoursBefore) {
		this.platform = platform;
		this.url = url;
		this.roomId = roomId;
		this.password = password;
		this.instructions = instructions;
		this.linkVisibility = linkVisibility;
		this.hoursBefore = hoursBefore;
	}

	public String getPlatform() {
		return platform;
	}

	public String getUrl() {
		return url;
	}

	public String getRoomId() {
		return roomId;
	}

	public String getPassword() {
		return password;
	}

	public String getInstructions() {
		return instructions;
	}

	public String getLinkVisibility() {
		return linkVisibility;
	}

	public Integer getHoursBefore() {
		return hoursBefore;
	}
}
