package org.asf.centuria.discord.handlers.discord.interactions.forms;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.textfilter.FilterSeverity;
import org.asf.centuria.textfilter.TextFilterService;
import org.asf.centuria.textfilter.result.FilterResult;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import reactor.core.publisher.Mono;

public class UpdateLoginNameHandler {

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
			// Prevent banned and filtered words as well as blacklisted names
			// Check display name
			FilterResult res = TextFilterService.getInstance().filter(username, true, "USERNAMEFILTER");
			if (res.isMatch() && res.getSeverity().ordinal() >= FilterSeverity.USER_STRICT_MODE.ordinal()) {
				// Reply with error
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
