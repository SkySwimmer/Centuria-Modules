package org.asf.centuria.discord.handlers.api;

import java.net.Socket;
import java.util.Base64;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.eventbus.IEventReceiver;
import org.asf.centuria.modules.events.servers.APIServerStartupEvent;
import org.asf.rats.processors.HttpUploadProcessor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AppealStatusHandler extends HttpUploadProcessor implements IEventReceiver {

	@Override
	public void process(String contentType, Socket client, String method) {
		try {
			// Load manager
			AccountManager manager = AccountManager.getInstance();

			// Parse JWT payload
			String token = this.getHeader("Authorization").substring("Bearer ".length());
			if (token.isBlank()) {
				this.setResponseCode(403);
				this.setResponseMessage("Access denied");
				return;
			}

			// Parse token
			if (token.isBlank()) {
				this.setResponseCode(403);
				this.setResponseMessage("Access denied");
				return;
			}

			// Verify signature
			String verifyD = token.split("\\.")[0] + "." + token.split("\\.")[1];
			String sig = token.split("\\.")[2];
			if (!Centuria.verify(verifyD.getBytes("UTF-8"), Base64.getUrlDecoder().decode(sig))) {
				this.setResponseCode(403);
				this.setResponseMessage("Access denied");
				return;
			}

			// Verify expiry
			JsonObject jwtPl = JsonParser
					.parseString(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), "UTF-8"))
					.getAsJsonObject();
			if (!jwtPl.has("exp") || jwtPl.get("exp").getAsLong() < System.currentTimeMillis() / 1000) {
				this.setResponseCode(403);
				this.setResponseMessage("Access denied");
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
				this.setBody("text/json", "{\"error\":\"invalid_credential\"}");
				this.setResponseCode(422);
				return;
			}

			// Find account
			CenturiaAccount acc = manager.getAccount(id);
			if (acc == null) {
				this.setResponseCode(401);
				this.setResponseMessage("Access denied");
				return;
			}

			// Check lock
			if (!acc.getSaveSharedInventory().containsItem("appeallock")) {
				// Invalid details
				this.setBody("text/json", "{\"error\":\"no_appeal_sent\"}");
				this.setResponseCode(400);
				return;
			}

			// Set response
			this.setBody("text/json", acc.getSaveSharedInventory().getItem("appeallock").toString());
		} catch (Exception e) {
			setResponseCode(500);
			setResponseMessage("Internal Server Error");
		}
	}

	@Override
	public boolean supportsGet() {
		return true;
	}

	@EventListener
	public void startAPI(APIServerStartupEvent event) {
		event.getServer().registerProcessor(this);
	}

	@Override
	public HttpUploadProcessor createNewInstance() {
		return new AppealStatusHandler();
	}

	@Override
	public String path() {
		return "/centuria/appealstatus";
	}

}
