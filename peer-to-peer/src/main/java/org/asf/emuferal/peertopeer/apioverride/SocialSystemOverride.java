package org.asf.emuferal.peertopeer.apioverride;

import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Base64;
import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.networking.http.api.FallbackAPIProcessor;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.rats.processors.HttpUploadProcessor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class SocialSystemOverride extends HttpUploadProcessor {

	private FallbackAPIProcessor fallback = new FallbackAPIProcessor();

	@Override
	public void process(String contentType, Socket client, String method) {
		String path = this.getRequestPath();
		AccountManager manager = AccountManager.getInstance();

		try {
			// Check path
			if (path.startsWith("/r/block/")) {
				// Find account
				CenturiaAccount acc = verifyAndGetAcc(manager);
				if (acc == null) {
					this.setResponseCode(401);
					this.setResponseMessage("Access denied");
					return;
				}

				String targetPlayerID = path.substring("/r/block/".length());

				// Check method
				if (!method.equalsIgnoreCase("get")) {
					// Check p2p
					if (PeerToPeerModule.getPlayer(targetPlayerID) != null) {
						// Nope
						getResponse().setResponseStatus(400, "Unavailable");
						getResponse().setContent("application/json", "{\"err\":\"peertopeer\"}");
						return;
					}
				}
			} else if (path.startsWith("/r/follow/")) {

				// Find account
				CenturiaAccount acc = verifyAndGetAcc(manager);
				if (acc == null) {
					this.setResponseCode(401);
					this.setResponseMessage("Access denied");
					return;
				}

				String targetPlayerID = path.split("/")[3];

				// Check method
				if (!method.equalsIgnoreCase("get")) {
					// Check p2p
					if (PeerToPeerModule.getPlayer(targetPlayerID) != null) {
						// Nope
						getResponse().setResponseStatus(400, "Unavailable");
						getResponse().setContent("application/json", "{\"err\":\"peertopeer\"}");
						return;
					}
				}
			}

			FallbackAPIProcessor proc = (FallbackAPIProcessor) fallback.instanciate(getServer(), getRequest());
			proc.process(contentType, client, method);
			setResponse(proc.getResponse());
		} catch (Exception e) {
			if (Centuria.debugMode) {
				System.err.println("[FALLBACKAPI] ERROR : " + e.getMessage() + " )");
			}
			Centuria.logger.error(getRequest().path + " failed", e);
		}
	}

	@Override
	public HttpUploadProcessor createNewInstance() {
		return new SocialSystemOverride();
	}

	@Override
	public String path() {
		return "/";
	}

	@Override
	public boolean supportsGet() {
		return true;
	}

	@Override
	public boolean supportsChildPaths() {
		return true;
	}

	private CenturiaAccount verifyAndGetAcc(AccountManager manager)
			throws JsonSyntaxException, UnsupportedEncodingException {
		// Parse JWT payload
		String token = this.getHeader("Authorization").substring("Bearer ".length());

		// Verify signature
		String verifyD = token.split("\\.")[0] + "." + token.split("\\.")[1];
		String sig = token.split("\\.")[2];
		if (!Centuria.verify(verifyD.getBytes("UTF-8"), Base64.getUrlDecoder().decode(sig))) {
			return null;
		}

		// Verify expiry
		JsonObject jwtPl = JsonParser
				.parseString(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), "UTF-8"))
				.getAsJsonObject();
		if (!jwtPl.has("exp") || jwtPl.get("exp").getAsLong() < System.currentTimeMillis() / 1000) {
			return null;
		}

		JsonObject payload = JsonParser
				.parseString(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), "UTF-8"))
				.getAsJsonObject();

		// Find account
		CenturiaAccount acc = manager.getAccount(payload.get("uuid").getAsString());

		return acc;
	}
}
