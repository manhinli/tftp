# tftp
TFTP Client and Server implementation, done as part of a course at the University of Auckland

*(If people find that you're copying this for your assignment for the same course, well... the onus is on you not to do so.)*

Synopsis
========
```
java Server [--port <num>] [--timeout <msec>] [--attempts <num>] [--enable-error-message-delivery] [--disable-block-messages]

java Client [--port <num>] [--timeout <msec>] [--attempts <num>] [--mode <mode>] [--enable-error-message-delivery] [--disable-block-messages] <host> {get|put} <source> <destination>
```

Description
===========
`Server` is a basic, multithreaded TFTP server.
`Client` is a basic TFTP client.

Both are compliant with RFC 1350, and have been tested with existing servers and
clients for cross-compatibility. However, it may still have bugs, since this has not been thoroughly tested. This applies especially to NetASCII mode.

Notes
=====
- A short message is printed to `STDOUT` with each major process and packet
acknowledged; use the relevant option to disable this behaviour.
The number in square brackets is the port number of the session from which the
message originates and can be used to differentiate between multiple concurrent
sessions on a server.

- `mail` mode is explicitly not supported and will return an `ERROR`.

- For NetASCII transmission, the following rules apply:

  Original sequence     | NetASCII sequence
  ----------------------|--------------------------
  `LF` (`0x0A`)           | `CR+LF` (`0x0D`, `0x0A`)
  `CR+LF` (`0x0D`, `0x0A`)| `CR+LF` (`0x0D`, `0x0A`)
  `CR` (`0x0D`)           | `CR+NUL` (`0x0D`, `0x00`)
This applies both ways, except when writing files: when the system line
terminator, as determined by Java, is used when the NetASCII sequence `CR+LF` is
encountered; otherwise the conversion is platform-independent.<br><br>
This is partially in line with RFC 764 and behaviour gathered from existing TFTP
servers and clients. The reason why `LF+CR` is considered as separate characters
is due to the possibility of UNIX line termination with one `CR` byte. Since `LF+CR`
is rare, and other TFTP clients/servers behave this way, this behaviour was
chosen for maximum compatibility.

- Block number wraparound is supported, so there is no upper theoretical limit for
the size of the file transmitted.

- Relative and absolute paths are accepted, as long as the file system can accept
them.

- `ERROR` codes are always `0`, except for when a file exists, when `6` is returned.
This is because Java File IO methods do not return specific enough exceptions,
making it difficult to distinguish between error cases.

- It is prone to the [Sorcerer's Apprentice Syndrome](https://en.wikipedia.org/wiki/Sorcerer%27s_Apprentice_Syndrome)


Options
=======
```
    --port <num>
        Sets the communication port (TID) for initial incoming/outgoing request.
        (Default = 69)

    --timeout <msec>
        Sets the number of milliseconds to timeout before resending the last
        packet.
        (Default = 5000)

    --attempts <num>
        Sets the maximum number of attempts of any one operation before
        terminating.
        (Default = 3)

    --mode <mode>
        Sets the transmission encoding for the session. Accepted values are 
        "netascii" and "octet". "mail" is explicitly not supported.
        (Default = "octet")
        
    --enable-error-message-delivery
        Enables delivery of the message in the Java exception in the ERROR
        packet back to the sender. Otherwise, the error message sent is a blank
        string.

        This behaviour is a potential security and privacy risk, and is off by
        default.

    --disable-block-messages
        Disables info messages for when sending ACKs and DATAs. Other messages
        such as errors or timeouts are still printed.
        
        Useful for not spamming terminals.
        
    get
        Retrieve a file located at <source> from the remote host and store it
        locally at the path given by <destination>.

    put
        Send a file located locally at <source> and store it on the remote host
        at the path given by <destination>.
```

Examples
========
- To run a server:

  `java Server`

- To run a server that replies with additional information in `ERROR` replies:

  `java Server --enable-error-message-delivery`

- To run the client with a request to retrieve a file `foo.txt` from the server
located at `localhost` and store it locally as `bar.txt`:

  `java Client localhost get foo.txt bar.txt`

- To run the client with a request to send a file `C:\Users\Alice\abc.pdf` to the
server located at `127.0.0.1` and store it remotely as `/home/bob/xyz.pdf`:

  `java Client 127.0.0.1 put C:\Users\Alice\abc.pdf /home/bob/xyz.pdf`
