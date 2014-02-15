package com.server;

public class CookieData {

	private long creationTime;
	private String uname;
	private int accessLevel = 1; //by default
	
	public CookieData(String uname2, int security_level) {
		uname = uname2;
		accessLevel = security_level;
		creationTime = System.currentTimeMillis();
	}

	public boolean isValid() {
		if ((System.currentTimeMillis() - creationTime) < Config.cookie_expiry_time)
			return true;
		return false;
	}

	public String getUserName() {
		return uname;
	}

	public boolean isAdminAllowed() {
		if (accessLevel == 2) return true;
		return false;		
	}
}
