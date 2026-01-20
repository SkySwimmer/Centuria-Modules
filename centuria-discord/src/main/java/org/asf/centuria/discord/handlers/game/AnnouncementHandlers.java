package org.asf.centuria.discord.handlers.game;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.ServerConfigUtils;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.eventbus.IEventReceiver;
import org.asf.centuria.modules.events.maintenance.MaintenanceEndEvent;
import org.asf.centuria.modules.events.maintenance.MaintenanceStartEvent;
import org.asf.centuria.modules.events.updates.ServerUpdateEvent;
import org.asf.centuria.updater.PolyUpdaterClient;
import org.asf.centuria.updater.collections.PolyCollection;

import com.google.gson.JsonObject;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.entity.RestChannel;

public class AnnouncementHandlers implements IEventReceiver {

	@EventListener
	public void maintenanceStart(MaintenanceStartEvent event) {
		announce(false, "**" + DiscordBotModule.getServerName() + " Server Maintenance**\n"
				+ "The Centuria servers are currently under maintenance, we will be back soon!", null);
	}

	@EventListener
	public void maintenanceEnd(MaintenanceEndEvent event) {
		announce(false, "**" + DiscordBotModule.getServerName() + " Server Maintenance Ended**\n"
				+ "Server maintenance has been complete and servers are coming back online!", null);
	}

	@EventListener
	public void update(ServerUpdateEvent event) {
		// Build update message
		String messageSimple = "";
		String messageComplete = "";
		if (event.hasVersionInfo())
			messageSimple = "**" + DiscordBotModule.getServerName() + " " + event.getUpdateVersion() + "**!\n";
		else
			messageSimple = "**" + DiscordBotModule.getServerName() + " has been Updated!**\n";
		messageSimple += "\n";
		messageSimple += "The server has been updated, and "
				+ (event.hasTimer() ? "will be restarted in __" + event.getTimeRemaining() + " minutes__"
						: "is restarting")
				+ "!";

		// Add changelog to complete message if possible
		messageComplete = messageSimple;
		messageComplete += "\n";
		messageComplete += "\n";
		LinkedHashMap<String, InputStream> files = null;
		try {
			boolean versionDataPresent = PolyUpdaterClient.getCollections().length != 0;
			if (versionDataPresent) {
				String changelogFullBaseServer = PolyUpdaterClient.getNextBaseSoftwareVersionChangelogData();

				// Get other versions
				String prettyDocument = "";
				boolean changedDocument = false;
				for (PolyCollection col : PolyUpdaterClient.getCollections()) {
					if (col.getId().equals("base"))
						continue;
					JsonObject content = col.getNewBuildManifest();
					if (content == null || !content.has("name"))
						continue;

					// Override document
					if (!changedDocument) {
						prettyDocument = messageSimple;
						prettyDocument += "\n";
						prettyDocument += "\n";
						prettyDocument += "Updated software:\n";
						prettyDocument += " - Centuria: "
								+ (col.getCurrentVersion() != null ? col.getCurrentVersion() + " -> " : "")
								+ col.getNewVersion() + "\n";
						changedDocument = true;
					}

					// Add software
					prettyDocument += " - " + content.get("name").getAsString() + ": "
							+ (col.getCurrentVersion() != null ? col.getCurrentVersion() + " -> " : "")
							+ col.getNewVersion();

					// Add changelog if persent
					if (content.has("changelog_link"))
						prettyDocument += " [link to changelog](" + content.get("changelog_link").getAsString() + ")";

					// Add newline
					prettyDocument += "\n";
				}
				if (changedDocument)
					messageComplete = prettyDocument;

				// Create message
				if (changelogFullBaseServer != null) {
					messageComplete += "Changelog:\n";
					messageComplete += "```\n";
					messageComplete += changelogFullBaseServer + "\n";
					messageComplete += "```";
					if (messageComplete.length() >= 2000) {
						// Too long
						messageComplete = messageSimple;
						if (changedDocument)
							messageComplete = prettyDocument;
						files = new LinkedHashMap<String, InputStream>();
						files.put("changelog.md", new ByteArrayInputStream(changelogFullBaseServer.getBytes("UTF-8")));
					}
				}
			} else
				messageComplete = null;
		} catch (Exception e) {
			messageComplete = null;
		}

		// Select message
		if (messageComplete == null || messageComplete.length() > 2000)
			announce(true, messageSimple, null);
		else
			announce(true, messageComplete, files);
	}

	private void announce(boolean ping, String message, Map<String, InputStream> files) {
		// Send to all guild log channels
		for (Guild g : DiscordBotModule.getClient().getGuilds().toIterable()) {
			String guildID = g.getId().asString();
			JsonObject config = ServerConfigUtils.getServerConfig(guildID);
			if (config.has("announcementChannel")) {
				// Find channel
				String ch = config.get("announcementChannel").getAsString();
				String srvMessage = message;
				if (config.has("announcementPingRole") && ping) {
					// Add ping
					srvMessage += "\n\n<@&" + config.get("announcementPingRole").getAsString() + ">";
				}

				// Attempt to send message
				try {
					RestChannel channel = g.getChannelById(Snowflake.of(ch)).block().getRestChannel();
					MessageCreateSpec.Builder builder = MessageCreateSpec.builder();
					builder.content(message);
					if (files != null) {

					}
					channel.createMessage(srvMessage).block();
				} catch (Exception e) {
				}
			}
		}
	}

}
