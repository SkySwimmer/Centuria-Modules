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

public class InventoryItemRequestPacket extends NexusPacket {

	public String id;
	public String inventory;

	@Override
	public P2PNexusPacketType type() {
		return P2PNexusPacketType.INVENTORY_ITEM_REQUEST;
	}

	@Override
	public NexusPacket newInstance() {
		return new InventoryItemRequestPacket();
	}

	@Override
	public void parse(PacketReader reader) throws IOException {
		id = reader.readString();
		inventory = reader.readString();
	}

	@Override
	public void write(PacketWriter writer) throws IOException {
		writer.writeString(id);
		writer.writeString(inventory);
	}

	@Override
	public void handle(Packet packet) {
		if (PeerToPeerModule.getPlayer(id) == null)
			return;

		P2PPlayer plr = PeerToPeerModule.getPlayer(id);
		Centuria.logger.debug(MarkerManager.getMarker("P2P"), "Inventory request: " + id + ": " + inventory);

		// Check if its safe
		if ((inventory.equals("avatars") || inventory.equals("5") || inventory.equals("6") || inventory.equals("10")
				|| inventory.equals("102") || inventory.equals("201") || inventory.equals("303")
				|| inventory.equals("level")) && plr.isLocal) {
			// Send item
			InventoryItemUpdatePacket res = new InventoryItemUpdatePacket();
			res.id = id;
			res.inventory = inventory;
			res.item = plr.player.account.getPlayerInventory().getItem(inventory).toString();
			try {
				packet.reply(res.type().ordinal(), res.build());
			} catch (IOException e) {
			}
		}
	}

}
