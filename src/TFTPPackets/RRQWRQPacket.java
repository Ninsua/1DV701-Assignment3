package TFTPPackets;

import java.nio.BufferUnderflowException;

public class RRQWRQPacket extends TFTPPacket {
	private static final short OP_RRQ = 1;
	private static final short OP_WRQ = 2;
	private String requestedFile;
	private String mode;
	private int modeIndex;

	// Reads an existing TFTP packet from a buffer. If it does not match, exceptions
	// are thrown
	public RRQWRQPacket(byte[] datagramBuffer) throws IllegalArgumentException, BufferUnderflowException {
		super(datagramBuffer);

		if (opcode < OP_RRQ || opcode > OP_WRQ)
			throw new IllegalArgumentException();

		requestedFile = readStringFromBuffer(2);
		modeIndex = byteBuffer.position();
		mode = readStringFromBuffer(modeIndex);
		length = byteBuffer.position();
	}

	private String readStringFromBuffer(int index) throws IllegalArgumentException {
		StringBuilder stringBuilder = new StringBuilder();
		byteBuffer.position(index);

		try {
			byte b = byteBuffer.get();

			while (b != 0) {
				stringBuilder.append((char) b);
				b = byteBuffer.get();
			}
		} catch (BufferUnderflowException e) {
			throw new IllegalArgumentException();
		}

		return stringBuilder.toString();
	}

	public String getRequestedFile() {
		return requestedFile;
	}

	public String getMode() {
		return mode;
	}

}
