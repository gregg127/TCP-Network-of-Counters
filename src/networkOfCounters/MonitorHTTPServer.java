package networkOfCounters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP server which controls the network
 * @author Grzegorz Golebiowski
 *
 */
public class MonitorHTTPServer {
	
	private static final int PORT = 8080;
	private static final int EMPTY_GET_APPROX_LENGTH = "GET /? HTTP/1.1 ".length();
	private static volatile List<Agent> agents = new ArrayList<Agent>();
	private static ServerSocket httpServerSocket;
	private static Thread serverThread;
	
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException{
		startServer();
	}
	
	/**
	 * Initializes and starts server thread
	 * @throws IOException
	 */
	public static void startServer() throws IOException{
		serverThread = new Thread( () -> {
			try {
				httpServerSocket = new ServerSocket(PORT);
				displayServerInfo("Server started");
				while(true) {
					Socket socket = httpServerSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
					String req = getRequest(in);
					processRequest(req);
			        sendHeader(out);
			        sendAddFirstAgentOption(out);
			        sendAgentsGroup(out);
			        close(socket, out, in);
				}
			} catch (IOException | NumberFormatException | ClassNotFoundException | InterruptedException ex) {
				ex.printStackTrace();
			}
		});
		serverThread.start();
	}

	/**
	 * Does certain actions according to a given request
	 * @param request
	 * @throws IOException
	 * @throws NumberFormatException
	 * @throws ClassNotFoundException
	 * @throws InterruptedException
	 */
	private static void processRequest(String request) throws IOException, NumberFormatException, ClassNotFoundException, InterruptedException {
		if(isRequestEmpty(request))
			return;
		else if(isRequestSingle(request))
			singleValueRequestProcess(request);
		else 
			doubleValuesRequestProcess(request);
	}

	private static boolean isRequestEmpty(String request) {
		return request == null || request.length() <=	 EMPTY_GET_APPROX_LENGTH;
	}

	/**
	 * Checks if GET request is single
	 * @param request
	 * @return true if request is single
	 */
	private static boolean isRequestSingle(String request) {
		return !request.contains("&");
	}

	/**
	 * Processes request when GET is single
	 * @param request
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException 
	 * @throws NumberFormatException 
	 */
	private static void singleValueRequestProcess(String request) throws IOException, InterruptedException, NumberFormatException, ClassNotFoundException {
		String name = RequestUtils.getReqName(request, RequestUtils.SINGLE);
		String value = RequestUtils.getReqValue(request, RequestUtils.SINGLE);
		if(name == null && value == null) 
			return;
		else if (name.equals("flag")) 
			processFlagRequest(value);
		else if(name.equals("agentAction"))
			processAgentActionRequest(value);
	}

	/**
	 * Processes GET request with flags SYN and DEL
	 * @param value
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException 
	 * @throws NumberFormatException 
	 */
	private static void processFlagRequest(String value) throws IOException, InterruptedException, NumberFormatException, ClassNotFoundException {
		if(value.contains("SYN")) {
			processSYNRequest(value);
		} else if(value.contains("DEL")) {
			processDELRequest(value);			
		}
	}
	
	private static void processSYNRequest(String value) throws IOException {
		for(Agent a : agents) {
			String agentInfo = value.substring(3); // IP Address and port
			if(a.toString().equals(agentInfo)) {
				displayServerInfo("Sending SYN flag ... ");
				Socket socket = new Socket(a.getIP(), a.getPort());
				MessageUtils.sendMessage(socket, MessageUtils.getSegment("SYN", a.getIP(), a.getPort()));
				MessageUtils.getMessage(socket);
			}
		}
	}
	private static void processDELRequest(String value) throws IOException, InterruptedException {
		Agent agentToDelete = null;
		for(Agent a : agents) {
			String agentInfo = value.substring(3); // IP Address and port
			if(a.toString().equals(agentInfo)) {
				displayServerInfo("Deleting agent");
				agentToDelete = a;
				break;
			}
		}
		if(agentToDelete != null)
			agents.remove(agents.indexOf(agentToDelete)).removeAgentFromNetwork();
	}

	/**
	 * Processes single requests with name parameter set to agentAction
	 * @param value
	 * @throws IOException
	 */
	private static void processAgentActionRequest(String value) throws IOException {
		if(value.equals("addFirstAgent") && agents.isEmpty()) {
			displayServerInfo("Adding first agent in the network");
			agents.add(new Agent());
		} else if(value.equals("toggle")) {
			toggleLogMessages();
			displayServerInfo("Toggled logs");
		}
	}

	private static void toggleLogMessages() {
		if(Agent.displayingLogMessages)
			Agent.displayingLogMessages = false;
		else
			Agent.displayingLogMessages = true;
	}

