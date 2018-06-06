package edu.wisc.cs.sdn.sr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.MACAddress;

/**
 * A cache of MAC address to IP address mappings.
 * @author Aaron Gember-Jacobson
 */
public class ArpCache implements Runnable
{
	/** Timeout (in milliseconds) for entries in the ARP cache */
	public static final int TIMEOUT = 15 * 1000;
	
	/** Maximum number of attempts (i.e., ARP request packets to send) to
	 *  determine the MAC address associated with an IP address */
	public static final int MAX_SEND_COUNT = 5;
	
	/** Router to which this cache belongs */
	private Router router;
	
	/** Entries in the cache; maps an IP address to an entry */
	private Map<Integer,ArpEntry> entries;
	
	/** Requests for IP address, MAC address pairs that should be added to the 
	 * cache; maps an IP address to a request */
	private Map<Integer,ArpRequest> requests;
	
	/** Thread for timing out requests and entries in the cache */
	private Thread timeoutThread;
	
	/**
	 * Initializes an empty ARP cache for a router.
	 * @param router router to which this cache belongs
	 */
	public ArpCache(Router router)
	{
		this.router = router;
		this.entries = new ConcurrentHashMap<Integer,ArpEntry>();
		this.requests = new ConcurrentHashMap<Integer,ArpRequest>();
		timeoutThread = new Thread(this);
		timeoutThread.start();
	}
	
	/**
	 * Every second: generate ARP request packets, timeout ARP requests, and
	 * timeout ARP entries.
	 */
	public void run()
	{
		while (true)
		{
			// Run every second
			try 
			{ Thread.sleep(1000); }
			catch (InterruptedException e) 
			{ break; }
			
			// Send ARP request packets and timeout ARP requests
			for (ArpRequest request : this.requests.values())
			{ this.updateArpRequest(request); }
			
			// Timeout ARP entries
			for (ArpEntry entry : this.entries.values())
			{
				if ((System.currentTimeMillis() - entry.getTimeAdded()) 
						> TIMEOUT)
				{ this.entries.remove(entry.getIp()); }
			}
		}
	}
	
	/**
	 * Send an ARP request packet for an IP if one second has elapsed and no
	 * reply has been received. Timeout an ARP request if MAX_SEND_COUNT
	 * request packets have been sent and no reply has been received. 
	 * @param request a pending ARP request
	 */
	private void updateArpRequest(ArpRequest request)
	{
		if ((System.currentTimeMillis() - request.getLastTimeSent()) 
				< 1000)
		{ return; }
		
		if (request.getSentCount() >= MAX_SEND_COUNT)
		{
			/*********************************************************/
		    /* TODO: send ICMP host unreachable to the source        */ 
		    /* address of all packets waiting on this request        */
			
			
		    /*********************************************************/
			
			this.requests.remove(request.getIpAddress());
		}
		else
		{
			// Send ARP request packet
			this.sendArpRequest(request);
			request.incrementSent();
		}
	}
	
	/**
	 * Insert an entry in the ARP cache for a specific IP address, MAC address
	 * pair, and return any pending request.
	 * @param mac MAC address corresponding to IP address
	 * @param ip IP address corresponding to MAC address
	 * @return pending request for the specified IP address; null if none exists
	 */
	public ArpRequest insert(MACAddress mac, int ip)
	{
		ArpRequest request = this.requests.remove(ip);
		this.entries.put(ip, new ArpEntry(mac, ip));
		return request;
	}
	
	/**
	 * Checks if an IP->MAC mapping is the in the cache.
	 * @param ip IP address whose MAC address is desired
	 * @return the IP->MAC mapping from the cache; null if none exists 
	 */
	public ArpEntry lookup(int ip)
	{ return this.entries.get(ip); }
	
	/**
	 * Adds an ARP request to the ARP request queue. Adds the packet to the 
	 * list of packets waiting for this request to be resolved.
	 * @param etherPacket packet waiting for the MAC for it's next hop IP
	 * @param outIface interface out which the packet will be sent
	 * @param nextHopIP the IP address whose MAC should be determined
	 */
	public void waitForArp(Ethernet etherPacket, Iface outIface, int nextHopIp)
	{
		ArpRequest request = this.requests.get(nextHopIp);
		if (null == request)
		{
			request = new ArpRequest(nextHopIp, outIface);
			this.requests.put(nextHopIp, request);
		}
		request.enqueuePacket(etherPacket);
		this.updateArpRequest(request);
	}
	
	/**
	 * Send an ARP request packet for a pending ARP request.
	 * @param request pending request for obtaining the MAC address for an IP
	 */
	private void sendArpRequest(ArpRequest request)
	{
		// Populate Ethernet header
		Ethernet etherPkt = new Ethernet();
		byte[] broadcastMac = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
				(byte)0xFF, (byte)0xFF};
		etherPkt.setDestinationMACAddress(broadcastMac);
		etherPkt.setSourceMACAddress(
				request.getIface().getMacAddress().toBytes());
		etherPkt.setEtherType(Ethernet.TYPE_ARP);
		
		// Populate ARP header
		ARP arpPkt = new ARP();
		arpPkt.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpPkt.setProtocolType(ARP.PROTO_TYPE_IP);
		arpPkt.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpPkt.setProtocolAddressLength((byte)4);
		arpPkt.setOpCode(ARP.OP_REQUEST);
		arpPkt.setSenderHardwareAddress(
				request.getIface().getMacAddress().toBytes());
		arpPkt.setSenderProtocolAddress(request.getIface().getIpAddress());
        arpPkt.setTargetHardwareAddress(
                new byte[Ethernet.DATALAYER_ADDRESS_LENGTH]);
		arpPkt.setTargetProtocolAddress(request.getIpAddress());
		
		// Stack headers
		etherPkt.setPayload(arpPkt);
		
		// Send ARP request
		System.out.println("Send ARP request");
		System.out.println(etherPkt.toString());
		this.router.sendPacket(etherPkt, request.getIface());
	}
	
	/**
	 * Send an ARP reply packet for a received ARP request packet.
	 * @param etherPacket ARP request packet received by the router
	 * @param iface interface on which the ARP request packet was received
	 */
	public void sendArpReply(Ethernet etherPacket, Iface iface)
	{
		// Populate Ethernet header
		Ethernet etherReply = new Ethernet();
		etherReply.setDestinationMACAddress(etherPacket.getSourceMACAddress());
		etherReply.setSourceMACAddress(iface.getMacAddress().toBytes());
		etherReply.setEtherType(Ethernet.TYPE_ARP);
		
		// Populate ARP header
		ARP arpPacket = (ARP)etherPacket.getPayload();
		ARP arpReply = new ARP();
		arpReply.setHardwareType(arpPacket.getHardwareType());
		arpReply.setProtocolType(arpPacket.getProtocolType());
		arpReply.setHardwareAddressLength(arpPacket.getHardwareAddressLength());
		arpReply.setProtocolAddressLength(arpPacket.getProtocolAddressLength());
		arpReply.setOpCode(ARP.OP_REPLY);
		arpReply.setSenderHardwareAddress(iface.getMacAddress().toBytes());
		arpReply.setSenderProtocolAddress(iface.getIpAddress());
		arpReply.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
		arpReply.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());
		
		// Stack headers
		etherReply.setPayload(arpReply);
		
		// Send ARP request
		System.out.println("Send ARP reply");
		System.out.println(arpReply.toString());
		this.router.sendPacket(etherReply, iface);
	}
}
