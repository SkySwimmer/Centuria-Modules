package org.asf.emuferal.peertopeer.p2pserver;

import java.net.ServerSocket;

import org.asf.centuria.networking.chatserver.ChatServer;
import org.asf.emuferal.peertopeer.p2pserver.chatpackets.CreateConversationPacket;
import org.asf.emuferal.peertopeer.p2pserver.chatpackets.SendMessage;

public class PeerToPeerChatServer extends ChatServer {

	public PeerToPeerChatServer(ServerSocket socket) {
		super(socket);
	}

	@Override
	protected void registerPackets() {
		// Packet registry
		registerPacket(new CreateConversationPacket());
		registerPacket(new SendMessage());
		super.registerPackets();
	}

}
