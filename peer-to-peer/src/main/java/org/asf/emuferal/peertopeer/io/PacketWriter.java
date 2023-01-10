package org.asf.emuferal.peertopeer.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.asf.centuria.entities.generic.Quaternion;
import org.asf.centuria.entities.generic.Vector3;

/**
 * 
 * Packet Content Writer (similar to the ModKit's packet writer)
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public class PacketWriter {

	private OutputStream writer;

	public PacketWriter(OutputStream writer) {
		this.writer = writer;
	}

	public PacketWriter writeBytes(byte[] data) throws IOException {
		writer.write(data);
		return this;
	}

	public PacketWriter writeByte(byte data) throws IOException {
		writer.write((int)data);
		return this;
	}

	public PacketWriter writeString(String str) throws IOException {
		byte[] buff;
		try {
			buff = str.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			buff = new byte[0];
		}
		writeInt(buff.length);
		writeBytes(buff);
		return this;
	}

	public PacketWriter writeChar(char ch) throws IOException {
		writer.write(ByteBuffer.allocate(2).putChar(ch).array());
		return this;
	}

	public PacketWriter writeInt(int data) throws IOException {
		writer.write(ByteBuffer.allocate(4).putInt(data).array());
		return this;
	}

	public PacketWriter writeBoolean(boolean data) throws IOException {
		writer.write(data ? 1 : 0);
		return this;
	}

	public PacketWriter writeLong(long data) throws IOException {
		writer.write(ByteBuffer.allocate(8).putLong(data).array());
		return this;
	}

	public PacketWriter writeShort(short data) throws IOException {
		writer.write(ByteBuffer.allocate(2).putShort(data).array());
		return this;
	}

	public PacketWriter writeFloat(float data) throws IOException {
		writer.write(ByteBuffer.allocate(4).putFloat(data).array());
		return this;
	}

	public PacketWriter writeDouble(double data) throws IOException {
		writer.write(ByteBuffer.allocate(8).putDouble(data).array());
		return this;
	}

	public PacketWriter writeVector3(Vector3 v) throws IOException {
		writeDouble(v.x);
		writeDouble(v.y);
		writeDouble(v.z);
		return this;
	}

	public PacketWriter writeQuaternion(Quaternion q) throws IOException {
		writeDouble(q.x);
		writeDouble(q.y);
		writeDouble(q.z);
		writeDouble(q.w);
		return this;
	}

}