import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to (naively) parse arguments provided to the program.
 * 
 * @author James Li
 */
class ArgumentsParser {
	private Map<String, Integer> keyType = new HashMap<String, Integer>();
	private Map<String, String> keyValue = new HashMap<String, String>();

	/**
	 * Small set of used types used to identify the key-value pairs stored, so
	 * that they can be converted properly when read.
	 */
	static final class type {
		/** boolean **/
		static final int BOOLEAN = 0;

		/** int **/
		static final int INT = 1;

		/** String **/
		static final int STRING = 2;
	}

	/**
	 * Registers an option for the parser.
	 * 
	 * @param key
	 *            Option key (e.g. --hello-world => "hello-world")
	 * @param type
	 *            One of ArgumentsParser.type
	 * @param defaultValue
	 *            Default value as a string
	 * @return This object
	 */
	ArgumentsParser register(String key, int type, String defaultValue) {
		this.keyType.put(key, type);
		this.write(key, defaultValue); // Writes the default value in, since
										// it's the default...
		return this;
	}

	/**
	 * Special option registration method for booleans, which are always false
	 * by default.
	 * 
	 * @param key
	 *            Option key (e.g. --hello-world => "hello-world")
	 * @return This object
	 */
	ArgumentsParser registerBoolean(String key) {
		return this.register(key, type.BOOLEAN, "false");
	}

	/**
	 * Writes the value associated to an option key into the internal store.
	 * 
	 * @param key
	 *            Option key
	 * @param value
	 *            Value
	 * @return This object
	 */
	private ArgumentsParser write(String key, String value) {
		this.keyValue.put(key, value);
		return this;
	}

	/**
	 * Reads string value stored against the option key.
	 * 
	 * @param key
	 *            Option key
	 * @return String value
	 */
	private String read(String key) {
		return this.keyValue.get(key);
	}

	/**
	 * Reads interpreted boolean value stored against the option key.
	 * 
	 * @param key
	 *            Option key
	 * @return Boolean value
	 */
	boolean readBool(String key) {
		return Boolean.parseBoolean(this.read(key));
	}

	/**
	 * Reads interpreted int value stored against the option key.
	 * 
	 * @param key
	 *            Option key
	 * @return Int value
	 */
	int readInt(String key) {
		return Integer.parseInt(this.read(key));
	}

	/**
	 * Reads string value stored against the option key.
	 * 
	 * @param key
	 *            Option key
	 * @return String value
	 */
	String readString(String key) {
		return this.read(key);
	}

	/**
	 * Very basic arguments parser just for this package.
	 * <p>
	 * Runs through the supplied arguments array, takes in accepted key/vals,
	 * and then spits out remaining arguments from the first argument that is
	 * determined not to be in the format of a flag or option.
	 * 
	 * @param args
	 *            Arguments string array
	 * @return Remaining unmunched arguments
	 */
	String[] munch(String[] args) {
		// There probably are more valid and better argument parsers out there,
		// but I only have a small set of things to consider, so this should
		// work fine.
		int length = args.length;
		int i = 0;

		for (; i < length; ++i) {
			String thisArg = args[i];

			// If we don't encounter option anymore, stop
			if (thisArg.length() < 2) {
				break;
			}

			// Read first two chars for "--" that indicates an option
			// If it's not present, we're done with options, so stop
			if (!thisArg.substring(0, 2).equals("--")) {
				break;
			}

			// Lookup
			String key = thisArg.substring(2);

			// If key not recognised, skip
			if (!this.keyType.containsKey(key)) {
				continue;
			}

			Integer registeredType = this.keyType.get(key);

			// Booleans only
			if (registeredType.intValue() == type.BOOLEAN) {
				this.write(key, "true");
				continue;
			}

			// We need to be able to fetch the next value - if we can't, skip
			if ((i + 1) >= length) {
				continue;
			}

			this.write(key, args[++i]);
		}

		// Return the remaining unmunched arguments
		return Arrays.copyOfRange(args, i, length);
	}
}
