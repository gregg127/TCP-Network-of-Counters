package networkOfCounters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Single node in the network
 * @author Grzegorz Golebiowski
 *
 */
public class Agent {
	
	static boolean displayingLogMessages = true;
	private static final String logsDir = System.getProperty("user.home") + "\\logs\\";
	private final String IPAddress = "127.0.0.1"; // runs on localhost
	private final int port;
	private final ServerSocket serverSocket;
	private boolean keepProcessing;
	private volatile long counterValue; // milliseconds
	private volatile List<String> agentsAddressesAndPorts;
	private Thread agentServer;
	private Thread counter;
	private FileWriter fileWriter;
	
	/**
	 * Constructor of first agent in the network
	 * @throws IOException 
	 */
	public Agent() throws IOException {
		serverSocket = new ServerSocket(0); // finds first free port
		port = serverSocket.getLocalPort();
		agentsAddressesAndPorts = new ArrayList<>();
		counterValue = 0;
		fileWriter = new FileWriter(new File(logsDir + "Agent_"+IPAddress+"("+port+")_first.txt"));
		keepProcessing = true;
		appendToLogActivity(info() + "Created as first agent");
		initTimerThread();
		initAgentServerThread();
		start(); // runs threads
	}
	
	/**
	 * Constructor with introducing agent's port. Used when there's at least one agent in the network
	 * @param initCounterVal counter value to be set at the very beginning
	 * @param introAgentPort introducing agent's port
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 * @throws InterruptedException 
	 */
	public Agent(long initCounterVal, int introAgentPort) throws UnknownHostException, IOException, ClassNotFoundException, InterruptedException {
		serverSocket = new ServerSocket(0);
		port = serverSocket.getLocalPort();
		counterValue = initCounterVal;
		agentsAddressesAndPorts = new ArrayList<>();
		fileWriter = new FileWriter(logsDir + "Agent_"+IPAddress+"("+port+").txt");
		keepProcessing = true;
		initTimerThread();
		initAgentServerThread();
		start(); // runs threads
		appendToLogActivity(info() + "Created with introducing agent on port: " + introAgentPort);
		agentsAddressesAndPorts = getAgentListFromIntroAgent(introAgentPort); // NET - sets agents list - SHORTEN
		agentsAddressesAndPorts.add(IPAddress+":"+introAgentPort); // adds introducing agent to list
		sendIPAndPortToOtherAgents(); // UPD - sends to each agent IP address and port
		counterValue = getAverageOfCounterValue(); //CLK - sets counterValue to average of all agents' counters in the network
		synchronizeCounters(); // SYN - sends to every agent SYN flag, which synchronizes counters
	}
	
