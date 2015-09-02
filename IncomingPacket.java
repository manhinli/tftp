import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Class for the inspection of an incoming packet.
 * 
 * @author James Li
 */
class IncomingPacket extends Packet {
	private short opcode;
	private byte[] contents;
	private String filename;
	private String mode;
	private byte[] blockNumber;
	private short errorCode;

	/**
	 * Decodes the opcode and remaining part of the packet ("contents").
	 * <p>
	 * Must be executed before any other method that requires the reading of the
	 * packet contents.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private IncomingPacket decodeOpcodeAndContents() throws Exception {
		int packetLength = this.getDatagramPacket().getLength();

		// Incredibly basic validation (every packet should have opcode (2) and
		// at least two additional bytes, such as block number)
		if (packetLength < 4) {
			throw new Exception(Constants.strings.PACKET_MALFORMED);
		}

		// Get the raw data
		byte[] packetData = this.getData();

		// Split the raw data into opcode and the other part ("contents")
		this.opcode = ByteBuffer.wrap(Arrays.copyOfRange(packetData, 0, 2)).getShort();
		this.contents = Arrays.copyOfRange(packetData, 2, packetLength);

		return this;
	}

	/**
	 * Decodes the filename and mode of the packet.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	private IncomingPacket decodeFilenameAndMode() throws Exception {
		// Contents = Filename (n) + NULL + Mode (n) + NULL
		int filenameNullIndex = nextNullIndex(this.contents, 0);
		int modeNullIndex = nextNullIndex(this.contents, filenameNullIndex + 1);

		// -1 is when we can't find the NULL terminator
		if (filenameNullIndex == -1) {
			throw new Exception(String.format(Constants.strings.X_NOT_RECOGNISABLE, "Filename"));
		}

		if (modeNullIndex == -1) {
			throw new Exception(String.format(Constants.strings.X_NOT_RECOGNISABLE, "Mode"));
		}

		// Copy out the correct portion from the NULL indices
		this.filename = Utils.byteArrayToString(Arrays.copyOfRange(this.contents, 0, filenameNullIndex));
		this.mode = Utils.byteArrayToString(Arrays.copyOfRange(this.contents, filenameNullIndex + 1, modeNullIndex))
				.toLowerCase();
		
		return this;
	}

	/**
	 * Decodes the block number from first two bytes in contents.
	 * 
	 * @return This object
	 */
	private IncomingPacket decodeBlockNumber() {
		this.blockNumber = this.getFirstTwoBytes();
		return this;
	}

	/**
	 * Decodes the error code from first two bytes in contents.
	 * 
	 * @return This object
	 */
	private IncomingPacket decodeErrorCode() {
		this.errorCode = Utils.byteArrayToShort(this.getFirstTwoBytes());
		return this;
	}

	/**
	 * Finds the next instance of NULL in the byte array from the index
	 * provided.
	 * 
	 * @param data
	 *            Byte array of data
	 * @param start
	 *            Starting index, inclusive
	 * @return Next index of NULL byte, or -1 if none found
	 */
	private static int nextNullIndex(byte[] data, int start) {
		for (int i = start; i < data.length; ++i) {
			if (data[i] == 0x00) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Gets the first two bytes stored in packet contents.
	 * 
	 * @return First two bytes in byte array
	 */
	private byte[] getFirstTwoBytes() {
		return Arrays.copyOfRange(this.contents, 0, 2);
	}

	/**
	 * Removes the first two bytes stored in packet contents.
	 * 
	 * @return This object
	 */
	private IncomingPacket stripFirstTwoBytes() {
		this.contents = Arrays.copyOfRange(this.contents, 2, this.contents.length);
		return this;
	}

	/**
	 * Removes the first NULL byte and everything else after in packet contents,
	 * if present.
	 * 
	 * @return This object
	 */
	private IncomingPacket stripAfterFirstNull() {
		int nextNullIndex = nextNullIndex(this.contents, 0);

		// If we can't find it, then don't do anything
		if (nextNullIndex < 0) {
			return this;
		}

		this.contents = Arrays.copyOfRange(this.contents, 0, nextNullIndex);
		return this;
	}

	/**
	 * Receives incoming packet data and holds its data internally.
	 * 
	 * @return This object
	 * @throws IOException
	 */
	IncomingPacket receive() throws IOException {
		this.setupReceive().receivePacket();

		return this;
	}

	/**
	 * Processes the contents of the incoming packet.
	 * <p>
	 * {@link #receive()} must have been executed beforehand.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	IncomingPacket process() throws Exception {
		this.decodeOpcodeAndContents();

		switch (this.getOpcode()) {
		case Packet.opcode.RRQ:
		case Packet.opcode.WRQ:
			this.decodeFilenameAndMode();

			break;

		case Packet.opcode.DATA:
			// Strip the first two bytes to remove the block number from the
			// contents
			this.decodeBlockNumber().stripFirstTwoBytes();

			break;

		case Packet.opcode.ACK:
			this.decodeBlockNumber();

			break;

		case Packet.opcode.ERROR:
			// Strip the first two bytes to remove the error code from the
			// contents, and whatever after the first NULL
			this.decodeErrorCode().stripFirstTwoBytes().stripAfterFirstNull();
			break;

		default:
			throw new Exception(Constants.strings.PACKET_MALFORMED);
		}

		return this;
	}

	/**
	 * Gets the opcode from the packet.
	 * <p>
	 * Only valid if {@link #process()} has been executed.
	 * 
	 * @return Opcode
	 */
	short getOpcode() {
		return this.opcode;
	}

	/**
	 * Gets the contents of the packet.
	 * <p>
	 * Contents will vary depending on what methods were executed, though
	 * generally represents the full DATA contents or a string (such as an error
	 * message.)
	 * <p>
	 * Only valid if {@link #process()} has been executed.
	 * 
	 * @return Contents byte array
	 */
	byte[] getContents() {
		return this.contents;
	}

	/**
	 * Gets the filename" from the packet.
	 * <p>
	 * Only valid if {@link #process()} has been executed, and opcode of packet
	 * is RRQ or WRQ.
	 * 
	 * @return Filename
	 */
	String getFilename() {
		return this.filename;
	}

	/**
	 * Gets the encoding mode from the packet.
	 * <p>
	 * This value is not validated.
	 * <p>
	 * Only valid if {@link #process()} has been executed, and opcode of packet
	 * is RRQ or WRQ.
	 * 
	 * @return Encoding mode
	 */
	String getMode() {
		return this.mode;
	}

	/**
	 * Gets the block number from the packet.
	 * <p>
	 * Only valid if {@link #process()} has been executed, and opcode of packet
	 * is ACK or DATA.
	 * 
	 * @return Block number byte array
	 */
	byte[] getBlockNumber() {
		return this.blockNumber;
	}

	/**
	 * Gets the error code from the packet.
	 * <p>
	 * Only valid if {@link #process()} has been executed, and opcode of packet
	 * is ERROR.
	 * 
	 * @return Error code
	 */
	short getErrorCode() {
		return this.errorCode;
	}

	/**
	 * Instantiates an incoming packet instance.
	 * 
	 * @param socket
	 *            Open socket
	 */
	IncomingPacket(DatagramSocket socket) {
		super(socket);
	}
}
