import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SimpleTftpClient {

	InetAddress hostAddress = null;
	int port = 69;
	ByteBuffer sendBuffer;
	ByteBuffer recvBuffer;
	DatagramSocket sock;
	DatagramPacket sendPacket;
	DatagramPacket recvPacket;
	Mode mode = Mode.BINARY;

	public SimpleTftpClient() {
		sendBuffer = ByteBuffer.allocate(516);
		recvBuffer = ByteBuffer.allocate(516);
		try {
			sock = new DatagramSocket();
		} catch (SocketException se) {
			System.err.println("Socket error: could not create datagram socket");
			System.exit(1);
		}
		sendPacket = new DatagramPacket(sendBuffer.array(), 0);
		recvPacket = new DatagramPacket(recvBuffer.array(), 0);
	}

	public static void main(String[] args) {
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
			return null;
		case "get":
			if (cmdArgs.length != 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: get filename";
			} else {
				return receiveFile(cmdArgs[1]);
			}
		case "put":
			if (cmdArgs.length != 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: get filename";
			}
			return null;
		case "mode":
			return null;
		case "help":
			return null;
		case "quit":
			System.exit(0);
			return null;
		default:
			return "unrecognized command: " + cmdArgs[0];
		}
	}

	public String receiveFile(String filename) {
		if (hostAddress == null) return "unable to receive file: host not specified";
		
		if (mode == Mode.BINARY) {
			sendBuffer.clear();
			sendBuffer.put(filename.getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			sendBuffer.put("octet".getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			
			sendPacket.setLength(sendBuffer.position());
			sendPacket.setAddress(hostAddress);
			sendPacket.setPort(port);
			
			try {
				sock.send(sendPacket);
				
				recvBuffer.clear();
				recvPacket.setLength(516);
				
				sock.receive(recvPacket);
				System.out.println(Arrays.toString(recvPacket.getData()));
				return ("we gotta packet boiiiisss");
				
			} catch (IOException e) {
				return "IO error: " + e.getMessage();
			}
			
			
			
		}
		
		
		return null;
	}
	
	public enum Mode {
		ASCII, BINARY
	}
}
