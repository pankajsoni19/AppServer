package com.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class AdminHandler implements HttpHandler {	
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		String Cookie = he.getRequestHeaders().getFirst("Cookie");	
		boolean cookieValidity = Config.isCookieValidForAdmin(Cookie);
		String requestMethod = he.getRequestMethod();		
		
		System.out.println(requestMethod + " " + Cookie  + " " + cookieValidity);
		if(!cookieValidity){
			String unAuthorized = "<p style=font-size:40px;><b>401 UnAuthorized Access!!!</b></p>" +
					"<p style=font-size:20px;>This content can only be accessed by admins.</p>";
			he.sendResponseHeaders(401, unAuthorized.getBytes().length);
			he.getResponseBody().write(unAuthorized.getBytes("UTF-8"));
			he.getResponseBody().flush();
			he.close();
			return;
		}
		process_request(he);
	}
	
	private void process_request(HttpExchange he) throws IOException {
		String requestMethod = he.getRequestMethod();
		
		switch (requestMethod) {
		case "GET":
			processGetRequests(he);			
			break;
		case "POST":
			processPost(he);
			break;
		default:
			break;
		}
	}

	private void processGetRequests(HttpExchange he) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();
		switch (path) {
		case "/admin/view_profile":
			sendResponseFile(new File("./data/admin_view_candidate_data.htm"), he);
			break;
		case "/admin/show_profile":			
			String uname = he.getRequestURI().getQuery().split("=")[1];
			String data = CQLHandler.getProfileData(uname);
			if(data == null){
				he.sendResponseHeaders(412, -1);
			} else{
				byte[] bytedata = data.getBytes("UTF-8");			
				he.sendResponseHeaders(200, bytedata.length);
				OutputStream os = he.getResponseBody();
				os.write(bytedata);
				os.flush();
				os.close();
			}
			he.close();
			break;
		default:
			sendResponseFile(new File("./data/admin_base.htm"), he);
			break;
		}		
	}

	private void processPost(HttpExchange he) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();
		switch (path) {
		case "/admin/upload_file":
			upload_file(he,false);
			break;
		case "/admin/gen_upload_file":
			upload_file(he,true);
			break;
		case "/admin/payments_update":
			BufferedReader r = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			String names = r.readLine();
			while(r.readLine()!= null){}
			r.close();
			he.sendResponseHeaders(200, -1);
			he.close();
			new PaymentUpdater(names).start();
		default:
			break;
		}		
	}

	private void upload_file(HttpExchange he, boolean makePublic) throws IOException {
		String fileName = he.getRequestHeaders().getFirst("X_filename");		
		
		System.out.println(makePublic + "  - " + fileName);
		
		try{
			BufferedInputStream bis = new BufferedInputStream(he.getRequestBody());
			FileOutputStream fos = null;
			if (makePublic) 
				fos = new FileOutputStream("./public_docs/" + fileName);
			else
				fos = new FileOutputStream("./docs/" + fileName);
			byte b[] = new byte[2048];
			int read = 0;
			while((read = bis.read(b)) !=-1){
				fos.write(b, 0, read);
				fos.flush();
			}
			
			bis.close();
			fos.close();
			
			he.sendResponseHeaders(200, -1);
		} catch(IOException ex){
			ex.printStackTrace();
			he.sendResponseHeaders(412, -1);
		}
		he.close();
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
        Headers h = getResponseHeaders(he);
		he.sendResponseHeaders(200, 0);
		GZIPOutputStream gos = new GZIPOutputStream(he.getResponseBody());
		BufferedInputStream bufread = new BufferedInputStream(
				new FileInputStream(f));
		long toRead = f.length();
		byte read[] = new byte[2048];
		int dataread = 0;
		while (toRead > 0 && (dataread = bufread.read(read)) != -1) {
			gos.write(read, 0, dataread);
			gos.flush();
			toRead = toRead - dataread;
		}
		gos.finish();
		gos.close();
		bufread.close();
		he.close();
	}
}
