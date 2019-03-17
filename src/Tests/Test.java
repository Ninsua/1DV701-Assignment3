package Tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;

import org.junit.jupiter.api.BeforeEach;

class Test {

	@BeforeEach
	void setUp() throws Exception {

	}

	private byte[] getRRQbuf(String filename, String mode) {
		byte[] buf = new byte[50];
		ByteBuffer bb = ByteBuffer.wrap(buf);

		// Set up RRQ request
		short opcode = 1;

		bb.putShort(opcode);

		try {
			for (byte b : filename.getBytes("US-ASCII")) {
				bb.put(b);
			}

			bb.put((byte) 0);

			for (byte b : mode.getBytes())
				bb.put(b);
		} catch (UnsupportedEncodingException e) {
			fail();
		}

		bb.put((byte) 0);

		return buf;
	}

	private byte[] getWRQbuf(String filename, String mode) {
		byte[] buf = new byte[516];
		ByteBuffer bb = ByteBuffer.wrap(buf);

		// Set up RRQ request
		short opcode = 2;

		bb.putShort(opcode);

		try {
			for (byte b : filename.getBytes("US-ASCII")) {
				bb.put(b);
			}

			bb.put((byte) 0);

			for (byte b : mode.getBytes())
				bb.put(b);
		} catch (UnsupportedEncodingException e) {
			fail();
		}

		bb.put((byte) 0);

		return buf;
	}

	private byte[] getDatabuf(short block, String data) {
		byte[] buf = new byte[50];
		ByteBuffer bb = ByteBuffer.wrap(buf);

		// Set up RRQ request
		short opcode = 3;

		bb.putShort(opcode);
		bb.putShort(block);

		try {
			for (byte b : data.getBytes("US-ASCII")) {
				bb.put(b);
			}
		} catch (UnsupportedEncodingException e) {
			fail();
		}

		return buf;
	}

	private short getShortFromBuffer(int position, byte[] buf) throws BufferUnderflowException {
		ByteBuffer byteBuffer = ByteBuffer.wrap(buf);

		byteBuffer.position(position);
		return byteBuffer.getShort();
	}

	private String readStringFromBuffer(int index, byte[] buf) throws IllegalArgumentException {
		StringBuilder stringBuilder = new StringBuilder();
		ByteBuffer byteBuffer = ByteBuffer.wrap(buf);

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

	private byte[] getAckBuf(short block) {
		byte[] buf = new byte[20];
		ByteBuffer bb = ByteBuffer.wrap(buf);

		short opcode = 4;

		bb.putShort(opcode);

		bb.putShort(block);

		return buf;
	}

	private DatagramSocket setUpSocket(int port) throws IOException {
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(port);
		socket.bind(localBindPoint);

		return socket;
	}

	// Read bytes from file and appends them to a ByteBuffer
	private int readdDataToByteBuffer(byte[] buf, FileInputStream fileReader) throws IOException {
		int readBytes = 0;
		if (fileReader.available() > 0)
			readBytes = fileReader.read(buf);

		return readBytes;
	}

	@org.junit.jupiter.api.Test
	void testRetransmission() {
		byte[] rpBuf = new byte[516];
		byte[] rrqRequest;
		byte[] ackBuf;
		String expectedString = "Success!";
		String filename = "test.txt";
		String mode = "octet";

		rrqRequest = getRRQbuf(filename, mode);
		ackBuf = getAckBuf((short) 1);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(rrqRequest, 0, rrqLength, remoteAddress);

			// Send RRQ request
			socket.send(dp);

			// Receive data
			socket.receive(rp);
			System.out.println("Received data");

			// Receive retransmission data
			socket.receive(rp);
			System.out.println("Received retransmissiondata");

			// Send ack
			remoteAddress = rp.getSocketAddress();
			DatagramPacket ackPack = new DatagramPacket(ackBuf, 0, 4, remoteAddress);
			socket.send(ackPack);

		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 4; i < rpBuf.length; i++) {
			char c = (char) rpBuf[i];
			sb.append(c);
		}

		String result = sb.toString();
		result = result.trim();

		System.out.println(result);
		System.out.println(expectedString);

		assertEquals(expectedString, result);
	}

	@org.junit.jupiter.api.Test
	void testTimeout() {
		byte[] rpBuf = new byte[516];
		byte[] rrqRequest;
		String expectedString = "Retransmission rate limit reached";
		String filename = "test.txt";
		String mode = "octet";

		rrqRequest = getRRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(rrqRequest, 0, rrqLength, remoteAddress);

			// Send RRQ request
			socket.send(dp);

			// Exhaust retransmission attempts
			int attempts = 0;
			int maxAttemptsServer = 10;
			while (attempts <= maxAttemptsServer) {
				socket.receive(rp);
				System.out.println("Received data");
				attempts++;
			}

			// Get error package
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}
		String result = readStringFromBuffer(4, rp.getData());
		result = result.trim();

		System.out.println(result);
		System.out.println(expectedString);

