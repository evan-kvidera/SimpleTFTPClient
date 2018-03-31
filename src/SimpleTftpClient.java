import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class SimpleTftpClient {

	InetAddress hostAddress;
	ByteBuffer bb;
	DatagramSocket sock;
	DatagramPacket packet;

	public SimpleTftpClient() {
		bb = ByteBuffer.allocate(516);

		try {
			sock = new DatagramSocket();
		} catch (SocketException se) {
			System.err.println("Socket error: could not create datagram socket");
			System.exit(1);
		}
		packet = new DatagramPacket(bb.array(), 0);	
	}

	public static void main() {
		SimpleTftpClient client = new SimpleTftpClient();
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String line;
		try {
			System.out.print("(to) ");
			String hostname = input.readLine().trim();
			try {
				client.hostAddress = InetAddress.getByName(hostname);
			} catch (UnknownHostException uhe){
				System.err.println("Error: " + uhe.getMessage());
				System.err.println(hostname + ": unknown host");
			}
			while (true) {
				System.out.print("tftp> ");
				line = input.readLine();
				System.out.println(client.executeTftpCommand(line));
			}
		} catch (IOException ioe) {
			System.err.println("Error: " + ioe.getMessage());
			System.err.println("I/O exception occurred. Exiting.");
			System.exit(1);
		}
	}
	
	public String executeTftpCommand(String command) {
		String[] cmdArgs = command.split(" ");
		switch (cmdArgs[0]) {
		case "connect":
			break;
		case "get":
			if (cmdArgs.length != 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: get filename";
			} else {
				return getFile(cmdArgs[1]);
			}
			break;
		case "put":
			if (cmdArgs.length != 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: get filename";
			}
			break;
		case "mode":
			break;
		case "help":
			break;
		case "quit":
			System.exit(0);
			break;
		default:
			return "unrecognized command: " + cmdArgs[0];
		}
		}
	}

	public String getFile(String filename) {
		
	}


}
