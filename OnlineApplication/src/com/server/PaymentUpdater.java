package com.server;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

public class PaymentUpdater extends Thread {

	private String names;
	
	public PaymentUpdater(String names2) {
		names = names2;
	}

	@Override
	public void run() {
		super.run();
		String[] namesList = names.split(",");
		System.out.println(namesList.length + "   " + namesList[0]);
		for(String uname: namesList){
			try {
				String profile_data = CQLHandler.getProfileData(uname);
				if(profile_data == null) continue;
				ObjectMapper mapper = new ObjectMapper();
				JsonNode rootNode = mapper.readTree(profile_data);
				Iterator<Entry<String, JsonNode>> elem = rootNode.getFields();
				String ddStatus = null;
				while(elem.hasNext()){
					Entry<String, JsonNode> data = elem.next();
					if (data.getKey().contains("Payment Details") && data.getValue().has("Status")){
						ddStatus = data.getValue().findValue("Status").getTextValue();
						break;
					} else continue;				
				}
			if(ddStatus != null) continue;
			StringBuilder builder = new StringBuilder();			
			if(profile_data.contains("\"Payment Details\"")){
				String toAppend = ",\"Status\":\"Confirmed\"";
				int index = profile_data.lastIndexOf('}', profile_data.length() -2);
				builder.append(profile_data.subSequence(0, index))
						.append(toAppend)
						.append(profile_data.subSequence(index, profile_data.length()));				
			} else { 
			/*if(profile_data.contains(",\"photourl\":\""))*/
				String toAppend = "},\"Payment Details\" : {\"Status\":\"Confirmed\"";
				int index = profile_data.lastIndexOf('}', profile_data.length() -2);
				builder.append(profile_data.subSequence(0, index))
						.append(toAppend)
						.append(profile_data.subSequence(index, profile_data.length()));
			} 
				CQLHandler.updateProfileData(uname, builder.toString());
				
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
		}
	}
}
