package org.asf.centuria.discord.handlers.api;

import java.util.Base64;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.eventbus.IEventReceiver;
import org.asf.centuria.modules.events.servers.APIServerStartupEvent;
import org.asf.connective.RemoteClient;
import org.asf.connective.processors.HttpPushProcessor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AppealStatusHandler extends HttpPushProcessor implements IEventReceiver {

	@Override
	public void process(String path, String method, RemoteClient client, String contentType) {
		try {
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

			// Check lock
			if (!acc.getSaveSharedInventory().containsItem("appeallock")) {
				// Invalid details
				this.setResponseContent("text/json", "{\"error\":\"no_appeal_sent\"}");
				this.setResponseStatus(400, "Bad request");
				return;
			}

			// Set response
			this.setResponseContent("text/json", acc.getSaveSharedInventory().getItem("appeallock").toString());
		} catch (Exception e) {
			setResponseStatus(500, "Internal Server Error");
		}
	}

	@Override
	public boolean supportsNonPush() {
		return true;
	}

	@EventListener
	public void startAPI(APIServerStartupEvent event) {
		event.getServer().registerProcessor(this);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new AppealStatusHandler();
	}

	@Override
	public String path() {
		return "/centuria/appealstatus";
	}

}
