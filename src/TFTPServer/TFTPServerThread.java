package TFTPServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import TFTPPackets.*;

public class TFTPServerThread implements Runnable {
	private static final int RECEIVE_TIMEOUT = 10000;
	private static final int RETRANSMISSION_TIMEOUT = 3000; // 3 seconds in milliseconds
	private static final int MAX_RETRANSMISSION = 10;
	private static final int MAX_RECEIVE_ATTEMPTS = 10;
	public static final int BUFSIZE = 516;
	private byte[] recivedBuffer;
	private byte[] sendBuffer;
	private final File readDirectory;
	private final File writeDirectory;
	private DatagramSocket sendSocket;
	private InetSocketAddress clientAddress;
	private RRQWRQPacket requestPacket;

	// OP codes
	public static final short OP_RRQ = 1;
	public static final short OP_WRQ = 2;
	public static final short OP_DAT = 3;
	public static final short OP_ACK = 4;
	public static final short OP_ERR = 5;

	TFTPServerThread(InetSocketAddress clientAddress, byte[] datagramBuffer, File readDir, File writeDir)
			throws SocketException {

		this.clientAddress = clientAddress;
		readDirectory = readDir;
		writeDirectory = writeDir;
		sendSocket = new DatagramSocket(0);
		sendBuffer = new byte[516];
		recivedBuffer = new byte[516];
		
		try {
			requestPacket = new RRQWRQPacket(datagramBuffer);
		} catch (IllegalArgumentException | BufferUnderflowException e) {
			requestPacket = null;
		}
	}

	public void run() {
		try {
			if (requestPacket == null) {
				System.err.println("Illegal TFTP operation recieved");
				sendErrorPacket(ErrorCode.ILLEGAL_TFTP_OPERATION, ErrorCode.ILLEGAL_TFTP_OPERATION.getMessage());
			}
			
			else {
				short opcode = requestPacket.getOpcode();
				boolean validMode = validMode(requestPacket.getMode());

				System.out.printf("%s request for %s from %s using port %d on thread %d \n",
						(opcode == OP_RRQ) ? "Read" : "Write", requestPacket.getRequestedFile(),
						clientAddress.getHostName(), clientAddress.getPort(), Thread.currentThread().getId());

				// Read request
				if (opcode == OP_RRQ && validMode) {
					handleRRQ();
				}
				// Write request
				else if (opcode == OP_WRQ && validMode) {
					handleWRQ();
				}

				else if (!validMode) {
					System.err.println("TFTP request with unsupported mode recieved");
					sendErrorPacket(ErrorCode.NOT_DEFINED, "Unsupported mode");
				}

				sendSocket.close();
			}
			} catch (IOException e) {
				//If the program is unable to send the error packets or unknown file IO errors
				System.err.println("Unexpected socket error");
			}		
	}

