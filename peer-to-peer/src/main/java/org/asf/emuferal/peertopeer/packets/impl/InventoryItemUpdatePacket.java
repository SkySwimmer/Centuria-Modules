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

import com.google.gson.JsonParser;

public class InventoryItemUpdatePacket extends NexusPacket {

	public String id;
	public String inventory;
	public String item;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.INVENTORY_ITEM_RESPONSE;
	}

	@Override
	public NexusPacket newInstance() {
		return new InventoryItemUpdatePacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		inventory = reader.readString();
		item = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(inventory);
		writer.writeString(item);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;

		P2PPlayer plr = PeerToPeerModule.getPlayer(id);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Inventory update: " + id + ": " + inventory + ": " + item);

		// Update item
		plr.inventoryItems.put(inventory, JsonParser.parseString(item));
	}

}
