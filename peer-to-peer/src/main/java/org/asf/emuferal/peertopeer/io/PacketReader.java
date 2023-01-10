package org.asf.emuferal.peertopeer.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.asf.centuria.entities.generic.Quaternion;
import org.asf.centuria.entities.generic.Vector3;

/**
 * 
 * Packet Content Reader (similar to the ModKit's packet reader)
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public class PacketReader {

	private InputStream reader;

	public PacketReader(InputStream reader) {
		this.reader = reader;
	}

	public byte[] readNBytes(int num) throws IOException {
		return reader.readNBytes(num);
	}

	public byte[] readAllBytes() throws IOException {
		return reader.readAllBytes();
	}

	public int readByte() throws IOException {
		return reader.read();
	}

	public String readString() throws IOException {
		byte[] b = readNBytes(readInt());
		try {
			return new String(b, "UTF-8");
		} catch (IOException e) {
			return new String(b);
		}
	}

	public char readChar() throws IOException {
		return ByteBuffer.wrap(readNBytes(2)).getChar();
	}

	public int readInt() throws IOException {
		return ByteBuffer.wrap(readNBytes(4)).getInt();
	}

	public boolean readBoolean() throws IOException {
		int data = readByte();
		if (data != 0)
			return true;
		else
			return false;
	}

	public long readLong() throws IOException {
		return ByteBuffer.wrap(readNBytes(8)).getLong();
	}

	public short readShort() throws IOException {
		return ByteBuffer.wrap(readNBytes(2)).getShort();
	}

	public float readFloat() throws IOException {
		return ByteBuffer.wrap(readNBytes(4)).getFloat();
	}

	public double readDouble() throws IOException {
		return ByteBuffer.wrap(readNBytes(8)).getDouble();
	}

	public Vector3 readVector3() throws IOException {
		return new Vector3(readDouble(), readDouble(), readDouble());
	}

	public Quaternion readQuaternion() throws IOException {
		return new Quaternion(readDouble(), readDouble(), readDouble(), readDouble());
	}

}