	private void handleRRQ() throws IOException {
		FileInputStream fileReader = null;
		DatagramPacket inUDPPacket;
		DataPacket outboundDataPacket;
		AckPacket ackPack;
		short block = 1;

		try {
			// Sends error message if the file doesn't exist
			if (!fileExists(readDirectory)) {
				System.err.print("File not found");
				sendErrorPacket(ErrorCode.FILE_NOT_FOUND, ErrorCode.FILE_NOT_FOUND.getMessage());
				return;
			}

			long sentBytes = 0;
			long filesize = 0;

			File file = new File(readDirectory.getAbsolutePath() + "/" + requestPacket.getRequestedFile());
			
			try {
				fileReader = new FileInputStream(file);
			} catch (IOException e) {
				System.err.println("Cannot read file " + file.getAbsolutePath());
				sendErrorPacket(ErrorCode.ACCESS_VIOLATION, ErrorCode.ACCESS_VIOLATION.getMessage());
				return;
			}
			
			filesize = file.length();

			outboundDataPacket = new DataPacket(block, sendBuffer);
			byte[] readData = new byte[512];

			int retransmissions = 0;

			while (sentBytes < filesize && retransmissions < MAX_RETRANSMISSION) {
				int readBytes = readdDataToByteBuffer(readData, fileReader);

				outboundDataPacket.setBlock(block);
				outboundDataPacket.setData(readData, readBytes);

				sendTFTPPacket(outboundDataPacket);

				// Wait for ack. If no ack or wrong ack, retransmission
				boolean validAck = false;
				while (!validAck && retransmissions < MAX_RETRANSMISSION) {
					try {
						inUDPPacket = waitForPacket(RETRANSMISSION_TIMEOUT);

						// Checks the transferid, exists if it doesn't match
						if (!correctTransferID(inUDPPacket)) {
							sendErrorPacket(ErrorCode.UNKNOWN_TRANSFER_ID, ErrorCode.UNKNOWN_TRANSFER_ID.getMessage());
							fileReader.close();
							return;
						}

						try {
							ackPack = new AckPacket(recivedBuffer);
							validAck = validBlock(block, ackPack);
						} catch (IllegalArgumentException | BufferUnderflowException e) {
							validAck = false;
						}

						if (!validAck) {
							sendTFTPPacket(outboundDataPacket);
							retransmissions++;
						}

						// Sends packet again if no ack has been received
					} catch (SocketTimeoutException e) {
						sendTFTPPacket(outboundDataPacket);
						retransmissions++;
					}
				}
				
				if (retransmissions == MAX_RETRANSMISSION) {
					sendErrorPacket(ErrorCode.NOT_DEFINED, "Retransmission rate limit reached");
					fileReader.close();
					return;
				}

				retransmissions = 0;
				sentBytes += readBytes;
				
				//Because there are no unsigned shorts in java
				if (block == -1)
					block = 1;
				else
					block++;
			}

		} catch (IOException e) {
			System.err.println("Error while sending datagram.");
			sendErrorPacket(ErrorCode.NOT_DEFINED, "Unexpected socket error");
		}
	}

	private void handleWRQ() throws IOException {
		FileOutputStream fileWriter = null;
		DatagramPacket udpPacket = null;
		AckPacket outboundAckPacket;
		DataPacket dataPack = null;
		short block = 0;
		int transmissionAttempts = 0;

		try {
			// Sends error message if the file exists
			if (fileExists(writeDirectory)) {
				System.err.print("File already exists");
				sendErrorPacket(ErrorCode.FILE_ALREADY_EXISTS, ErrorCode.FILE_ALREADY_EXISTS.getMessage());
				return;
			}

			if (requestPacket.getRequestedFile().length() == 0) {
				System.err.println("No file specified in the write request");
				sendErrorPacket(ErrorCode.ILLEGAL_TFTP_OPERATION, "No file specified");
				return;
			}

			File file = new File(writeDirectory.getAbsolutePath() + "/" + requestPacket.getRequestedFile());

			try {
				fileWriter = new FileOutputStream(file, true);
			} catch (IOException e) {
				System.err.println("Cannot write to file " + file.getAbsolutePath());
				sendErrorPacket(ErrorCode.ACCESS_VIOLATION, ErrorCode.ACCESS_VIOLATION.getMessage());
				return;
			}

			outboundAckPacket = new AckPacket(block, sendBuffer);
			outboundAckPacket.setBlock(block);
			sendTFTPPacket(outboundAckPacket);
			block++;

			boolean endOfFile = false;
			while (!endOfFile && transmissionAttempts < MAX_RECEIVE_ATTEMPTS) {
				// Wait for data
				boolean expectedDatapacket = false;
				while (!expectedDatapacket) {
					try {
						udpPacket = waitForPacket(RECEIVE_TIMEOUT);

						// Checks the transferid, exists if it doesn't match
						if (!correctTransferID(udpPacket)) {
							sendErrorPacket(ErrorCode.UNKNOWN_TRANSFER_ID, ErrorCode.UNKNOWN_TRANSFER_ID.getMessage());
							fileWriter.close();
							return;
						}

						try {
							dataPack = new DataPacket(recivedBuffer, udpPacket.getLength());
							expectedDatapacket = validBlock(block, dataPack);
							
							//Acknowledge received data packet
							outboundAckPacket.setBlock(dataPack.getBlock());
							sendTFTPPacket(outboundAckPacket);
						} catch (IllegalArgumentException | BufferUnderflowException e) {
							// In case of broken package
							expectedDatapacket = false;
						}
					} catch (SocketTimeoutException e) {
						System.err.println("Data reciever timeout");
						sendErrorPacket(ErrorCode.NOT_DEFINED, "Data reciever timeout");
						fileWriter.close();
						return;
					}
				}

				// If expected data, increase block, write data to file
				if (expectedDatapacket) {
					transmissionAttempts = 0;
					
					//Because there are no unsigned shorts in java
					if (block == -1)
						block = 1;
					else
						block++;
					
					if (!canFitOnDisk(file, dataPack.getLength())) {
						System.err.println("Cannot write buffer to file, out of disk space");
						sendErrorPacket(ErrorCode.DISK_FULL, ErrorCode.DISK_FULL.getMessage());
						fileWriter.close();
						return;
					}

					try {
						fileWriter.write(dataPack.getData());
					} catch (IOException e) {
						System.err.println("Cannot write to file " + file.getAbsolutePath());

						if (!file.canWrite())
							sendErrorPacket(ErrorCode.ACCESS_VIOLATION, ErrorCode.ACCESS_VIOLATION.getMessage());
						else
							sendErrorPacket(ErrorCode.NOT_DEFINED, "Unexpected error when writing to file");

						fileWriter.close();
						return;
					}
				} else {
					transmissionAttempts++;
				}

				if (expectedDatapacket && udpPacket.getLength() < 516) {
					endOfFile = true;
				}
			}

			fileWriter.close();
		} catch (IOException e) {
			System.err.println("Error while sending datagram");
			sendErrorPacket(ErrorCode.NOT_DEFINED, "Unexpected socket error");
			fileWriter.close();
		}
	}

