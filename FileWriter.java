import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles file writing for OCTET and NETASCII modes.
 * 
 * @author James Li
 */
public class FileWriter {
	static final byte NUL = 0x00;
	static final byte CR = 0x0D;
	static final byte LF = 0x0A;

	byte[] systemLineSeparator;

	String mode;
	OutputStream stream;
	byte[] buffer;

	List<Byte> queuedBytes = new ArrayList<Byte>();

	/**
	 * Closes file stream.
	 * 
	 * @throws IOException
	 */
	void close() throws IOException {
		// Check out if there are any queued bytes, and if so, write them
		if (this.queuedBytes.size() > 0) {
			this.stream.write(Utils.byteObjListToByteArray(this.queuedBytes));
		}

		this.stream.close();
	}

	/**
	 * Writes bytes straight into file.
	 * 
	 * @throws IOException
	 */
	private void writeOctet() throws IOException {
		this.stream.write(this.getBuffer());
		return;
	}

	/**
	 * Writes NetASCII bytes into file, following the translation process.
	 * 
	 * @throws IOException
	 */
	private void writeNetascii() throws IOException {
		// Temporary store of incoming bytes
		List<Byte> tempInBytes = new ArrayList<Byte>();

		// Put in queued bytes
		tempInBytes.addAll(queuedBytes);
		queuedBytes.clear();

		// Copy over the stuff to write
		Utils.addByteArrayToByteObjList(tempInBytes, this.getBuffer());

		// Temporary store of outgoing bytes (to disk)
		List<Byte> tempOutBytes = new ArrayList<Byte>();

		// Go over in bytes, and convert
		int tempInBytesSize = tempInBytes.size();
		for (int i = 0; i < tempInBytesSize; ++i) {
			byte byteA = tempInBytes.get(i);

			if (byteA == CR) {
				// If last byte, put onto queue
				if (i >= tempInBytesSize - 1) {
					queuedBytes.add(byteA);
					continue;
				}

				// Check the next byte for LF or NUL
				byte byteB = tempInBytes.get(++i);

				switch (byteB) {
				case LF:
					// Add system separator
					Utils.addByteArrayToByteObjList(tempOutBytes, this.systemLineSeparator);
					break;
				case NUL:
					// CR+NUL -> CR only
					tempOutBytes.add(CR);
					break;
				default:
					// Copy straight if not anything special in NetASCII
					tempOutBytes.add(byteB);
				}

				continue;
			}

			// If not sep char, copy straight
			tempOutBytes.add(byteA);
		}

		// Write the out bytes
		this.stream.write(Utils.byteObjListToByteArray(tempOutBytes));
	}

	/**
	 * Writes contents of byte array into the file.
	 * <p>
	 * Behaviour depends on mode set when initialising FileWriter.
	 * 
	 * @param dataBuffer
	 *            Byte array of data
	 * @throws IOException
	 */
	void write(byte[] dataBuffer) throws IOException {
		this.buffer = dataBuffer;

		// Write behaviour is dependent on mode
		switch (this.mode) {
		case Packet.modestr.NETASCII:
			this.writeNetascii();
			break;
		case Packet.modestr.OCTET:
			this.writeOctet();
			break;
		}

		// Wipe buffer
		this.buffer = null;

		return;
	}

	/**
	 * Gets file buffer.
	 * 
	 * @return Byte array of buffer
	 */
	private byte[] getBuffer() {
		return this.buffer;
	}

	/**
	 * Sets up a FileWriter with the set mode and opens the file ready for
	 * writing.
	 * 
	 * @param mode
	 *            Mode string
	 * @param filename
	 *            Filename
	 * @throws Exception
	 */
	FileWriter(String mode, String filename) throws Exception {
		// The system line separator is whatever Java determines it to be
		this.systemLineSeparator = Utils.stringToByteArray(System.getProperty("line.separator"));

		// Determine if mode valid
		// This is separate from the one in Session because the behaviour here
		// is for file IO only
		switch (mode) {
		case Packet.modestr.NETASCII:
		case Packet.modestr.OCTET:
			this.stream = new FileOutputStream(filename);
			break;

		default:
			throw new Exception(String.format(Constants.strings.MODE_X_NOT_SUPPORTED, mode));
		}

		this.mode = mode;
	}
}
