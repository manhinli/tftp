import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Basic TFTP Server, adhering to RFC1350.
 * 
 * @author James Li
 */
public class Server {
	public static void main(String[] args) {
		IncomingPacket requestPacket;

		List<ServerSession> activeSessions = new ArrayList<ServerSession>();
		Iterator<ServerSession> activeSessionsIterator;

		boolean addrTIDClash;
		String addrTIDPair;

		InetAddress addr;
		int port;

		// Arguments parser
		ArgumentsParser argsParser = new ArgumentsParser()
				.register("port", ArgumentsParser.type.INT, Integer.toString(Constants.defaultServerTID)) // Port
				.register("timeout", ArgumentsParser.type.INT, Integer.toString(Constants.defaultOperationTimeout)) // Timeout
				.register("attempts", ArgumentsParser.type.INT, Integer.toString(Constants.defaultMaxOperationAttempts)) // Max
																															// attempts
				.registerBoolean("enable-error-message-delivery") // Error msg
																	// delivery
				.registerBoolean("disable-block-messages"); // Disables standard
															// block ack
															// messages

		// Parse what we can
		args = argsParser.munch(args);

		try {
			// Start
			System.out.println("TFTP Server starting");
			
			// Set up port
			DatagramSocket welcomeSocket;
			
			try {
				welcomeSocket = new DatagramSocket(argsParser.readInt("port"));
			} catch (SocketException e) {
				System.err.println(String.format(Constants.strings.SOCKET_PORT_X_SETUP_FAILED, argsParser.readInt("port")));
				return;
			}

			System.out.println(String.format(Constants.strings.SOCKET_PORT_X_LISTENING, welcomeSocket.getLocalPort()));
			
			// Main loop
			while (true) {
				// Receive initial request
				requestPacket = new IncomingPacket(welcomeSocket).receive().process();

				// See if same addr-TID pair is active
				activeSessionsIterator = activeSessions.iterator();
				addrTIDClash = false;

				addr = requestPacket.getDatagramPacket().getAddress();
				port = requestPacket.getDatagramPacket().getPort();

				addrTIDPair = addr.getHostAddress() + ":" + port;

				// Go over the list to see if the same addr-TID pair is in there
				while (activeSessionsIterator.hasNext()) {
					ServerSession activeSession = activeSessionsIterator.next();

					// Cleanup expired sessions from the list
					if (!activeSession.isSessionActive()) {
						activeSessionsIterator.remove();
						continue;
					}

					// Check
					if (!addrTIDClash && addrTIDPair.equals(activeSession.getAddrTID())) {
						addrTIDClash = true;
						continue; // Continue so that we go through the whole
									// list to clean up any old ones
					}
				}

				// If clash, respond with ERROR
				if (addrTIDClash) {
					System.err.println(
							"Address-TID pair clashing request from '" + addrTIDPair + "'; replying with ERROR");

					new OutgoingPacket(welcomeSocket, addr, port) //
							.addOpcode(Packet.opcode.ERROR) //
							.addErrorCode(Packet.errcode.NOT_DEFINED) //
							.addString("") //
							.addNullByte() //
							.send();

					System.err.println(String.format(Constants.strings.PACKET_TYPE_X_SENT, "ERROR"));

					continue;
				}

				// Create new session to handle requestor if everything is fine
				// so far
				ServerSession session = new ServerSession(argsParser, requestPacket);
				session.start();
				activeSessions.add(session);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class ServerSession extends Session {

	/*
	 * (non-Javadoc)
	 * 
	 * @see Session#begin()
	 */
	@Override
	Session begin() throws Exception {
		// Interpret the incoming request type, and set up session
		short opcode = this.getInPacket().getOpcode();

		switch (opcode) {
		case Packet.opcode.RRQ:
			this.setMode(this.getInPacket().getMode()) //
					.setupSessionForReadingFromLocal(true, opcode, this.getInPacket().getFilename());

			break;

		case Packet.opcode.WRQ:
			this.setMode(this.getInPacket().getMode()) //
					.setupSessionForWritingToLocal(true, opcode, this.getInPacket().getFilename());
			break;

		default:
			throw new Exception(Constants.strings.INVALID_REQUEST);
		}

		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Session#processInPacket()
	 */
	@Override
	Session processInPacket() throws Exception {
		return this.commonProcessInPacket(Packet.opcode.RRQ, Packet.opcode.WRQ);
	}

	/**
	 * @param argsParser
	 *            Arguments passed to the program
	 * @param incomingPacket
	 *            First incoming packet from the client
	 * @throws Exception
	 */
	ServerSession(ArgumentsParser argsParser, IncomingPacket incomingPacket) throws Exception {
		this.setInPacket(incomingPacket) // Initial packet
				.setDeliveryInfo() // Both address and port are set via. init
									// packet
				.setAddrTID() // Set the Addr-TID pair
				.setTimeout(argsParser.readInt("timeout")) // Timeout
				.setMaxAttempts(argsParser.readInt("attempts")) // Max
																// attempts
				// Encoding is set in #begin() as we need to be able to handle
				// exceptions raised from reading the mode string
				.print(String.format(Constants.strings.COMM_WITH_CLIENT_OVER_IP_X_PORT_X,
						this.getDeliveryAddress().getHostAddress(), this.getDeliveryPort()));

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