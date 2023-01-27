package org.asf.centuria.discord.applications;

import java.util.ArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ApplicationDefinition {

	public String name;
	public int applicantLimit;
	public long deadline;
	public long reviewServer;
	public long reviewChannel;

	public ArrayList<ApplicationCommandDefinition> application = new ArrayList<ApplicationCommandDefinition>();
	public ArrayList<ApplicationCommandDefinition> accept = new ArrayList<ApplicationCommandDefinition>();
	public ArrayList<ApplicationCommandDefinition> reject = new ArrayList<ApplicationCommandDefinition>();

	public ApplicationDefinition fromJson(JsonObject obj) {
		name = obj.get("name").getAsString();
		applicantLimit = obj.get("applicantLimit").getAsInt();
		deadline = obj.get("deadline").getAsLong();
		reviewServer = obj.get("reviewServer").getAsLong();
		reviewChannel = obj.get("reviewChannel").getAsLong();

		for (JsonElement ele : obj.get("application").getAsJsonArray())
			application.add(new ApplicationCommandDefinition().fromJson(ele.getAsJsonObject()));
		for (JsonElement ele : obj.get("accept").getAsJsonArray())
			accept.add(new ApplicationCommandDefinition().fromJson(ele.getAsJsonObject()));
		for (JsonElement ele : obj.get("reject").getAsJsonArray())
			reject.add(new ApplicationCommandDefinition().fromJson(ele.getAsJsonObject()));
		return this;
	}

}
