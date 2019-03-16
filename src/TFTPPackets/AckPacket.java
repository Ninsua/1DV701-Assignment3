package TFTPPackets;

import java.nio.BufferUnderflowException;

public class AckPacket extends TFTPBlockPacket {
	private static final short OP_ACK = 4;

	// Reads an existing TFTP packet from a buffer. If it does not match, exceptions
	// are thrown
	public AckPacket(byte[] datagramBuffer) throws IllegalArgumentException, BufferUnderflowException {
		super(datagramBuffer);

		if (opcode != OP_ACK) {
			throw new IllegalArgumentException("Not a ack packet");
		}
	}

	public AckPacket(short newBlock, byte[] buffer) throws IllegalArgumentException {
		super(OP_ACK, newBlock, buffer);
	}

}
