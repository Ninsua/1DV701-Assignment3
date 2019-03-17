package TFTPPackets;

import java.nio.BufferUnderflowException;

public class DataPacket extends TFTPBlockPacket {
	private static final short OP_DAT = 3;

	// Reads an existing TFTP packet from a buffer. If it does not match, exceptions
	// are thrown
	public DataPacket(byte[] datagramBuffer, int datagramLength)
			throws IllegalArgumentException, BufferUnderflowException {
		super(datagramBuffer);

		if (opcode != OP_DAT || datagramLength < 2 || datagramLength > 516) {
			throw new IllegalArgumentException("Not a data packet");
		}
		block = getShortFromBuffer(2);

		length = datagramLength;
	}

	public DataPacket(short newBlock, byte[] buffer, byte[] data, int len) throws IllegalArgumentException {
		super(OP_DAT, newBlock, buffer);
		setDataToBuffer(data, len);
		length = data.length + 4;
	}

	public DataPacket(short newBlock, byte[] buffer) throws IllegalArgumentException {
		super(OP_DAT, newBlock, buffer);
		setDataToBuffer(new byte[] { 0 }, 0);
		length = 4;
	}

	public byte[] getData() {
		byte[] data = new byte[length - 4];
		System.arraycopy(buf, 4, data, 0, length - 4);

		return data;
	}

	public void setData(byte[] data, int len) throws IllegalArgumentException {
		setDataToBuffer(data, len);
	}

	private void setDataToBuffer(byte[] dataBuffer, int len) throws IllegalArgumentException {
		if (dataBuffer.length > 512)
			throw new IllegalArgumentException();

		if (len < 0)
			throw new IllegalArgumentException();

		byteBuffer.position(4);
		for (int i = 0; i < len; i++) {
			byteBuffer.put(dataBuffer[i]);
		}

		length = byteBuffer.position();
	}
}
