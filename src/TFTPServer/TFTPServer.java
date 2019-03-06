package TFTPServer;

/*
 * Comments:
 * 		Acks opcode and block is weird. Not what expected.
 */

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TFTPServer {
	// Maximum threads in the thread pool is number of CPUs + 1.
	private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors() + 1;
	public static final int BUFSIZE = 516;

	private final String readDirectory;
	private final String writeDirectory;
	private final int port;

	public TFTPServer(String readDir, String writeDir, int port) {
		this.port = port;
		readDirectory = readDir;
		writeDirectory = writeDir;
	}

	public void start() throws IOException {
		byte[] buf = new byte[BUFSIZE];
		ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(port);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", port);

		// TODO: Should everything be handled in the thread?
		// Loop to handle client requests
		while (true) {
			// Create datagram packet
			DatagramPacket receivedPacket = new DatagramPacket(buf, buf.length);

			try {
				// Blocks until a packet is received
				socket.receive(receivedPacket);
				threadPool.execute(new TFTPServerThread(receiveFrom(receivedPacket), buf, new File(readDirectory),
						new File(writeDirectory)));
			} catch (SocketException e) {
				System.err.println("Could not bind to ephemeral port");
			} catch (IOException e) {
				System.err.println("Could not recieve initial packet");
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}

		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or
	 * write).
	 * 
	 * @param socket (socket to read from)
	 * @param buf    (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 * @throws IllegalArgumentException
	 */
	private InetSocketAddress receiveFrom(DatagramPacket packet) throws IllegalArgumentException {
		// Get client address and port from the packet
		InetAddress ip = packet.getAddress();
		int port = packet.getPort();

		InetSocketAddress address = new InetSocketAddress(ip, port);

		return address;
	}
}