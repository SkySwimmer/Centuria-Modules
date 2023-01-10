package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.networking.gameserver.GameServer;
import org.asf.centuria.packets.xt.gameserver.room.RoomJoinPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

public class PlayerSanctuarySelectPacket extends NexusPacket {

	public String id;
	public String currentLookID;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_SANCUPDATE;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerSanctuarySelectPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		currentLookID = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(currentLookID);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;

		P2PPlayer plr = PeerToPeerModule.getPlayer(id);
		plr.currentSanctuaryLook = currentLookID;
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Player sanctuary update: " + id);

		// Make players in this sanctuary rejoin
		for (Player player : Centuria.gameServer.getPlayers()) {
			if (player.room != null && player.room.equals("sanctuary_" + id)) {
				// Build room join
				RoomJoinPacket join = new RoomJoinPacket();
				join.levelType = 2;
				join.levelID = 1689;
				join.roomIdentifier = "sanctuary_" + id;
				join.teleport = id;

				// Sync
				GameServer srv = Centuria.gameServer;
				for (Player plr2 : srv.getPlayers()) {
					if (plr2.room != null && player.room != null && player.room != null && plr2.room.equals(player.room)
							&& plr2 != player) {
						player.destroyAt(plr2);
					}
				}

				// Assign room
				player.roomReady = false;
				player.pendingLevelID = 1689;
				player.pendingRoom = "sanctuary_" + id;
				player.levelType = join.levelType;

				// Send packet
				player.client.sendPacket(join);
			}
		}
	}

}
