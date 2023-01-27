package org.asf.centuria.discord.handlers.discord.interactions.selectmenu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.PlayerInventory;
import org.asf.centuria.accounts.SaveMode;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.discord.TimedActions;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.component.SelectMenu.Option;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class AccountOptionsMenuHandler {

	/**
	 * Handles the 'account config' select menu event
	 * 
	 * @param event   Select menu event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(String id, SelectMenuInteractionEvent event, GatewayDiscordClient gateway) {
		// Load option
		String option = event.getValues().get(0);

		// Find owner UserID
		String userID = event.getInteraction().getUser().getId().asString();

		// Find account
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
		if (account == null)
			return Mono.empty();

		// Reset selection
		if (account.getSaveMode() == SaveMode.SINGLE)
			event.getMessage().get().edit().withComponents(ActionRow.of(SelectMenu.of("accountoption",
					Option.of("Change display name", "displayname"), Option.of("Change login name", "loginname"),
					Option.of("Enable/disable 2-factor authentication", "2fa"),
					Option.of("Forgot/change password", "forgotpassword"),
					Option.of("Forgot login name", "forgotloginname"),
					Option.of("Download your account inventory (including avatars)", "downloaddata"),
					Option.of("Migrate to Managed Save Data", "migrate"),
					Option.of("Permanently delete account", "deleteaccount")))).subscribe();
		else
			event.getMessage().get().edit()
					.withComponents(ActionRow.of(SelectMenu.of("accountoption",
							Option.of("Change display name", "displayname"),
							Option.of("Change login name", "loginname"),
							Option.of("Enable/disable 2-factor authentication", "2fa"),
							Option.of("Forgot/change password", "forgotpassword"),
							Option.of("Forgot login name", "forgotloginname"),
							Option.of("Download your account inventory (including avatars)", "downloaddata"),
							Option.of("Select active save (currently " + account.getSaveManager().getCurrentActiveSave()
									+ ")", "selectsave"),
							Option.of("Permanently delete account", "deleteaccount"))))
					.subscribe();

		// Handle request
		switch (option) {

		// Display name
		case "displayname": {
			// Show display name change form
			InteractionPresentModalSpec.Builder modal = InteractionPresentModalSpec.builder();
			modal.title("Update display name");
			modal.addComponent(ActionRow.of(TextInput.small("displayname", "New display name", 2, 16).required()
					.prefilled(account.getDisplayName())));
			modal.customId("updatedisplayname");

			// Show form
			return event.presentModal(modal.build());
		}

		// Login name
		case "loginname": {
			// Show login name change form
			InteractionPresentModalSpec.Builder modal = InteractionPresentModalSpec.builder();
			modal.title("Update login name");
			modal.addComponent(ActionRow.of(TextInput.small("oldname", "Old login name", 1, 320).required()));
			modal.addComponent(ActionRow.of(TextInput.small("newname", "New login name", 1, 320).required()));
			modal.addComponent(ActionRow.of(TextInput.small("confirmname", "Confirm login name", 1, 320).required()));
			modal.customId("updateloginname");

			// Show form
			return event.presentModal(modal.build());
		}

		// Account deletion
		case "deleteaccount": {
			// Show delete confirm form
			InteractionPresentModalSpec.Builder modal = InteractionPresentModalSpec.builder();
			modal.title("Delete your account");
			modal.addComponent(ActionRow
					.of(TextInput.small("confirm", "Confirm with 'Yes, fully delete my account!'").required()));
			modal.customId("deleteaccount");

			// Show form
			return event.presentModal(modal.build());
		}

		// Save selection
		case "selectsave": {
			// Build message
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();

			// Check
			if (account.getSaveMode() != SaveMode.MANAGED)
				return event.deferEdit();

			// Message content
			msg.content("Select save");

			// Add saves
			ArrayList<Option> options = new ArrayList<Option>();
			for (String save : account.getSaveManager().getSaves())
				if (account.getSaveManager().getCurrentActiveSave().equals(save))
					options.add(Option.ofDefault(save, save));
				else
					options.add(Option.of(save, save));

			// Dropdown
			msg.addComponent(ActionRow.of(SelectMenu.of("saveselect", options)));

			// Send message
			return event.reply(msg.build());
		}

		// Data migration
		case "migrate": {
			// Build message
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();

			// Check
			if (account.getSaveMode() == SaveMode.MANAGED)
				return event.deferEdit();

			// Message content
			msg.content("**WARNING!**\n"
					+ "Account data migration is a permanent operation! Are you sure you wish to continue?\n\n"
					+ "If you confirm migration the system will send you a backup of your data first.");

			// Buttons
			msg.addComponent(ActionRow.of(Button.secondary("confirmmigrateaccount", "Confirm migration"),
					Button.primary("dismiss", "Dismiss")));
			// Send message
			return event.reply(msg.build());
		}

		// 2-factor authentication
		case "2fa": {
			// Build message
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();

			// Get status of 2fa
			boolean enabled2fa = account.getSaveSharedInventory().containsItem("accountoptions")
					&& account.getSaveSharedInventory().getItem("accountoptions").getAsJsonObject().has("enable2fa")
					&& account.getSaveSharedInventory().getItem("accountoptions").getAsJsonObject().get("enable2fa")
							.getAsBoolean();

			// Message content
			msg.content("User account options: **2-factor authentication**");

			// Message buttons
			if (enabled2fa)
				msg.addComponent(ActionRow.of(
						Button.success("confirmenable2fa/" + userID + "/" + account.getAccountID(), "Enable")
								.disabled(),
						Button.danger("confirmdisable2fa/" + userID + "/" + account.getAccountID(), "Disable")));
			else
				msg.addComponent(ActionRow.of(
						Button.success("confirmenable2fa/" + userID + "/" + account.getAccountID(), "Enable"),
						Button.danger("confirmdisable2fa/" + userID + "/" + account.getAccountID(), "Disable")
								.disabled()));

			// Send message
			return event.reply(msg.build());
		}

		// Forgot password
		case "forgotpassword": {
			// Build message
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();

			// Message content
			String message = "**Received account password reset request.**\n";
			message += "\n";
			message += "A account password reset request was just made, here follow the details:\n";
			message += "**Account login name:** `" + account.getLoginName() + "`\n";
			message += "**Ingame player name:** `" + account.getDisplayName() + "`\n";
			message += "**Last login time:** "
					+ (account.getLastLoginTime() == -1 ? "`Unknown`" : "<t:" + account.getLastLoginTime() + ">")
					+ "\n";
			message += "**Requested at:** <t:" + System.currentTimeMillis() / 1000 + ">\n";
			message += "\n";
			message += "This request is only valid for 5 minutes.\n";
			message += "\n";
			message += "When clicking `Reset password`, the account password will be unlocked and the next login will be saved.\n";
			message += "__Please do not press reset until you started the Fer.al client, otherwise it might get abused.__\n";
			msg.content(message);

			// Schedule reset action
			String code = TimedActions.addAction(account.getAccountID() + "-forgotpassword", () -> {
				// Release password lock
				AccountManager.getInstance().makePasswordUpdateRequested(account.getAccountID());
			}, 5 * 60);

			// Buttons
			msg.addComponent(
					ActionRow.of(Button.danger("confirmresetpassword/" + userID + "/" + code, "Reset password"),
							Button.primary("dismiss", "Dismiss")));

			// Send message
			return event.reply(msg.build());
		}

		// Display login name
		case "forgotloginname": {
			// Build message
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();

			// Message content
			String message = "**Received a request to show the account login name.**\n";
			message += "\n";
			message += "Account login name: ||" + account.getLoginName() + "||\n";
			message += "\n";
			message += "__Please delete this message as soon as possible, the given information is sensitive.__";
			msg.content(message);

			// Buttons
			msg.addComponent(ActionRow.of(Button.success("dismissDelete", "Delete this message")));

			// Send message
			return event.reply(msg.build());
		}

		// Account inventory download
		case "downloaddata": {
			// Build message
			MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
			event.deferReply().block();

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

			// Delete original reply
			return event.deleteReply();
		}

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
