package TFTPPackets;

public class ErrorPacket extends TFTPPacket {
	private static final short OP_ERR = 5;
	private ErrorCode errorCode;
	private String message;

	public ErrorPacket(ErrorCode errorCode, String message, byte[] buffer) throws IllegalArgumentException {
		super(OP_ERR, buffer);
		setErrorcode(errorCode);
		setMessage(message);
	}
	
	public ErrorPacket(byte[] buffer) throws IllegalArgumentException {
		super(OP_ERR, buffer);

		setErrorcode(ErrorCode.NOT_DEFINED);
		setMessage(message);
	}

	public void setErrorcode(ErrorCode newErrorCode) {
		putShortInBuffer(2, newErrorCode.getCode());
		errorCode = newErrorCode;
	}

	public short getErrorCode() {
		return errorCode.getCode();
	}

	public void setMessage(String newMessage) throws IllegalArgumentException {
		length = setStringInBuffer(4, newMessage);
		message = newMessage;
	}
	
	public String getMessage() throws IllegalArgumentException {
		return message;
	}
}
