import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * Abstract class for either server or client sessions.
 * <p>
 * This is threaded, so implementations can use {@link #start()} to start it in
 * a new thread or run it normally by executing {@link #run()}.
 * 
 * @author James Li
 */
abstract class Session extends Thread {
	// About self
	private boolean sessionActive;
	private short sessionType;

	// Options
	private int timeout = Constants.defaultOperationTimeout;
	private int maxAttempts = Constants.defaultMaxOperationAttempts;
	private boolean sendExceptionMessage = false;
	private boolean disableBlockAckMessages = false;

	// Socket and packets
	private DatagramSocket socket;
	private int ownPort;
	private IncomingPacket inPacket = null;
	private OutgoingPacket outPacket = null;

	// Delivery and addressing
	private InetAddress deliveryAddress;
	private int deliveryPort;
	private String addrTIDPair = null;

	// File stuff
	private String filename;
	private String fileMode;
	private FileReader fileReader;
	private FileWriter fileWriter;
	private byte[] fileBuffer;
	private int fileBufferSize;

	// Block number
	private BlockNumber blockNumber;

	// Operation variables
	private int packetSendRetryCount;
	private int timeoutCount;
	private boolean hasRespondedWithReadPrior = false;

	// Properties and internal operation

	/**
	 * Sets the delivery address.
	 * 
	 * @param address
	 *            `InetAddress`
	 * @return This object
	 */
	Session setDeliveryAddress(InetAddress address) {
		this.deliveryAddress = address;
		return this;
	}

	/**
	 * Sets the delivery port.
	 * 
	 * @param port
	 *            Port number
	 * @return This object
	 */
	Session setDeliveryPort(int port) {
		this.deliveryPort = port;
		return this;
	}

	/**
	 * Sets delivery information from the already stored incoming packet.
	 * 
	 * @return This object
	 */
	Session setDeliveryInfo() {
		// Obtain the sender IP and port for the session, reuse the originating
		// port for replies
		DatagramPacket datagramPacket = this.getInPacket().getDatagramPacket();
		return this.setDeliveryAddress(datagramPacket.getAddress()).setDeliveryPort(datagramPacket.getPort());
	}

	/**
	 * Gets the delivery address.
	 * 
	 * @return Delivery address
	 */
	InetAddress getDeliveryAddress() {
		return this.deliveryAddress;
	}

	/**
	 * Gets the delivery port.
	 * 
	 * @return Delivery port
	 */
	int getDeliveryPort() {
		return this.deliveryPort;
	}

	/**
	 * Sets the Address-TID pair string.
	 * <p>
	 * Delivery information must be set prior.
	 * 
	 * @return This object
	 */
	Session setAddrTID() {
		// Simply "IP:Port" - used to prevent Addr-TID clashes
		this.addrTIDPair = this.getDeliveryAddress().getHostAddress() + ":" + this.getDeliveryPort();
		return this;
	}

	/**
	 * Gets the Address-TID pair string.
	 * 
	 * @return Address-TID pair string
	 */
	String getAddrTID() {
		return this.addrTIDPair;
	}

	/**
	 * Gets the packet send retry count.
	 * 
	 * @return Packet send retry count
	 */
	private int getPacketSendRetryCount() {
		return this.packetSendRetryCount;
	}

	/**
	 * Increments the timeout count.
	 * 
	 * @return This object
	 */
	private Session incrPacketSendRetryCount() {
		++this.packetSendRetryCount;
		return this;
	}

	/**
	 * Resets the timeout count.
	 * 
	 * @return This object
	 */
	private Session resetPacketSendRetryCount() {
		this.packetSendRetryCount = 0;
		return this;
	}

	/**
	 * Gets the timeout count.
	 * 
	 * @return Timeout count
	 */
	private int getTimeoutCount() {
		return this.timeoutCount;
	}

	/**
	 * Increments the timeout count.
	 * 
	 * @return This object
	 */
	private Session incrTimeoutCount() {
		++this.timeoutCount;
		return this;
	}

	/**
	 * Resets the timeout count.
	 * 
	 * @return This object
	 */
	private Session resetTimeoutCount() {
		this.timeoutCount = 0;
		return this;
	}

	/**
	 * Enables exception messages to be delivered with ERROR packets.
	 * 
	 * @return This object
	 */
	Session enableExceptionMessageDelivery() {
		this.sendExceptionMessage = true;
		return this;
	}

	/**
	 * Sets timeout to specified value.
	 * 
	 * @param timeout
	 *            Timeout in milliseconds
	 * @return This object
	 */
	Session setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * Sets maximum number of attempts of a single operation.
	 * 
	 * @param maxAttempts
	 *            Maximum number of attempts
	 * @return This object
	 */
	Session setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
		return this;
	}

	/**
	 * Disables printing of block ACK and DATA lines to STDOUT.
	 * <p>
	 * Other messages are not affected.
	 * 
	 * @return This object
	 */
	Session disableBlockAckMessagePrinting() {
		this.disableBlockAckMessages = true;
		return this;
	}

	// Utils

	/**
	 * Prints a message.
	 * 
	 * @param error
	 *            `true` to print to STDERR, otherwise STDOUT
	 * @param msg
	 *            Message to print
	 * @return This object
	 */
	private Session print(boolean error, String msg) {
		String outmsg = "[" + this.ownPort + "] " + msg;

		if (error) {
			// STDERR
			System.err.println(outmsg);

		} else {
			// STDOUT
			System.out.println(outmsg);
		}

		return this;
	}

	/**
	 * Prints a message to STDOUT.
	 * 
	 * @param msg
	 *            Message to print
	 * @return This object
	 */
	Session print(String msg) {
		return this.print(false, msg);
	}

	// Sockets

	/**
	 * Opens the session's socket.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session openSocket() throws Exception {
		// Receiver communicates with sender over new, random TID for remainder
		// of session
		// DatagramSocket will automatically attach to available ephemeral port
		// on the machine
		this.socket = new DatagramSocket();
		this.socket.setSoTimeout(this.timeout); // Timeout

		this.ownPort = this.socket.getLocalPort();

		return this.print(String.format(Constants.strings.SOCKET_LOCAL_PORT_X_OPEN, this.ownPort));
	}

	/**
	 * Closes the session's socket.
	 * 
	 * @return This object
	 */
	private Session closeSocket() {
		this.socket.close();
		return this.print(Constants.strings.SOCKET_CLOSED);
	}

	// Session

	/**
	 * Sets session up for writing local files.
	 * 
	 * @param isServer
	 *            Indicates if the session instance is running on a server
	 *            (otherwise, client)
	 * @param opcode
	 *            Session type to be set
	 * @param localFile
	 *            Local filename
	 * @return This object
	 * @throws IOException
	 */
	Session setupSessionForWritingToLocal(boolean isServer, short opcode, String localFile) throws Exception {
		// Set the opcode, and initialise block number and file
		this.setSessionType(opcode) //
				.initBlockNumber() //
				.setFilename(localFile) //
				.createFile();

		if (isServer) {
			return this
					.print("Client requested write to local file '" + localFile + "' with mode '" + this.getMode()
							+ "'") //
					.sendACK(Constants.strings.OUT_PACKET_INIT_ACK);
		}

		return this.print(
				"Requested read from server to local file '" + localFile + "' with mode '" + this.getMode() + "'");
	}

	/**
	 * Sets session up for reading local files.
	 * 
	 * @param isServer
	 *            Indicates if the session instance is running on a server
	 *            (otherwise, client)
	 * @param opcode
	 *            Session type to be set
	 * @param localFile
	 *            Local filename
	 * @return This object
	 * @throws Exception
	 */
	Session setupSessionForReadingFromLocal(boolean isServer, short opcode, String localFile) throws Exception {
		// Set the opcode, and initialise block number and file
		this.setSessionType(opcode) //
				.initBlockNumber() //
				.setFilename(localFile) //
				.openFile();

		if (isServer) {
			// Immediately read and send data back to client
			return this
					.print("Client requested read from local file '" + localFile + "' with mode '" + this.getMode()
							+ "'") //
					.readAndReply(false);
		}

		return this.print(
				"Requested write to server from local file '" + localFile + "' with mode '" + this.getMode() + "'");

	}

	/**
	 * Abstract method containing instance-specific session initialisation code,
	 * generally processing the request type and utilising the appropriate
	 * session set up method.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	abstract Session begin() throws Exception;

	/**
	 * Cleans up session, ready for termination.
	 * 
	 * @return This object
	 */
	private Session end() {
		return this.closeFile() //
				.closeSocket() //
				.setSessionInactive() //
				.print(Constants.strings.SESSION_ENDED);
	}

	/**
	 * Resets session internal variables.
	 * 
	 * @return This object
	 */
	private Session reset() {
		return this.setSessionInactive() //
				.resetPacketSendRetryCount() //
				.resetTimeoutCount() //
				.setSessionType((short) 0) //
				.setFilename(null) //
				.nullFileBuffer();
	}

	/**
	 * Sets session active.
	 * 
	 * @return This object
	 */
	private Session setSessionActive() {
		this.sessionActive = true;
		return this;
	}

	/**
	 * Sets session inactive.
	 * 
	 * @return This object
	 */
	private Session setSessionInactive() {
		this.sessionActive = false;
		return this;
	}

	/**
	 * Returns if session is active or not.
	 * 
	 * @return Session active state
	 */
	boolean isSessionActive() {
		return this.sessionActive;
	}

	/**
	 * Sets the session type.
	 * 
	 * @param sessionType
	 *            Request opcode of the session type
	 * @return This object
	 */
	private Session setSessionType(short sessionType) {
		this.sessionType = sessionType;
		return this;
	}

	/**
	 * Gets the session type.
	 * 
	 * @return Request opcode of the session type
	 */
	short getSessionType() {
		return this.sessionType;
	}

	/**
	 * Checks if the transmission mode is supported.
	 * 
	 * @param mode
	 *            Mode string
	 * @return `true` if mode is supported; otherwise `false`
	 */
	private boolean isModeSupported(String mode) {
		// Permit only netascii and octet
		switch (mode.toLowerCase()) {
		case Packet.modestr.NETASCII:
		case Packet.modestr.OCTET:
			return true;
		}

		return false;
	}

	/**
	 * Sets transmission mode of the session.
	 * <p>
	 * Throws exception when not supported.
	 * 
	 * @param mode
	 *            Mode string
	 * @return This object
	 * @throws Exception
	 */
	Session setMode(String mode) throws Exception {
		// Convert to lowercase, and then check validity before saving
		mode = mode.toLowerCase();

		if (this.isModeSupported(mode)) {
			this.fileMode = mode;
		} else {
			throw new Exception(String.format(Constants.strings.MODE_X_NOT_SUPPORTED, mode));
		}

		return this;
	}

	/**
	 * Gets the transmission mode.
	 * 
	 * @return Mode string
	 */
	String getMode() {
		return this.fileMode;
	}

	// Block number

	/**
	 * Initialises the block number to a new instance.
	 * 
	 * @return This object
	 */
	private Session initBlockNumber() {
		this.blockNumber = new BlockNumber();
		return this;
	}

	/**
	 * Increments the block number.
	 * 
	 * @return This object
	 */
	private Session incrBlockNumber() {
		this.blockNumber.incr();
		return this;
	}

	// Packet sending and handling

	/**
	 * Stores the incoming packet.
	 * 
	 * @param packet
	 *            Incoming packet
	 * @return This object
	 */
	Session setInPacket(IncomingPacket packet) {
		this.inPacket = packet;
		return this;
	}

	/**
	 * Gets the incoming packet.
	 * 
	 * @return `IncomingPacket` instance
	 */
	IncomingPacket getInPacket() {
		return this.inPacket;
	}

	/**
	 * Accepts the next incoming request and stores and processes the incoming
	 * packet to this session.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session storeInPacket() throws Exception {
		// Receives and processes the incoming packet, so that we can extract
		// the info inside later
		IncomingPacket newPacket = new IncomingPacket(this.socket).receive().process();
		this.setInPacket(newPacket);
		return this;
	}

	/**
	 * Abstract method for instance-specific packet processing code.
	 * <p>
	 * {@link #commonProcessInPacket(short requestTypeACK, short requestTypeDATA)}
	 * should be used at the end of this method.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	abstract Session processInPacket() throws Exception;

	/**
	 * Common packet processing method across both client and server session
	 * instances.
	 * <p>
	 * Should be used at the end of the implemented {@link #processInPacket()}
	 * method.
	 * 
	 * @param requestTypeACK
	 *            The request type that this session instance expects for
	 *            incoming ACK packets (e.g. RRQ for server)
	 * @param requestTypeDATA
	 *            The request type that this session instance expects for
	 *            incoming DATA packets (e.g. WRQ for server)
	 * @return This object
	 * @throws Exception
	 */
	Session commonProcessInPacket(short requestTypeACK, short requestTypeDATA) throws Exception {
		// Need to check the opcode and see what to do next
		switch (this.getInPacket().getOpcode()) {
		case Packet.opcode.DATA:
			if (this.getSessionType() != requestTypeDATA) {
				throw new Exception(Constants.strings.CANNOT_ACCEPT_DATA_PACKETS);
			}

			return this.handleDATA();

		case Packet.opcode.ACK:
			if (this.getSessionType() != requestTypeACK) {
				throw new Exception(Constants.strings.CANNOT_ACCEPT_ACK_PACKETS);
			}

			return this.handleACK();

		case Packet.opcode.ERROR:
			return this.handleERROR();

		default:
			throw new Exception(Constants.strings.PACKET_MALFORMED);
		}
	}

	/**
	 * Creates and returns an `OutgoingPacket` object that is saved to this
	 * session with the current active socket and delivery address and port.
	 * 
	 * @return An `OutgoingPacket` instance
	 */
	OutgoingPacket createOutPacket() {
		return this.outPacket = new OutgoingPacket(this.socket, this.deliveryAddress, this.deliveryPort);
	}

	/**
	 * Gets the outgoing packet.
	 * 
	 * @return `OutgoingPacket` instance
	 */
	private OutgoingPacket getOutPacket() {
		return this.outPacket;
	}

	/**
	 * Sends a DATA packet with provided block number and data.
	 * 
	 * @param blockNumber
	 *            Block number to send
	 * @param data
	 *            Data contents to send
	 * @return This object
	 * @throws Exception
	 */
	private Session sendDATA(BlockNumber blockNumber, byte[] data) throws Exception {
		// Using int representation of block number because Java doesn't have
		// unsigned shorts
		if (!this.disableBlockAckMessages) {
			this.print(String.format(Constants.strings.BLOCK_NUMBER_DATA_X_SIZE_X,  blockNumber.intVal(), this.fileBufferSize));
		}

		this.createOutPacket() //
				.addOpcode(Packet.opcode.DATA) //
				.addBlockNumber(blockNumber) //
				.addDataBytes(data) //
				.send();

		return this;
	}

	/**
	 * Sends a DATA packet with current block number and data in file buffer.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session sendDATA() throws Exception {
		return this.sendDATA(this.blockNumber, this.getFileBuffer());
	}

	/**
	 * Sends an ACK packet with the provided block number and print a message.
	 * 
	 * @param blockNumber
	 *            The block number to send with the ACK packet.
	 * @param message
	 *            Message to print
	 * @return This object
	 * @throws Exception
	 */
	private Session sendACK(BlockNumber blockNumber, String message) throws Exception {
		if (!this.disableBlockAckMessages) {
			this.print(message);
		}

		this.createOutPacket() //
				.addOpcode(Packet.opcode.ACK) //
				.addBlockNumber(blockNumber) //
				.send();

		return this;
	}

	/**
	 * Sends an ACK packet with the provided block number.
	 * 
	 * @param blockNumber
	 *            The block number to send with the ACK packet.
	 * @return This object
	 * @throws Exception
	 */
	private Session sendACK(BlockNumber blockNumber) throws Exception {
		// Using int representation of block number because Java doesn't have
		// unsigned shorts
		return this.sendACK(blockNumber, String.format(Constants.strings.BLOCK_NUMBER_ACK_X_SIZE_X,  blockNumber.intVal(), this.fileBufferSize));
	}

	/**
	 * Sends an ACK packet with this object's current block number and print a
	 * message.
	 * 
	 * @param message
	 *            Message to print
	 * @return This object
	 * @throws Exception
	 */
	private Session sendACK(String message) throws Exception {
		return this.sendACK(this.blockNumber, message);
	}

	/**
	 * Sends an ACK packet with this object's current block number.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session sendACK() throws Exception {
		return this.sendACK(this.blockNumber);
	}

	/**
	 * Resends the last outgoing packet.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session resend() throws Exception {
		// Just resends whatever was last stored as the outgoing packet
		this.getOutPacket().send();
		return this;
	}

	/**
	 * Method for handling ACK packets.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session handleACK() throws Exception {
		// If incoming block # = current block # - 1
		// The recipient is calling for a resend, since the ACK is for the
		// previous one
		if (BlockNumber.isInSeq(this.getInPacket().getBlockNumber(), this.blockNumber)) {
			return this.readAndReply(true);
		}

		// If the expected block number and the incoming one are not equal,
		// they're out of order!
		if (!BlockNumber.equals(this.blockNumber, this.getInPacket().getBlockNumber())) {
			throw new Exception(Constants.strings.BLOCK_NUMBER_OUT_OF_ORDER);
		}

		// Incoming ACK means that this session is reading data and sending back
		// DATA
		return this.readAndReply(false);
	}

	/**
	 * Method for handling DATA packets.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session handleDATA() throws Exception {
		// If incoming block # = current block #
		// The recipient resent the data again, but we don't need to write
		// anything (since it has already been written) except for resending the
		// ACK
		if (BlockNumber.equals(this.blockNumber, this.getInPacket().getBlockNumber())) {
			return this.writeAndReply(true);
		}

		// If the expected block number and the incoming one are not in
		// sequence,
		// they're out of order!
		if (!BlockNumber.isInSeq(this.blockNumber, this.getInPacket().getBlockNumber())) {
			throw new Exception(Constants.strings.BLOCK_NUMBER_OUT_OF_ORDER);
		}

		// Incoming DATA means that this session is writing data and sending
		// back ACK
		return this.writeAndReply(false);
	}

	/**
	 * Method for handling ERROR packets.
	 * 
	 * @return This object
	 * @throws UnsupportedEncodingException
	 */
	private Session handleERROR() throws UnsupportedEncodingException {
		// We don't raise an exception here, because an ERROR packet is part of
		// normal behaviour
		return this
				.print("Sender error code " + this.getInPacket().getErrorCode() + "; '"
						+ Utils.byteArrayToString(this.getInPacket().getContents()) + "'; terminating") //
				.setSessionInactive();
	}

	// Handlers

	/**
	 * Method to deal with timeouts.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session handleTimeout() throws Exception {
		// (Max attempts - 1) because we have (n-1) timeouts between n attempts
		if (this.incrTimeoutCount().getTimeoutCount() > (this.maxAttempts - 1)) {
			return this.setSessionInactive() //
					.print(Constants.strings.MAX_ATTEMPTS_REACHED);
		}

		// Resend last outgoing packet
		return this.print(Constants.strings.PACKET_TIMEOUT_RESEND).resend();
	}

	/**
	 * Basic exception handler that create the appropriate error packet, and
	 * optionally sends it to the sender.
	 * <p>
	 * To send the full exception message generated by Java, execute
	 * {@link #enableExceptionMessageDelivery()} on this object first.
	 * 
	 * @param err
	 *            Exception to handle
	 * @param send
	 *            Indicates if the ERROR packet is to be sent immediately
	 */
	private void handleException(Exception err, boolean send) {
		try {
			// Get error information
			short errcode = Packet.errcode.NOT_DEFINED; // No error code by
														// default since most
														// exceptions raised are
														// not specific enough
			String errmsg = err.getMessage();
			String sentErrmsg = errmsg;

			if (errmsg != null) {
				// Support FILE EXISTS error
				if (errmsg == Packet.errstr[Packet.errcode.FILE_EXISTS]) {
					errcode = Packet.errcode.FILE_EXISTS;
				}
			}

			// For security/privacy reasons, exception messages are sent only if
			// explicitly enabled
			if (!this.sendExceptionMessage || sentErrmsg == null) {
				sentErrmsg = "";
			}

			// Construct error packet
			this.print(true, errmsg) //
					.createOutPacket() //
					.addOpcode(Packet.opcode.ERROR) //
					.addErrorCode(errcode) //
					.addString(sentErrmsg) //
					.addNullByte();

			// Send only if told to
			if (send) {
				this.getOutPacket().send();
				this.print(String.format(Constants.strings.PACKET_TYPE_X_SENT, "ERROR"));
			}

		} catch (Exception metaerr) {
			// Give up trying to send an ERROR - since we have meta-errored
			err.printStackTrace();
			metaerr.printStackTrace();
		}
	}

	/**
	 * Basic exception handler that sends the appropriate error packet back to
	 * the sender.
	 * <p>
	 * To send the full exception message generated by Java, execute
	 * {@link #enableExceptionMessageDelivery()} on this object first.
	 * 
	 * @param err
	 *            Exception to handle
	 */
	private void handleException(Exception err) {
		this.handleException(err, true);
	}

	// Files

	/**
	 * Sets local file in session.
	 * 
	 * @param filename
	 *            Local filename
	 * @return This object
	 */
	private Session setFilename(String filename) {
		this.filename = filename;
		return this;
	}

	/**
	 * Gets local file in session.
	 * 
	 * @return Local filename
	 */
	private String getFilename() {
		return this.filename;
	}

	/**
	 * Sets the file buffer with the input byte array.
	 * 
	 * @param data
	 *            Input byte array
	 * @return This object
	 */
	private Session setFileBuffer(byte[] data) {
		this.fileBuffer = data;

		// We set buffer size -1 in the null case because we can have 0 size
		// buffers from edge cases (n % 512 = 0)
		if (data == null) {
			this.fileBufferSize = -1;
		} else {
			this.fileBufferSize = data.length;
		}

		return this;
	}

	/**
	 * Sets the file buffer with a copy of the input byte array, between indices
	 * [from, to).
	 * 
	 * @param data
	 *            Input byte array
	 * @param from
	 *            Initial index, inclusive
	 * @param to
	 *            Final index, exclusive
	 * @return This object
	 */
	private Session setFileBuffer(byte[] data, int from, int to) {
		return this.setFileBuffer(Arrays.copyOfRange(data, from, to));
	}

	/**
	 * Gets the file buffer.
	 * 
	 * @return Byte array of the file buffer
	 */
	private byte[] getFileBuffer() {
		return this.fileBuffer;
	}

	/**
	 * Sets the contents of the file buffer to null.
	 * 
	 * @return This object
	 */
	private Session nullFileBuffer() {
		return this.setFileBuffer(null);
	}

	/**
	 * Opens a local file, ready for reading data from.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session openFile() throws Exception {
		this.fileReader = new FileReader(this.getMode(), this.getFilename());
		return this;
	}

	/**
	 * Creates a local file, ready for writing data into.
	 * <p>
	 * Overwriting existing files will raise an exception.
	 * 
	 * @return This object
	 * @throws FileNotFoundException
	 */
	private Session createFile() throws Exception {
		// Check existence
		File file = new File(this.getFilename());

		if (file.exists()) {
			throw new Exception(Packet.errstr[Packet.errcode.FILE_EXISTS]);
		}

		// Initialise
		this.fileWriter = new FileWriter(this.getMode(), this.getFilename());
		return this;
	}

	/**
	 * Reads the next block of bytes from the local file into the file buffer.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private Session readFile() throws Exception {
		// Read
		int bytesInBuffer = fileReader.newByteBuffer(Constants.maxDataContentLength).read();
		byte[] tempFileBuffer = fileReader.getBuffer();

		// EOF
		if (bytesInBuffer < 0) {
			// EOF has been reached NOT when there is a `n % 512 = 0` situation
			// This is because we run .readFile() one more time after the last
			// byte has been sent, so we just set the buffer to `null` which is
			// picked up by the RRQ method as a termination step.
			if (this.hasRespondedWithReadPrior && this.fileBufferSize != Constants.maxDataContentLength) {
				return this.nullFileBuffer();
			}

			// This is because we can have a `n % 512 = 0` situation, in which
			// case the last one will be -1 as EOF has been reached.
			// We need to send one final blank data packet to indicate EOF to
			// the client.
			bytesInBuffer = 0;
		}

		// This variable is required so that we know that we've at least
		// attempted to read something before terminating the first time (e.g.
		// if the file is 0 bytes.)
		this.hasRespondedWithReadPrior = true;

		// Store
		return this.setFileBuffer(tempFileBuffer, 0, bytesInBuffer);
	}

	/**
	 * Writes the relevant contents of the incoming packet to file.
	 * 
	 * @return This object
	 * @throws IOException
	 */
	private Session writeFile() throws IOException {
		// Need to copy into session file buffer first so that we can update
		// other things at the same time
		this.setFileBuffer(this.getInPacket().getContents());
		this.fileWriter.write(this.getFileBuffer());
		return this;
	}

	/**
	 * Closes the local file in session.
	 * 
	 * @return This object
	 */
	private Session closeFile() {
		try {
			// Go through each stream and see if they're assigned
			if (this.fileReader != null) {
				this.fileReader.close();
				this.print("Local read file '" + this.getFilename() + "' closed");
			}

			if (this.fileWriter != null) {
				this.fileWriter.close();
				this.print("Local write file '" + this.getFilename() + "' closed");
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return this;
	}

	// Higher level operations

	/**
	 * Performs operations to read the contents of the file to file buffer and
	 * replies to the sender with the appropriate DATA packet.
	 * 
	 * @param retry
	 *            Indicates if this operation is a retry
	 * @return This object
	 * @throws Exception
	 */
	private Session readAndReply(boolean retry) throws Exception {
		if (retry) {
			// If this is a retry, we need to check if we're going over the
			// maximum number of attempts
			if (this.incrPacketSendRetryCount().getPacketSendRetryCount() > (this.maxAttempts - 1)) {
				return this.setSessionInactive() //
						.print(Constants.strings.MAX_ATTEMPTS_REACHED);
			}
		} else {
			// Read and increment block number
			this.readFile() //
					.incrBlockNumber() //
					.resetPacketSendRetryCount();

			// Negative file buffer sizes indicate EOF
			if (this.fileBufferSize < 0) {
				return this.setSessionInactive() //
						.print(Constants.strings.READ_COMPLETED);
			}
		}

		// Send DATA
		return this.sendDATA();
	}

	/**
	 * Performs operations to write the contents of the file buffer to disk and
	 * replies to the sender with the appropriate ACK packet.
	 * 
	 * @param retry
	 *            Indicates if this operation is a retry
	 * @return This object
	 * @throws Exception
	 */
	private Session writeAndReply(boolean retry) throws Exception {
		if (retry) {
			// If this is a retry, we need to check if we're going over the
			// maximum number of attempts
			if (this.incrPacketSendRetryCount().getPacketSendRetryCount() > (this.maxAttempts - 1)) {
				return this.setSessionInactive() //
						.print(Constants.strings.MAX_ATTEMPTS_REACHED);
			}
		} else {
			// Write and increment block number
			this.writeFile() //
					.incrBlockNumber() //
					.resetPacketSendRetryCount();
		}

		// Send ACK
		this.sendACK();

		// Finished if we a DATA packet with content less than 512 bytes
		// No dallying: We terminate write session *after* we send the last ACK
		if (this.getInPacket().getContents().length < Constants.maxDataContentLength) {
			return this.setSessionInactive() //
					.print(Constants.strings.WRITE_COMPLETED);
		}

		return this;
	}

	// Main

	/**
	 * Method embodying the main execution loop.
	 * 
	 * @throws Exception
	 */
	private void mainLoop() throws Exception {
		// As long as we are good to continue, grab and process/act on incoming
		// packet data and reset any timeout counter
		while (this.isSessionActive()) {
			this.storeInPacket() //
					.processInPacket() //
					.resetTimeoutCount();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		try {
			// Run initialisation
			this.reset().setSessionActive().openSocket().begin();

			// Need to maintain loop so that we reattempt after timeout has been
			// handled
			while (true) {
				try {
					// Executes the core processes in loop
					this.mainLoop();
					break;

				} catch (SocketTimeoutException e) {
					// Handles response timeout
					this.handleTimeout();

					// Stop if we're no longer continuing (e.g. too many
					// retries)
					if (!this.isSessionActive()) {
						break;
					}
				}
			}

		} catch (Exception e) {
			// This handles exceptions which become ERRORs that are transmitted
			// back to the sender
			this.handleException(e);

		} finally {
			// Clean up and end
			this.end();
		}
	}
}
