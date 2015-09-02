import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles file reading for OCTET and NETASCII modes.
 * 
 * @author James Li
 */
class FileReader {
	static final byte NUL = 0x00;
	static final byte CR = 0x0D;
	static final byte LF = 0x0A;

	String mode;
	InputStream stream;
	byte[] buffer;

	List<Byte> queuedUnconvertedBytes = new ArrayList<Byte>();
	List<Byte> queuedOutBytes = new ArrayList<Byte>();

	/**
	 * Closes file stream.
	 * 
	 * @throws IOException
	 */
	void close() throws IOException {
		this.stream.close();
	}

	/**
	 * Reads bytes straight from file.
	 * 
	 * @return Bytes read, or -1 if EOF
	 * @throws IOException
	 */
	private int readOctet() throws IOException {
		return this.stream.read(this.getBuffer());
	}

	/**
	 * Reads NetASCII bytes from file, following the translation process.
	 * 
	 * @return Bytes read, or -1 if EOF
	 * @throws IOException
	 */
	private int readNetascii() throws IOException {
		// Copies BufferedReader behaviour:
		// All instances of \n, \r, \r\n are turned into \n\r. (not \n\r!)

		// A little inefficient (with the copying) but tries to not overload
		// memory by only reading in bytes when needed, rather than using
		// BufferedReader.readLine() which can be theoretically huge.

		// We use a temporary buffer which is one char bigger than the supplied
		// buffer - this is so that we can catch any CR/LFs and replace them as
		// necessary

		// Working input bytes list
		int expectedOutputBufferSize = this.getBuffer().length;

		// Temporary store of incoming bytes
		List<Byte> tempInBytes = new ArrayList<Byte>();

		// Put in queued unconverted bytes
		tempInBytes.addAll(queuedUnconvertedBytes);
		queuedUnconvertedBytes.clear();

		// We only read when necessary
		final int sizeOfBufferWeNeed = expectedOutputBufferSize + 1 - tempInBytes.size();

		if (sizeOfBufferWeNeed > 0) {
			byte[] inBuffer = new byte[sizeOfBufferWeNeed];
			int inBufferSize = this.stream.read(inBuffer);

			if (inBufferSize >= 0) {
				// Copy in the read bytes into the store of incoming bytes
				Utils.addByteArrayToByteObjList(tempInBytes, Arrays.copyOfRange(inBuffer, 0, inBufferSize));
			}
		}

		// Temporary store of outgoing bytes (to remote host)
		List<Byte> tempOutBytes = new ArrayList<Byte>();

		// Go over in bytes, and convert
		int tempInBytesSize = tempInBytes.size();
		for (int i = 0; i < tempInBytesSize; ++i) {
			byte byteA = tempInBytes.get(i);

			if (i >= expectedOutputBufferSize) {
				// We have reached the end, and need to store whatever bytes are
				// left into the unprocessed bytes pile
				queuedUnconvertedBytes.add(byteA);
				continue;
			}

			// LF -> CR+LF
			if (byteA == LF) {
				tempOutBytes.add(CR);
				tempOutBytes.add(LF);
				continue;

				// Handles CR+?
			} else if (byteA == CR) {
				// Check if only byte in buffer
				// OR
				// if we have reached the last byte in temp in bytes but have
				// not reached the expected output buffer size
				// THEN
				// transmit it as CR+NUL as we have finished reading
				if ((tempInBytesSize == 1 && i == 0)
						|| (i >= tempInBytesSize - 1 && i < expectedOutputBufferSize - 1)) {
					tempOutBytes.add(CR);
					tempOutBytes.add(NUL);
					continue;

					// Check if last byte (and have gone over expected output
					// buffer size; implicit from above condition)
				} else if (i >= tempInBytesSize - 1) {
					// We can't determine this just yet, so put into unconverted
					// bytes
					queuedUnconvertedBytes.add(byteA);
					continue;

				} else {
					// Check if 2nd one is also part of sequence
					byte byteB = tempInBytes.get(++i);

					if (byteB == LF) {
						// CR+LF -> CR+LF
						tempOutBytes.add(CR);
						tempOutBytes.add(LF);
						continue;

					} else {
						// Lone CR -> CR+NUL
						// Need to rewind so we don't lose track of the
						// "skipped" byte in the next round
						--i;

						// Add CR+NUL
						tempOutBytes.add(CR);
						tempOutBytes.add(NUL);

						continue;
					}
				}
			}

			// If not sep char, copy straight
			tempOutBytes.add(byteA);
		}

		// Crop the converted bytes
		List<Byte> outBuffer = new ArrayList<Byte>();
		outBuffer.addAll(queuedOutBytes); // Put in queued bytes first
		outBuffer.addAll(tempOutBytes); // then the out bytes we just converted
		queuedOutBytes.clear();

		// Send out only the appropriate number of bytes back
		int outBufferSize = outBuffer.size();
		if (outBufferSize >= this.getBuffer().length) {
			// Copy data out and store excess as queued bytes
			this.buffer = Utils.byteObjListToByteArray(outBuffer.subList(0, this.getBuffer().length));
			queuedOutBytes.addAll(outBuffer.subList(this.getBuffer().length, outBufferSize));
			return this.getBuffer().length;

		} else {
			if (outBufferSize == 0) {
				// EOF
				return -1;
			}

			// Copy
			for (int i = 0; i < outBufferSize; ++i) {
				this.getBuffer()[i] = outBuffer.get(i);
			}

			return outBuffer.size();
		}

	}

	/**
	 * Reads contents of file and puts it in this instance's buffer.
	 * <p>
	 * Behaviour depends on mode set when initialising FileReader.
	 * <p>
	 * Usage of this method must be preceeded by {@link #newByteBuffer(int)} if
	 * a clean buffer is required.
	 * 
	 * @return Bytes read, or -1 if EOF
	 * @throws IOException
	 */
	int read() throws Exception {
		switch (this.mode) {
		case Packet.modestr.NETASCII:
			return this.readNetascii();
		case Packet.modestr.OCTET:
			return this.readOctet();
		}

		// Invalid
		return -1;
	}

	/**
	 * Clears and sets size of buffer.
	 * 
	 * @param size
	 *            Size of buffer for next read
	 * @return This object
	 */
	FileReader newByteBuffer(int size) {
		this.buffer = new byte[size];
		return this;
	}

	/**
	 * Gets file buffer.
	 * 
	 * @return Byte array of buffer
	 */
	byte[] getBuffer() {
		return this.buffer;
	}

	/**
	 * Sets up a FileReader with the set mode and opens the file ready for
	 * reading.
	 * 
	 * @param mode
	 *            Mode string
	 * @param filename
	 *            Filename
	 * @throws Exception
	 */
	FileReader(String mode, String filename) throws Exception {
		switch (mode) {
		case Packet.modestr.NETASCII:
		case Packet.modestr.OCTET:
			this.stream = new FileInputStream(filename);
			break;

		default:
			throw new Exception(String.format(Constants.strings.MODE_X_NOT_SUPPORTED, mode));
		}

		this.mode = mode;
	}

}
