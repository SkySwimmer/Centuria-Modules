package org.asf.centuria.discord.handlers.discord.interactions.forms;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.LinkUtils;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import reactor.core.publisher.Mono;

public class DeleteAccountHandler {

	/**
	 * Handles the 'delete account' form submission event
	 * 
	 * @param event   Modal submission event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(String id, ModalSubmitInteractionEvent event, GatewayDiscordClient gateway) {
		// Load fields
		String confirm = event.getInteraction().getData().data().get().components().get().get(0).components().get()
				.get(0).value().get();

		// Find owner UserID
		String userID = event.getInteraction().getUser().getId().asString();

		// Find account
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
		if (account == null)
			return Mono.empty();

		// Check
		if (!confirm.equalsIgnoreCase("yes, fully delete my account!"))
			return event.reply(
					"Confirmation does not match, make sure to include the comma and exclamation mark should you wish to delete your account");

		event.deferReply().block();
		account.deleteAccount();
		return event.editReply("Account deleted successfully.");
	}
}
