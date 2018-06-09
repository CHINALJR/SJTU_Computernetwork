package edu.wisc.cs.sdn.sr;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.List;
import edu.wisc.cs.sdn.sr.vns.VNSComm;
import java.util.*;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.util.MACAddress;

/**
 * @author Aaron Gember-Jacobson
 */
public class Router 
{
	/** User under which the router is running */
	private String user;
	
	/** Hostname for the router */
	private String host;
	
	/** Template name for the router; null if no template */
	private String template;
	
	/** Topology ID for the router */
	private short topo;
	
	/** List of the router's interfaces; maps interface name's to interfaces */
	private Map<String,Iface> interfaces;
	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/** PCAP dump file for logging all packets sent/received by the router;
	 *  null if packets should not be logged */
	private DumpFile logfile;
	
	/** Virtual Network Simulator communication manager for the router */
	private VNSComm vnsComm;

    /** RIP subsystem */
    private RIP rip;
	
	/**
	 * Creates a router for a specific topology, host, and user.
	 * @param topo topology ID for the router
	 * @param host hostname for the router
	 * @param user user under which the router is running
	 * @param template template name for the router; null if no template
	 */
	public Router(short topo, String host, String user, String template)
	{
		System.out.println("??fuck");
		this.topo = topo;
		this.host = host;
		this.setUser(user);
		this.template = template;
		this.logfile = null;
		this.interfaces = new HashMap<String,Iface>();
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache(this);
		this.vnsComm = null;
        this.rip = new RIP(this);
	}
	
	public void init()
	{ this.rip.init(); }
	
	/**
	 * @param logfile PCAP dump file for logging all packets sent/received by 
	 * 		  the router; null if packets should not be logged
	 */
	public void setLogFile(DumpFile logfile)
	{ this.logfile = logfile; }
	
	/**
	 * @return PCAP dump file for logging all packets sent/received by the
	 *         router; null if packets should not be logged
	 */
	public DumpFile getLogFile()
	{ return this.logfile; }
	
	/**
	 * @param template template name for the router; null if no template
	 */
	public void setTemplate(String template)
	{ this.template = template; }
	
	/**
	 * @return template template name for the router; null if no template
	 */
	public String getTemplate()
	{ return this.template; }
		
	/**
	 * @param user user under which the router is running; if null, use current 
	 *        system user
	 */
	public void setUser(String user)
	{
		if (null == user)
		{ this.user = System.getProperty("user.name"); }
		else
		{ this.user = user; }
	}
	
	/**
	 * @return user under which the router is running
	 */
	public String getUser()
	{ return this.user; }
	
	/**
	 * @return hostname for the router
	 */
	public String getHost()
	{ return this.host; }
	
	/**
	 * @return topology ID for the router
	 */
	public short getTopo()
	{ return this.topo; }
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * @return list of the router's interfaces; maps interface name's to
	 * 	       interfaces
	 */
	public Map<String,Iface> getInterfaces()
	{ return this.interfaces; }
	
	/**
	 * @param vnsComm Virtual Network System communication manager for the router
	 */
	public void setVNSComm(VNSComm vnsComm)
	{ this.vnsComm = vnsComm; }
	
	/**
	 * Close the PCAP dump file for the router, if logging is enabled.
	 */
	public void destroy()
	{
		if (logfile != null)
		{ this.logfile.close(); }
	}
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loading routing table");
		System.out.println("---------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("---------------------------------------------");
	}
	
	/**
	 * Add an interface to the router.
	 * @param ifaceName the name of the interface
	 */
	public Iface addInterface(String ifaceName)
	{
		Iface iface = new Iface(ifaceName);
		this.interfaces.put(ifaceName, iface);
		return iface;
	}
	
	/**
	 * Gets an interface on the router by the interface's name.
	 * @param ifaceName name of the desired interface
	 * @return requested interface; null if no interface with the given name 
	 * 		   exists
	 */
	public Iface getInterface(String ifaceName)
	{ return this.interfaces.get(ifaceName); }
	
