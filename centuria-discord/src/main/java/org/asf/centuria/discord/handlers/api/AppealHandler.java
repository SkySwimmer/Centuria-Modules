package org.asf.centuria.discord.handlers.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Base64;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.discord.ServerConfigUtils;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.eventbus.IEventReceiver;
import org.asf.centuria.modules.events.servers.APIServerStartupEvent;
import org.asf.connective.RemoteClient;
import org.asf.connective.processors.HttpPushProcessor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.spec.MessageCreateSpec;

public class AppealHandler extends HttpPushProcessor implements IEventReceiver {

	@Override
	public void process(String path, String method, RemoteClient client, String contentType) {
		try {
			// Parse body
			ByteArrayOutputStream strm = new ByteArrayOutputStream();
			getRequest().transferRequestBody(strm);
			byte[] body = strm.toByteArray();
			strm.close();

			// Parse body
			JsonObject request = JsonParser.parseString(new String(body, "UTF-8")).getAsJsonObject();
			if (!request.has("short_why") || !request.has("appeal") || !request.has("will_you_follow_rules")) {
				this.setResponseStatus(400, "Bad request");
				return;
			}

			// Load manager
			AccountManager manager = AccountManager.getInstance();

			// Parse JWT payload
			String token = this.getHeader("Authorization").substring("Bearer ".length());
			if (token.isBlank()) {
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			// Parse token
			if (token.isBlank()) {
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			// Verify signature
			String verifyD = token.split("\\.")[0] + "." + token.split("\\.")[1];
			String sig = token.split("\\.")[2];
			if (!Centuria.verify(verifyD.getBytes("UTF-8"), Base64.getUrlDecoder().decode(sig))) {
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			// Verify expiry
			JsonObject jwtPl = JsonParser
					.parseString(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), "UTF-8"))
					.getAsJsonObject();
			if (!jwtPl.has("exp") || jwtPl.get("exp").getAsLong() < System.currentTimeMillis() / 1000) {
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			JsonObject payload = JsonParser
					.parseString(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), "UTF-8"))
					.getAsJsonObject();

			// Find account
			String id = payload.get("uuid").getAsString();

			// Check existence
			if (id == null) {
				// Invalid details
				this.setResponseContent("text/json", "{\"error\":\"invalid_credential\"}");
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			// Find account
			CenturiaAccount acc = manager.getAccount(id);
			if (acc == null) {
				this.setResponseStatus(401, "Unauthorized");
				return;
			}

			// Check penalty
			if (!acc.isBanned() && !acc.isMuted()) {
				// Invalid details
				this.setResponseContent("text/json", "{\"error\":\"no_penalty\"}");
				this.setResponseStatus(400, "Bad request");
				return;
			}

			// Check lock
			if (acc.getSaveSharedInventory().containsItem("appeallock")) {
				// Invalid details
				this.setResponseContent("text/json", "{\"error\":\"already_appealed\"}");
				this.setResponseStatus(400, "Bad request");
				return;
			}

			// Set lock
			JsonObject obj = new JsonObject();
			obj.addProperty("status", "pending");
			acc.getSaveSharedInventory().setItem("appeallock", obj);

			// Load params
			String shortWhy = request.get("short_why").getAsString();
			String pardonReason = request.get("appeal").getAsString();
			String followRules = request.get("will_you_follow_rules").getAsString();

			// Build report
			String report = "Appeal form:\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += "\n";
			report += "What was the reason for your ban?\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += shortWhy + "\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += "\n";
			report += "Why do you believe you should be pardoned?\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += pardonReason + "\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += "\n";
			report += "Will you follow the rules in the game?\n";
			report += "--------------------------------------------------------------------------------------------------\n";
			report += followRules + "\n";
			report += "--------------------------------------------------------------------------------------------------\n";

			// Send to all guild log channels
			for (Guild g : DiscordBotModule.getClient().getGuilds().toIterable()) {
				String guildID = g.getId().asString();
				JsonObject config = ServerConfigUtils.getServerConfig(guildID);
				if (config.has("moderationLogChannel")) {
					// Find channel
					String ch = config.get("moderationLogChannel").getAsString();

					// Build message content
					String srvMessage = "**Ban Appeal Received**\n";
					srvMessage += "\n";
					String userID = LinkUtils.getDiscordAccountFrom(acc);
					if (userID != null)
						srvMessage += "Appeal issuer: `" + acc.getDisplayName() + "` (<@!" + userID + ">)\n";
					else
						srvMessage += "Appeal issuer: `" + acc.getDisplayName() + "`\n";
					srvMessage += "Appeal issued at: <t:" + (System.currentTimeMillis() / 1000) + ">\n";
					if (config.has("moderatorRole")) {
						// Add ping
						srvMessage += "\n\n<@&" + config.get("moderatorRole").getAsString() + ">";
					}

					// Build message object
					MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
					msg.content(srvMessage);

					// Add report
					try {
						msg.addFile("appeal.txt", new ByteArrayInputStream(report.getBytes("UTF-8")));
					} catch (UnsupportedEncodingException e1) {
					}

					// Add buttons
					msg.addComponent(ActionRow.of(Button.danger("acceptappeal/" + acc.getAccountID(), "Accept appeal"),
							Button.danger("rejectappeal/" + acc.getAccountID(), "Reject appeal")));

					// Attempt to send message
					try {
						g.getChannelById(Snowflake.of(ch)).block().getRestChannel()
								.createMessage(msg.build().asRequest()).block();
					} catch (Exception e) {
					}
				}
			}

			this.setResponseContent("text/json", "{\"status\":\"pending\"}");
		} catch (Exception e) {
			setResponseStatus(500, "Internal Server Error");
		}
	}

	@EventListener
	public void startAPI(APIServerStartupEvent event) {
		event.getServer().registerProcessor(this);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new AppealHandler();
	}

	@Override
	public String path() {
		return "/centuria/appeal";
	}

}
