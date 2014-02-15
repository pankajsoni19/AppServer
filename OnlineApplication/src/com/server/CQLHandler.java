package com.server;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.UUID;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.JsonParserSequence;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class CQLHandler {

	private static Cluster cluster;
	private static Session session;
		
	public void start(){
		connect(Config.casshost);
	}
	
	public void connect(String node) {
	   cluster = Cluster.builder()
	         .addContactPoint(node)
	         .withCredentials(Config.cassuname, Config.casspwd)
	         .withPort(Config.cassport)
	         // .withSSL() // Uncomment if using client to node encryption
	         .build();
	   
	   Metadata metadata = cluster.getMetadata();
	   System.out.printf("Connected to cluster: %s\n", 
	         metadata.getClusterName());
	   for ( Host host : metadata.getAllHosts() ) {
	      System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
	         host.getDatacenter(), host.getAddress(), host.getRack());
	   }
	   
	   session = cluster.connect();
	}
	
	public Session getSession() {
		return session;
	}
	
	public static void shutdown() {
		session.shutdown();
		cluster.shutdown();
	}
	
	public static boolean register_user(String uname, String email_id, String pwd) throws UnknownHostException {
		ResultSet results = session.execute("SELECT password FROM application_data.users WHERE user_name='" + uname +"';");
		System.out.println(results);
		System.out.println(results.one());
		Row row = results.one();
		if( row != null) {
			return false;
		}
		String Token = UUID.randomUUID().toString();
		String register_token = Token + "-" + System.currentTimeMillis();
		String profile_data = "{ \"Contact Information\" : {\"Email\": \"" + email_id + "\"}}";
		session.execute("INSERT INTO application_data.users (user_name, password, profile_data, register_token, role) VALUES ('" +
								uname +"','"+pwd +"','" + profile_data + "','" + register_token + "', 'stu');");
		String proto = "http://";
		if (Config.isHTTPSsupported) proto = "https://";
		String mailerLink = proto + Config.getHost() + "/login/register?uname="+ uname +"&register_token=" + Token;
		SendMailTLS.sendmessage(email_id, mailerLink);			
		return true;
	}
	
	public static boolean register_user_final(String uname, String register_token){
		ResultSet results = session.execute("SELECT register_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if(row == null) return false;
		String RegToken = row.getString("register_token");
		if(RegToken != null) {
			if(RegToken.startsWith(register_token) && 
					( Config.registration_reset_link_expiry_time > (System.currentTimeMillis() - new Long(RegToken.split("-")[5].trim())))){
				session.execute("UPDATE application_data.users SET register_token = 'registered' WHERE user_name='" + uname +"';");
				return true;	
			} else if (RegToken.contentEquals("registered")) {				
				return true;
			}   			
		}		
		return false;
	}

	@SuppressWarnings("unused")
	public static boolean canResetPassword(String uname) throws UnknownHostException {
		ResultSet results = session.execute("SELECT profile_data FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		String profile_data = row.getString("profile_data");
		String mailID = null;
		try {			
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(profile_data);
			Iterator<Entry<String, JsonNode>> elem = rootNode.getFields();
			while(elem.hasNext()){
				Entry<String, JsonNode> data = elem.next();
				if(data.getKey().contains("Contact Information")){
					mailID = data.getValue().findValue("Email").getTextValue();	
				} else continue;				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(mailID == null) return false;
		
		String Token = UUID.randomUUID().toString();
		String SessionToken = Token + "-" + System.currentTimeMillis();
		session.execute("UPDATE application_data.users SET session_token = '" + SessionToken +"' WHERE user_name='" + uname +"';");
		String proto = "http://";
		if (Config.isHTTPSsupported) proto = "https://";
		String mailerLink = proto + Config.getHost() + "/login/reset?uname="+ uname +"&session_token="+ Token; 
		//System.out.println("mailer link" + mailerLink);
		SendMailTLS.sendmessage(mailID, mailerLink);			
		return true;
	}

	public static boolean canResetPassword(String uname, String token) {
		ResultSet results = session.execute("SELECT session_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		String SessToken = row.getString("session_token");		
		if(SessToken != null 
				&& SessToken.startsWith(token) 
				&& ( Config.password_reset_link_expiry_time > (System.currentTimeMillis() - new Long(SessToken.split("-")[5].trim())))){
			return true;
		}		
		return false;
	}
	

	public static void setNewPassword(String uname, String passwd) {
		session.execute("UPDATE application_data.users SET password = '" + passwd +"' WHERE user_name='" + uname +"';");
	}
	
	public static boolean setNewPassword(String uname, String pswd, String token) {
		ResultSet results = session.execute("SELECT session_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		String SessToken = row.getString("session_token");
		if(SessToken != null && SessToken.startsWith(token)){
			session.execute("UPDATE application_data.users SET session_token = 'null' , password = '" + pswd +"' WHERE user_name='" + uname +"';");
			return true;
		}		
		return false;
	}

	public static int checkLogon(String uname, String pwd) {
		ResultSet results = session.execute("SELECT register_token, role, password FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return 0;
		String readPwd = row.getString("password");
		String register_token = row.getString("register_token");
		String role = row.getString("role");
		if (readPwd != null && readPwd.equals(pwd) && register_token != null && register_token.contains("registered")){
			if (role != null && role.contains("admin")) return 2;
			return 1;
		}			
		return 0;
	}

	public static String getProfileData(String uname) {
		ResultSet results = session.execute("SELECT profile_data FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if(row == null) return null;
		return row.getString("profile_data");		
	}

	public static void updateProfileData(String uname, String profile_data) {
		session.execute("UPDATE application_data.users SET profile_data = '" + profile_data +"' WHERE user_name='" + uname +"';");		
	}	
}
