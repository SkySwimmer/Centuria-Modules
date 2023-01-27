package org.asf.centuria.discord.applications;

import com.google.gson.JsonObject;

public class ApplicationCommandDefinition {

	public String command;
	public JsonObject parameters;

	public ApplicationCommandDefinition fromJson(JsonObject obj) {
		command = obj.get("command").getAsString();
		parameters = obj.get("parameters").getAsJsonObject();
		return this;
	}
}
