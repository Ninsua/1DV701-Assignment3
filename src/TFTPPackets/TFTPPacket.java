package TFTPPackets;

/*
 * This class and the subsequent subclasses wrap around the provided buffer to simplify handling of the buffers in a TFTP context
 */

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public abstract class TFTPPacket {
	protected ByteBuffer byteBuffer;
	protected byte[] buf;
	protected short opcode;
	protected int length;

	protected TFTPPacket(short opcode, byte[] buffer) throws IllegalArgumentException {
		if (opcode < 1 || opcode > 5)
			throw new IllegalArgumentException();

		if (buffer.length < 4 || buffer.length > 516)
			throw new IllegalArgumentException();

		buf = buffer;
		byteBuffer = ByteBuffer.wrap(buf);
		setOpcode(opcode);
		this.opcode = opcode;
		length = 2;
	}

	// For existing datagrambuffers that contains a TFTP packet
	protected TFTPPacket(byte[] datagramBuffer) throws BufferUnderflowException {
		if (datagramBuffer.length < 4 || datagramBuffer.length > 516)
			throw new IllegalArgumentException();

		buf = datagramBuffer;
		byteBuffer = ByteBuffer.wrap(buf);
		opcode = getShortFromBuffer(0);
		setLengthToPosition();
	}

	protected short getShortFromBuffer(int position) throws BufferUnderflowException {
		byteBuffer.position(position);
		return byteBuffer.getShort();
	}

	protected void setLengthToPosition() {
		length = byteBuffer.position();
	}

	public short getOpcode() {
		return opcode;
	}

	public int getLength() {
		return length;
	}

	public byte[] getBuffer() {
		return buf;
	}

	public void setOpcode(short newOpcode) {
		byteBuffer.position(0);
		byteBuffer.putShort(newOpcode);
		opcode = newOpcode;
	}
}