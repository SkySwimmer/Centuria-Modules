package org.asf.emuferal.peertopeer.p2pplayerinfo;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.networking.smartfox.BaseSmartfoxServer;
import org.asf.centuria.networking.smartfox.SmartfoxClient;
import org.asf.centuria.packets.smartfox.ISmartfoxPacket;
import org.asf.centuria.packets.xt.IXtPacket;
import org.asf.centuria.packets.xt.gameserver.levels.XpUpdatePacket;
import org.asf.centuria.packets.xt.gameserver.object.ObjectDeletePacket;
import org.asf.centuria.packets.xt.gameserver.room.RoomJoinPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.packets.impl.JoinRoomPacket;
import org.asf.emuferal.peertopeer.packets.impl.LevelUpdatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectRemovePacket;
import org.asf.emuferal.peertopeer.players.P2PPlayer;

public class P2PSmartfoxClient extends SmartfoxClient {

	private P2PPlayer player;

	public P2PSmartfoxClient(P2PPlayer player) {
		this.player = player;
	}

	public P2PPlayer getPlayer() {
		return player;
	}

	@Override
	protected void stop() {
	}

	@Override
	protected void closeClient() {
	}

	@Override
	public void disconnect() {
	}

	@Override
	public String getAddress() {
		return "p2p:" + player.id;
	}

	@Override
	public BaseSmartfoxServer getServer() {
		return Centuria.gameServer;
	}

	@Override
	public boolean isConnected() {
		return PeerToPeerModule.getPlayer(player.id) != null;
	}

	@Override
	public void sendPacket(ISmartfoxPacket packet) {
		// Handle packet
		if (packet instanceof ObjectDeletePacket) {
			// Object delete
			ObjectDeletePacket del = (ObjectDeletePacket) packet;

			// Create p2p packet
			PlayerObjectRemovePacket pkt = new PlayerObjectRemovePacket();
			pkt.id = del.objectId;
			PeerToPeerModule.sendNexusPacket(pkt);
		} else if (packet instanceof RoomJoinPacket) {
			// Room join
			RoomJoinPacket join = (RoomJoinPacket) packet;

			// Create p2p packet
			if (player != null) {
				player.roomReady = false;
				JoinRoomPacket rj = new JoinRoomPacket();
				rj.id = player.id;
				rj.success = join.success;
				rj.levelID = join.levelID;
				rj.levelType = join.levelType;
				rj.room = join.roomIdentifier;
				rj.teleport = join.teleport;
				PeerToPeerModule.sendNexusPacket(rj);
			} 
		} else if (packet instanceof XpUpdatePacket) {
			// XP Update
			XpUpdatePacket update = (XpUpdatePacket)packet;
			
			// Create p2p packet
			LevelUpdatePacket pkt = new LevelUpdatePacket();
			pkt.recipient = player.id;
			pkt.userId = update.userId;
			pkt.totalXp = update.totalXp;
			pkt.addedXp = update.addedXp;
			pkt.current = update.current;
			pkt.previous = update.previous;
			pkt.completedLevels = update.completedLevels;
			PeerToPeerModule.sendNexusPacket(pkt);
		}

		// Log
		if (packet instanceof IXtPacket)
			try {
				Centuria.logger.debug(MarkerManager.getMarker("P2P/SF"),
						"Proxy: " + ((IXtPacket<?>) packet).id() + ": " + packet.build());
			} catch (Exception e) {
			}
	}

	@Override
	public void sendPacket(String raw) {
		// Uhhhhhhh why did i do this, shit, raw packets suck
		Centuria.logger.debug(MarkerManager.getMarker("P2P/SF"), "Proxy raw: " + raw);
	}

	@Override
	public <T extends ISmartfoxPacket> T readPacket(Class<T> arg0) throws IOException {
		throw new IOException("Cannot read from P2P");
	}

	@Override
	public String readRawPacket() throws IOException {
		throw new IOException("Cannot read from P2P");
	}

}
