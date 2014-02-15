package com.server;

import java.io.IOException;

import org.apache.log4j.Logger;

public class Bootup {

	private ServerShutdown hook;
	private HTTPListerner httpListener;
	private HTTPSListener httpsListener;
	
	static Logger logger = Logger.getLogger(Bootup.class);
	static PublicIPListener publicIpListner;
	public Bootup() {
		hook = new ServerShutdown();
		publicIpListner = new PublicIPListener();
		publicIpListner.start();
		httpListener = new HTTPListerner();
		httpListener.start();
		logger.info("Starting http server on port: " + Config.httpport);
		if (Config.isHTTPSsupported){
			httpsListener = new HTTPSListener();
			httpsListener.start();
			logger.info("Starting https server on port: " + Config.httpsport);
		}
		
		Runtime.getRuntime().addShutdownHook(hook);
		new CQLHandler().start();
	}
	
	public static void main(String[] args) {
		//BasicConfigurator.configure();
		try {
			Config.init();
		} catch (IOException e) {
			System.out.println("Exiting server.. Settings cannot be read.");
			e.printStackTrace();
			System.exit(0);
		}		
		
		new Bootup();
	}
	
	private class ServerShutdown extends Thread {
		
		@Override
		public void run() {
			super.run();
			logger.info("Shutting down server. Please wait...");
			publicIpListner.interrupt();
			CQLHandler.shutdown();
			httpListener.stopServer();
			if (Config.isHTTPSsupported)
				httpsListener.stopServer();
		}
	}

}
