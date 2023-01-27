package org.asf.centuria.discord.handlers.discord.interactions.buttons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.PlayerInventory;
import org.asf.centuria.accounts.SaveMode;
import org.asf.centuria.discord.LinkUtils;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

public class ConfirmMigrateHandler {

	/**
	 * Handles the confirm migration button event
	 * 
	 * @param event   Button event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(String id, ButtonInteractionEvent event, GatewayDiscordClient gateway) {
		// Find owner UserID
		String userID = event.getInteraction().getUser().getId().asString();

		// Check link
		if (!LinkUtils.isPairedWithCenturia(userID)) {
			// Return error
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();
			msg.content("Could not locate a Centuria account linked with your Discord account.");
			msg.ephemeral(true);
			return event.reply(msg.build());
		} else {
			// Find account
			CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);

			// Check
			if (account.getSaveMode() == SaveMode.MANAGED)
				return event.reply("Migration was already completed.");

			new Thread(() -> {
				try {
					// Build message
					MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
					event.deferReply().block();
					event.getMessage().get().edit().withComponents().subscribe();

					// Message
					if (account.getSaveMode() == SaveMode.MANAGED) {
						msg.content("The following zip contains your player inventory.\n"
								+ "Please note that some items aren't included for server protection.\n\n**IMPORTANT NOTICE:**\nThis only includes your ACTIVE save data, you need to switch saves and download the data separately if you want your other saves. Managed Save Data is very complicated and cannot be switched on the run for downloading.");
					} else {
						msg.content("The following zip contains your player inventory.\n"
								+ "Please note that some items aren't included for server protection.");
					}

					ByteArrayOutputStream strm = new ByteArrayOutputStream();
					try {
						ZipOutputStream invZip = new ZipOutputStream(strm);

						// Add all inventory objects
						addItemToZip(account.getSaveSpecificInventory(), "1", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "10", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "100", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "102", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "103", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "104", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "105", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "110", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "111", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "2", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "201", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "3", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "300", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "302", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "303", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "304", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "311", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "4", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "400", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "5", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "6", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "7", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "8", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "9", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "avatars", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "level", invZip);
						addItemToZip(account.getSaveSpecificInventory(), "savesettings", invZip);
						invZip.close();
						strm.close();
					} catch (IOException e) {
					}

					// Add file
					msg.addFile("inventory.zip", new ByteArrayInputStream(strm.toByteArray()));

					// Send message
					event.getInteraction().getChannel().block().createMessage(msg.build()).block();
					event.getInteraction().getChannel().block().createMessage(EmbedCreateSpec.builder()
							.title("Data migration in progress...").color(Color.ORANGE).build()).block();

					// Migrate
					account.migrateSaveDataToManagedMode();

					// Complete
					event.getInteraction().getChannel().block().createMessage(
							EmbedCreateSpec.builder().title("Data migration completed!").color(Color.GREEN).build())
							.block();

					// Delete original reply
					event.deleteReply().block();
				} catch (Exception e) {
					event.editReply("Failed to create the launcher").block();
				}
			}).start();
		}

		// Default response
		return Mono.empty();
	}

	private static void addItemToZip(PlayerInventory inv, String item, ZipOutputStream zipStrm)
			throws UnsupportedEncodingException, IOException {
		if (inv.containsItem(item))
			transferDataToZip(zipStrm, item + ".json", inv.getItem(item).toString().getBytes("UTF-8"));
	}

	private static void transferDataToZip(ZipOutputStream zip, String file, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(file));
		zip.write(data);
		zip.closeEntry();
	}

}
