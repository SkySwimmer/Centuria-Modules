package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.entities.generic.Quaternion;
import org.asf.centuria.entities.generic.Vector3;
import org.asf.centuria.packets.xt.gameserver.object.ObjectUpdatePacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.p2pplayerinfo.P2PSmartfoxClient;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

public class PlayerObjectUpdatePacket extends NexusPacket {

	public String id;
	public String room;
	public int mode;
	public Vector3 position;
	public Vector3 heading;
	public Quaternion rotation;
	public float speed;
	public int action;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_SYNC;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerObjectUpdatePacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		room = reader.readString();
		mode = reader.readByte();
		position = reader.readVector3();
		heading = reader.readVector3();
		rotation = reader.readQuaternion();
		speed = reader.readFloat();
		action = reader.readInt();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(room);
		writer.writeByte((byte) mode);
		writer.writeVector3(position);
		writer.writeVector3(heading);
		writer.writeQuaternion(rotation);
		writer.writeFloat(speed);
		writer.writeInt(action);
	}

	@Override
	public void handle(Packet pk) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;

		// Save in memory
		P2PPlayer player = PeerToPeerModule.getPlayer(id);
		player.room = room;
		player.position = position;
		player.rotation = rotation;
		player.action = action;
		
		if (!player.isLocal && player.player != null && player.player.client instanceof P2PSmartfoxClient) {
			// Sync
			player.player.activeLook = player.currentLook;
			player.player.activeSanctuaryLook = player.currentSanctuaryLook;
			player.player.lastPos = player.position;
			player.player.lastRot = player.rotation;
			player.player.levelID = player.levelID;
			player.player.levelType = player.levelType;
			player.player.lastAction = player.action;
			player.player.room = player.room;
			player.player.roomReady = player.roomReady;
			player.player.wasInChat = true;
		}

		// Send object update
		ObjectUpdatePacket packet = new ObjectUpdatePacket();
		packet.action = action;
		packet.id = player.id;
		packet.mode = mode;
		packet.position = position;
		packet.heading = heading;
		packet.rotation = rotation;
		packet.speed = speed;
		packet.time = System.currentTimeMillis();

		// Broadcast in room
		PeerToPeerModule.broadcast(packet, room, player.id);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"),
				"Player object update: " + player.id + ": position: " + player.position.x + ":" + player.position.y
						+ ":" + player.position.z + ", rotation: " + player.rotation.x + ":" + player.rotation.y + ":"
						+ player.rotation.z + ":" + player.rotation.w + ", room: " + player.room);
	}

}
