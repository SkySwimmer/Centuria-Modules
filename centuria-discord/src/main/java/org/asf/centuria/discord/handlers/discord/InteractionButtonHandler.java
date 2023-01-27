package org.asf.centuria.discord.handlers.discord;

import java.util.ArrayList;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.AppealButtonHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.BasicDismissDeleteHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.BasicDismissHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.ConfirmMigrateHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.DownloadSingleplayerLauncherHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.FeedbackReplyButtonHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.ReportReplyButtonHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.accountoptions.ConfirmDisable2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.accountoptions.ConfirmEnable2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.accountoptions.Disable2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.accountoptions.Enable2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.AllowHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.BlockIpHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.ConfirmAllowHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.ConfirmBlockIpHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.ConfirmRejectHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.ConfirmWhitelistHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.RejectHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.auth2fa.WhitelistHandler2fa;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.linking.RelinkButtonHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.linking.UnlinkButtonHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.panel.AccountPanelHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.panel.PairAccountHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.panel.RegisterAccountHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.password.BlockIpHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.password.ConfirmBlockIpHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.password.ConfirmResetPasswordHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.password.ResetPasswordHandler;
import org.asf.centuria.discord.handlers.discord.interactions.buttons.registration.CreateAccountHandler;
import org.asf.centuria.networking.gameserver.GameServer;

import com.google.gson.JsonObject;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

public class InteractionButtonHandler {

