package com.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class LoginHandler implements HttpHandler {

	static Logger logger = Logger.getLogger(LoginHandler.class);
	
	@Override
	public void handle(HttpExchange he) {
		try{
		String Cookie = null;
		if(he.getRequestHeaders().containsKey("Cookie"))
			Cookie = he.getRequestHeaders().getFirst("Cookie");
		
		boolean cookieValidity = Config.isCookieValid(Cookie);
		
		String requestMethod = he.getRequestMethod();
		
		logger.info(requestMethod + ": " 
					+ he.getRequestURI().getPath() 
					+ " cookie: " + Cookie
					+ " validity: " + cookieValidity);
		
		if(Cookie != null && !cookieValidity){			
			deleteCookie(Cookie);
			sendRefreshHeaders("/login/expired_session", he,Config.getDeleteCookie());
			return;
		} 
		
		if(Cookie == null) {			
			if(he.getRequestURI().getPath().contains("/logout")){
				sendRefreshHeaders("/", he);
				return;
			} else if(he.getRequestURI().getPath().contains("/home")){
				sendRefreshHeaders("/login/expired_session", he);
				return;
			}				
		}
		
		switch (requestMethod) {
			case "GET":
				if (cookieValidity)
					processSignedInRequest(he, Cookie, cookieValidity);
				else
					processLoginData(he);
				break;
			case "POST":
				if (cookieValidity)
					processSignedInPostRequest(he, Cookie, cookieValidity);
				else
					processLoginPostData(he);
				break;
			default:
				break;
		}
	}catch(Exception ex){
		ex.printStackTrace();
	}
	
	}
	
	private void processLoginPostData(HttpExchange he) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();
		System.out.println(path);
		if ( path.contentEquals("/login")) {				
			String[] readData = getPostData(he);
			
			String uname = readData[0].split("uname=")[1];
			String pwd = readData[1].split("passwd=")[1];
						
			int security_level = CQLHandler.checkLogon(uname, pwd);
			
			if (security_level > 0) {
				sendRefreshHeaders(getURLbyAccess(security_level), he, getNewCookie(uname, security_level));
			} else {
				sendResponseFile(new File("./data/login_error.htm"), he);
			}
			
		} else if(path.contentEquals("/login/reset")){		
			String data[] = getPostData(he);
			String pswd = data[0].split("=")[1].trim();
			String data2[] = data[1].split("%26");
			String uname = data2[0].split("%3D")[1];
			String session_token = data2[1].split("%3D")[1];
			if (CQLHandler.setNewPassword(uname, pswd, session_token)){
				sendRefreshHeaders("/login", he);	
			}else{
				sendResponseFile(new File("./data/passord_recover_error.htm"), he);
			}
		}
		
	}

	private void processSignedInPostRequest(HttpExchange he, String cookie, boolean cookieValidity) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();
		String[] readData;
		switch (path) {
		case "/home/update_profile":
			BufferedReader bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			String readline = URLDecoder.decode(bufr.readLine(), "UTF-8");
			bufr.close();
			readData = readline.split("&");
			StringBuilder builder = new StringBuilder();
			builder.append("{ \"General Information\" : {");
			for(int i =0 ; i< readData.length; i++){
				String data[] = readData[i].split("=");
				builder.append("\"").append(data[0]).append("\":\"").append(data[1]).append("\"");
				if(i == readData.length -1) 
					continue;
				switch (data[0]) {
				case "Gender":
					builder.append("}, \"Contact Information\" : {");
					break;
				case "Email":
					builder.append("}, \"Education History\" : {");
					break;
				case "Other Achievements":
					builder.append("}, \"Payment Details\" : {");
					break;
				default:
					builder.append(",");
					break;
				}		
			}
			String uname = Config.getUserName(cookie);
			String profile_data = CQLHandler.getProfileData(uname);
			//builder.append("}}");
			String photourl = null;
			String ddStatus = null;
			try {			
				ObjectMapper mapper = new ObjectMapper();
				JsonNode rootNode = mapper.readTree(profile_data);
				Iterator<Entry<String, JsonNode>> elem = rootNode.getFields();
				while(elem.hasNext()){
					Entry<String, JsonNode> data = elem.next();
					if(data.getKey().contains("photourl")){
						photourl = data.getValue().getTextValue();	
					} else if (data.getKey().contains("Payment Details") && data.getValue().has("Status")){
						ddStatus = data.getValue().findValue("Status").getTextValue();
					} else continue;				
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if(ddStatus !=null){
				builder.append(", \"Status\":\"").append(ddStatus).append("\"");
			}
			//This has to be outside..
			builder.append("}");
			if(photourl !=null) {
				builder.append(",\"photourl\":\"").append(photourl).append("\"");
			}
			builder.append("}");
			
			logger.info(builder.toString());
			logger.info("uname: " + uname);
			
			CQLHandler.updateProfileData(uname, builder.toString());
			
			sendResponseFile(new File("./data/home_update_success.htm"), he);
			break;
		case "/home/change_password":
			readData = getPostData(he);
			String passwd = readData[0].split("=")[1].trim();
			uname= Config.getUserName(cookie);			
			CQLHandler.setNewPassword(uname, passwd);
			if(Config.hasAdminPowers(cookie))
				sendResponseFile(new File("./data/home_pwd_admin_success.htm"), he);
			else
				sendResponseFile(new File("./data/home_pwd_success.htm"), he);
			break;
		case "/home/updatephoto":
			if( he.getRequestHeaders().containsKey("X_FILESIZE") && Integer.valueOf(he.getRequestHeaders().getFirst("X_FILESIZE")) > 102400){
				he.sendResponseHeaders(412, -1 );
				he.close();				
				return;
			}
			uname = Config.getUserName(cookie);
			profile_data = CQLHandler.getProfileData(uname);
			photourl = null;
			boolean inDb = false;
			try {			
				ObjectMapper mapper = new ObjectMapper();
				JsonNode rootNode = mapper.readTree(profile_data);
				Iterator<Entry<String, JsonNode>> elem = rootNode.getFields();
				while(elem.hasNext()){
					Entry<String, JsonNode> data = elem.next();
					if(data.getKey().contains("photourl")){
						photourl = data.getValue().getTextValue();
						inDb = true;
					} else continue;				
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			File ftemp = null, origFile = null;		
			try {
				if(photourl !=null){
					char t = photourl.charAt(0);
					ftemp = new File("./temp/" + t + "/");
					if(!ftemp.exists()) ftemp.mkdir();
					origFile = new File(Config.getImageFilePath("/photos/" + photourl));
					ftemp = new File("./temp/" + photourl );
					Files.copy(origFile.toPath(), ftemp.toPath());
					origFile.delete();
				}else{
					String filename = he.getRequestHeaders().getFirst("X_FILENAME");
					String temp[] = filename.split("\\.");
					String extension = temp[temp.length -1].trim();
					String uid = UUID.randomUUID().toString();
					char c = uid.charAt(0);
					photourl = c +"/" + uid.substring(1, uid.length()) + "." + extension;
					File f = new File(Config.getImageFilePath("/photos/" + c + "/"));
					f.mkdir();					
				}
				
				logger.debug("Updating image: " + photourl + " was in db:" + inDb + " store as: " + Config.getImageFilePath("/photos/" + photourl));
				
				BufferedInputStream bis = new BufferedInputStream(he.getRequestBody());
				byte[] buffer = new byte[1024];
				int read = 0;
				File f = new File(Config.getImageFilePath("/photos/" + photourl));
				FileOutputStream fos = new FileOutputStream(f,false);
				while((read = bis.read(buffer)) != -1){
					fos.write(buffer,0,read);
					fos.flush();
				}
				
				fos.close();
				bis.close();
				String photourltosend = "/photos/" + photourl;
				
				he.sendResponseHeaders(200, photourltosend.getBytes("UTF-8").length);
				OutputStream os = he.getResponseBody();
				os.write(photourltosend.getBytes("UTF-8"));
				os.flush();
				os.close();
				if(ftemp != null && ftemp.exists())
					ftemp.delete();
				
				if(!inDb){
					builder = new StringBuilder();
					
					builder.append(profile_data.substring(0, profile_data.length() -1))
						.append(", \"photourl\" : \"")
						.append(photourl)
						.append("\"")
						.append("}");
					profile_data = builder.toString();
					CQLHandler.updateProfileData(uname,profile_data);	
				}				
			} catch (IOException e) {
				e.printStackTrace();
				if(photourl !=null && !origFile.exists() && ftemp!=null && ftemp.exists()){			
					origFile = new File(Config.getImageFilePath("/photos/" + photourl));
					Files.copy(ftemp.toPath(), origFile.toPath());					
					ftemp.delete();
				}
				if (ftemp.exists())
					ftemp.delete();
				
				he.sendResponseHeaders(412, -1 );
			}
			he.close();
			break;			
		default:
			break;
		}		
	}

	private void processSignedInRequest(HttpExchange he, String cookie, boolean cookieValidity) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();
		switch (path) {
		case "/home":
			sendResponseFile(new File("./data/home.htm"), he);
			break;
		case "/home/change_password":
			if(Config.hasAdminPowers(cookie))
				sendResponseFile(new File("./data/home_pwd_admin.htm"), he);
			else
				sendResponseFile(new File("./data/home_pwd.htm"), he);
			break;
		case "/home/list_documents" :
			File f = new File("./docs/");
			String files[] = f.list();
			StringBuilder strbuild = new StringBuilder();
			String fileurl = null;
			if (Config.isHTTPSsupported) {
				fileurl = "https://"  + Config.getHost() + "/docs/";	
			} else {
				fileurl = "http://"  + Config.getHost() + "/docs/";
			}
			strbuild.append("{").append("\"url\" : \"" + fileurl).append("\",");
			for(int i = 0; i< files.length; i ++){				
				strbuild.append("\"")
					.append(files[i])
					.append("\" : \"")
					.append(URLEncoder.encode(files[i], "UTF-8"))
					.append("\"");
				if(i<(files.length - 1))
					strbuild.append(",");
			}
			strbuild.append("}");
			String filesparsed = strbuild.toString();
			byte[] bdata = filesparsed.getBytes("UTF-8");
			he.sendResponseHeaders(200, bdata.length);
			OutputStream os = he.getResponseBody();
			os.write(bdata);
			os.flush();
			os.close();
			he.close();			
			break;
		case "/home/getdetails":
			String data = CQLHandler.getProfileData(Config.getUserName(cookie));
			byte[] bytedata = data.getBytes("UTF-8");			
			he.sendResponseHeaders(200, bytedata.length);
			os = he.getResponseBody();
			os.write(bytedata);
			os.flush();
			os.close();
			he.close();
			break;
		case "/home/new_info" :
			sendResponseFile(new File("./data/home_new_info.htm"), he);
			break;
		case "/home/edit_profile" :
			sendResponseFile(new File("./data/home_edit_profile.htm"), he);
			break;
		case "/logout":
			deleteCookie(cookie);			
			sendRefreshHeaders("/login", he, Config.getDeleteCookie());
			break;
		default:
			String refreshurl = null;
			if (Config.isHTTPSsupported) {
				refreshurl = "0; url=https://"  + Config.getHost() + "/home";	
			} else {
				refreshurl = "0; url=http://"  + Config.getHost() + "/home";
			}
			sendRefreshHeaders(refreshurl, he);			
			break;
		}
	}
	
	private void processLoginData(HttpExchange he) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();		
		switch (path) {
		case "/":
		case "/login":
			sendResponseFile(new File("./data/login.htm"), he);			
			break;
		case "/list_documents":
			File f = new File("./public_docs/");
			String files[] = f.list();
			StringBuilder strbuild = new StringBuilder();
			String fileurl = null;
			if (Config.isHTTPSsupported) {
				fileurl = "https://"  + Config.getHost() + "/public_docs/";	
			} else {
				fileurl = "http://"  + Config.getHost() + "/public_docs/";
			}
			strbuild.append("{").append("\"url\" : \"" + fileurl).append("\",");
			for(int i = 0; i< files.length; i ++){				
				strbuild.append("\"")
					.append(files[i])
					.append("\" : \"")
					.append(URLEncoder.encode(files[i], "UTF-8"))
					.append("\"");
				if(i<(files.length - 1))
					strbuild.append(",");
			}
			strbuild.append("}");
			String filesparsed = strbuild.toString();
			byte[] bdata = filesparsed.getBytes("UTF-8");
			he.sendResponseHeaders(200, bdata.length);
			OutputStream os = he.getResponseBody();
			os.write(bdata);
			os.flush();
			os.close();
			he.close();
			break;
		case "/reaquirepasswd":
			String uname = he.getRequestURI().getQuery().split("=")[1].trim();
			Headers h = he.getResponseHeaders();
			h.set("Cache-Control", "no-cache");
			h.set("Connection", "keep-alive");
			int responseVal = 412;
					
			if (CQLHandler.canResetPassword(uname)) {
				responseVal = 200;
			}
			he.sendResponseHeaders(responseVal, -1);
			he.close();
			break;
		case "/login/expired_session":
			sendResponseFile(new File("./data/expired_session.htm"), he);
			break;
		case "/login/no_access":
			sendResponseFile(new File("./data/no_access.htm"), he);
			break;
		case "/login/register":
			String tokens[] = he.getRequestURI().getQuery().split("&");
			uname = tokens[0].split("=")[1].trim();
			String register_token = tokens[0].split("=")[1].trim();
			if (CQLHandler.register_user_final(uname, register_token)){
				sendResponseFile(new File("./data/registered.htm"), he);
			} else {
				sendResponseFile(new File("./data/register_timeout.htm"), he);
			}
			break;
		case "/new_account/register":
			tokens = he.getRequestURI().getQuery().split("&");
			uname = tokens[0].split("=")[1].trim();
			String email_id = tokens[1].split("=")[1].trim();
			String pwd = tokens[2].split("=")[1].trim();
			if (CQLHandler.register_user(uname, email_id, pwd)){
				sendResponseFile(new File("./data/register_success.htm"), he);
			} else {
				sendResponseFile(new File("./data/register_failure.htm"), he);
			}
			break;
		case "/new_account":
			sendResponseFile(new File("./data/new_account.htm"), he);
			break;
		case "/login/reset":
			try{
				tokens = he.getRequestURI().getQuery().split("&");
				uname = tokens[0].split("=")[1];
				String token = tokens[1].split("=")[1];
				System.out.println(uname + " "+token);
				if(CQLHandler.canResetPassword(uname,token)){
					sendResponseFile(new File("./data/passord_recover.htm"), he);
				} else {
					sendResponseFile(new File("./data/passord_recover_error.htm"), he);
				}				
			}catch(Exception ex){
				sendResponseFile(new File("./data/passord_recover_error.htm"), he);
			}
			break;
		default:
			sendResponseFile(new File("./data/Internal_error.htm"), he);
			break;
		}
	}

	private String[] getPostData(HttpExchange he) throws IOException{
		BufferedReader bufr = new BufferedReader(new InputStreamReader(
				he.getRequestBody()));
		String[] readData = bufr.readLine().split("&");
			bufr.close();
			return readData;		 
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he) throws IOException{
		sendRefreshHeaders(refreshurl, he, null);
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he, String cookie) throws IOException{		
		Headers h = he.getResponseHeaders();
		if(cookie!=null)
			h.set("Set-Cookie", cookie);
		h.set("Refresh", getRefreshURL(refreshurl));		
		he.sendResponseHeaders(200, -1);
		he.close();
	}
	
	private String getRefreshURL(String path) throws UnknownHostException{
		String refreshurl = null;
		if (Config.isHTTPSsupported) {
			refreshurl = "0; url=https://"  + Config.getHost() + path;	
		} else {
			refreshurl = "0; url=http://"  + Config.getHost() + path;
		}
		return refreshurl;
	}
	
	private String getURLbyAccess(int security_level) throws UnknownHostException{		
		if (security_level == 2)
			return "/admin";
		else
			return "/home";
	}

	private Headers getResponseHeaders(HttpExchange he) {
		Headers h = he.getResponseHeaders();
		h.set("Cache-Control", "no-cache");
		h.set("Connection", "keep-alive");
		h.set("Content-Type", "text/html");
		h.set("Content-Encoding", "gzip");
		return h;
	}

	private void sendResponseFile(File f, HttpExchange he) throws IOException {
		sendResponseFile(f, he, null);
	}
	
	private void sendResponseFile(File f, HttpExchange he,String cookie) throws IOException {
        Headers h = getResponseHeaders(he);
        if(cookie!=null){
        	h.set("Set-Cookie", cookie);
        }
		
		he.sendResponseHeaders(200, 0);
		GZIPOutputStream gos = new GZIPOutputStream(he.getResponseBody());
		BufferedInputStream bufread = new BufferedInputStream(
				new FileInputStream(f));
		long toRead = f.length();
		byte buffer[] = new byte[2048];
		int dataread = 0;
		while (toRead > 0 && (dataread = bufread.read(buffer)) != -1) {			
			gos.write(buffer, 0, dataread);
			gos.flush();
			toRead = toRead - dataread;
		}
		bufread.close();
		gos.finish();
		gos.close();
		he.close();
	}

	private void deleteCookie(String Cookie) {
		Config.removeCookie(Cookie);
	}
	
	private String getNewCookie(String uname, int security_level) {
		String uuid = UUID.randomUUID().toString();
		String cookie;
		try {
			cookie = Config.getNewCookie(uuid);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
		String Key = "SID="+ uuid;
		CookieData cd = new CookieData(uname, security_level);
		Config.addCookie(Key, cd);
		return cookie;
	}

}