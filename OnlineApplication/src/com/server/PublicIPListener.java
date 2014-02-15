package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;

import org.apache.log4j.Logger;

public class PublicIPListener extends Thread {
	
	private static String mIPAddress = "192.168.1.1";
	private final long mSleepTime = 1000*60;  // 1 min.
	private static final String mInvalidIP = "0.0.0.0";
	
	static Logger logger = Logger.getLogger(PublicIPListener.class);
	
	public static String getPublicIp() {
		return mIPAddress;
	}
	
	@Override
	public void run() {
		super.run();
		
		while(true && !isInterrupted()){			

			String currIP = getIPfromModem();
		
			if(currIP != null && !currIP.contains(mIPAddress) && !currIP.contains(mInvalidIP)){								
				logger.info("System got new ip: " + currIP + "   OldIpWas:     " + mIPAddress);
				mIPAddress = currIP;
				
				try {
					updateServerSettings();
				} catch (IOException e) {
					  
				}
			}	
			
			synchronized (this) {
				try {
					sleep(mSleepTime);
				} catch (InterruptedException e) {
					break;
				}			
			}		
		}		
	}	
	
	private String getIPfromModem(){
		
		String ip = null;
		
		try {
			URL url = new URL("http://192.168.1.1/status/status_deviceinfo.htm");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("Connection","keep-alive");
			con.setRequestProperty("Authorization","Basic YWRtaW46YWRtaW4=");
			con.setRequestProperty("DNT","1");
			try{
				int code = con.getResponseCode();
				if(code == 401) return null;
			}catch(NoRouteToHostException ex){				
				return null;
			}			
			
			BufferedReader bufr  = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String LineRead = null;
			boolean nextlineip = false;
			
			while((LineRead=bufr.readLine().trim())!=null){
				
				if(LineRead.startsWith("Status</td></tr><TR><TD align='center' class='tabdata'>PVC0</TD><TD align='center' class='tabdata'>0/32</TD><TD align='center' class='tabdata'>")){
					ip = LineRead.toLowerCase().split("</td>")[3].split(">")[1].trim();
					break;
				}
				
				if(nextlineip && !LineRead.contains("192.168.1.1") && !LineRead.contains("Subnet")){					
					ip = LineRead.split("</td>")[0].trim();
					break;			
				}
				nextlineip = false;
				if(LineRead.contains("IP Address</font>")) nextlineip = true;				
			}
			
			while(bufr.readLine()!=null){}
			
			bufr.close();
			con.disconnect();			
			
		} catch (MalformedURLException e) {			  
		} catch (IOException e) {}		
		return ip;
	}
	
	private void updateServerSettings() throws IOException {
		URL url = new URL("http://softwarejoint.com/erp?setlocalserveripforerpmodule="+mIPAddress);
		HttpURLConnection con = (HttpURLConnection)url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("cache-control", "no-cache");				
		con.setRequestProperty("user-agent","Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.62 Safari/537.36");
		con.setRequestProperty("accept-language","en-US,en;q=0.8");
		con.setRequestProperty("accept: text/html","application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		if(con!=null){
			BufferedReader bufr = new BufferedReader(new InputStreamReader(con.getInputStream()));
			while(bufr.readLine()!=null){}
			bufr.close();
		}
		con.disconnect();			
	}
}