import java.io.File;
import java.net.InetAddress;

/**
 * Basic TFTP Client, adhering to RFC1350.
 * 
 * @author James Li
 */
public class Client {
	public static void main(String[] args) {
		// Arguments parser
		ArgumentsParser argsParser = new ArgumentsParser()
				.register("port", ArgumentsParser.type.INT, Integer.toString(Constants.defaultServerTID)) // Port
				.register("timeout", ArgumentsParser.type.INT, Integer.toString(Constants.defaultOperationTimeout)) // Timeout
				.register("attempts", ArgumentsParser.type.INT, Integer.toString(Constants.defaultMaxOperationAttempts)) // Max
																															// attempts
				.register("mode", ArgumentsParser.type.STRING, Constants.defaultEncoding) // Encoding
				.registerBoolean("enable-error-message-delivery") // Error msg
																	// delivery
				.registerBoolean("disable-block-messages"); // Disables standard
															// block ack
															// messages

		// Parse what we can
		args = argsParser.munch(args);

		// There should be exactly 4 unmunched arguments
		// <host> {get|put} <source> <dest>
		if (args.length != 4) {
			System.err.println(Constants.strings.INSUFFICIENT_ARGS);
			return;
		}

		// The remaining 4 are fixed in order
		String inputAddress = args[0];
		String inputType = args[1].toLowerCase();
		String file1 = args[2];
		String file2 = args[3];

		String localFile;
		String remoteFile;

		try {
			// Attempt to convert inputs into what we need
			InetAddress address = InetAddress.getByName(inputAddress);
			short outgoingRequestType = Packet.opcode.ERROR;

			File file;
			
			switch (inputType) {
			case "get":
				// Read file from remote A -> local B
				outgoingRequestType = Packet.opcode.RRQ;
				remoteFile = file1;
				localFile = file2;
				
				// Check if file exists
				file = new File(localFile);
				
				if (file.exists()) {
					throw new Exception(Packet.errstr[Packet.errcode.FILE_EXISTS]);
				}
				
				break;

			case "put":
				// Write file from local A -> remote B
				outgoingRequestType = Packet.opcode.WRQ;
				localFile = file1;
				remoteFile = file2;
				
				// Check if file does not exist
				file = new File(localFile);
				
				if (!file.exists()) {
					throw new Exception(Packet.errstr[Packet.errcode.FILE_NOT_FOUND]);
				}
				
				break;

			default:
				System.err.println(Constants.strings.INVALID_REQUEST_TYPE_ARG);
				return;
			}

			
			
			
			
			
			// Start here
			System.out.println("TFTP Client starting");
			new ClientSession(argsParser, address, outgoingRequestType, localFile, remoteFile).run();

		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
}

/**
 * Client-relevant implementation of `Session`.
 * 
 * @see Session.
 */
class ClientSession extends Session {
	private short outgoingRequestType;
	private String localFile;
	private String remoteFile;
	private boolean switchedRemotePort;

	/**
	 * Sends client-side initial request packet to the server.
	 * 
	 * @return `OutgoingPacket` instance
	 * @throws Exception
	 */
	private OutgoingPacket sendInitialRequestPacket() throws Exception {
		// In the format of a RRQ/WRQ packet
		return this.createOutPacket() //
				.addOpcode(this.outgoingRequestType) // RRQ/WRQ opcode
				.addString(this.remoteFile) // Filename
				.addNullByte() //
				.addString(this.getMode()) // Encoding
				.addNullByte() //
				.send();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Session#startSession()
	 */
	@Override
	Session begin() throws Exception {
		// Interpret the outgoing request type, set up session, and send the
		// initial request packet
		switch (this.outgoingRequestType) {
		case Packet.opcode.RRQ:
			this.setupSessionForWritingToLocal(false, this.outgoingRequestType, this.localFile);
			this.sendInitialRequestPacket();
			this.print(String.format(Constants.strings.REQUEST_REMOTE_FILE_X_READ_VIA_MODE_X, this.remoteFile,
					this.getMode()));

			break;

		case Packet.opcode.WRQ:
			this.setupSessionForReadingFromLocal(false, this.outgoingRequestType, this.localFile);
			this.sendInitialRequestPacket();
			this.print(String.format(Constants.strings.REQUEST_REMOTE_FILE_X_WRITE_VIA_MODE_X, this.remoteFile,
					this.getMode()));

			break;

		default:
			throw new Exception(Constants.strings.INVALID_REQUEST);
		}

		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Session#processPacket()
	 */
	@Override
	Session processInPacket() throws Exception {
		if (!switchedRemotePort) {
			// This is the session's port
			this.setDeliveryPort(this.getInPacket().getDatagramPacket().getPort()).print(String.format(
					Constants.strings.CLIENT_SWITCH_REMOTE_PORT_X, this.getInPacket().getDatagramPacket().getPort()));
			this.switchedRemotePort = true;
		}

		return this.commonProcessInPacket(Packet.opcode.WRQ, Packet.opcode.RRQ);
	}

	/**
	 * @param argsParser
	 *            Arguments passed to the program
	 * @param address
	 *            Destination server address
	 * @param outgoingRequestType
	 *            Outgoing request type
	 * @param localFile
	 *            Local filename
	 * @param remoteFile
	 *            Remote filename
	 * @throws Exception
	 */
	ClientSession(ArgumentsParser argsParser, InetAddress address, short outgoingRequestType, String localFile,
			String remoteFile) throws Exception {
		this.outgoingRequestType = outgoingRequestType;
		this.localFile = localFile;
		this.remoteFile = remoteFile;

		this.switchedRemotePort = false;

		this.setDeliveryAddress(address) // Address
				.setDeliveryPort(argsParser.readInt("port")) // Port
				.setTimeout(argsParser.readInt("timeout")) // Timeout
				.setMaxAttempts(argsParser.readInt("attempts")) // Max
																// attempts
				// Encoding is set prior to connection pull up
				.setMode(argsParser.readString("mode"))
				.print(String.format(Constants.strings.COMM_WITH_SERVER_OVER_IP_X_PORT_X_VIA_MODE_X,
						this.getDeliveryAddress().getHostAddress(), this.getDeliveryPort(), this.getMode()));

		// If error message delivery is enabled
		if (argsParser.readBool("enable-error-message-delivery")) {
			this.enableExceptionMessageDelivery();
		}

		// If std block ack messages are enabled
		if (argsParser.readBool("disable-block-messages")) {
			this.disableBlockAckMessagePrinting();
		}
	}
}