import java.util.Arrays;

/**
 * Represents a 16-bit block number used in TFTP transmission.
 * 
 * @author James Li
 */
class BlockNumber {
	byte[] value = { 0x00, 0x00 };

	/**
	 * Returns the byte array representation of the block number.
	 * 
	 * @return Byte array representation
	 */
	byte[] val() {
		return this.value;
	}
	
	/**
	 * Returns the `int` representation of the block number.
	 * 
	 * @return `int` representation
	 */
	int intVal() {
		return Utils.shortToInt(Utils.byteArrayToShort(this.val()));
	}

	/**
	 * Increments block number by one.
	 * 
	 * @return This object
	 */
	BlockNumber incr() {
		this.value = addOne(this.value);
		return this;
	}

	/**
	 * Returns byte array representation of a block number incremented by one.
	 * <p>
	 * This wraps at maximum unsigned 16-bit value.
	 * 
	 * @param value
	 *            Byte array representation of block number
	 * @return Incremented byte array
	 */
	private static byte[] addOne(byte[] value) {
		int shortVal = Utils.shortToInt(Utils.byteArrayToShort(value));
		return Utils.shortToByteArray(Utils.intToShort(++shortVal));
	}

	/**
	 * Evaluates whether two block numbers are equal.
	 * 
	 * @param a
	 *            Byte array representation of block number
	 * @param b
	 *            Byte array representation of block number
	 * @return `true` if equal, `false` otherwise
	 */
	static boolean equals(byte[] a, byte[] b) {
		return Arrays.equals(a, b);
	}

	/**
	 * Evaluates whether two block numbers are equal.
	 * 
	 * @param a
	 *            `BlockNumber` instance
	 * @param b
	 *            Byte array representation of block number
	 * @return `true` if equal, `false` otherwise
	 */
	static boolean equals(BlockNumber a, byte[] b) {
		return equals(a.val(), b);
	}

	/**
	 * Evaluates whether two block numbers are equal.
	 * 
	 * @param a
	 *            `BlockNumber` instance
	 * @param b
	 *            `BlockNumber` instance
	 * @return `true` if equal, `false` otherwise
	 */
	static boolean equals(BlockNumber a, BlockNumber b) {
		return equals(a.val(), b.val());
	}

	/**
	 * Checks if the block numbers are in sequence (`a` then `b`).
	 * 
	 * @param a
	 *            Byte representation of block number
	 * @param b
	 *            Byte representation of block number
	 * @return `true` if in sequence, `false` otherwise
	 */
	static boolean isInSeq(byte[] a, byte[] b) {
		return Arrays.equals(addOne(a), b);
	}

	/**
	 * Checks if the block numbers are in sequence (`a` then `b`).
	 * 
	 * @param a
	 *            `BlockNumber` instance
	 * @param b
	 *            Byte representation of block number
	 * @return `true` if in sequence, `false` otherwise
	 */
	static boolean isInSeq(BlockNumber a, byte[] b) {
		return isInSeq(a.val(), b);
	}

	/**
	 * Checks if the block numbers are in sequence (`a` then `b`).
	 * 
	 * @param a
	 *            Byte representation of block number
	 * @param b
	 *            `BlockNumber` instance
	 * @return `true` if in sequence, `false` otherwise
	 */
	static boolean isInSeq(byte[] a, BlockNumber b) throws Exception {
		return isInSeq(a, b.val());
	}

	/**
	 * Checks if the block numbers are in sequence (`a` then `b`).
	 * 
	 * @param a
	 *            `BlockNumber` instance
	 * @param b
	 *            `BlockNumber` instance
	 * @return `true` if in sequence, `false` otherwise
	 */
	static boolean isInSeq(BlockNumber a, BlockNumber b) throws Exception {
		return Arrays.equals(addOne(a.val()), b.val());
	}
}
