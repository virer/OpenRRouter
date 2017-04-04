/*
OpenRViewer Router part, connecting viewer part using RFB protocol to take remote control.
Copyright (C) 2017 Sebastien CAPS

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * OpenRViewer Router part
 * @author: Sebastien CAPS
 */
package orv.Router;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import org.apache.logging.log4j.core.Logger;

public class RouterClass implements Runnable {
	/**
	 * Client socket instance provided in constructor of this class.
	 */
	Socket clientSocket;
	
	/**
	 * Remote IP
	 */
	String address = "127.0.0.1";
	
	/**
	 * Input stream instance.
	 */
	BufferedInputStream in;
	
	/**
	 * Output stream instance.
	 */
	BufferedOutputStream out;
	
	/**
	 * OpenRViewer messageType protocol
	 */
	private final static int auth_pw          = 2;
	private final static int auth_ok          = 3;
	private final static int auth_failed      = 4;
	
	/**
	 * Use to known the state of a client: registered or not as RCuser or manager
	 */
	boolean registered = false;
	
	/**
	 * Is th password was accepted by RCuser
	 */
	boolean authenticated = false;
	
	/**
	 * Is the client socket a manager?
	 */
	boolean manager = false;
	
	/**
	 * Identifier use to connect a manager an RCuser together
	 */
	String serverId = "N/A";
	
	/** 
	 * Client type: 0 = RCuser ; 1=manager
	 */
	int clientType = 9;
	
	/**
	 * Main server class access
	 */
	Server server = Server.getServer();
	
	/**
	 * Client version
	 */
	private String version = "";
	
	Logger log4j = null; 
	
	/**
	 * Instance of RFB service.
	 * 
	 * @param clientSocket client socket when a new connection is accepted
	 * 
	 * @throws IOException
	 */
	public RouterClass(Socket clientSocket, Logger log4j) throws IOException {
		this.clientSocket = clientSocket;
		this.log4j = log4j;
		
		/*
		 * Create input and output streams. They will be used to write and read bytes.
		 */
		this.in = new BufferedInputStream(clientSocket.getInputStream());
		this.out = new BufferedOutputStream(clientSocket.getOutputStream());
	}
	
