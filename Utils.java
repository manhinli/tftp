import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Utilities for manipulations of data relevant for this package.
 * 
 * @author James Li
 */
class Utils {
	/**
	 * Converts from `short` to byte array.
	 * <p>
	 * Code based from <a href=
	 * "http://www.java2s.com/Tutorial/Java/Data_Type/Array_Convert/Convert_short_to_byte_array_in_Java.htm">
	 * this</a>.
	 * 
	 * @param value
	 *            Input value of type `short`
	 * @return Byte array representation of input value
	 */
	static byte[] shortToByteArray(short value) {
		byte[] bytes = new byte[2];
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.putShort(value);
		return buffer.array();
	}

	/**
	 * Converts from byte array to `short`.
	 * 
	 * @param value
	 *            Byte array
	 * @return `short` representation of input value
	 */
	static short byteArrayToShort(byte[] value) {
		return ByteBuffer.wrap(value).getShort();
	}

	/**
	 * Converts from `short` to `int`. Useful for unsigned display.
	 * 
	 * @param value
	 *            Input value of type `short`
	 * @return `int` representation of input value
	 */
	static int shortToInt(short value) {
		return value & 0xFFFF;
	}

	/**
	 * Converts from `int` to `short`.
	 * 
	 * @param value
	 *            Input value of type `int`
	 * @return `short` representation of input value
	 */
	static short intToShort(int value) {
		return (short) (value & 0xFFFF);
	}

	/**
	 * Obtains ASCII representation of byte array.
	 * 
	 * @param array
	 *            Byte array
	 * @return String representation of input value, in "US-ASCII" encoding
	 * @throws UnsupportedEncodingException
	 */
	static String byteArrayToString(byte[] array) throws UnsupportedEncodingException {
		return new String(array, "US-ASCII");
	}

	/**
	 * Obtains byte array representation of an ASCII string.
	 * 
	 * @param string
	 *            String, compatible with "US-ASCII" encoding
	 * @return Byte array representation of input value
	 * @throws UnsupportedEncodingException
	 */
	static byte[] stringToByteArray(String string) throws UnsupportedEncodingException {
		return string.getBytes("US-ASCII");
	}

	/**
	 * Converts a list of Bytes to a byte array.
	 * 
	 * @param byteList
	 *            List of Bytes
	 * @return Byte array containing the bytes in the list
	 */
	static byte[] byteObjListToByteArray(List<Byte> byteList) {
		// Stupid `Byte` is different from `byte` so we can't do a straight
		// array conversion
		final int arrSize = byteList.size();
		byte[] byteArray = new byte[arrSize];

		for (int i = 0; i < arrSize; ++i) {
			byteArray[i] = byteList.get(i);
		}

		return byteArray;
	}

	/**
	 * Adds the array of bytes to a list of bytes.
	 * <p>
	 * Does not clear list.
	 * 
	 * @param byteList
	 *            List of Bytes
	 * @param byteArray
	 *            Byte array
	 */
	static void addByteArrayToByteObjList(List<Byte> byteList, byte[] byteArray) {
		for (byte b : byteArray) {
			byteList.add(b);
		}
	}
}
