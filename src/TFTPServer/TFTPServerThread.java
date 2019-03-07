package TFTPServer;

/*
 * Unix file names: spaces = 0 in bytes, breaks the protocol. Any tips?
 * 
 * TODO:
 * Implement ASCII mode.
 * Implement put
 * implement errors
 */

import java.io.File;
import java.io.FileInputStream;
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
	private static final int RETRANSMISSION_TIMEOUT = 5000; // 5 seconds in milliseconds
	private static final int MAX_RETRANSMISSION = 10;
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
			throws IllegalArgumentException, SocketException {

		this.clientAddress = clientAddress;
		readDirectory = readDir;
		writeDirectory = writeDir;
		requestPacket = new RRQWRQPacket(datagramBuffer);
		sendSocket = new DatagramSocket(0);
		sendBuffer = new byte[516];
		recivedBuffer = new byte[516];
	}

	public void run() {
		try {
			short opcode = requestPacket.getOpcode();
			boolean validMode = validMode(requestPacket.getMode());
					
			// Connect to client
			sendSocket.connect(clientAddress);

			System.out.printf("%s request for %s from %s using port %d on thread %d \n",
					(opcode == OP_RRQ) ? "Read" : "Write", requestPacket.getRequestedFile(),
					clientAddress.getHostName(), clientAddress.getPort(), Thread.currentThread().getId());

			// Read request
			if (opcode == OP_RRQ && validMode) {
				HandleRRQ();
			}
			// Write request
			else if (opcode == OP_WRQ && validMode) {

			}

			else {
				sendErrorPacket();
			}

			sendSocket.close();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		System.out.println("Exit: " + Thread.currentThread().getId());
	}

	/**
	 * Handles RRQ requests
	 */
	private void HandleRRQ() {
		boolean netasciiMode = false;
		DataPacket outboundDataPacket;
		short block = 1;
		sendBuffer = new byte[BUFSIZE];
		netasciiMode = isNetASCIIMode(requestPacket.getMode());

		// Sends error message if the file doesn't exist
		if (!fileExists()) {
			sendErrorPacket();
			return;
		}

		try {
			long sentBytes = 0;
			long filesize = 0;

			File file = new File(readDirectory.getAbsolutePath() + "/" + requestPacket.getRequestedFile());
			FileInputStream fileReader = new FileInputStream(file);
			filesize = file.length();

			outboundDataPacket = new DataPacket(block, sendBuffer);
			byte[] readData = new byte[512];

			int retransmissions = 0;

			while (sentBytes < filesize && retransmissions < MAX_RETRANSMISSION) {
				int readBytes = readdDataToByteBuffer(readData, fileReader);
				
				outboundDataPacket.setBlock(block);
				outboundDataPacket.setData(readData, readBytes);
				
				sendDataPacket(outboundDataPacket);

				// Wait for ack. If no ack or wrong ack, retransmission
				boolean validAck = false;
				while (!validAck && retransmissions < MAX_RETRANSMISSION) {
					try {
						waitForAck();
						validAck = validAck(block, recivedBuffer);

						if (!validAck) {
							sendDataPacket(outboundDataPacket);
							retransmissions++;
							System.out.println("Wrong ack, retransmitting...");
						}

					} catch (SocketTimeoutException e) {
						System.out.println("No ack, retransmitting...");
						sendDataPacket(outboundDataPacket);
					}
				}

				sentBytes += readBytes;
				block++;
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	// Sends data
	private DatagramPacket sendDataPacket(DataPacket dataPack) throws IOException {
		DatagramPacket outboundDatagramPacket = new DatagramPacket(dataPack.getBuffer(), 0, dataPack.getLength(),
				clientAddress);
		System.out.println(
				"Send package of size " + dataPack.getLength() + " on thread " + Thread.currentThread().getId());

		sendSocket.send(outboundDatagramPacket);

		return outboundDatagramPacket;
	}

	private boolean receive_DATA_send_ACK() {
		return true;
	}

	private void sendErrorPacket() {

	}
	
	private boolean isNetASCIIMode(String mode) {
		return mode.toLowerCase().contentEquals("netascii");	
	}
	
	private boolean validMode(String mode) {
		String modeLowerCase = mode.toLowerCase();
		return modeLowerCase.contentEquals("netascii") || modeLowerCase.contentEquals("octet");	
	}

	private void waitForAck() throws IOException, SocketTimeoutException {
		DatagramPacket ackPack = new DatagramPacket(recivedBuffer, recivedBuffer.length);

		System.out.println("Waiting for ack on thread " + Thread.currentThread().getId());
		sendSocket.setSoTimeout(RETRANSMISSION_TIMEOUT);
		sendSocket.receive(ackPack);
		System.out.println("Ack recieved on thread " + Thread.currentThread().getId());
	}

	// Checks to make sure the received datagramPacket contains a proper ACK packet
	// with the expected block
	private boolean validAck(short expectedBlock, byte[] datagramPacket) {
		try {
			AckPacket ackPack = new AckPacket(datagramPacket);

			if (ackPack.getBlock() != expectedBlock)
				return false;
		} catch (IllegalArgumentException | BufferUnderflowException e) {
			return false;
		}

		return true;
	}

	// Read bytes from file and appends them to a ByteBuffer
	private int readdDataToByteBuffer(byte[] buf, FileInputStream fileReader) throws IOException {

		int readBytes = 0;
		if (fileReader.available() > 0)
			readBytes = fileReader.read(buf);

		return readBytes;
	}

	private boolean fileExists() {
		System.out.println(requestPacket.getRequestedFile());
		try {
			Path path = Paths.get(readDirectory.getAbsolutePath() + "/" + requestPacket.getRequestedFile());

			if (Files.notExists(path) && !Files.isDirectory(path))
				return false;
		} catch (InvalidPathException e) {
			return false;
		}

		return true;
	}
}