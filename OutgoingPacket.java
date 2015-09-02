import java.io.UnsupportedEncodingException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for the creation of outgoing packets.
 * <p>
 * Data is added in a builder pattern.
 * 
 * @author James Li
 */
class OutgoingPacket extends Packet {
	private InetAddress address;
	private int port;
	private short opcode;
	private List<Byte> tempData = new ArrayList<Byte>();
	private int sectionCount = 0;

	/**
	 * Increments section count used to keep track of how many things have been
	 * added to the packet for validation later.
	 * 
	 * @return This object
	 */
	private OutgoingPacket incrSectionCount() {
		++this.sectionCount;
		return this;
	}

	/**
	 * Appends a single byte to the raw data of the packet.
	 * 
	 * @param data
	 *            Byte to append
	 * @return This object
	 */
	private OutgoingPacket append(byte data) {
		this.tempData.add(data);
		return this;
	}

	/**
	 * Appends a byte array to the raw data of the packet.
	 * 
	 * @param data
	 *            Byte array to append
	 * @return This object
	 */
	private OutgoingPacket append(byte[] data) {
		for (byte b : data) {
			this.append(b);
		}

		return this;
	}

	/**
	 * Appends a `short` value to the raw data of the packet.
	 * 
	 * @param data
	 *            `short` to append
	 * @return This object
	 */
	private OutgoingPacket append(short data) {
		return this.append(Utils.shortToByteArray(data));
	}

	/**
	 * Gets the byte array representation of the data of the constructed packet.
	 * 
	 * @return Byte array of packet raw data
	 */
	private byte[] getByteArray() {
		byte[] dataArray = null;

		// Stupid `Byte` is different from `byte` so we can't do a straight
		// array conversion
		final int dataSize = this.tempData.size();
		dataArray = new byte[dataSize];

		for (int i = 0; i < dataSize; ++i) {
			dataArray[i] = this.tempData.get(i);
		}

		return dataArray;
	}

	/**
	 * Sends this packet with the constructed data and set delivery information.
	 * 
	 * @return This object
	 * @throws Exception
	 */
	OutgoingPacket send() throws Exception {
		// Validation will throw exception if it fails
		this.validate().setupSend(this.getByteArray(), this.address, this.port).sendPacket();

		return this;
	}

	/**
	 * Validates the constructed outgoing packet.
	 * 
	 * @return This object if valid, otherwise will throw exception
	 * @throws Exception
	 */
	private OutgoingPacket validate() throws Exception {
		// Check the opcode and perform simple section count verification for
		// each type
		switch (this.opcode) {
		case Packet.opcode.ACK:
			if (this.sectionCount == 2) {
				break;
			}

			throw new Exception(Constants.strings.PACKET_BUILD_SECTIONS_INCORRECT);

		case Packet.opcode.DATA:
			if (this.sectionCount == 3) {
				// We can't go over 512 bytes
				if (this.tempData.size() <= Constants.maxDataPacketLength) {
					break;
				}

				throw new Exception(Constants.strings.OUT_PACKET_DATA_CONTENT_LENGTH_EXCEEDED);
			}

			throw new Exception(Constants.strings.PACKET_BUILD_SECTIONS_INCORRECT);

		case Packet.opcode.ERROR:
			if (this.sectionCount == 4) {
				break;
			}

			throw new Exception(Constants.strings.PACKET_BUILD_SECTIONS_INCORRECT);

		case Packet.opcode.RRQ:
		case Packet.opcode.WRQ:
			if (this.sectionCount == 5) {
				break;
			}

			throw new Exception(Constants.strings.PACKET_BUILD_SECTIONS_INCORRECT);

		default:
			throw new Exception(Constants.strings.INVALID_OPCODE);
		}

		// Max packet size set by the program itself - we should not overfill
		// any internal buffers set at this size
		if (tempData.size() > Constants.maxAnyPacketLength) {
			throw new Exception(Constants.strings.OUT_PACKET_LENGTH_EXCEEDED);
		}

		return this;
	}

	/**
	 * Adds opcode to packet data.
	 * <p>
	 * Only for use with ACK and DATA packets.
	 * 
	 * @param data
	 *            Byte array of data
	 * @return This object
	 */
	OutgoingPacket addOpcode(short opcode) {
		if (this.sectionCount == 0) {
			this.opcode = opcode;

			this.append(opcode).incrSectionCount();
		}

		return this;
	}

	/**
	 * Adds block number to packet data.
	 * <p>
	 * Only for use with ACK and DATA packets.
	 * 
	 * @param data
	 *            Byte array of data
	 * @return This object
	 */
	OutgoingPacket addBlockNumber(BlockNumber blockNumber) {
		if (this.sectionCount == 1 && (this.opcode == Packet.opcode.ACK || this.opcode == Packet.opcode.DATA)) {
			this.append(blockNumber.val()).incrSectionCount();
		}

		return this;
	}

	/**
	 * Adds raw bytes to packet data.
	 * <p>
	 * Only for use with DATA packets.
	 * 
	 * @param data
	 *            Byte array of data
	 * @return This object
	 */
	OutgoingPacket addDataBytes(byte[] data) {
		if (this.sectionCount == 2 && this.opcode == Packet.opcode.DATA) {
			this.append(data).incrSectionCount();
		}

		return this;
	}

	/**
	 * Adds string to packet data.
	 * <p>
	 * Only for use with ERROR, RRQ and WRQ packets.
	 * 
	 * @param string
	 *            String
	 * @return This object
	 */
	OutgoingPacket addString(String string) {
		try {
			if ((this.sectionCount == 2 && this.opcode == Packet.opcode.ERROR)
					|| (((this.sectionCount == 1) || (this.sectionCount == 3))
							&& ((this.opcode == Packet.opcode.RRQ) || (this.opcode == Packet.opcode.WRQ)))) {
				this.append(Utils.stringToByteArray(string)).incrSectionCount();
			}

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return this;
	}

	/**
	 * Adds error code to packet data.
	 * <p>
	 * Only for use with ERROR packets.
	 * 
	 * @param errcode
	 *            Error code
	 * @return This object
	 */
	OutgoingPacket addErrorCode(short errcode) {
		if (this.sectionCount == 1 && this.opcode == Packet.opcode.ERROR) {
			this.append(errcode).incrSectionCount();
		}

		return this;
	}

	/**
	 * Adds NULL byte to packet data.
	 * 
	 * @return This object
	 */
	OutgoingPacket addNullByte() {
		return this.append((byte) 0x00).incrSectionCount();
	}

	/**
	 * Instantiates an outgoing packet instance.
	 * 
	 * @param socket
	 *            Open socket
	 * @param address
	 *            Destination address
	 * @param port
	 *            Destination port
	 */
	OutgoingPacket(DatagramSocket socket, InetAddress address, int port) {
		super(socket);

		this.address = address;
		this.port = port;
	}
}
