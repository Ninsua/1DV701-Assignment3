package TFTPPackets;

import java.nio.BufferUnderflowException;

public abstract class TFTPBlockPacket extends TFTPPacket {
	protected short block;
	
	// Reads an existing TFTP packet from a buffer. If it does not match, exceptions
	// are thrown
	public TFTPBlockPacket(byte[] datagramBuffer) throws IllegalArgumentException, BufferUnderflowException {
		super(datagramBuffer);
		block = getShortFromBuffer(2);
		length = 4;
	}
	
	public TFTPBlockPacket(short opcode, short newBlock, byte[] buffer) throws IllegalArgumentException {
		super(opcode, buffer);
		setBlock(newBlock);
		block = newBlock;
		length = 4;
	}
	
	public short getBlock() {
		return block;
	}

	public void setBlock(short newBlock) throws IllegalArgumentException {
		byteBuffer.position(2);
		byteBuffer.putShort(newBlock);
		block = newBlock;
	}

}
