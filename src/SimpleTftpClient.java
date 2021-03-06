import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	public String executeTftpCommand(String command) throws IOException {
		String[] cmdArgs = command.split(" ");
		switch (cmdArgs[0]) {
		case "connect":
			if (cmdArgs.length < 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: connect host-name [port]";
			} else if (cmdArgs.length ==2) {
				String hostname = cmdArgs[1];
				try {
					hostAddress = InetAddress.getByName(hostname);
				} catch (UnknownHostException uhe){
					return hostname + ": unknown host";
				}
				return "connected to: " +hostname;
			} else if (cmdArgs.length ==3) {
				String hostname = cmdArgs[1];
				port = Integer.parseInt(cmdArgs[2]);
				try {
					hostAddress = InetAddress.getByName(hostname);
				} catch (UnknownHostException uhe){
					return hostname + ": unknown host";
				}
				return "connected to: " +hostname;
			}
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
			} else {
				return putFile(cmdArgs[1]);
			}
		case "mode":
			if (cmdArgs.length != 2) {
				return "invalid number of arguments: " + (cmdArgs.length - 1) 
						+ "\nusage: mode ascii|binary";
			} else {
				if(cmdArgs[1].equalsIgnoreCase("ascii")) {
					mode=Mode.ASCII;
					return "Your mode is "+mode.name();
				}else if(cmdArgs[1].equalsIgnoreCase("binary")) {
					mode=Mode.BINARY;
					return "Your mode is "+mode.name();
				}else {
					return("invalid entry");
				}
			}
		case "help":
			return "connect: host-name [port] \nget: filename \nput: filename \nmode: ascii|binary \nquit: closes the client";
		case "quit":
			System.exit(0);
			return null;
		default:
			return "unrecognized command: " + cmdArgs[0];
		}
	}


	public String receiveFile(String filename) {


		if (hostAddress == null) return "unable to receive file: host not specified";

		sendBuffer.clear();

		sendBuffer.putShort(Opcode.RRQ);
		sendBuffer.put(filename.getBytes(StandardCharsets.US_ASCII));
		sendBuffer.put((byte) 0);

		if (mode == Mode.BINARY) {
			sendBuffer.put("octet".getBytes(StandardCharsets.US_ASCII));
		} else if (mode == Mode.ASCII) {
			sendBuffer.put("netascii".getBytes(StandardCharsets.US_ASCII));
		}
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

			//boolean flag for port pairing
			boolean portEstablished = false; 

			//boolean flag for netascii translation, gets set to true when the previous char symbol consumed was a carriage return
			boolean carriageReturnPrevious = false; 


			//get the file in a loop
			while(true) {
				recvBuffer.clear();
				recvPacket.setLength(516);
				sock.receive(recvPacket);
				int pktLength = recvPacket.getLength();

				//if port not set, set the port for the transaction
				if (!portEstablished) {
					sendPacket.setPort(recvPacket.getPort());
					portEstablished = true;
				}

				//get the opcode and switch on it
				short opcode = recvBuffer.getShort();
				switch(opcode) {
				case Opcode.DATA:
					short packetNum = recvBuffer.getShort();
					int dataLength = pktLength - recvBuffer.position();

					//write the remaining parts of the buffer (data) to file





					if (mode==Mode.ASCII) {
						byte character;
						while (recvBuffer.hasRemaining()) {
							
							character = recvBuffer.get();

							if (carriageReturnPrevious) {

								//if we read a linefeed, output the system newline encoding
								if (character == 0x0A) {
									outStream.write(System.getProperty("line.separator").getBytes(StandardCharsets.US_ASCII));

									//if we read a null char, output a lone carriage return
								} else if (character == 0x00) {
									outStream.write(0x0D);
								}

								carriageReturnPrevious = false;
								continue;

							}
							if (character == 0x0D) {
								carriageReturnPrevious = true;
								continue;
							} else {
								outStream.write((int) character);
								carriageReturnPrevious = false;
							}
						}

					} else if (mode==Mode.BINARY) {
						outStream.write(recvBuffer.array(), recvBuffer.position(), dataLength);
					}

					sendBuffer.clear();

					//ack the data packet
					sendBuffer.putShort(Opcode.ACK);
					sendBuffer.putShort(packetNum);
					sendPacket.setLength(4);

					sock.send(sendPacket);

					//if the data size was less than 512, it was the last packet
					//flush the stream and return
					if (dataLength < 512)
					{	
						outStream.flush();
						return "";
					}
					break;
				case Opcode.ERROR:
					outStream.flush();
					outStream.close();
					return getErrMsgFromBuffer(recvBuffer, pktLength);
				default:
					outStream.flush();
					outStream.close();
					return "Unrecognized packet opcode: "+opcode;
				}
			}
		} catch (IOException e) {
			return "IO error: " + e.getMessage();
		}
	}


	private static String getErrMsgFromBuffer(ByteBuffer buf, int pktLength) {
		buf.rewind();
		if (buf.getShort() != Opcode.ERROR) return "Error printing invoked on non-error packet";

		short errorCode = buf.getShort();
		byte[] msgBytes = new byte[pktLength-buf.position()];
		buf.get(msgBytes);
		try {
			String errMsg = new String(msgBytes, "US-ASCII");
			return String.format("error code %d: %s", errorCode, errMsg);
		} catch (UnsupportedEncodingException e) {
			return "Unsupported encoding: US-ASCII. Could not decode error msg";
		}	
	}


	public String putFile(String filename) throws IOException {
		if (hostAddress == null) return "unable to put file: host not specified";
		InputStream fis = null;
		File file =new File(filename); 

		try {
			fis = new FileInputStream(file); 

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
		}
		if (mode ==Mode.ASCII) {
			if (!System.getProperty("line.separator").equals( "\r\n")) {
				Path path = Paths.get(filename);
				String content = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
				content = content.replaceAll(System.getProperty("line.separator"), "\r\n");
				content = content.replaceAll("\r(?!\n)", "\r\0");
				fis = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII)); 
				
			}

			sendBuffer.clear();
			sendBuffer.putShort((short)2);
			sendBuffer.put(filename.getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);
			sendBuffer.put("ASCII".getBytes(StandardCharsets.US_ASCII));
			sendBuffer.put((byte) 0);

			sendPacket.setLength(sendBuffer.position());
			sendPacket.setAddress(hostAddress);
			sendPacket.setPort(port);
		}

		try {
			short block = 0;

			while(fis.available()>0) {
				try {

					int temp =fis.available();
					sock.send(sendPacket);

					recvBuffer.clear();
					recvPacket.setLength(516);

					sock.receive(recvPacket);
					while(recvBuffer.getShort()==Opcode.ACK && recvBuffer.getShort()!=block) {

						sock.send(sendPacket);
						recvBuffer.clear();
						sock.receive(recvPacket);
					}

					block++;

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
					sendPacket.setLength(bytearr.length+4);
					sendPacket.setPort(recvPacket.getPort());




				} catch (IOException e) {
					return "IO error: " + e.getMessage();
				}
				}
				sock.send(sendPacket);
				if (sendPacket.getLength()==516) {
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
