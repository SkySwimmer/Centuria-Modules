package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.nexus.NexusClient.Packet;

public class PlayerLeavePacket extends NexusPacket {
	
	public String id;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_REMOVE;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerLeavePacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;
		PeerToPeerModule.removePlayer(PeerToPeerModule.getPlayer(id));
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Player leave: " + id);
	}

}
