package org.asf.centuria.discord.events;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.modules.eventbus.EventObject;
import org.asf.centuria.modules.eventbus.EventPath;

/**
 * 
 * Account Unpaired Event - called on account unpair
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
@EventPath("accounts.discord.unpair")
public class AccountUnpairedEvent extends EventObject {

	private CenturiaAccount account;
	private String userId;

	public AccountUnpairedEvent(CenturiaAccount account, String userId) {
		this.account = account;
		this.userId = userId;
	}

	@Override
	public String eventPath() {
		return "accounts.discord.unpair";
	}

	/**
	 * Retrieves the account that is being deleted
	 * 
	 * @return CenturiaAccount instance
	 */
	public CenturiaAccount getAccount() {
		return account;
	}

	/**
	 * Retrieves the discord user id that was paired
	 * 
	 * @return Discord user id
	 */
	public String getDiscordUserId() {
		return userId;
	}

}
