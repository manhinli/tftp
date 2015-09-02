import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Wrapper for handling basic tasks around `DatagramSocket` and
 * `DatagramPacket`.
 * 
 * @author James Li
 */
class Packet {
	private byte[] data;
	private DatagramPacket packet;
	private DatagramSocket socket;

	/**
	 * Opcodes based on Section 5
	 */
	static final class opcode {
		/** Read request */
		static final short RRQ = 1;

		/** Write request */
		static final short WRQ = 2;

		/** Data */
		static final short DATA = 3;

		/** Acknowledgement */
		static final short ACK = 4;

		/** Error */
		static final short ERROR = 5;
	}

	/**
	 * Error codes based on Appendix
	 * <p>
	 * Most are not used in this package, because the errors that are returned
	 * from Java methods tend not to be specific enough to be distinguishable.
	 */
	static final class errcode {
		/** Not defined, see error message **/
		static final short NOT_DEFINED = 0;

		/** File not found **/
		static final short FILE_NOT_FOUND = 1;

		/** Access violation **/
		static final short ACCESS_VIOLATION = 2;

		/** Disk full or allocation exceeded **/
		static final short DISK_FULL = 3;

		/** Illegal TFTP operation **/
		static final short ILLEGAL_OPERATION = 4;

		/** Unknown transfer ID **/
		static final short UNKNOWN_TID = 5;

		/** File already exists **/
		static final short FILE_EXISTS = 6;

		/** No such user **/
		static final short NO_SUCH_USER = 7;
	}

	/**
	 * Error strings based on Appendix
	 * <p>
	 * Most are not used in this package, because the errors that are returned
	 * from Java methods tend not to be specific enough to be distinguishable.
	 */
	static final String[] errstr = {
		"",
		"File not found",
		"Access violation",
		"Disk full or allocation exceeded",
		"Illegal TFTP operation",
		"Unknown transfer ID",
		"File already exists",
		"No such user"
	};
	
	/**
	 * Mode strings
	 */
	static final class modestr {
		static final String NETASCII = "netascii";
		static final String OCTET = "octet";
		static final String MAIL = "mail";	// Not supported
	}
	
	/**
	 * Retrieves the data buffer in this packet.
	 * 
	 * @return Data buffer
	 */
	byte[] getData() {
		return this.data;
	}

	/**
	 * Retrieves the `DatagramPacket` stored in this packet.
	 * 
	 * @return `DatagramPacket` object
	 */
	DatagramPacket getDatagramPacket() {
		return this.packet;
	}

	/**
	 * Retrieves the `DatagramSocket` stored in this packet.
	 * 
	 * @return DatagramSocket object
	 */
	DatagramSocket getSocket() {
		return this.socket;
	}

	/**
	 * Sets up this object for receiving data.
	 * 
	 * @return This object
	 */
	Packet setupReceive() {
		this.data = new byte[Constants.maxAnyPacketLength];
		this.packet = new DatagramPacket(this.data, this.data.length);
		return this;
	}

	/**
	 * Sets up this object for sending data.
	 * 
	 * @return This object
	 */
	Packet setupSend(byte[] data, InetAddress address, int port) {
		this.data = data;
		this.packet = new DatagramPacket(this.data, this.data.length, address, port);
		return this;
	}

	/**
	 * Receives datagram packet.
	 * 
	 * @return This object
	 */
	Packet receivePacket() throws IOException {
		this.getSocket().receive(this.getDatagramPacket());
		return this;
	}

	/**
	 * Sends datagram packet.
	 * 
	 * @return This object
	 */
	Packet sendPacket() throws IOException {
		this.getSocket().send(this.getDatagramPacket());
		return this;
	}

	/**
	 * @param socket
	 *            `DatagramSocket` object
	 */
	Packet(DatagramSocket socket) {
		this.socket = socket;
	}
}