	/**
	 * Send an Ethernet packet out a specific interface.
	 * @param etherPacket an Ethernet packet with all fields, encapsulated
	 * 		  headers, and payloads completed
	 * @param iface interface on which to send the packet
	 * @return true if the packet was sent successfully, otherwise false
	 */
	public boolean sendPacket(Ethernet etherPacket, Iface iface)
	{ return this.vnsComm.sendPacket(etherPacket, iface.getName()); }
	
	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	private final String IP_RIP_MULTICAST = "224.0.0.9";
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		//System.out.println("*** ->Received packet: " +
    //            etherPacket.toString().replace("\n", "\n\t"));
		switch(etherPacket.getEtherType())
		{
			case Ethernet.TYPE_IPv4:
				IPv4 ip = (IPv4)etherPacket.getPayload();
				if (IPv4.toIPv4Address(IP_RIP_MULTICAST) == ip.getDestinationAddress())
				{
					if (IPv4.PROTOCOL_UDP == ip.getProtocol()) 
					{
						
						UDP udp = (UDP)ip.getPayload();
						if (UDP.RIP_PORT == udp.getDestinationPort())
						{ 
							rip.handlePacket(etherPacket, inIface);
							break;
						}
						
					}
				}
				this.handleIpPacket(etherPacket, inIface);
				break;
			case Ethernet.TYPE_ARP:
				this.handleArpPacket(etherPacket, inIface);
		}
		
		
		/********************************************************************/
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		/********************************************************************/
	}
	private final int TIME_EXCEEDED = 0;
	private final int DEST_NET_UNREACHABLE = 1;
	public final int DEST_HOST_UNREACHABLE = 2;
	private final int DEST_PORT_UNREACHABLE = 3;
	private final int ICMP_ECHO_REPLY = 4;
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		/*
		for (Iface iface : this.interfaces.values()) {
			arpCache.insert(iface.getMacAddress(), iface.getIpAddress());
		}
		*/
		System.out.println("*** ->Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle Normal IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum){ 
			return; 
		}
		
		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl()){ 
			sendICMP(TIME_EXCEEDED, etherPacket);
			return; 
		}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();
		System.out.println("OK checksum and TTL");
		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values()){
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				byte protocol = ipPacket.getProtocol();
				if(protocol == IPv4.PROTOCOL_TCP || protocol == IPv4.PROTOCOL_UDP) {
					sendICMP(DEST_PORT_UNREACHABLE ,etherPacket);
				} 
				else if (protocol == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();
					// echo
					if(icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) {
						sendICMP(ICMP_ECHO_REPLY ,etherPacket);
					}
				}
				return;
			}
		}
		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}
	private RouteTableEntry getBest(int dstAddr){
		List<RouteTableEntry> fuck = routeTable.getEntries();
		RouteTableEntry bestMatch =null;
		long t = 0;
		for (RouteTableEntry x : fuck){
			int ccc1 = ( x.getMaskAddress() & dstAddr );
			int ccc2 = ( x.getDestinationAddress() & x.getMaskAddress() );
			if (ccc1 == ccc2){
				if (x.getMaskAddress()<t){
					System.out.println("Find best match!");		
					t = x.getMaskAddress();
					bestMatch = x;
				}
			}
		}
		return bestMatch;
	}
	
	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// checksum ok TLL ok 
		// Get IP header
		
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();
		System.out.println("Forward IP Packet Route Table:\n"+this.getRouteTable());
		// Find matching route table entry 
		
		RouteTableEntry bestMatch = getBest(dstAddr);
		if (null == bestMatch){ 
			sendICMP(DEST_NET_UNREACHABLE, etherPacket);
			return; 
		}
		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = interfaces.get(bestMatch.getInterface());
		if (outIface == inIface){ 
			return; 
		}
		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop){ 
			nextHop = dstAddr; 
		}
		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = arpCache.lookup(nextHop);
		if (null == arpEntry){ 
			//handleArpMiss(nextHop, etherPacket, inIface, outIface);
			arpCache.waitForArp(etherPacket,outIface,nextHop);
			return;
		}
		System.out.println("Forward Successfull!");
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
		this.sendPacket(etherPacket, outIface);
	}
	

	/**
	 * Handle an ARP packet received on a specific interface.
	 * @param etherPacket the complete ARP packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	private void handleArpPacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("Handle ARP Packet");
		// Make sure it's an ARP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_ARP)
		{ return; }
		
		// Get ARP header
		ARP arpPacket = (ARP)etherPacket.getPayload();
		int targetIp = ByteBuffer.wrap(
				arpPacket.getTargetProtocolAddress()).getInt();
		
		switch(arpPacket.getOpCode())
		{
		case ARP.OP_REQUEST:
			// Check if request is for one of my interfaces
			if (targetIp == inIface.getIpAddress())
			{ this.arpCache.sendArpReply(etherPacket, inIface); }
			break;
		case ARP.OP_REPLY:
			// Check if reply is for one of my interfaces
			if (targetIp != inIface.getIpAddress())
			{ break; }
			
			// Update ARP cache with contents of ARP reply
		    int senderIp = ByteBuffer.wrap(
				    arpPacket.getSenderProtocolAddress()).getInt();
			// get the ip 
			ArpRequest request = this.arpCache.insert(
					new MACAddress(arpPacket.getSenderHardwareAddress()),
					senderIp);
			MACAddress mac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
			// Process pending ARP request entry, if there is one
			if (request != null)
			{				
				for (Ethernet packet : request.getWaitingPackets())
				{
					System.out.println("ARP Reply and resend the queue");
					packet.setDestinationMACAddress(mac.toBytes());
					sendPacket(packet, request.getIface());
					/*********************************************************/
					/* TODO: send packet waiting on this request             */
					
					/*********************************************************/
				}
			}
			break;
		}
	}
	public void sendICMP(int type, Ethernet etherPacket)
	{
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();
		
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int srcAddr = ipPacket.getSourceAddress();
		RouteTableEntry bestMatch = getBest(srcAddr);
		
		if (null == bestMatch){  	
			return;   
		}
		Iface outIface = interfaces.get(bestMatch.getInterface());
		
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop){ 
			nextHop = srcAddr; 
		}
		

    // set ether 
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
		// set ip
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setDestinationAddress(ipPacket.getSourceAddress());

		byte[] iData;

		if (ICMP_ECHO_REPLY != type) 
		{
			ip.setSourceAddress(outIface.getIpAddress());

			byte[] ipHP = ipPacket.serialize();
			int ipHLength = ipPacket.getHeaderLength() * 4;

			iData = new byte[4 + ipHLength + 8];

			Arrays.fill(iData, 0, 4, (byte)0);

			for (int i = 0; i < ipHLength + 8; i++) 
				{ iData[i + 4] = ipHP[i]; }
		}
		else{ 

			ip.setSourceAddress(ipPacket.getDestinationAddress());
			iData = ((ICMP)ipPacket.getPayload()).getPayload().serialize();
		}
		switch(type) 
		{
			case TIME_EXCEEDED:
				icmp.setIcmpType((byte)11);
				icmp.setIcmpCode((byte)0);
				break;
			case DEST_NET_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)0);
				break;
			case DEST_HOST_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)1);
				break;
			case DEST_PORT_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)3);
				break;
			case ICMP_ECHO_REPLY:
				icmp.setIcmpType((byte)0);
				icmp.setIcmpCode((byte)0);
				break;
			default:
				return;
		}
		data.setData(iData);
		icmp.setPayload(data);
		icmp.setChecksum((short)(0));
		byte[] serialized = icmp.serialize();
		icmp.deserialize(serialized, 0, serialized.length);
		
		ip.setPayload(icmp);
		ip.setChecksum((short)(0));
		serialized = ip.serialize();
		ip.deserialize(serialized, 0, serialized.length);
		System.out.println("good RIP for ICMP");
		ether.setPayload(ip);
		
		ArpEntry arpEntry = arpCache.lookup(nextHop);
		if (null == arpEntry){
			arpCache.waitForArp(ether,outIface,nextHop);
			return;   
		}
		ether.setDestinationMACAddress(arpEntry.getMac().toBytes());
		System.out.println("good ARP for ICMP");
		System.out.println(":::::ICMP:::::");
		System.out.println(icmp.toString());
		this.sendPacket(ether, outIface);
	}
}
