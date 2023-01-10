package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;
import java.time.LocalDateTime;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

import com.google.gson.JsonObject;

public class ChatPacket extends NexusPacket {

	public String id;
	public String message;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.CHAT;
	}

	@Override
	public NexusPacket newInstance() {
		return new ChatPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		message = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(message);
	}

	@Override
	public void handle(Packet pk) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;
		P2PPlayer player = PeerToPeerModule.getPlayer(id);

		// Create chat message
		JsonObject res = new JsonObject();
		res.addProperty("conversationType", "room");
		res.addProperty("conversationId", player.room);
		res.addProperty("message", message);
		res.addProperty("source", player.id);
		res.addProperty("sentAt", LocalDateTime.now().toString());
		res.addProperty("eventId", "chat.postMessage");
		res.addProperty("success", true);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Chat: " + player.displayName + ": " + message);

		// Broadcast
		for (ChatClient cl : Centuria.chatServer.getClients()) {
			if (cl.isInRoom(player.room))
				cl.sendPacket(res);
		}
	}

}