	// Sends data
	private DatagramPacket sendTFTPPacket(TFTPPacket packet) throws IOException {
		DatagramPacket outboundDatagramPacket = new DatagramPacket(packet.getBuffer(), 0, packet.getLength(),
				clientAddress);
		sendSocket.send(outboundDatagramPacket);

		return outboundDatagramPacket;
	}

	private void sendErrorPacket(ErrorCode errorcode, String message) throws IOException {
		ErrorPacket errorPacket = new ErrorPacket(errorcode, message, sendBuffer);
		sendTFTPPacket(errorPacket);
	}

	private boolean validMode(String mode) {
		String modeLowerCase = mode.toLowerCase();
		return modeLowerCase.contentEquals("octet");
	}

	private DatagramPacket waitForPacket(int timeoutTime) throws IOException, SocketTimeoutException {
		DatagramPacket packet = new DatagramPacket(recivedBuffer, recivedBuffer.length);

		sendSocket.setSoTimeout(timeoutTime);
		sendSocket.receive(packet);
		
		return packet;
	}

	// Checks to make sure the received datagramPacket contains a proper ACK packet
	// with the expected block
	private boolean validBlock(short expectedBlock, TFTPBlockPacket pack) {
		return pack.getBlock() == expectedBlock;
	}

	// Read bytes from file and appends them to a ByteBuffer
	private int readdDataToByteBuffer(byte[] buf, FileInputStream fileReader) throws IOException {
		int readBytes = 0;
		if (fileReader.available() > 0)
			readBytes = fileReader.read(buf);

		return readBytes;
	}

	private boolean fileExists(File directory) {
		try {
			Path path = Paths.get(directory.getAbsolutePath() + "/" + requestPacket.getRequestedFile());

			if (Files.notExists(path) && !Files.isDirectory(path))
				return false;
		} catch (InvalidPathException e) {
			return false;
		}

		return true;
	}

	private boolean canFitOnDisk(File file, int bytesToWrite) {
		if (file.getUsableSpace() < bytesToWrite) {
			return false;
		}

		return true;
	}

	// Checks the transferID ie port
	private boolean correctTransferID(DatagramPacket incomingPacket) {
		if (clientAddress.getPort() != incomingPacket.getPort()) {
			return false;
		}

		return true;
	}
}