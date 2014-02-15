package com.server;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

 
public class SendMailTLS {

	public static boolean sendmessage(String emailto,String Url) throws UnknownHostException {
		try {		 
			
	 
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", "smtp.gmail.com");
			props.put("mail.smtp.port", "587");
	 
			
			Session session = Session.getInstance(props,
			  new javax.mail.Authenticator() {
				protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
					return new javax.mail.PasswordAuthentication(Config.email_username, Config.email_pwd);
				}
			  });
			
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(Config.email_from_addr));
			message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(emailto));
			message.setSubject("Password reset request for account at " + Config.getHost());
			message.setText(gettext(Url)); 
			Transport.send(message);
			
			return true;
		} catch (MessagingException e) {
			//e.printStackTrace();
			return false;
		} 
	}
	
	private static String gettext(String url) {
		return 
				"Click the link to reset your password" + "\r\n" + "\r\n"   
				+ url
				+"\r\n" + "This link would expire in " + (Config.password_reset_link_expiry_time/(1*60*60*1000)) + " Hours."
				+ "\r\n"
				+ "If password reset request is not from you, then you can ignore this mail.";
	}	
}