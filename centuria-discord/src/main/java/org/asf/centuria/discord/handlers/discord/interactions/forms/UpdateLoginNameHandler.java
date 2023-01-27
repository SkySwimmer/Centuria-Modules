package org.asf.centuria.discord.handlers.discord.interactions.forms;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.packets.xt.gameserver.inventory.InventoryItemDownloadPacket;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import reactor.core.publisher.Mono;

public class UpdateLoginNameHandler {

	private static String[] nameBlacklist = new String[] { "kit", "kitsendragn", "kitsendragon", "fera", "fero",
			"wwadmin", "ayli", "komodorihero", "wwsam", "blinky", "fer.ocity" };

	private static ArrayList<String> muteWords = new ArrayList<String>();
	private static ArrayList<String> filterWords = new ArrayList<String>();

	static {
		// Load filter
		try {
			InputStream strm = InventoryItemDownloadPacket.class.getClassLoader()
					.getResourceAsStream("textfilter/filter.txt");
			String lines = new String(strm.readAllBytes(), "UTF-8").replace("\r", "");
			for (String line : lines.split("\n")) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String data = line.trim();
				while (data.contains("  "))
					data = data.replace("  ", "");

				for (String word : data.split(" "))
					filterWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
		}

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

				for (String word : data.split(" "))
					muteWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
		}
	}

	/**
	 * Handles the 'update login name' form submission event
	 * 
	 * @param event   Modal submission event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(String id, ModalSubmitInteractionEvent event, GatewayDiscordClient gateway) {
		// Load fields
		String oldName = event.getInteraction().getData().data().get().components().get().get(0).components().get()
				.get(0).value().get();
		String username = event.getInteraction().getData().data().get().components().get().get(1).components().get()
				.get(0).value().get();
		String confirm = event.getInteraction().getData().data().get().components().get().get(2).components().get()
				.get(0).value().get();

		// Load account manager
		AccountManager manager = AccountManager.getInstance();

		// Find owner UserID
		String userID = event.getInteraction().getUser().getId().asString();

		// Find account
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
		if (account == null)
			return Mono.empty();

		// Verify old name
		if (!oldName.equals(account.getLoginName())) {
			// Reply with error
			return event.reply("Old login name does not match.");
		}

		// Verify new name
		if (!username.equals(confirm)) {
			// Reply with error
			return event
					.reply("Login name confirmation failed, please make sure the name matches the confirmation box.");
		}

		// Check if the name is in use
		if (manager.getUserByLoginName(username) != null) {
			// Reply with error
			return event.reply("Selected login name is already in use.");
		}

		if (!account.updateLoginName(username)) {
			// Prevent banned and filtered words
			for (String word : username.split(" ")) {
				if (muteWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())) {
					return event.reply("Selected login name was rejected as it may not be appropriate.");
				}

				if (filterWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())) {
					return event.reply("Selected login name was rejected as it may not be appropriate.");
				}
			}

			// Prevent blacklisted names from being used
			for (String name : nameBlacklist) {
				if (username.equalsIgnoreCase(name))
					return event.reply("Selected login name was rejected as it may not be appropriate.");
			}

			// Failed
			return event.reply("Selected login name is invalid.");
		}

		// Update login name
		manager.releaseLoginName(oldName);
		return event.reply("Login name updated successfully.");
	}
}
