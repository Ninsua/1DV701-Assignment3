package TFTPServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProgramStart {
	public static final int DEFAULT_TFTPPORT = 4970;

	public static void main(String[] args) {
		int port = DEFAULT_TFTPPORT;

		// Check arguments
		if (args.length < 2) {
			System.err.println("usage: read_directory write_directory port");
			System.exit(1);
		}

		if (!validDirPath(args[0])) {
			System.err.println("Provided read directory does not exist or is not a directory");
			System.exit(1);
		}

		if (!validDirPath(args[1])) {
			System.err.println("Provided write directory does not exist or is not a directory");
			System.exit(1);
		}

		try {
			if (validPort(args[2]))
				port = Integer.parseInt(args[2]);
			else
				printInvalidPortErrorMsg();
		} catch (IndexOutOfBoundsException | NumberFormatException e) {
			printInvalidPortErrorMsg();
		}

		// Starting the server
		try {
			TFTPServer server = new TFTPServer(args[0], args[1], port);
			server.start();
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	// Makes sure given path exists and is a directory
	private static boolean validDirPath(String pathString) {
		Path path = Paths.get(pathString);
		if (Files.notExists(path) || !Files.isDirectory(path))
			return false;

		return true;
	}

	// Makes a basic check to see if provided port is valid or not
	private static boolean validPort(String port) {
		try {
			int portAsInteger = Integer.parseInt(port);
			if (portAsInteger <= 0 || portAsInteger > 65535) // Port cannot be less than 0 or more than 65535
				return false;
		} catch (NumberFormatException e) {
			return false; // If the port is not parsable to int, valid = false.
		}
		return true;
	}

	private static void printInvalidPortErrorMsg() {
		System.err.println("Invalid port detected. Binding to default port: " + DEFAULT_TFTPPORT);
	}
}