	/**
	 * Creates agent-server thread responsible for accepting connections and processing requests
	 */
	private void initAgentServerThread() {
		agentServer = new Thread( () -> {
			try {
				while(keepProcessing) {
					appendToLogActivity("\t" + info() + "Waiting for a connection ...");
					Socket socket = serverSocket.accept();
					answerToClient(socket);
				}
			} catch (IOException | NumberFormatException | ClassNotFoundException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Processes socket connection?
	 * @param socket
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException
	 */
	private void answerToClient(Socket socket) throws IOException, InterruptedException, ClassNotFoundException {
		String receivedData = MessageUtils.getMessage(socket);
		String clientData = MessageUtils.getIPAndPortFromSegment(receivedData);
		String flagReceived =  MessageUtils.getFlagFromSegment(receivedData);
		appendToLogActivity("\t" + info() + "Connected to agent: " + clientData);
		appendToLogActivity("\t" + info() + "Flag received: " + flagReceived);
		switch(flagReceived) {
			case "NET": // sends ArrayList<String> of agents IPs and ports
				MessageUtils.sendObject(socket, agentsAddressesAndPorts);
				appendToLogActivity("\t"+info()+"agents list sent to: " + clientData);
				break;
			case "CLK": // sends counter value
				MessageUtils.sendObject(socket, counterValue);
				appendToLogActivity("\t"+info()+"Timer's value sent to: " + clientData);
				break;
			case "SYN": // sets counter value to average of all agents' counters
				counterValue = getAverageOfCounterValue();
				MessageUtils.sendMessage(socket, "ACK\n");
				appendToLogActivity("\t"+info()+"Timer synchronized");
				break;
			case "UPD": // update list with new agent
				agentsAddressesAndPorts.add(clientData);
				MessageUtils.sendMessage(socket, "ACK\n");
				appendToLogActivity("\t"+info()+"Updated list with: " + clientData);
				break;
			case "DEL": // deletes agent from list
				agentsAddressesAndPorts.removeIf(e -> e.equals(clientData));
				MessageUtils.sendMessage(socket, "ACK\n");
				appendToLogActivity("\t"+info()+"Deleted agent: " + clientData);
				break;
			default:
				appendToLogActivity("\t" + info() + "Received incorrect flag from: " + clientData);
				MessageUtils.sendMessage(socket, "INCORRECT FLAG");
		}
		appendToLogActivity("\t" + info() + "Connection finished with: " + clientData);
		socket.close();
	}


	/** 
	 * Sends NET flag to introducing agent
	 * @param introPort
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<String> getAgentListFromIntroAgent(int introPort) throws IOException, ClassNotFoundException {
		appendToLogActivity(info() + "Sending request for agents list (NET flag) to agent on port: " + introPort);
		Socket socket = new Socket(IPAddress, introPort);
		MessageUtils.sendMessage(socket, MessageUtils.getSegment("NET", IPAddress, port));
		ArrayList<String> objectReceived = (ArrayList<String>) MessageUtils.getObject(socket);
		appendToLogActivity(info() + "Received agents list from agent on port: " + introPort);
		socket.close();
		return objectReceived;
	}

	/**
	 * Sends to each agent in the list segment with UPD flag in order to update contact lists with sender's IP Address and port
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	private void sendIPAndPortToOtherAgents() throws UnknownHostException, IOException, InterruptedException {
		for(String s : agentsAddressesAndPorts) {
			String temp[] = s.split(":");
			String receiverIP = temp[0];
			int receiverPort = Integer.parseInt(temp[1]);
			appendToLogActivity(info() + "Sending data (UPD flag) to agent: " + s);
			Socket socket = new Socket(receiverIP, receiverPort);
			MessageUtils.sendMessage(socket,  MessageUtils.getSegment("UPD", IPAddress, port));
			MessageUtils.getMessage(socket); // gets response
			socket.close();
		}
	}

	/**
	 * Calculates and returns average of all agent's counters in the network
	 * @return average of all agent's counters
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws NumberFormatException
	 * @throws ClassNotFoundException
	 * @throws InterruptedException
	 */
	public long getAverageOfCounterValue() throws IOException, NumberFormatException, ClassNotFoundException, InterruptedException {
		long sum = counterValue;
		for(String s : agentsAddressesAndPorts) {
			String ipAndPort[] = s.split(":");
			String receiverIP = ipAndPort[0];
			int receiverPort = Integer.parseInt(ipAndPort[1]);
			appendToLogActivity(info()+"Receiving timer value for calculating average (CLK flag) from: " + s);
			Socket socket = new Socket(receiverIP, receiverPort);
			MessageUtils.sendMessage(socket, MessageUtils.getSegment("CLK", IPAddress, port));
			sum += Long.parseLong(MessageUtils.getObject(socket).toString());
			socket.close();
		}
		appendToLogActivity(info() + "Average set to: " + sum/(agentsAddressesAndPorts.size()+1));
		return sum/(agentsAddressesAndPorts.size()+1);
	}
	
	/**
	 * Sends to every agent in the list SYN flag in order to synchronize each agent's counter
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void synchronizeCounters() throws IOException, InterruptedException {
		for(String s : agentsAddressesAndPorts) {
			String ipAndPort[] = s.split(":");
			String receiverIP = ipAndPort[0];
			int receiverPort = Integer.parseInt(ipAndPort[1]);
			appendToLogActivity(info()+ "Synchronizing timers (SYN flag) for: " + s);
			Socket socket = new Socket(receiverIP, receiverPort);
			MessageUtils.sendMessage(socket,  MessageUtils.getSegment("SYN", IPAddress, port));
			MessageUtils.getMessage(socket); // gets response
			socket.close();
		}
	}

	/**
	 * Removes agent from the network
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void removeAgentFromNetwork() throws IOException, InterruptedException {
		for(String s : agentsAddressesAndPorts) {
			String ipAndPort[] = s.split(":");
			String receiverIP = ipAndPort[0];
			int receiverPort = Integer.parseInt(ipAndPort[1]);
			appendToLogActivity(info()+ "Deletion request (DEL) sent to agent: " + s);
			Socket socket = new Socket(receiverIP, receiverPort);
			MessageUtils.sendMessage(socket,  MessageUtils.getSegment("DEL", IPAddress, port));
			MessageUtils.getMessage(socket); // gets response
			socket.close();
		}
		synchronizeCounters();
		stopThisAgent();
	}

	/**
	 * Appends to activity log (Agent_IP(port).txt in current directory) and writes to standard output if displayingLogMessages is true
	 * @param logMsg
	 */
	private void appendToLogActivity(String logMsg) {
		try {
			if(displayingLogMessages) {
				System.out.println(logMsg);
			}
			fileWriter.write(MessageUtils.SDF.format(System.currentTimeMillis()) +": " + logMsg + "\n");
			fileWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets counter value and synchronizes all agents' counter
	 * @param milliseconds
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void setCounter(long milliseconds) throws UnknownHostException, IOException, InterruptedException {
		counterValue = milliseconds;
	}
	
	/**
	 * Initializes thread that increases counter every millisecond while keepProcessing is true
	 */
	private void initTimerThread() {
		counter = new Thread(() -> {
			try {
				while(keepProcessing) {
					counterValue++;
						Thread.sleep(1);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	/**
	 * Starts counter and agentServer thread
	 */
	private void start() {
		agentServer.start();
		counter.start();
	}
	
	private String info() {
		return this+" -> ";
	}
	
	private void stopThisAgent() {
		keepProcessing = false;
	}
	
	public String getIP() {
		return IPAddress;	
	}
	
	public int getPort() {
		return port;
	}
	
	public long getTimerValue() {
		return counterValue;
	}
	
	@Override
	public String toString() {
		return IPAddress+":"+port;
	}
}