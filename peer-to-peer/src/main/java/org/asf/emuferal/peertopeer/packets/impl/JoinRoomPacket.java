package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.packets.xt.gameserver.room.RoomJoinPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

public class JoinRoomPacket extends NexusPacket {

	public String id;
	public boolean success;
	public String room;
	public String teleport;
	public int levelID;
	public int levelType;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.ROOM_JOIN;
	}

	@Override
	public NexusPacket newInstance() {
		return new JoinRoomPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		success = reader.readBoolean();
		if (success) {
			room = reader.readString();
			teleport = reader.readString();
			levelID = reader.readInt();
			levelType = reader.readInt();
		}
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeBoolean(success);
		if (success) {
			writer.writeString(room);
			writer.writeString(teleport);
			writer.writeInt(levelID);
			writer.writeInt(levelType);
		}
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;
		P2PPlayer plr = new P2PPlayer();
		if (!success && plr.isLocal) {
			RoomJoinPacket fail = new RoomJoinPacket();
			fail.markAsFailed();
			plr.player.client.sendPacket(fail);
			return;
		}
		if (plr.roomReady || !plr.isLocal)
			return;
		if (success) {
			plr.roomReady = false;
			plr.room = room;
			plr.levelType = levelType;
			plr.levelID = levelID;
			plr.room = room;
			plr.player.teleportToRoom(levelID, levelType, -1, room, teleport);
		}
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Room join: " + id);
	}

}
