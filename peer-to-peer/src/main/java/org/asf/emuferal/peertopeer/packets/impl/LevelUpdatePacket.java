package org.asf.emuferal.peertopeer.packets.impl;

import java.io.IOException;
import java.util.ArrayList;

import org.asf.centuria.Centuria;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.packets.xt.gameserver.levels.XpUpdatePacket;
import org.asf.centuria.packets.xt.gameserver.levels.XpUpdatePacket.CompletedLevel;
import org.asf.centuria.packets.xt.gameserver.levels.XpUpdatePacket.Level;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.nexus.NexusClient.Packet;

public class LevelUpdatePacket extends NexusPacket {

	public String recipient;
	public String userId;
	public int totalXp;
	public int addedXp;

	public Level current;
	public Level previous;

	public ArrayList<CompletedLevel> completedLevels = new ArrayList<CompletedLevel>();

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.LEVEL_UPDATE;
	}

	@Override
	public NexusPacket newInstance() {
		return new LevelUpdatePacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		recipient = reader.readString();

		// Basics
		userId = reader.readString();
		totalXp = reader.readInt();
		addedXp = reader.readInt();

		// Current level
		current = new Level();
		current.level = reader.readInt();
		current.xp = reader.readInt();
		current.levelUpXp = reader.readInt();

		// Last level
		previous = new Level();
		previous.level = reader.readInt();
		previous.xp = reader.readInt();
		previous.levelUpXp = reader.readInt();

		// Completed levels
		int l = reader.readInt();
		for (int i = 0; i < l; i++) {
			CompletedLevel lv = new CompletedLevel();
			lv.level = reader.readInt();
			lv.levelUpXp = reader.readInt();
			lv.levelUpRewardDefId = reader.readInt();
			lv.levelUpRewardQuantity = reader.readInt();
			lv.levelUpRewardGiftId = reader.readString();
			completedLevels.add(lv);
		}
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(recipient);

		// Basics
		writer.writeString(userId);
		writer.writeInt(totalXp);
		writer.writeInt(addedXp);

		// Current level
		writer.writeInt(current.level);
		writer.writeInt(current.xp);
		writer.writeInt(current.levelUpXp);

		// Last level
		writer.writeInt(previous.level);
		writer.writeInt(previous.xp);
		writer.writeInt(previous.levelUpXp);

		// Completed levels
		writer.writeInt(completedLevels.size());
		for (CompletedLevel lvl : completedLevels) {
			writer.writeInt(lvl.level);
			writer.writeInt(lvl.levelUpXp);
			writer.writeInt(lvl.levelUpRewardDefId);
			writer.writeInt(lvl.levelUpRewardQuantity);
			writer.writeString(lvl.levelUpRewardGiftId);
		}
	}

	@Override
	public void handle(Packet pk) {
		// Broadcast XP update
		XpUpdatePacket pkt = new XpUpdatePacket();
		pkt.userId = userId;
		pkt.addedXp = addedXp;
		pkt.totalXp = totalXp;
		pkt.current = current;
		pkt.previous = previous;
		pkt.completedLevels = completedLevels;
		Player player = Centuria.gameServer.getPlayer(recipient);
		if (player != null) {
			player.client.sendPacket(pkt);
		}
	}

}
