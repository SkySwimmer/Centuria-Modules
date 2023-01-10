package org.asf.emuferal.peertopeer.accounts;

import org.asf.centuria.accounts.PlayerInventory;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.packets.impl.InventoryItemRequestPacket;
import org.asf.emuferal.peertopeer.players.P2PPlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class PeerToPeerPlayerInventory extends PlayerInventory {

	private P2PPlayer player;

	public PeerToPeerPlayerInventory(P2PPlayer player) {
		this.player = player;
	}

	@Override
	public void deleteItem(String arg0) {
	}

	@Override
	public void setItem(String arg0, JsonElement arg1) {
	}

	@Override
	public boolean containsItem(String item) {
		if (item.equals("avatars") || item.equals("5") || item.equals("6") || item.equals("10") || item.equals("102")
				|| item.equals("201") || item.equals("303") || item.equals("level")) {
			if (player.inventoryItems.containsKey(item))
				return true;

			// Send packet
			InventoryItemRequestPacket pk = new InventoryItemRequestPacket();
			pk.id = player.id;
			pk.inventory = item;
			PeerToPeerModule.sendNexusPacket(pk);

			// Wait
			for (int i = 0; i < 1000 && !player.inventoryItems.containsKey(item); i++) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}

			// Not found
			return player.inventoryItems.containsKey(item);
		}
		return false;
	}

	@Override
	public JsonElement getItem(String item) {
		if (item.equals("avatars") || item.equals("5") || item.equals("6") || item.equals("10") || item.equals("102")
				|| item.equals("201") || item.equals("303") || item.equals("level")) {
			JsonElement old = player.inventoryItems.get(item);
			if (player.inventoryItems.containsKey(item))
				player.inventoryItems.remove(item);

			// Send packet
			InventoryItemRequestPacket pk = new InventoryItemRequestPacket();
			pk.id = player.id;
			pk.inventory = item;
			PeerToPeerModule.sendNexusPacket(pk);

			// Wait
			for (int i = 0; i < 1500 && !player.inventoryItems.containsKey(item); i++) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}

			// Not found
			if (old != null && !player.inventoryItems.containsKey(item))
				player.inventoryItems.put(item, old);
			return player.inventoryItems.get(item);
		}
		if (item.matches("^[0-9]+$"))
			return new JsonArray();
		return null;
	}

}
