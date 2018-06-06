package edu.wisc.cs.sdn.sr.vns;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.MACAddress;

import edu.wisc.cs.sdn.sr.Router;
import edu.wisc.cs.sdn.sr.Iface;

public class VNSComm 
{
	private Socket socket;
	private Router router;
	
	public VNSComm(Router router)
	{ this.router = router; }
	
	public boolean connectToServer(short port, String server)
	{
		// Grab server address from name
		InetAddress addr;
		try 
		{ addr = InetAddress.getByName(server); }
		catch (UnknownHostException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Create socket and attempt to connect to the server
		try 
		{ socket = new Socket(addr, port); }
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Wait for authentication to be completed (server sends the first message)
		if (!this.readFromServerExpect(Command.VNS_AUTH_REQUEST)
				|| !this.readFromServerExpect(Command.VNS_AUTH_STATUS))
		{
			// Failed to receive expected message
			return false;
		}
		
		byte[] buf;
		
		if (this.router.getTemplate() != null)
		{
			// Send VNS_OPEN_TEMPLATE message to server
			CommandOpenTemplate cmdOpenTemplate = new CommandOpenTemplate();
			cmdOpenTemplate.templateName = this.router.getTemplate();
			cmdOpenTemplate.mVirtualHostId = this.router.getHost();
			buf = cmdOpenTemplate.serialize();
		}
		else
		{
			// Send VNS_OPEN message to server
			CommandOpen cmdOpen = new CommandOpen();
			cmdOpen.topoId = this.router.getTopo();
			cmdOpen.mVirtualHostId = this.router.getHost();
			cmdOpen.mUID = this.router.getUser();
			buf = cmdOpen.serialize();
		}
		
		try
		{
			OutputStream outStream = socket.getOutputStream();
			outStream.write(buf);
            outStream.flush();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		if (this.router.getTemplate() != null)
		{
			if (!this.readFromServerExpect(Command.VNS_RTABLE))
			{
				// Needed to get the rtable
				return false;
			}
		}
		
		return true; 
	}
	
	private boolean handleHwInfo(CommandHwInfo cmdHwInfo)
	{
		Iface lastIface = null;
		for (CommandHwEntry hwEntry : cmdHwInfo.mHwInfo)
		{
			switch(hwEntry.mKey)
			{
			case CommandHwEntry.HW_FIXED_IP:
				break;
			case CommandHwEntry.HW_INTERFACE:
				lastIface = this.router.addInterface(
                        new String(hwEntry.value).trim());
				break;
			case CommandHwEntry.HW_SPEED:
				break;
			case CommandHwEntry.HW_SUBNET:
				break;
			case CommandHwEntry.HW_MASK:
				lastIface.setSubnetMask(ByteBuffer.wrap(hwEntry.value).getInt());
				break;
			case CommandHwEntry.HW_ETH_IP:
				lastIface.setIpAddress(ByteBuffer.wrap(hwEntry.value).getInt());
				break;
			case CommandHwEntry.HW_ETHER:
				lastIface.setMacAddress(new MACAddress(hwEntry.value));
				break;
			default:
				System.out.println(String.format(" %d", hwEntry.mKey));
			}
		}
		
		System.out.println("Router interfaces:");
		if (0 == this.router.getInterfaces().size())
		{ System.out.println(" Interface list empty"); }
		else
		{
			for (Iface iface : this.router.getInterfaces().values())
			{ System.out.println(iface.toString()); }
		}
		
		return true;
	}
	
	public boolean handleRtable(CommandRtable cmdRtable)
	{
		String filename = String.format("rtable.%s.", cmdRtable.mVIrtualHostId);
		try
		{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			writer.write(cmdRtable.rtable);
			writer.close();
			return true;
		}
		catch(IOException e)
		{
			System.err.println("Unable to write new rtable file");
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean handleAuthRequest(CommandAuthRequest cmdAuthRequest)
	{
		// TODO
		
		// Read in the user's auth key
		String auth_key = null;
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader("auth_key"));
			auth_key = reader.readLine().trim();
		}
		catch(IOException e)
		{
			System.err.println("Unable to read credentials form 'auth_key' file");
			e.printStackTrace();
			return false;
		}
		
		// Compute the salted SHA1 of password from auth_key
		ByteBuffer sha1 = null;
	    try 
	    {
	    	MessageDigest md = MessageDigest.getInstance("SHA-1");
	    	String convert = cmdAuthRequest.salt + auth_key;
	    	sha1 = ByteBuffer.wrap(md.digest(convert.getBytes()));
	    }
	    catch(NoSuchAlgorithmException e) 
	    { return false; } 
		
		// Build the auth reply packet and then send it
	    CommandAuthReply cmdAuthReply = new CommandAuthReply();
	    cmdAuthReply.username = this.router.getUser();
	    cmdAuthReply.usernameLen = cmdAuthReply.username.length();
	    cmdAuthReply.key = new int[5];
	    for (int i = 0; i < 5; i++)
	    { cmdAuthReply.key[i] = sha1.getInt(); }
	    
	    byte[] buf = cmdAuthReply.serialize();
	    try
		{
			OutputStream outStream = socket.getOutputStream();
			outStream.write(buf);
            outStream.flush();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean handleAuthStatus(CommandAuthStatus cmdAuthStatus)
	{
		if (cmdAuthStatus.authOk)
		{
			System.out.println("successfully authenticated as " 
					+ this.router.getUser());
		}
		else
		{
			System.err.println(String.format("Authentiation failed as %s: %s", 
					this.router.getUser(), cmdAuthStatus.msg));
		}
		return cmdAuthStatus.authOk;
	}
	
	public boolean readFromServer()
	{ return this.readFromServerExpect(0); }
	
	public boolean readFromServerExpect(int expectedCmd)
	{
		int bytesRead = 0;
		InputStream inStream = null;
		
		// Get input stream
		try 
		{ inStream = this.socket.getInputStream(); } 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Attempt to read the size of the incoming packet
		byte[] lenBytes = new byte[4];
		while (bytesRead < 4)
		{
			try 
			{
				int ret = inStream.read(lenBytes, bytesRead, 4 - bytesRead);
				if (ret < 0)
				{ throw new Exception(); }
				bytesRead += ret;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
				return false;
			}
		}
		
		int len = ByteBuffer.wrap(lenBytes).getInt();
		
		if (len > 10000 || len < 0)
		{
			System.err.println(String.format(
					"Error: comamnd length too large %d", len));
			try { socket.close(); } catch (IOException e) { }
			return false;
		}
		
		// Allocate buffer
		ByteBuffer buf = ByteBuffer.allocate(len);
		
		// Set first field of command since we've already read it
		buf.putInt(len);
		
		// Read the rest of the command
		while (bytesRead < len)
		{
			try 
			{
				int ret = inStream.read(buf.array(), bytesRead, len - bytesRead);
				if (ret < 0)
				{ throw new Exception(); }
				bytesRead += ret;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
				System.err.println("Error: failed reading command body");
				try { socket.close(); } catch (IOException e2) { }
				return false;
			}
		}
		
		// Make sure the command is what we expected if we were expecting something
		int command = buf.getInt();
		if (expectedCmd != 0 && command != expectedCmd)
		{
			if (command != Command.VNS_CLOSE) // VNS_CLOSE is always ok
			{
				System.err.println(String.format(
						"Error: expected command %d but got %d", expectedCmd,
						command));
				return false;
			}
		}
		
		buf.position(0);
		switch(command)
		{
		case Command.VNS_PACKET:
			CommandPacket cmdPkt = new CommandPacket();
			cmdPkt.deserialize(buf);
			
			// Check if it is an ARP to another router if so drop
			if (this.arpRequestNotForUs(cmdPkt.etherPacket, 
					cmdPkt.mInterfaceName))
			{ break; }
			
			// Log packet
            if (this.router.getLogFile() != null)
            { this.router.getLogFile().dump(cmdPkt.etherPacket); }
			
			// Pass to router, student's code should take over here
			this.router.handlePacket(cmdPkt.etherPacket, 
					this.router.getInterface(cmdPkt.mInterfaceName));
			break;
			
		case Command.VNS_CLOSE:
			System.err.println("VNS server closed session.");
			CommandClose cmdClose = new CommandClose();
			cmdClose.deserialize(buf);
			System.err.println("Reason: " + new String(cmdClose.mErrorMessage));
			return true;
		
		case Command.VNS_BANNER:
			CommandBanner cmdBanner = new CommandBanner();
			cmdBanner.deserialize(buf);
			System.err.println(new String(cmdBanner.mBannerMessage));
			break;
			
		case Command.VNS_HW_INFO:
			CommandHwInfo cmdHwInfo = new CommandHwInfo();
			cmdHwInfo.deserialize(buf);
			this.handleHwInfo(cmdHwInfo);
			if (!this.router.getRouteTable().verify(this.router.getInterfaces()))
			{
				System.err.println("Routing table not consistent with hardware");
                return false;
			}
			System.out.println("<-- Ready to process packets -->");
			break;
			
		case Command.VNS_RTABLE:
			CommandRtable cmdRtable = new CommandRtable();
			cmdRtable.deserialize(buf);
			if (!this.handleRtable(cmdRtable))
			{ return false; }
			break;
			
		case Command.VNS_AUTH_REQUEST:
			CommandAuthRequest cmdAuthRequest = new CommandAuthRequest();
			cmdAuthRequest.deserialize(buf);
			if (!this.handleAuthRequest(cmdAuthRequest))
			{ return false; }
			break;
			
		case Command.VNS_AUTH_STATUS:
			CommandAuthStatus cmdAuthStatus = new CommandAuthStatus();
			cmdAuthStatus.deserialize(buf);
			if (!this.handleAuthStatus(cmdAuthStatus))
			{ return false; }
			break;
		
		default:
			System.err.println(String.format("unknown command: %d", command));
			break;
		}

		return true;
	}
	
	// sr_arp_req_not_for_us
	private boolean arpRequestNotForUs(Ethernet etherPacket, String ifaceName)
	{
		// Check if it's an ARP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_ARP)
		{ return false; }
		
		Iface iface = this.router.getInterface(ifaceName);
		ARP arpPacket = (ARP)etherPacket.getPayload();
		int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
		
		// Check if it's a request and we are not the target
		if ((arpPacket.getOpCode() == ARP.OP_REQUEST)
			&& (targetIp != iface.getIpAddress()))
		{ return true; }
		
		return false;
	}
	
	// sr_ether_addrs_match_interface
	public boolean etherAddrsMatchInterface(Ethernet etherPacket, 
			String ifaceName)
	{
		Iface iface = this.router.getInterface(ifaceName);
		if (null == iface)
		{
			System.err.println("** Error, interface " + ifaceName 
					+ ", does not exist");
			return false;
		}
		if (!iface.getMacAddress().equals(etherPacket.getSourceMAC()))
		{
			System.err.println("** Error, source address does not match interface"); 
			return false;
		}
		return true;
	}
	
	// sr_send_packet
	public boolean sendPacket(Ethernet etherPacket, String ifaceName)
	{
		CommandPacket cmdPacket = new CommandPacket();
		cmdPacket.mInterfaceName = ifaceName;
		cmdPacket.etherPacket = etherPacket;
		
		byte[] buf = cmdPacket.serialize();
		
		if (!etherAddrsMatchInterface(etherPacket, ifaceName))
		{
			System.err.println("*** Error: problem with ethernet header, check log");
			return false;
		}
		
		// Log packet
        if (this.router.getLogFile() != null)
        { this.router.getLogFile().dump(etherPacket); }
		
	    try
		{
			OutputStream outStream = socket.getOutputStream();
			outStream.write(buf);
            outStream.flush();
		}
		catch(IOException e)
		{
			System.err.println("Error writing packet");
			return false;
		}
		return true;
	}
}
