package org.asf.emuferal.peertopeer.p2pserver.chatpackets;

import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.centuria.networking.chatserver.networking.AbstractChatPacket;
import org.asf.centuria.networking.chatserver.rooms.ChatRoom;
import org.asf.centuria.networking.chatserver.rooms.ChatRoomTypes;
import org.asf.centuria.textfilter.TextFilterService;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.packets.impl.ChatPacket;

import com.google.gson.JsonObject;

public class SendMessage extends org.asf.centuria.networking.chatserver.networking.SendMessage {
	private String message;
	private String room;

	@Override
	public String id() {
		return "chat.postMessage";
	}

	@Override
	public AbstractChatPacket instantiate() {
		return new SendMessage();
	}

	@Override
	public void parse(JsonObject data) {
		message = data.get("message").getAsString();
		room = data.get("conversationId").getAsString();
		super.parse(data);
	}

	@Override
	public void build(JsonObject data) {
		super.build(data);
	}

	@Override
	public boolean handle(ChatClient client) {
		ChatRoom r = client.getRoom(room);
		if (r == null || r.getType().equalsIgnoreCase(ChatRoomTypes.ROOM_CHAT)) {
			String source = client.getPlayer().getAccountID();

			// Check filter
			String filteredMessage = TextFilterService.getInstance().filterString(message, false);

			// Send packet
			if (!message.startsWith(">")) {
				ChatPacket packet = new ChatPacket();
				packet.id = source;
				packet.message = filteredMessage;
				PeerToPeerModule.sendNexusPacket(packet);
			}
		}
		return super.handle(client);
	}

}
