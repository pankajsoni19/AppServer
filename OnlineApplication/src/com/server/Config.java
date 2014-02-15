package com.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public final class Config {

	// public static DelayQueue<ResetPasswordRequest> passwordResetReq = new
	// DelayQueue<ResetPasswordRequest>();
	// Additional config SendMailTLS..
	
	public static final String settings_file_path ="./conf/config.yml";
	public static final int stopDelay = 5; // in seconds
	//public static CQLHandler cqlHandler;
	
	public static String image_store_dir = "D:/Softwares/workspace-official/OnlineApplication";
/*	public static ArrayList<String> staticDirs = new ArrayList<String>();
*/	
	public static String cassuname = "pooja";
	public static String casspwd = "pooja";
	public static String casshost = "192.168.1.42";
	public static int cassport = 9042;
	
	public static int httpport = 80;
	public static boolean isHTTPSsupported = false ;
	public static int httpsport = 443;	
	public static String cert_store = "school.jks";
	public static char[] cert_password = "rootpass".toCharArray();
	
	//public static String file_upload_url_key = "5bd1e534-5a08-40ba-a7e3-6a0f568e0f28-5e3a3ec6-7edc-495b-8947-da003b12b8c4";
	public static int maximum_backlog = 10;
	public static long cookie_expiry_time = 10 * 60 * 1000; //10 mins in ms.  //browser will take care of this;
	public static long password_reset_link_expiry_time = 2 * 60 * 60 * 1000; // in ms total 2 hrs. for email
	public static long registration_reset_link_expiry_time = 24 * 60 * 60 * 1000; // in ms total 1 day for new user registration
	public static int max_thread_pool_size = 128;
	
	protected static String email_username;
	protected static String email_pwd;
	protected static String email_from_addr;
	
	public static HashMap<String, String> mimeData = new HashMap<>();
	
	//public static ConcurrentHashMap<String, CookieData> loggedInEntry = new ConcurrentHashMap<String, CookieData>(32, (float) 0.75, 2);
	private static Cache<String, CookieData> cookieStore;
	
	public static void init() throws IOException{  
		readSettings();
		load_mimetypes();
		cookieStore = CacheBuilder.newBuilder()
							.expireAfterWrite(cookie_expiry_time, TimeUnit.MILLISECONDS)
							.concurrencyLevel(4)
							.maximumSize(128)
							.build();	
		    
	/*	staticDirs.add("/js/");
		staticDirs.add("/css/");
		staticDirs.add("/docs/");
		staticDirs.add("/public_docs/");
		staticDirs.add( "/photos/");*/
	}
	
	public static String getImageFilePath(String path){
		return image_store_dir + path;
	}
	
	public static String getUserName(String cookie) {
		CookieData cd = cookieStore.getIfPresent(cookie);
		if (cd == null) return null;
		return cd.getUserName();
	}
	
	public static void removeCookie(String cookie) {
		cookieStore.invalidate(cookie);		
	}
	
	public static boolean isCookieValid(String cookie){
		if (cookie == null) return false;
		CookieData cd = cookieStore.getIfPresent(cookie);
		if(cd != null && cd.isValid()) return true;
		removeCookie(cookie);
		return false;
	}
	
	public static boolean hasAdminPowers(String cookie){
		CookieData cd = cookieStore.getIfPresent(cookie);
		if(cd.isAdminAllowed()) return true;
		return false;
	}
	
	public static boolean isCookieValidForAdmin(String cookie) {
		CookieData cd = cookieStore.getIfPresent(cookie);
		if(cd != null && cd.isValid()) {
			if(cd.isAdminAllowed()) return true;
			return false;
		}
		removeCookie(cookie);
		return false;
	}
	
	public static void addCookie(String key, CookieData cd) {
		cookieStore.put(key, cd);
	}
	
	private static void load_mimetypes() throws IOException {
		BufferedReader bufr = new BufferedReader(new InputStreamReader(new FileInputStream("./conf/mime_type.data")));
		String lr = null;
		while((lr = bufr.readLine())!=null){
			String mimes[] = lr.split(" ");
			mimeData.put(mimes[1].trim(), mimes[0].trim());
		}
		bufr.close();
	} 
	
	private static void readSettings() throws IOException {
		BufferedReader bufr = new BufferedReader(new InputStreamReader(new FileInputStream(settings_file_path)));
		
		String lr = null;
		while((lr = bufr.readLine())!=null){
			if(lr.startsWith("*") || lr.startsWith("/") || lr.length() < 5) continue;
			String data[] = lr.split(":",2);
			switch (data[0].trim()) {
				case "casshost":
					casshost = data[1].trim();
					break;
				case "cassport":
					cassport = Integer.valueOf(data[1].trim());
					break;
				case "cassuname":
					cassuname = data[1].trim();
					break;
				case "casspwd":
					casspwd = data[1].trim();
					break;
				case "httpport":
					httpport = Integer.valueOf(data[1].trim());
					break;
				case "maximum_backlog":
					maximum_backlog = Integer.valueOf(data[1].trim());
					break;
				case "image_store_dir":
					image_store_dir = data[1].trim();
					break;
				case "max_thread_pool_size":
					max_thread_pool_size = Integer.valueOf(data[1].trim());
					break;
/*				case "file_upload_url_key":
					file_upload_url_key = data[1].trim();*/
				case "https_server":
					isHTTPSsupported = Boolean.valueOf(data[1].trim());
					break;
				case "httpsport":
					httpsport = Integer.valueOf(data[1].trim());
					break;
				case "cert_store":
					cert_store = data[1].trim();
					break;
				case "cert_password":
					cert_password = data[1].trim().toCharArray();
					break;
				case "cookie_expiry_time":
					cookie_expiry_time = Long.valueOf(data[1].trim());
					break;
				case "password_reset_link_expiry_time":
					password_reset_link_expiry_time = Long.valueOf(data[1].trim());
					break;
				case "registration_reset_link_expiry_time":
					registration_reset_link_expiry_time = Long.valueOf(data[1].trim());
					break;
				case "email_username":
					email_username = data[1].trim();
					break;
				case "email_pwd":
					email_pwd = data[1].trim();
					break;
				case "email_from_addr":
					email_from_addr = data[1].trim();
					break;
				default:
					break;
			}			
			//System.out.println(lr);
		}	
		bufr.close();
	}		
	
	public static String getHost() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostAddress();
		//return PublicIPListener.getPublicIPString();
	}

	public static String getNewCookie(String uuid) throws UnknownHostException {
		Date date = new Date();
		date.setTime(date.getTime() + cookie_expiry_time);	//
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(date);
		String secure = "";		
		if (isHTTPSsupported) secure = "Secure";
		String cookie = "SID="+ uuid + "; expires=" + expires + "; Path=/; Domain="+ Config.getHost() + "; " + secure + " ; HttpOnly" ;
		return cookie;
	}

	public static String getDeleteCookie() throws UnknownHostException {
		Date date = new Date();
		date.setTime(date.getTime() - cookie_expiry_time);	//
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(date);
		String secure = "";		
		if (isHTTPSsupported) secure = "Secure";
		String toReturn = "SID=deleted; Expires=" + expires  + "; Path=/; Domain="+ Config.getHost() + "; " + secure + " ; HttpOnly" ; 
		return toReturn;
	}
}
