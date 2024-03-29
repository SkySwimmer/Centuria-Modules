package org.asf.centuria.discord.handlers.game;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.discord.ServerConfigUtils;
import org.asf.centuria.discord.applications.ApplicationManager;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.eventbus.IEventReceiver;
import org.asf.centuria.modules.events.accounts.AccountBanEvent;
import org.asf.centuria.modules.events.accounts.AccountDeletionEvent;
import org.asf.centuria.modules.events.accounts.AccountMuteEvent;
import org.asf.centuria.modules.events.accounts.AccountPardonEvent;
import org.asf.centuria.modules.events.accounts.MiscModerationEvent;
import org.asf.centuria.modules.events.accounts.AccountKickEvent;

import com.google.gson.JsonObject;

import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;

public class ModerationHandlers implements IEventReceiver {

	@EventListener
	public void handleMisc(MiscModerationEvent ev) {
		String userID = null;
		if (ev.getTarget() != null)
			userID = LinkUtils.getDiscordAccountFrom(ev.getTarget());
		String data = "";
		for (String key : ev.getDetails().keySet()) {
			if (!data.isEmpty())
				data += "\n";
			data += key + ": **" + ev.getDetails().get(key) + "**";
		}
		moderationLog(ev.getModerationEventTitle(), userID,
				ev.getTarget() != null ? ev.getTarget().getDisplayName() : null,
				ev.getTarget() != null ? ev.getTarget().getAccountID() : null, data, ev.getIssuer(), null);
	}

	@EventListener
	public void handleBan(AccountBanEvent ev) {
		// Find discord client (if present)
		String userID = LinkUtils.getDiscordAccountFrom(ev.getAccount());
		if (userID != null) {
			User user = null;
			try {
				user = DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block();
			} catch (Exception e) {
			}
			if (user != null)
				ApplicationManager.cancelApplication(user, false, "Centuria account was moderated");
		}

		// Log moderation
		moderationLog("Ban", userID, ev.getAccount().getDisplayName(), ev.getAccount().getAccountID(),
				ev.isPermanent() ? "Ban type: **Permanent**" : ("Ban type: **Temporary** (" + ev.getDays() + " days)"),
				ev.getIssuer(), ev.getReason());

		if (userID != null) {
			// DM them if not appealed before
			try {
				EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

				// Description content
				String message = "You have been banned from our servers for unacceptable behavior.\n";
				if (ev.getReason() != null)
					message = "You have been banned from our servers:\n`" + ev.getReason() + "`\n";
				message += "\n";
				if (ev.isPermanent()) {
					message += "This is a permanent ban, "
							+ (ev.getAccount().getSaveSharedInventory().containsItem("appeallock")
									? "you cannot log in until pardoned."
									: " you can attempt to appeal by pressing the button below.")
							+ "\n"
							+ "Due to this being a permanent ban, you receive a singleplayer launcher with your data. However note that you will not be able to play with others, multiplayer is completely disabled.\n\nNote that if you have no mutual servers with the bot you cannot download the launcher, use the website if you are banned from the Discord server to obtain your data.";
				} else
					message += "This is a temporary ban, you cannot log on for " + ev.getDays() + " days.";

				// Embed
				embed.title((ev.isPermanent() ? "Permanently" : "Temporarily") + " banned from "
						+ DiscordBotModule.getServerName());
				embed.color(Color.RED);
				embed.description(message);
				embed.footer(DiscordBotModule.getServerName(),
						DiscordBotModule.getClient().getSelf().block().getAvatarUrl());

				// Message object
				MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
				msg.addEmbed(embed.build());

				// Appeal button (if permanent)
				if (ev.isPermanent() && !ev.getAccount().getSaveSharedInventory().containsItem("appeallock")) {
					msg.addComponent(ActionRow.of(
							Button.danger("appeal/" + userID + "/" + ev.getAccount().getAccountID(),
									"Appeal for pardon"),
							Button.success("downloadsingleplayerlauncher", "Download singleplayer launcher")));
				}

				// Send response
				DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block().getPrivateChannel().block()
						.createMessage(msg.build()).subscribe();
			} catch (Exception e) {
			}
		}
	}

	@EventListener
	public void handleDelete(AccountDeletionEvent ev) {
		// Find discord client (if present)
		String userID = LinkUtils.getDiscordAccountFrom(ev.getAccount());
		if (userID != null) {
			User user = null;
			try {
				user = DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block();
			} catch (Exception e) {
			}
			if (user != null)
				ApplicationManager.cancelApplication(user, false, "Centuria account was delted");
		}

		// Log moderation
		moderationLog("Account Deleted", userID, ev.getAccount().getDisplayName(), ev.getAccount().getAccountID(), null,
				"SYSTEM", null);
	}

