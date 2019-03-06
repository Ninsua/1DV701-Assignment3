package TFTPPackets;

import java.nio.BufferUnderflowException;

public class AckPacket extends TFTPPacket {
	private static final short OP_ACK = 4;
	private short block;

	// Reads an existing TFTP packet from a buffer. If it does not match, exceptions
	// are thrown
	public AckPacket(byte[] datagramBuffer) throws IllegalArgumentException, BufferUnderflowException {
		super(datagramBuffer);

		if (opcode != OP_ACK) {
			throw new IllegalArgumentException("Not a ack packet");
		}

		block = getShortFromBuffer(2);

		length = 4;
	}

	public AckPacket(short newBlock, byte[] buffer) throws IllegalArgumentException {
		super(OP_ACK, buffer);

		if (newBlock < 0) {
			throw new IllegalArgumentException();
		}

		block = newBlock;
		length = 4;
	}

	public short getBlock() {
		return block;
	}

	public void setBlock(short newBlock) throws IllegalArgumentException {
		if (newBlock < 0) {
			throw new IllegalArgumentException();
		}

		block = newBlock;
	}

}
