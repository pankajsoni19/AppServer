package com.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class Test extends Thread{
	
	@Override
	public void run() {
		super.run();
	
		
		
		
	}
public static void main(String[] args) {
	long expiryTime = 364 * 24 * 60 * 60 * 1000; //  1 year in ms.
	
	Calendar date = Calendar.getInstance();
	
	date.add(Calendar.YEAR, 1);
	
	SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
	dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
	String expires = dateFormatGmt.format(date.getTime());
	
	System.out.println(expires);
	
	
}
}
