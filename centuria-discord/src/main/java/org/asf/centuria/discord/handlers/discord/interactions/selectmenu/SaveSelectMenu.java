package org.asf.centuria.discord.handlers.discord.interactions.selectmenu;

import java.util.Arrays;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.SaveMode;
import org.asf.centuria.discord.LinkUtils;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

public class SaveSelectMenu {

	/**
	 * Handles the 'save selection' select menu event
	 * 
	 * @param event   Select menu event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(String id, SelectMenuInteractionEvent event, GatewayDiscordClient gateway) {
		// Find save
		String save = event.getValues().get(0);

		// Find owner UserID
		String userID = event.getInteraction().getUser().getId().asString();

		// Find account
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
		if (account == null)
			return Mono.empty();

		// Check saves
		if (account.getSaveMode() == SaveMode.SINGLE)
			return event.reply("You are not using managed save mode");

		// Set reply
		event.deferReply().block();

		// Switch
		if (!account.getSaveManager().switchSave(save))
			return event
					.editReply(InteractionReplyEditSpec.builder()
							.embeds(Arrays.asList(EmbedCreateSpec.builder().title("Save switch failure")
									.description("Could not switch to '" + save + "'").color(Color.RED).build()))
							.build());

		// Default response
		return event
				.editReply(InteractionReplyEditSpec.builder()
						.embeds(Arrays.asList(EmbedCreateSpec.builder().title("Switched save")
								.description("Successfully switched to '" + save + "'").color(Color.GREEN).build()))
						.build());
	}

}
