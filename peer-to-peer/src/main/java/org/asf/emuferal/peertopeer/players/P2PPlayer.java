package org.asf.emuferal.peertopeer.players;

import java.util.HashMap;

import org.asf.centuria.entities.generic.Quaternion;
import org.asf.centuria.entities.generic.Vector3;
import org.asf.centuria.entities.players.Player;

import com.google.gson.JsonElement;

public class P2PPlayer {
	
	public HashMap<String, JsonElement> inventoryItems = new HashMap<String, JsonElement>(); 
	
	public boolean isLocal;
	
	public String clientID;
	public String id;
	public String displayName;
	public Player player;
	
	public String currentSanctuaryLook;
	public String currentLook;
	
	public String look;
	
	public String room;
	public Vector3 position;
	public Quaternion rotation;
	
	public int action;
	
	public boolean roomReady = false;
	public int levelID;
	public int levelType;

}