	/**
	 * Handles interaction buttons
	 * 
	 * @param event   Button event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handle(ButtonInteractionEvent event, GatewayDiscordClient gateway) {
		String id = event.getCustomId();

		if (id.startsWith("relink/") && id.split("/").length == 3) {
			return RelinkButtonHandler.handle(id, event, gateway);
		} else if (id.startsWith("unlink/") && id.split("/").length == 3) {
			return UnlinkButtonHandler.handle(id, event, gateway);
		} else if (id.startsWith("appeal/") && id.split("/").length == 3) {
			return AppealButtonHandler.handle(id, event, gateway);
		} else if (id.startsWith("doblockip/") && id.split("/").length == 4) {
			return BlockIpHandler.handle(id, event, gateway);
		} else if (id.startsWith("confirmblockip/") && id.split("/").length == 4) {
			return ConfirmBlockIpHandler.handle(id, event, gateway);
		} else if (id.startsWith("confirmresetpassword/") && id.split("/").length == 3) {
			return ConfirmResetPasswordHandler.handle(id, event, gateway);
		} else if (id.startsWith("doresetpassword/") && id.split("/").length == 3) {
			return ResetPasswordHandler.handle(id, event, gateway);
		} else if (id.equals("dismiss")) {
			return BasicDismissHandler.handle(id, event, gateway);
		} else if (id.equals("confirmmigrateaccount")) {
			return ConfirmMigrateHandler.handle(id, event, gateway);
		} else if (id.equals("dismissDelete")) {
			return BasicDismissDeleteHandler.handle(id, event, gateway);
		} else if (id.equals("pair")) {
			return PairAccountHandler.handle(id, event, gateway);
		} else if (id.equals("register")) {
			return RegisterAccountHandler.handle(id, event, gateway);
		} else if (id.equals("accountpanel")) {
			return AccountPanelHandler.handle(id, event, gateway);
		} else if (id.startsWith("doblock2fa/") && id.split("/").length == 3) {
			return BlockIpHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmblock2fa/") && id.split("/").length == 3) {
			return ConfirmBlockIpHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("dodeny2fa/") && id.split("/").length == 3) {
			return RejectHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmdeny2fa/") && id.split("/").length == 3) {
			return ConfirmRejectHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("doallow2fa/") && id.split("/").length == 3) {
			return AllowHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmallow2fa/") && id.split("/").length == 3) {
			return ConfirmAllowHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("dowhitelistip2fa/") && id.split("/").length == 3) {
			return WhitelistHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmwhitelistip2fa/") && id.split("/").length == 3) {
			return ConfirmWhitelistHandler2fa.handle(id, event, gateway);
		} else if (id.startsWith("dodisable2fa/") && id.split("/").length == 3) {
			return Disable2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmdisable2fa/") && id.split("/").length == 3) {
			return ConfirmDisable2fa.handle(id, event, gateway);
		} else if (id.startsWith("doenable2fa/") && id.split("/").length == 3) {
			return Enable2fa.handle(id, event, gateway);
		} else if (id.startsWith("confirmenable2fa/") && id.split("/").length == 3) {
			return ConfirmEnable2fa.handle(id, event, gateway);
		} else if (id.startsWith("createaccount/") && id.split("/").length >= 3) {
			return CreateAccountHandler.handle(id, event, gateway);
		} else if (id.startsWith("feedbackreply/") && id.split("/").length == 2) {
			return FeedbackReplyButtonHandler.handle(id, event, gateway);
		} else if (id.startsWith("reportreply/") && id.split("/").length == 3) {
			return ReportReplyButtonHandler.handle(id, event, gateway);
		} else if (id.equals("downloadsingleplayerlauncher")) {
			return DownloadSingleplayerLauncherHandler.handle(id, event, gateway);
		} else if (id.startsWith("rejectappeal/")) {
			// Required permissions: mod (ingame)
			CenturiaAccount modacc = LinkUtils
					.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
			if (modacc == null) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** You dont have a Centuria account linked to your Discord account")
						.ephemeral(true).build()).block();
				return Mono.empty();
			}

			String permLevel = "member";
			if (modacc.getSaveSharedInventory().containsItem("permissions")) {
				permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
						.get("permissionLevel").getAsString();
			}
			if (!GameServer.hasPerm(permLevel, "moderator")) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** no Centuria moderator permissions.").ephemeral(true).build()).block();
				return Mono.empty();
			}

			// Build response (the 'Are you sure' prompt)
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();
			msg.content("Reject appeal?");

			// Add buttons
			msg.addComponent(
					ActionRow.of(Button.danger("confirm" + id, "Confirm"), Button.primary("dismissDelete", "Cancel")));
			return event.reply(msg.build());
		} else if (id.startsWith("acceptappeal/")) {
			// Required permissions: mod (ingame)
			CenturiaAccount modacc = LinkUtils
					.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
			if (modacc == null) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** You dont have a Centuria account linked to your Discord account")
						.ephemeral(true).build()).block();
				return Mono.empty();
			}

			String permLevel = "member";
			if (modacc.getSaveSharedInventory().containsItem("permissions")) {
				permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
						.get("permissionLevel").getAsString();
			}
			if (!GameServer.hasPerm(permLevel, "moderator")) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** no Centuria moderator permissions.").ephemeral(true).build()).block();
				return Mono.empty();
			}

			// Build response (the 'Are you sure' prompt)
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();
			msg.content("Accept appeal?");

			// Add buttons
			msg.addComponent(
					ActionRow.of(Button.danger("confirm" + id, "Confirm"), Button.primary("dismissDelete", "Cancel")));
			return event.reply(msg.build());
		} else if (id.startsWith("confirmrejectappeal/")) {
			// Reject appeal
			String accID = id.substring("confirmacceptappeal/".length());
			CenturiaAccount acc = AccountManager.getInstance().getAccount(accID);
			if (acc == null)
				return event.reply("**Error:** account no longer exists");

			// Required permissions: mod (ingame)
			CenturiaAccount modacc = LinkUtils
					.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
			if (modacc == null) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** You dont have a Centuria account linked to your Discord account")
						.ephemeral(true).build()).block();
				return Mono.empty();
			}

			String permLevel = "member";
			if (modacc.getSaveSharedInventory().containsItem("permissions")) {
				permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
						.get("permissionLevel").getAsString();
			}
			if (!GameServer.hasPerm(permLevel, "moderator")) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** no Centuria moderator permissions.").ephemeral(true).build()).block();
				return Mono.empty();
			}

			// Reject the appeal
			JsonObject obj = new JsonObject();
			obj.addProperty("status", "rejected");
			acc.getSaveSharedInventory().setItem("appeallock", obj);

			// DM them
			String userID = LinkUtils.getDiscordAccountFrom(acc);
			if (userID != null) {
				try {
					EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

					// Embed
					embed.title("Appeal rejected");
					embed.color(Color.RED);
					embed.description("Your appeal has been rejected.");
					embed.footer(DiscordBotModule.getServerName(),
							DiscordBotModule.getClient().getSelf().block().getAvatarUrl());

					// Message object
					MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
					msg.addEmbed(embed.build());

					// Send response
					DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block().getPrivateChannel().block()
							.createMessage(msg.build()).subscribe();
				} catch (Exception e) {
				}
			}

			// Delete message
			MessageReferenceData ref = event.getMessage().get().getData().messageReference().get();
			Message oMsg = gateway
					.getMessageById(Snowflake.of(ref.channelId().get().asString()), Snowflake.of(ref.messageId().get()))
					.block();
			oMsg.edit(MessageEditSpec.builder().components(new ArrayList<LayoutComponent>()).build()).block();
			event.deferEdit().block();
			event.getMessage().get().getChannel().block()
					.createMessage("Rejected " + acc.getDisplayName() + "'s appeal").block();
			event.getMessage().get().delete().block();
		} else if (id.startsWith("confirmacceptappeal/")) {
			// Accept appeal
			String accID = id.substring("confirmacceptappeal/".length());
			CenturiaAccount acc = AccountManager.getInstance().getAccount(accID);
			if (acc == null)
				return event.reply("**Error:** account no longer exists");

			// Required permissions: mod (ingame)
			CenturiaAccount modacc = LinkUtils
					.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
			if (modacc == null) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** You dont have a Centuria account linked to your Discord account")
						.ephemeral(true).build()).block();
				return Mono.empty();
			}

			String permLevel = "member";
			if (modacc.getSaveSharedInventory().containsItem("permissions")) {
				permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
						.get("permissionLevel").getAsString();
			}
			if (!GameServer.hasPerm(permLevel, "moderator")) {
				event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.content("**Error:** no Centuria moderator permissions.").ephemeral(true).build()).block();
				return Mono.empty();
			}

			// Accept the appeal
			acc.pardon("Appeal has been accepted");

			// Delete message
			MessageReferenceData ref = event.getMessage().get().getData().messageReference().get();
			Message oMsg = gateway
					.getMessageById(Snowflake.of(ref.channelId().get().asString()), Snowflake.of(ref.messageId().get()))
					.block();
			oMsg.edit(MessageEditSpec.builder().components(new ArrayList<LayoutComponent>()).build()).block();
			event.deferEdit().block();
			event.getMessage().get().getChannel().block()
					.createMessage("Accepted " + acc.getDisplayName() + "'s appeal").block();
			event.getMessage().get().delete().block();
		}

		// Default handler
		return Mono.empty();
	}

}
