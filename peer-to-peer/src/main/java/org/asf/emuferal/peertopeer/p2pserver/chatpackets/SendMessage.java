package org.asf.emuferal.peertopeer.p2pserver.chatpackets;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.centuria.networking.chatserver.networking.AbstractChatPacket;
import org.asf.centuria.packets.xt.gameserver.inventory.InventoryItemDownloadPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.packets.impl.ChatPacket;

import com.google.gson.JsonObject;

public class SendMessage extends org.asf.centuria.networking.chatserver.networking.SendMessage {
	private static ArrayList<String> muteWords = new ArrayList<String>();
	private static ArrayList<String> alwaysfilterWords = new ArrayList<String>();

	static {
		// Load ban words
		try {
			InputStream strm = InventoryItemDownloadPacket.class.getClassLoader()
					.getResourceAsStream("textfilter/instamute.txt");
			String lines = new String(strm.readAllBytes(), "UTF-8").replace("\r", "");
			for (String line : lines.split("\n")) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String data = line.trim();
				while (data.contains("  "))
					data = data.replace("  ", "");

				for (String word : data.split(";"))
					muteWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Load always filtered words
		try {
			InputStream strm = InventoryItemDownloadPacket.class.getClassLoader()
					.getResourceAsStream("textfilter/alwaysfilter.txt");
			String lines = new String(strm.readAllBytes(), "UTF-8").replace("\r", "");
			for (String line : lines.split("\n")) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String data = line.trim();
				while (data.contains("  "))
					data = data.replace("  ", "");

				for (String word : data.split(";"))
					alwaysfilterWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String message;
	private String room;

	@Override
	public String id() {
		return "chat.postMessage";
	}

	@Override
	public AbstractChatPacket instantiate() {
		return new SendMessage();
	}

	@Override
	public void parse(JsonObject data) {
		message = data.get("message").getAsString();
		room = data.get("conversationId").getAsString();
		super.parse(data);
	}

	@Override
	public void build(JsonObject data) {
		super.build(data);
	}

	@Override
	public boolean handle(ChatClient client) {
		if (!client.isRoomPrivate(room)) {
			String source = client.getPlayer().getAccountID();

			// Check filter
			String filteredMessage = "";
			for (String word : message.split(" ")) {
				// check always filtered
				if (alwaysfilterWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())
						|| muteWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())) {
					// Filter it
					for (String filter : alwaysfilterWords) {
						while (word.toLowerCase().contains(filter.toLowerCase())) {
							String start = word.substring(0, word.toLowerCase().indexOf(filter.toLowerCase()));
							String rest = word
									.substring(word.toLowerCase().indexOf(filter.toLowerCase()) + filter.length());
							String tag = "";
							for (int i = 0; i < filter.length(); i++) {
								tag += "#";
							}
							word = start + tag + rest;
						}
					}
				}

				if (!filteredMessage.isEmpty())
					filteredMessage += " " + word;
				else
					filteredMessage = word;
			}

			// Send packet
			if (!message.startsWith(">")) {
				ChatPacket packet = new ChatPacket();
				packet.id = source;
				packet.message = filteredMessage;
				PeerToPeerModule.sendNexusPacket(packet);
			}
		}
		return super.handle(client);
	}

}
