package org.asf.emuferal.peertopeer.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.io.PacketWriter;
import org.asf.nexus.NexusClient.Packet;

public abstract class NexusPacket {

	/**
	 * Defines the nexus packet type
	 * 
	 * @return P2PNexusPacketType value
	 */
	public abstract P2PNexusPacketType type();

	/**
	 * Creates a new packet instance
	 * 
	 * @return NexusPacket instance
	 */
	public abstract NexusPacket newInstance();

	/**
	 * Parses the packet
	 * 
	 * @param reader Packet reader
	 */
	public abstract void parse(PacketReader reader) throws IOException;

	/**
	 * Writes the packet
	 * 
	 * @param writer Packet writer
	 */
	public abstract void write(PacketWriter writer) throws IOException;
	
	/**
	 * Builds the packet
	 * @return Packet payload bytes
	 */
	public byte[] build() {
		ByteArrayOutputStream strm = new ByteArrayOutputStream();
		PacketWriter wr = new PacketWriter(strm);
		try {
			write(wr);
		} catch (IOException e) {
		}
		return strm.toByteArray();
	}

	/**
	 * Handles the packet
	 * 
	 * @param packet Nexus packet
	 */
	public abstract void handle(Packet packet);

}
