package TFTPPackets;

public enum ErrorCode {
	NOT_DEFINED((short) 0, ""), FILE_NOT_FOUND((short) 1, "File not found"),
	ACCESS_VIOLATION((short) 2, "Access violation"), DISK_FULL((short) 3, "Disk full or allocation exceeded"),
	ILLEGAL_TFTP_OPERATION((short) 4, "Illegal TFTP operation)"), UNKNOWN_TRANSFER_ID((short) 5, "Unknown transfer ID"),
	FILE_ALREADY_EXISTS((short) 6, "File already exists"), NO_SUCH_USER((short) 7, "No such user");

	private short code;
	private String message;

	ErrorCode(short code, String message) {
		this.code = code;
		this.message = message;
	}

	public short getCode() {
		return this.code;
	}

	public String getMessage() {
		return this.message;
	}

}