	/**
	 * Processes requests with doubled parameters e.g. timerValue=12 and agentAction=IPaddress:port
	 * @throws NumberFormatException
	 * @throws UnknownHostException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static void doubleValuesRequestProcess(String request) throws NumberFormatException, ClassNotFoundException, IOException, InterruptedException {
		String[] doubleValReq = request.split("&");
		String timerValue = RequestUtils.getReqValue(doubleValReq[0], RequestUtils.DOUBLE);
		String agentAddValue = RequestUtils.getReqValue(doubleValReq[1], RequestUtils.SINGLE);
		if(timerValue == null) {
			return ; // counter value not set
		} else {
			String[] data = agentAddValue.split(":");
			displayServerInfo("Adding new agent with intro agent on port " + data[1]);
			agents.add(new Agent(Long.parseLong(timerValue),Integer.parseInt(data[1])));
		}
	}

	/**
	 * Returns GET request
	 * @param in
	 * @return GET
	 * @throws IOException
	 */
	private static String getRequest(BufferedReader in) throws IOException {
		try {
			String getRequestLine;
			while((getRequestLine = in.readLine()) != null) {
				if(getRequestLine.contains("GET")) {
					return URLDecoder.decode(getRequestLine,"UTF-8");
				}
				if(getRequestLine.isEmpty()){
					break;
				}
			}
			return null;
		} catch (SocketException ex) {
			System.err.println(ex.getMessage());
			return null;
		}
	}

	/**
	 * Sends HTTP header
	 * @param out
	 * @throws IOException
	 */
	private static void sendHeader(BufferedWriter out) throws IOException {
        out.write("HTTP/1.0 200 OK\r\n");
        out.write("Date: "+DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")))+"\r\n");
        out.write("Content-Type: text/html; charset=\"UTF-8\"\r\n");
        out.write("\r\n"); // CRLF - carriage return + line feed
        out.write("<title>Siec licznikow</title>");
	}

	/**
	 * Sends HTML code form of refresh, toggle and addFirstAgent button
	 * @param out
	 * @throws IOException
	 */
	private static void sendAddFirstAgentOption(BufferedWriter out) throws IOException {
	    out.write("<form action=\"\" method=\"get\"><div>\r\n");	
	    out.write("</br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<button type=\"submit\">REFRESH</button>\r\n");
	    out.write("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<button type=\"submit\" name=\"agentAction\" value=\"toggle\">TOGGLE LOGS</button></div></br></br>\r\n");out.write("<label for=\"say\">Kliknij aby dodac pierwszego agenta</label>\r\n");
	    out.write("<button name=\"agentAction\" type=\"submit\" value=\"addFirstAgent\">ADD AGENT</button></div>\r\n");
	    out.write("</form><br/>\r\n");	    		
	}

	/**
	 * Sends HTML code with list of all agents in the network
	 * @param out
	 * @throws IOException
	 */
	private static void sendAgentsGroup(BufferedWriter out) throws IOException {
		out.write("");
		out.write("<table>\r\n");
		for(Agent a : agents) {
		    out.write("<form action=\"\" method=\"get\">");	
			out.write("Agent: "+a+" "+" || Wartosc licznika: "+a.getTimerValue()+ " ");
			out.write("<button name=\"flag\" type=\"submit\" value=\"SYN"+a+"\">SEND SYN</button>\r\n ");
			out.write("<button name=\"flag\" type=\"submit\" value=\"DEL"+a+"\">SEND DEL</button>\r\n<br/> ");
			out.write("</form>\r\n");
			
			out.write("<form action=\"\" method=\"get\">");
			out.write("<label for=\"say\">Wprowadz nowego agenta. Licznik: </label>");
			out.write("<input type=\"number\" min=\"0\" max=\"1000000000\" name=\"timerValue\">");
			out.write("<button type=\"submit\" name=\"agentAction\" value=\"addAgentByPort"+a+"\">ADD AGENT</button></div>");
			out.write("</form><br/><br/><br/>\r\n");
		}
		out.write("</table>\r\n");
	}	
	
	/**
	 * Displays text in console
	 * @param string
	 */
	private static void displayServerInfo(String string) {
		if(Agent.displayingLogMessages)
			System.out.println("\n\t=== "+string+" ===\n");
	}
	
	/**
	 * Closes connection
	 * @param socket
	 * @param out
	 * @param in
	 * @throws IOException
	 */
	private static void close(Socket socket, BufferedWriter out, BufferedReader in) throws IOException {
        try {
        	out.close();
            in.close();
            socket.close();
        } catch(SocketException ex) {
        	ex.printStackTrace(); 
        	//System.err.println("\nServer executed\n");
        	//System.exit(1);
        }
	}
	
}
