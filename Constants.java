/**
 * Constants for TFTP parameters.
 * 
 * @author James Li
 */
final class Constants {
	/** TFTP serving host default TID for listening to requests */
	static final int defaultServerTID = 69;

	/** Default timeout, in milliseconds */
	static final int defaultOperationTimeout = 5000;

	/** Default maximum number of attempts */
	static final int defaultMaxOperationAttempts = 3;

	/** Default byte encoding used */
	static final String defaultEncoding = Packet.modestr.OCTET;

	/** Maximum byte size of the content of a DATA packet */
	static final int maxDataContentLength = 512;

	/** Maximum byte size of the full raw DATA packet */
	// Length: opcode (2) + block number (2) + data content (n)
	static final int maxDataPacketLength = maxDataContentLength + 4;

	/**
	 * Maximum byte size of any packet, set to 2KiB to allow long filenames and
	 * messages, etc.
	 */
	static final int maxAnyPacketLength = 2048;

	/**
	 * Strings used through most of the package.
	 */
	static final class strings {
		static final String CANNOT_ACCEPT_DATA_PACKETS = "Cannot accept DATA packets";
		static final String CANNOT_ACCEPT_ACK_PACKETS = "Cannot accept ACK packets";
		static final String INVALID_REQUEST = "Invalid request";
		static final String INVALID_OPCODE = "Invalid opcode";

		static final String MAX_ATTEMPTS_REACHED = "Maximum operation attempts reached; terminating";
		
		static final String SESSION_ENDED = "Session ended";
		
		static final String WRITE_COMPLETED = "Write completed";
		static final String READ_COMPLETED = "Read completed";

		static final String PACKET_TYPE_X_SENT = "%1$s packet sent";
		static final String PACKET_MALFORMED = "Packet malformed";
		static final String PACKET_BUILD_SECTIONS_INCORRECT = "Incorrect number of sections built for this type of packet";
		static final String PACKET_TIMEOUT_RESEND = "Timeout; resending last outgoing packet";
		
		static final String OUT_PACKET_LENGTH_EXCEEDED = "Outgoing packet exceeds maximum length";
		static final String OUT_PACKET_DATA_CONTENT_LENGTH_EXCEEDED = "Outgoing DATA content exceeds maximum length";
		static final String OUT_PACKET_INIT_ACK = "Sending initial acknowledgement packet";

		static final String BLOCK_NUMBER_OUT_OF_ORDER = "Out-of-order block number";
		static final String BLOCK_NUMBER_ACK_X_SIZE_X = "Acknowledging block %1$d (%2$dB)";
		static final String BLOCK_NUMBER_DATA_X_SIZE_X = "Sending block %1$d (%2$dB)";

		static final String INSUFFICIENT_ARGS = "Arguments not sufficient or not recognisable";
		static final String INVALID_REQUEST_TYPE_ARG = "Invalid request type (should be either 'get' or 'put')";

		static final String REQUEST_REMOTE_FILE_X_READ_VIA_MODE_X = "Requested read from remote file '%1$s' with mode '%2$s'";
		static final String REQUEST_REMOTE_FILE_X_WRITE_VIA_MODE_X = "Requested write to remote file '%1$s' with mode '%2$s'";

		static final String CLIENT_SWITCH_REMOTE_PORT_X = "Switching to server remote port %1$d";
		static final String COMM_WITH_SERVER_OVER_IP_X_PORT_X_VIA_MODE_X = "Communicating with server at '%1$s:%2$d' with mode '%3$s'";
		static final String COMM_WITH_CLIENT_OVER_IP_X_PORT_X = "Communicating with client at '%1$s:%2$d'";


		static final String SOCKET_PORT_X_SETUP_FAILED = "Socket could not be established on port %1$d";
		static final String SOCKET_PORT_X_LISTENING = "Listening on port %1$d";
		static final String SOCKET_LOCAL_PORT_X_OPEN = "Local socket open on port %1$d";
		static final String SOCKET_CLOSED = "Socket closed";
		
		static final String MODE_X_NOT_SUPPORTED = "Mode '%1$s' is not supported";
		static final String X_NOT_RECOGNISABLE = "%1$s not recognisable";
	}

}
