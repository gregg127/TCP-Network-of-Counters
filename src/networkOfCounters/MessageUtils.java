package networkOfCounters;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;

/**
 * Additional class for sending data to agents and parsing sent segments  
 * @author Grzesiek
 *
 */
public class MessageUtils {
	
	public static final int FLAG_START_INDEX = 0;
	public static final int FLAG_END_INDEX = 3;
	public static final int SEGMENT_IP_AND_PORT_INDEX = 1;
	public static final String SEGMENT_SEPARATOR = "->";
	public static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS"); // time format with ms
	
	/**
	 * Sends given message to a given socket
	 * @param socket
	 * @param msg
	 * @throws IOException
	 */
	public static void sendMessage(Socket socket, String msg) throws IOException {
		OutputStream oStream = socket.getOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(oStream);
		oos.writeUTF(msg);
		oos.flush();
	} 
	
	/**
	 * Sends Object to a given socket
	 * @param socket
	 * @param obj
	 * @throws IOException
	 */
	public static void sendObject(Socket socket, Object obj) throws IOException {
		OutputStream oStream = socket.getOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(oStream);
		oos.writeObject(obj);
		oos.flush();
	}
	
	/**
	 * Returns server answer as String
	 * @param socket
	 * @return
	 * @throws IOException
	 */
	public static String getMessage(Socket socket) throws IOException {
		InputStream iStream = socket.getInputStream();
		ObjectInputStream ois = new ObjectInputStream(iStream);
		return ois.readUTF();
	}
	
	/**
	 * Returns server answer as Object
	 * @param socket
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object getObject(Socket socket) throws IOException, ClassNotFoundException {
		InputStream iStream = socket.getInputStream();
		ObjectInputStream ois = new ObjectInputStream(iStream);
		return ois.readObject();
	}
	
	/**
	 * Adds to given flag IP address and port
	 * @param flag
	 * @return String of properly formatted segment with given flag
	 */
	public static String getSegment(String flag, String IPAddress, int port) {
		return flag + MessageUtils.SEGMENT_SEPARATOR + IPAddress + ":" + port;
	}
	
	/**
	 * Returns flag from a given segment
	 * @param segment
	 * @return flag
	 */
	public static String getFlagFromSegment(String segment) {
		return segment.substring(FLAG_START_INDEX, FLAG_END_INDEX);
	}
	
	/**
	 * Returns IP and port of a given segment
	 * @param data
	 * @return ipAndPort
	 */
	public static String getIPAndPortFromSegment(String data) {
		String[] splitedSegment = data.split(SEGMENT_SEPARATOR); // e.g. UDP->127.0.0.1:8888
		return splitedSegment[SEGMENT_IP_AND_PORT_INDEX];
	}
}
