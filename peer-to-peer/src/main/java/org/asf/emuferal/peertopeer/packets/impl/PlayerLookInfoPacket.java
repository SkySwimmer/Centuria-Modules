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

public class PlayerLookInfoPacket extends NexusPacket {

	public String id;
	public String avatar;
	public String currentLookID;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_UPDATEAVATAR;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerLookInfoPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		avatar = reader.readString();
		currentLookID = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(avatar);
		writer.writeString(currentLookID);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;

		P2PPlayer plr = PeerToPeerModule.getPlayer(id);
		plr.look = avatar;
		plr.currentLook = currentLookID;
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Player avatar update: " + id);
	}

}
