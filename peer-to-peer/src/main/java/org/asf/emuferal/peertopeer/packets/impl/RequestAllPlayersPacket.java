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

public class RequestAllPlayersPacket extends NexusPacket {

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.REQUEST_PLAYERS;
	}

	@Override
	public NexusPacket newInstance() {
		return new RequestAllPlayersPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
	}

	@Override
	public void handle(Packet pk) {
		for (P2PPlayer player : PeerToPeerModule.getPlayers()) {
			if (!player.isLocal)
				continue;
			Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Request all players");

			// Send player
			PlayerJoinPacket packet = new PlayerJoinPacket();
			packet.id = player.id;
			packet.name = player.displayName;
			packet.currentLookID = player.currentLook;
			packet.currentSanctuaryLookID = player.currentSanctuaryLook;
			try {
				pk.reply(packet.type().ordinal(), packet.build());
			} catch (IOException e) {
			}

			// Send look if info is present
			if (player.look != null) {
				PlayerLookInfoPacket pkt = new PlayerLookInfoPacket();
				pkt.id = player.id;
				pkt.avatar = player.look;
				pkt.currentLookID = player.currentLook;
				try {
					pk.reply(pkt.type().ordinal(), pkt.build());
				} catch (IOException e) {
				}
			}
			
			// Sync position
			if (player.position != null) {
				PlayerObjectCreatePacket sync = new PlayerObjectCreatePacket();
				sync.id = player.id;
				sync.action = player.action;
				sync.room = player.room;
				sync.position = player.position;
				sync.rotation = player.rotation;
				sync.levelID = player.levelID;
				sync.levelType = player.levelType;
				try {
					pk.reply(sync.type().ordinal(), sync.build());
				} catch (IOException e) {
				}
			}
		}
	}

}
