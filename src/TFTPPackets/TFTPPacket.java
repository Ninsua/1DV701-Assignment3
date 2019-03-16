package TFTPPackets;

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;

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

	protected void putShortInBuffer(int position, short shrt) {
		byteBuffer.position(position);
		byteBuffer.putShort(shrt);
	}

	protected void setLengthToPosition() {
		length = byteBuffer.position();
	}

	protected String readStringFromBuffer(int index) throws IllegalArgumentException {
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

	// Returns the position in the buffer
	protected int setStringInBuffer(int startIndex, String message) throws IllegalArgumentException {
		if (startIndex < 2 || startIndex >= 516)
			throw new IllegalArgumentException();

		if (message.length() == 0 || (buf.length - startIndex - message.length()) <= 0)
			throw new IllegalArgumentException();

		byteBuffer.position(startIndex);

		try {
			byte[] splitMessage = message.getBytes("UTF-8");

			for (int i = 0; i < splitMessage.length; i++) {
				byteBuffer.put(splitMessage[i]);
			}

			byte zero = 0;
			byteBuffer.put(zero);
		} catch (BufferOverflowException | UnsupportedEncodingException e) {
			throw new IllegalArgumentException();
		}

		return byteBuffer.position();
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
		putShortInBuffer(0, newOpcode);
		opcode = newOpcode;
	}
}