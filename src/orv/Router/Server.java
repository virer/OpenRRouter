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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLServerSocketFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Server {
	private static final Server Server = new Server();
	static Logger log4j = null; 
	static lib lib = new lib();
	/**
	 * List of open OpenRViewer sessions. 
	 */
	public static List<RouterClass> oRvClientList = new ArrayList<RouterClass>();
	
	public static void main(String[] args) {
		int port = 443;
		for (String s: args) {
			 	if(s.startsWith("-port=")) {
			 		String[] params = s.split("="); 
			 		port = Integer.valueOf(params[1]);
			 	}     
	    }
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
    	File file = new File("config/log4j2.properties");
    	context.setConfigLocation(file.toURI());
    	log4j = context.getLogger("OpenRRouter");
    	log4j.setLevel(Level.DEBUG);
    	
		ServerSocket serverSocket;
		try {
			SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
			serverSocket = ssf.createServerSocket(port);
		    
			log4j.info("Server started on port "+ port);
			/*
			 * Limit sessions number to 1024. 
			 * TODO detect/block too many connections from one IP
			 */
			while (oRvClientList.size() < 1024) {
				Socket client = serverSocket.accept();
				client.setTcpNoDelay(true);
				log4j.info("New connection from : "+ client.getInetAddress().getHostAddress() );
				RouterClass oRvService = new RouterClass(client, log4j);
			
				/*
				 * Add it to the list to be able to send data from manager or RCuser
				 */
				oRvClientList.add(oRvService);
			
				(new Thread(oRvService, "ORVService" + oRvClientList.size())).start();			
			}
		serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	public static Server getServer() {
		return Server;
	}
	
    public void changeManagerStatus(String senderServerId) {
    	for (RouterClass cs : oRvClientList ) {
    		if(!cs.clientSocket.isClosed() && cs.clientType == orv.Router.lib.clientType_manager && senderServerId.equals(cs.serverId)) {
    			cs.authenticated = true;
    		}
    	}  
    }
    
    public void disconnectPeer(int senderClientType, String senderServerId) {
    	for (RouterClass cs : oRvClientList) {
    		if (!cs.clientSocket.isClosed() && cs != null && cs.serverId != "N/A" && senderServerId != "N/A" && senderServerId.equals(cs.serverId) && cs.clientType != senderClientType && cs.authenticated) {
    			try {
    				cs.serverId = "N/A";
    				cs.authenticated = false;
					cs.clientSocket.close();
				} catch (IOException e) {
					log4j.debug(e.getMessage());
				}
            }
          }
    }
    
    public void disconnectOtherUserWsameId(String serverId, int clientTypeToDisconnect) {
    	for (RouterClass cs : oRvClientList) {
    		if (!cs.clientSocket.isClosed() && cs != null && cs.serverId != "N/A" && serverId != "N/A" && serverId.equals(cs.serverId) && cs.clientType == clientTypeToDisconnect ) {
    			try {
    				cs.serverId = "N/A";
    				cs.authenticated = false;
					cs.clientSocket.close();
				} catch (IOException e) {
					log4j.debug(e.getMessage());
				}
            }
          }
    }
    
    public boolean isRCuserConnected(String senderServerId) {
    	boolean result = false;
    	for (RouterClass cs : oRvClientList) {
    		if(!cs.clientSocket.isClosed() && cs.clientType == orv.Router.lib.clientType_rcuser && senderServerId.equals(cs.serverId)) {
    			result = true;
    		}
    	}
    	return result;
    }
    
    public boolean isRCuserAuthenticated(String senderServerId) {
    	boolean result = false;
    	for (RouterClass cs : oRvClientList) {
    		if(!cs.clientSocket.isClosed() && cs.clientType == orv.Router.lib.clientType_rcuser && senderServerId.equals(cs.serverId)) {
    			result = cs.authenticated;
    		}
    	}
    	return result;
    }
    
    public void sendToPeer(byte b[], int senderClientType, String senderServerId, boolean flush) {
         for (RouterClass cs : oRvClientList) {
          if (cs.clientSocket.isConnected() && !cs.clientSocket.isClosed() && cs != null && cs.serverId != "N/A" && senderServerId != "N/A" && senderServerId.equals(cs.serverId) && cs.clientType != senderClientType) {
        	    if(!cs.authenticated) {
        	    	log4j.debug("Sending "+b.length+" bytes to "+(cs.manager ? "manager" : "RCuser")+" ("+cs.clientType+")" );
        	    }
                try {
                    cs.out.write(b);
                    if(flush) cs.out.flush();
                } catch (Exception e) {
                    log4j.error("Error when sending to " + cs.serverId + " (" + cs.address + "):");
                	try {
                		disconnectPeer(senderClientType, senderServerId);
                		cs.serverId = "N/A";
        				cs.authenticated = false;
						cs.clientSocket.close();
					} catch (IOException e1) {
						log4j.debug(e1.getMessage());
					}
                	
                    String msg = e.getMessage();
                    if(msg != null) log4j.error(e.getMessage());
                }
          }
        }
    }
}