	/**
	 * Show/log an error message and disconnect client 
	 * @param log
	 */
	private void errDisconnect(String log) {
		err(log);
		try {
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Disconnect RCuser and manager socket, ensure all authentification and identifcation are removed
	 */
	private void safeRemove() {
		// Disconnect the other peer (if Iam a RCuser then I disconnect the manager and vice, versa)
		server.disconnectPeer(this.clientType, this.serverId);
		
		Server.oRvClientList.remove(this);
		authenticated = false;
		this.serverId = "N/A";
		try {
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Authentication phase (messageType = 2)
	 */
	private boolean authenticationPhase() {
		debug("Authentication phase from "+ (this.manager ? "manager" : "RCuser") + " for serverId=" + this.serverId + " from: " + this.address );
		try {
			in.reset(); // rewind stream
			int max = in.available();
			byte[] data = new byte[max];
			in.read(data, 0, max);
			// Send data to peer
	        server.sendToPeer(data, this.clientType, this.serverId, true);
	        return true;
		} catch (IOException e) {
			errDisconnect("Apw: Something wrong detected for " + this.serverId + " from "+ this.address + " disconnecting... :" +e.getMessage());
		}
		return false;
	}
	
	/**
	 * RCuser reply with authentication phase done and the password was good (messageType = 3)
	 * Only allowed if the auth_ok message type comes from the RCuser side
	 */
	private boolean authentificationOk() {
		if(clientType == lib.clientType_rcuser) {
			debug("Authentication successfull for serverId=" + this.serverId);
			try {
				in.reset(); // rewind stream
				int max = in.available();
				byte[] data = new byte[max];
				in.read(data, 0, max);
				// Send data to peer
		        server.sendToPeer(data, this.clientType, this.serverId, true);
		        return true;
			} catch (IOException e) {
				safeRemove();
				errDisconnect("Aok: "+e.getMessage());
			}
		} else {
			safeRemove();
		}
		return false;
	}
	
	/**
	 * RCuser reply with authentication phase done, the password was not good (messageType = 4)
	 * Only allowed if the auth_nok message type comes from the RCuser side
	 */
	private boolean authenticationFailed(){
		if(clientType == lib.clientType_rcuser) {
			debug("Authentication Unsuccessfull for manager serverId=" + this.serverId + " disconnecting");
			server.disconnectPeer(this.clientType, this.serverId);
			return true;
		} else {
			Server.oRvClientList.remove(this);
			this.authenticated = false;
			this.serverId = "N/A";
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	/**
	 * Parse client info
	 */
	private boolean parseClientInfo(String header) {
		version = header.substring(header.indexOf("User-Agent: ")+24, header.indexOf("Accept:")-1).trim();
		String clientTypeStr = header.substring(header.indexOf("Cookie: ")+8, header.indexOf("=")).trim();;
		if(clientTypeStr.equals("MANAGER")) {
			this.clientType = 1;
			this.manager = true;
		} else if(clientTypeStr.equals("RCUSER")) {
			this.clientType = 0;
		} else {
			System.out.println("CT!"+clientTypeStr+" "+version);
			safeRemove();
			return false;
		}
		String tmpserverId = header.substring(header.indexOf("=")+1, header.indexOf("Pragma: ")-1).trim();
		if(tmpserverId.length() != 14) {
			System.out.println("L:"+tmpserverId.length());
			safeRemove();
			return false;
		} else {
			server.disconnectOtherUserWsameId(tmpserverId, clientType);
			serverId=tmpserverId;
			tmpserverId=null;
		}
		return true;
	}
	
	public void run() {
		address = clientSocket.getInetAddress().getHostAddress();
	    try {
		 	/*
			 * Main loop where clients messages are read from socket.
			 */
	    	int max = 0;
	    	int messageType = 9;
	    	
	    	byte[] data= new byte[1];
	    	while (clientSocket.isConnected() && !clientSocket.isClosed()) {
	    		if(!authenticated) {
	    			if(!registered) {
	    				in.mark(1); // enable buffer rewind 1 byte to send all received data to peer
	    				messageType = in.read();
	    				if (messageType == -1) break;
	    				in.reset();
	    				max = in.available();
	    				data = new byte[max];
	    				in.read(data, 0, max);
	    				String header = new String(data);
	    				if(header.indexOf("/openrviewer/") == -1) {
	    					log("Not an openrviewer client => closing!"); 
	    					break;  
	    				} else {
	    					if(parseClientInfo(header)) {
	    						log("New "+(this.manager ? "manager " : "RCuser ")+" with version="+version+" with serverId=" + this.serverId + " from: " + address );
	    						out.write(lib.genHttpHeaderReply(1).getBytes());
	    						out.write(0x00);
	    						out.flush();
	    						registered = true;
	    					} else {
	    						log("Error parsing openrviewer client informations => closing!");
	    						break;
	    					}
	    				}
	    			} else {
	    			
	    			messageType = 9;
	    			
	    			try {
	    				in.mark(1); // enable buffer rewind 1 byte to send all received data to peer
	    				messageType = in.read();
	    				if (messageType == -1) break;
	    				if(authenticated) { in.reset(); }	
	    			} catch (IOException e) {
	    				errDisconnect(e.getMessage());
	    				break;
	    			}
	    			
	    			switch(messageType) {
	    				case auth_pw:
	    					authenticationPhase();
	    					break;
	    				case auth_ok:
	    					if(authentificationOk()) {
	    						server.changeManagerStatus(this.serverId);
	    						authenticated = true;
	    					}
	    					break;
	    				case auth_failed:
	    					authenticationFailed();
	    					break;
	    				default:
		    				try {
		    					Thread.sleep(100); // Pause for 0.1 seconds
		    				} catch (InterruptedException e) {
		    					debug(e.getMessage());
		    				}
	    					break;
	    				}	
	    			}
				} else { 
					/*
					 * Here the RCuser and the manager are authenticated
					 */
					in.mark(1); // enable buffer rewind 1 byte to send all received data to peer
					in.read();
					in.reset();
					max = in.available();
					if(max > 0 ) {
						data = new byte[max];
						in.read(data, 0, max);
						server.sendToPeer(data, this.clientType, this.serverId, true);
					}
				}
			} // end while
	    	Server.oRvClientList.remove(this);
	    	log((this.manager ? "manager: " : "RCuser: ")  + " from "+ this.address + " Client connection closed.");
		} catch (SocketException e) {		
			log((this.manager ? "manager: " : "RCuser: ")  + " from "+ this.address + " Client connection closed!");
			safeRemove();
		} catch (IOException e) {
			err((this.manager ? "manager: " : "RCuser: ")  + " from "+ this.address + ">>" + e.getMessage());
			safeRemove();
		}
	}
	
	private void log(String line) {
		log4j.info(line);
	}
	
	private void debug(String line) {
		log4j.debug(line);
	}

	private void err(String line) {
		log4j.error(line);
	}
}
