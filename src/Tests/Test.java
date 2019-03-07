package Tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;

import org.junit.jupiter.api.BeforeEach;

class Test {

	@BeforeEach
	void setUp() throws Exception {
		
	}
	
	byte[] getRRQbuf(String filename) {
		byte[] buf = new byte[20];
		ByteBuffer bb = ByteBuffer.wrap(buf);
		
		//Set up RRQ request
		short opcode = 1;

		bb.putShort(opcode);
		
		try {
			for (byte b : filename.getBytes("US-ASCII")) {
				bb.put(b);
			}
		} catch (UnsupportedEncodingException e) {
			fail();
		}

		bb.put((byte) 0);
		
		return buf;
	}
	
	byte[] getAckBuf(short block) {
		byte[] buf = new byte[20];
		ByteBuffer bb = ByteBuffer.wrap(buf);
		
		short opcode = 4;

		bb.putShort(opcode);

		bb.putShort(block);
		
		return buf;
	}
	
	@org.junit.jupiter.api.Test
	void retransmission() {
		byte[] rpBuf = new byte[20];
		byte[] rrqRequest;
		byte[] ackBuf;
		String expectedString = "Success!";
		String filename = "test.txt";
		
		rrqRequest = getRRQbuf(filename);
		ackBuf = getAckBuf((short) 1);
		
		int rrqLength = 2+filename.length();

		DatagramPacket rp = new DatagramPacket(rpBuf, rpBuf.length);
		
		// Create socket
		try {
			SocketAddress remoteAddress = new InetSocketAddress("localhost",4970);
			DatagramSocket socket = new DatagramSocket(null);
			
			// Create local bind point
			SocketAddress localBindPoint = new InetSocketAddress(0);
			socket.bind(localBindPoint);
			
			DatagramPacket dp = new DatagramPacket(rrqRequest,0,rrqLength,remoteAddress);
			
			//Send RRQ request
			socket.send(dp);
			
			//Receive data
			socket.receive(rp);
			System.out.println("Received data");
			
			//Receive retransmission data
			socket.receive(rp);
			System.out.println("Received retransmissiondata");
			
			//Send ack
			remoteAddress = rp.getSocketAddress();
			DatagramPacket ackPack = new DatagramPacket(ackBuf,0,4,remoteAddress);
			socket.send(ackPack);
			
		} catch (IOException e) {
			System.out.println("Fail");
			fail();
		}
		
		StringBuilder sb = new StringBuilder();
		for (int i = 4; i < rpBuf.length;i++) {
			char c = (char) rpBuf[i];
			sb.append(c);
		}
		
		String result = sb.toString();
		result = result.trim();
		
		System.out.println(result);
		System.out.println(expectedString);
		
		assertEquals(expectedString,result);
	}

}