		assertEquals(expectedString, result);
	}

	@org.junit.jupiter.api.Test
	void testFileNotFound() {
		byte[] rpBuf = new byte[516];
		byte[] rrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 1;
		String expectedString = "File not found";
		String filename = "doesNot.exist";
		String mode = "octet";

		rrqRequest = getRRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(rrqRequest, 0, rrqLength, remoteAddress);

			// Send RRQ request
			socket.send(dp);

			// Get error package
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);

	}

	@org.junit.jupiter.api.Test
	void testAccessViolation() {
		byte[] rpBuf = new byte[516];
		byte[] wrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 2;
		String expectedString = "Access violation";
		String filename = "doesNot.exist";
		String mode = "octet";

		wrqRequest = getWRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(wrqRequest, 0, rrqLength, remoteAddress);

			// Send WRQ request
			socket.send(dp);

			// Get errorcode
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);

	}

	@org.junit.jupiter.api.Test
	void testFileAlreadyExists() {
		byte[] rpBuf = new byte[516];
		byte[] wrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 6;
		String expectedString = "File already exists";
		String filename = "test.txt";
		String mode = "octet";

		wrqRequest = getWRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(wrqRequest, 0, rrqLength, remoteAddress);

			// Send WRQ request
			socket.send(dp);

			// Get errorcode
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);

	}

	@org.junit.jupiter.api.Test
	void testUnsupportedMode() {
		byte[] rpBuf = new byte[516];
		byte[] wrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 0;
		String expectedString = "Unsupported mode";
		String filename = "test.txt";
		String mode = "NAHB";

		wrqRequest = getWRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(wrqRequest, 0, rrqLength, remoteAddress);

			// Send WRQ request
			socket.send(dp);

			// Get errorcode
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);

	}

	@org.junit.jupiter.api.Test
	void testIllegalFTFPOperation() {
		byte[] rpBuf = new byte[516];
		byte[] wrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 4;
		String expectedString = "Illegal TFTP operation";
		String filename = "test.txt";
		String mode = "octet";

		wrqRequest = new byte[] { 0, 0, 5, 1, 8, 13, 127, 127, 2, 3, 3, 15, 86, 0, 84, 15, 48, 123, 1, 3 };

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(wrqRequest, 0, rrqLength, remoteAddress);

			// Send WRQ request
			socket.send(dp);

			// Get errorcode
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		System.out.println("expected code:" + expectedErrorcode + " actual code: " + actualErrorcode);

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);
	}

	@org.junit.jupiter.api.Test
	void testUnknownTransferID() {
		byte[] rpBuf = new byte[516];
		byte[] rrqRequest;
		byte[] ackBuf;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 5;
		String expectedString = "Unknown transfer ID";
		String filename = "assassin.mp3";
		String mode = "octet";

		rrqRequest = getRRQbuf(filename, mode);
		ackBuf = getAckBuf((short) 5);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);
		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);
			DatagramSocket socket2 = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(rrqRequest, 0, rrqLength, remoteAddress);

			// Send WRQ request
			socket.send(dp);

			// Get data block 1
			socket.receive(rp);

			// Send ack from wrong port
			remoteAddress = rp.getSocketAddress();
			DatagramPacket ackPack = new DatagramPacket(ackBuf, 0, 4, remoteAddress);

			socket2.connect(remoteAddress);
			socket2.send(ackPack);

			// Get error code
			socket.receive(rp);
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		System.out.println(expectedOpcode + " " + actualOpcodde);

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);
	}

	// @org.junit.jupiter.api.Test
	void testDiskfull() {
		byte[] rpBuf = new byte[516];
		byte[] wrqRequest;
		short expectedOpcode = (short) 5;
		short expectedErrorcode = (short) 3;
		String expectedString = "Disk full or allocation exceeded";
		String filename = "asd.mp3";
		String mode = "octet";

		wrqRequest = getWRQbuf(filename, mode);

		int rrqLength = 2 + filename.length() + mode.length() + 2;

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);

		// Create socket and file stuff
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost", 4970);
			DatagramSocket socket = setUpSocket(0);

			DatagramPacket dp = new DatagramPacket(wrqRequest, 0, rrqLength, remoteAddress);

			File file = new File("F:\\tftp test\\client\\" + filename);
			FileInputStream fileReader = fileReader = new FileInputStream(file);

			byte[] readData = wrqRequest;
			ByteBuffer bb = ByteBuffer.wrap(readData);
			
			// Send WRQ request
			socket.send(dp);

			long size = file.length();
			long bytesSent = 0;
			int readBytes = 0;

			short block = 1;

			boolean error = false;
			while (bytesSent < size && !error) {
				bb.position(0);
				bb.putShort((short) 3);
				bb.putShort(block);
				block++;

				readBytes = fileReader.read(readData, 4, 512);
				dp = new DatagramPacket(readData, 0, readBytes + 4, remoteAddress);

				socket.send(dp);

				bytesSent += readBytes;
				
				// Get ack/error
				socket.receive(rp);
				
				if (getShortFromBuffer(0, rp.getData()) == expectedOpcode) {
					error = true;
				}
			}
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}

		short actualOpcodde = getShortFromBuffer(0, rp.getData());
		short actualErrorcode = getShortFromBuffer(2, rp.getData());
		String actualString = readStringFromBuffer(4, rp.getData());

		actualString = actualString.trim();

		assertEquals(expectedOpcode, actualOpcodde);
		assertEquals(expectedErrorcode, actualErrorcode);
		assertEquals(expectedString, actualString);

	}
}