	private void moderationLog(String type, String userID, String displayName, String accountID, String data,
			String issuer, String reason) {
		// Log moderation action to the moderation log
		String message = "**Centuria Moderation Log**\n";
		message += "\n";
		message += "Action: **" + type + "**";
		if (data != null)
			message += "\n" + data + "\n";
		if (reason != null)
			message += "\nAction reason: **" + reason + "**\n";
		else if (data == null)
			message += "\n";
		String issuerStr = "**" + issuer + "**";
		if (!issuer.equals("SYSTEM")) {
			issuerStr = "`" + issuer + "`";
			CenturiaAccount issuerAcc = AccountManager.getInstance().getAccount(issuer);
			if (issuerAcc != null) {
				issuer = issuerAcc.getDisplayName();
				issuerStr = "`" + issuer + "`";
				String isUid = LinkUtils.getDiscordAccountFrom(issuerAcc);
				if (isUid != null)
					issuerStr += " (<@!" + isUid + ">)";
			}
		}
		message += "Action issuer: " + issuerStr + "\n";
		if (displayName != null)
			message += "Affected player: `" + displayName + (userID != null ? "` (<@!" + userID + ">)" : "`") + "\n";
		message += "Action was taken on: <t:" + (System.currentTimeMillis() / 1000) + ">";

		// Send to all guild log channels
		for (Guild g : DiscordBotModule.getClient().getGuilds().toIterable()) {
			String guildID = g.getId().asString();
			JsonObject config = ServerConfigUtils.getServerConfig(guildID);
			if (config.has("moderationLogChannel")) {
				// Find channel
				String ch = config.get("moderationLogChannel").getAsString();
				String srvMessage = message;
				if (config.has("moderatorRole")) {
					// Add ping
					srvMessage += "\n\n<@&" + config.get("moderatorRole").getAsString() + ">";
				}

				// Attempt to send message
				try {
					g.getChannelById(Snowflake.of(ch)).block().getRestChannel().createMessage(srvMessage).block();
				} catch (Exception e) {
				}
			}
		}
	}

	@EventListener
	public void handleMute(AccountMuteEvent ev) {
		// Find discord client (if present)
		String userID = LinkUtils.getDiscordAccountFrom(ev.getAccount());
		if (userID != null) {
			User user = null;
			try {
				user = DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block();
			} catch (Exception e) {
			}
			if (user != null)
				ApplicationManager.cancelApplication(user, false, "Centuria account was moderated");
		}

		// Log moderation
		moderationLog("Mute", userID, ev.getAccount().getDisplayName(), ev.getAccount().getAccountID(),
				"Unmute timestamp: <t:" + (ev.getUnmuteTimestamp() / 1000) + ">", ev.getIssuer(), ev.getReason());

		if (userID != null) {
			// DM them
			try {
				EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

				// Description content
				String message = "You have been muted for violating the server rules.\n";
				if (ev.getReason() != null)
					message = "You have been muted, reason: `" + ev.getReason() + "`\n";
				message += "You are not allowed to use public chat until <t:" + (ev.getUnmuteTimestamp() / 1000) + ">";

				// Embed
				embed.title("Public chat mute in " + DiscordBotModule.getServerName());
				embed.color(Color.ORANGE);
				embed.description(message);
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
	}

	@EventListener
	public void handeKick(AccountKickEvent ev) {
		// Find discord client (if present)
		String userID = LinkUtils.getDiscordAccountFrom(ev.getAccount());

		// Log moderation
		moderationLog("Kick", userID, ev.getAccount().getDisplayName(), ev.getAccount().getAccountID(), null,
				ev.getIssuer(), ev.getReason());
	}

	@EventListener
	public void handlePardon(AccountPardonEvent ev) {
		// Find discord client (if present)
		String userID = LinkUtils.getDiscordAccountFrom(ev.getAccount());

		// Log moderation
		moderationLog("Pardon", userID, ev.getAccount().getDisplayName(), ev.getAccount().getAccountID(), null,
				ev.getIssuer(), ev.getReason());

		// Remove appeal lock
		if (ev.getAccount().getSaveSharedInventory().containsItem("appeallock"))
			ev.getAccount().getSaveSharedInventory().deleteItem("appeallock");

		if (userID != null) {
			// DM them
			try {
				EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

				// Description content
				String message = "Your account has been pardoned and all penalties have been removed.";
				if (ev.getReason() != null) {
					message += "\nReason: " + ev.getReason();
				}

				// Embed
				embed.title("Pardoned in " + DiscordBotModule.getServerName());
				embed.color(Color.GREEN);
				embed.description(message);
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
	}

}
