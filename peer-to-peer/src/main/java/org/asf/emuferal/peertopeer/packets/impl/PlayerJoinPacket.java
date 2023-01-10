package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

public class PlayerJoinPacket extends NexusPacket {

	public String id;
	public String name;
	public String currentLookID;
	public String currentSanctuaryLookID;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_CREATE;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerJoinPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		name = reader.readString();
		currentLookID = reader.readString();
		currentSanctuaryLookID = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(name);
		writer.writeString(currentLookID);
		writer.writeString(currentSanctuaryLookID);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) != null)
			return;
		P2PPlayer plr = new P2PPlayer();
		plr.id = id;
		plr.displayName = name;
		plr.currentLook = currentLookID;
		plr.currentSanctuaryLook = currentSanctuaryLookID;
		plr.clientID = packet.getSender();
		PeerToPeerModule.addPlayer(plr);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Player join: " + id);
	}

}
