package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;

import org.apache.logging.log4j.MarkerManager;
import org.asf.centuria.Centuria;
import org.asf.centuria.entities.generic.Quaternion;
import org.asf.centuria.entities.generic.Vector3;
import org.asf.centuria.entities.generic.Velocity;
import org.asf.centuria.entities.objects.WorldObjectMoveNodeData;
import org.asf.centuria.entities.objects.WorldObjectPositionInfo;
import org.asf.centuria.enums.objects.WorldObjectMoverNodeType;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarObjectInfoPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient.Packet;

import com.google.gson.JsonParser;

public class PlayerObjectCreatePacket extends NexusPacket {

	public String id;
	public String room;
	public int levelID;
	public int levelType;
	public Vector3 position;
	public Quaternion rotation;
	public int action;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.PLAYER_OBJECTCREATE;
	}

	@Override
	public NexusPacket newInstance() {
		return new PlayerObjectCreatePacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		room = reader.readString();
		levelID = reader.readInt();
		levelType = reader.readInt();
		position = reader.readVector3();
		rotation = reader.readQuaternion();
		action = reader.readInt();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(room);
		writer.writeInt(levelID);
		writer.writeInt(levelType);
		writer.writeVector3(position);
		writer.writeQuaternion(rotation);
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
		player.levelID = levelID;
		player.levelType = levelType;
		player.roomReady = true;
		player.player.roomReady = true;

		// Create object info
		AvatarObjectInfoPacket packet = new AvatarObjectInfoPacket();

		// Object creation parameters
		packet.id = player.id;
		packet.defId = 852;
		packet.ownerId = player.id;

		packet.lastMove = new WorldObjectMoveNodeData();
		packet.lastMove.serverTime = System.currentTimeMillis() / 1000;
		packet.lastMove.positionInfo = new WorldObjectPositionInfo(player.position.x, player.position.y,
				player.position.z, player.rotation.x, player.rotation.y, player.rotation.z, player.rotation.w);
		packet.lastMove.velocity = new Velocity();
		packet.lastMove.nodeType = WorldObjectMoverNodeType.InitPosition;
		packet.lastMove.actorActionType = player.action;

		// Look and name
		packet.look = JsonParser.parseString(player.look).getAsJsonObject();
		packet.displayName = player.displayName;
		packet.unknownValue = 0;

		// Broadcast in room
		PeerToPeerModule.broadcast(packet, room, player.id);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Player object create: " + player.id + ": "
				+ player.position.x + ":" + player.position.y + ":" + player.position.z + ", " + player.room);
	}

}
