package org.asf.centuria.discord.events;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.modules.eventbus.EventObject;

/**
 * 
 * Account Paired Event - called on account pairing
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public class AccountPairedEvent extends EventObject {

	private CenturiaAccount account;
	private String userId;

	public AccountPairedEvent(CenturiaAccount account, String userId) {
		this.account = account;
		this.userId = userId;
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
