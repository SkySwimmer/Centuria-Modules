package org.asf.emuferal.peertopeer.p2pserver.chatpackets;

import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.centuria.networking.chatserver.networking.AbstractChatPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class CreateConversationPacket
		extends org.asf.centuria.networking.chatserver.networking.CreateConversationPacket {

	private JsonArray participants;
	private String type;

	@Override
	public String id() {
		return "conversations.create";
	}

	@Override
	public AbstractChatPacket instantiate() {
		return new CreateConversationPacket();
	}

	@Override
	public void parse(JsonObject data) {
		participants = data.get("participants").getAsJsonArray();
		type = data.get("conversationType").getAsString();
		super.parse(data);
	}

	@Override
	public void build(JsonObject data) {
		super.build(data);
	}

	@Override
	public boolean handle(ChatClient client) {
		if (type.equals("private")) {
			// Find participants and check block
			for (JsonElement participant : participants) {
				String id = participant.getAsString();
				if (PeerToPeerModule.getPlayer(id) != null && !PeerToPeerModule.getPlayer(id).isLocal) {
					// NOPE
					JsonObject res = new JsonObject();
					res.addProperty("eventId", "conversations.create");
					res.addProperty("error", "unrecognized_participant");
					res.addProperty("success", false);
					return true;
				}
			}
		}
		return super.handle(client);
	}

}
