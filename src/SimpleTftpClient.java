import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
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
	boolean acked = false;

	public SimpleTftpClient() {
		sendBuffer = ByteBuffer.allocate(516);
		recvBuffer = ByteBuffer.allocate(516);
		try {
			sock = new DatagramSocket();
		} catch (SocketException se) {
			System.err.println("Socket error: could not create datagram socket");
			System.err.flush();
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
				System.err.println(hostname + ": unknown host");
			}
			while (true) {
				System.out.print("tftp> ");
				line = input.readLine().trim();
				if (!line.isEmpty()) {
					System.out.println(client.executeTftpCommand(line));
				}
			}
		} catch (IOException ioe) {
			System.err.println("Error: " + ioe.getMessage());
			System.err.println("I/O exception occurred. Exiting.");
			System.err.flush();
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
						+ "\nusage: put filename";
			} else {
				return putFile(cmdArgs[1]);
			}
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

			sendBuffer.putShort((short) 1);
			sendBuffer.put(filename.getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			sendBuffer.put("octet".getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);

			sendPacket.setLength(sendBuffer.position());
			sendPacket.setAddress(hostAddress);
			sendPacket.setPort(port);
			
			FileOutputStream outStream;
			try {
				outStream = new FileOutputStream(new File(filename));
			} catch (FileNotFoundException fnfe) {
				return "Error when creating file: " + fnfe.getMessage();
			}
			try {
				sock.send(sendPacket);

				recvBuffer.clear();

				while(true) {
					sock.receive(recvPacket);
					int pktLength = recvPacket.getLength();
					switch(recvBuffer.getShort()) { //get the opcode and switch on it
					case Opcode.DATA:
						int dataLength = pktLength - recvBuffer.position();
						outStream.getChannel().write(recvBuffer); //write the remaining parts of the buffer (data) to file
						
						if (dataLength < 512) {
							return "\n";
						}
						break;
					case Opcode.ERROR:
						outStream.close();
						return getErrMsgFromBuffer(recvBuffer, pktLength);
					}
				}

			} catch (IOException e) {
				return "IO error: " + e.getMessage();
			}

		}


		return null; 
	}


	private static String getErrMsgFromBuffer(ByteBuffer buf, int pktLength) {
		buf.rewind();
		if (buf.getShort() != Opcode.ERROR) return "Error printing invoked on non-error packet";

		short errorCode = buf.getShort();
		byte[] msgBytes = new byte[pktLength-buf.position()];
		buf.get(msgBytes);
		try {
			String errMsg = new String(msgBytes, "US-ASCII");
			return String.format("Error %d: %s", errorCode, errMsg);
		} catch (UnsupportedEncodingException e) {
			return "Unsupported encoding: US-ASCII. Could not decode error msg";
		}	
	}


	
	public String putFile(String filename) {
		if (hostAddress == null) return "unable to put file: host not specified";
		FileInputStream fis = null;

		
		try {
			 fis = new FileInputStream(filename);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (mode == Mode.BINARY) {
			sendBuffer.clear();
			sendBuffer.putShort((short)2);
			sendBuffer.put(filename.getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			sendBuffer.put("octet".getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			
			sendPacket.setLength(sendBuffer.position());
			sendPacket.setAddress(hostAddress);
			sendPacket.setPort(port);
			
			try {
				short block = 0;
				while(fis.available()>0) {
				try {
					
					int temp =fis.available();
					sock.send(sendPacket);
					
					recvBuffer.clear();
					recvPacket.setLength(516);
					
					sock.receive(recvPacket);
					System.out.println(Arrays.toString(recvPacket.getData()));
					
					System.out.println(block);
					block++;
					
					System.out.println(fis.available());
					if (temp>512) {
						temp=512;
					}
						byte[] bytearr = new byte[temp];

					
					fis.read(bytearr);
					
					sendBuffer.clear();
					sendBuffer.putShort((short)3);
					sendBuffer.putShort(block);
					for (int n=0; n<bytearr.length;n++) {
					sendBuffer.put(bytearr[n]);}
					System.out.println("bytearr: "+bytearr.length);
					sendPacket.setLength(bytearr.length+4);
					sendPacket.setPort(recvPacket.getPort());
					
					
					//return ("we put some data boiiiisss");
					
				
				} catch (IOException e) {
					return "IO error: " + e.getMessage();
				}}
				sock.send(sendPacket);
				if (sendPacket.getLength()==516) {
					sock.receive(recvPacket);
					sendBuffer.clear();
					sendBuffer.putShort((short)3);
					block++;
					//if (block<255) {
					//sendBuffer.put((byte) 0);}
					sendBuffer.putShort(block);
					//sendBuffer.put((byte) block);
					sendPacket.setLength(4);
					sendPacket.setPort(recvPacket.getPort());
					sock.send(sendPacket);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			
		}
		
		
		return null;
	}
	public enum Mode {
		ASCII, BINARY
	}

	public static final class Opcode {
		public static final short RRQ = 1;
		public static final short WRQ = 2;
		public static final short DATA = 3;
		public static final short ACK = 4;
		public static final short ERROR = 5;
	}




}
