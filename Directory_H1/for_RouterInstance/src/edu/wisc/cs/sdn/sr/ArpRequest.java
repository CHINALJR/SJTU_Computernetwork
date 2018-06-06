package edu.wisc.cs.sdn.sr;

import java.util.LinkedList;
import java.util.List;

import net.floodlightcontroller.packet.Ethernet;

/**
 * A pending request for obtaining the MAC address for an IP using ARP. 
 * @author Aaron Gember-Jacobson
 */
public class ArpRequest 
{
	/** IP address whose corresponding MAC address is being requested */
	private int ipAddress;
	
	/** Interface over which the resolution should occur */
	private Iface iface;
	
	/** Last time an ARP request packet was sent for this request */ 
	private long lastTimeSent; 
	
	/** Number of times an ARP request packet has been sent for this request */
	private int sentCount; 
	
	/** List of packets waiting on this request to be resolved */
	private List<Ethernet> waitingPackets;
	
	/**
	 * Create a request for a pending resolution of an IP address's MAC address.
	 * @param ip IP address whose corresponding MAC address is being requested
	 * @param iface interface over which the resolution should occur
	 */
	public ArpRequest(int ip, Iface iface)
	{
		this.ipAddress = ip;
		this.iface = iface;
		this.lastTimeSent = 0;
		this.sentCount = 0;
		this.waitingPackets = new LinkedList<Ethernet>();
	}
	
	/**
	 * @return IP address whose corresponding MAC address is being requested
	 */
	public int getIpAddress()
	{ return this.ipAddress; }
	
	/**
	 * @return interface over which the resolution should occur
	 */
	public Iface getIface()
	{ return this.iface; }
	
	/**
	 * @return the last time (in milliseconds since the epoch) an ARP request 
	 * packet was sent for this request
	 */
	public long getLastTimeSent()
	{ return this.lastTimeSent; }
	
	/**
	 * @return number of times an ARP request packet has been sent
	 */
	public int getSentCount()
	{ return this.sentCount; }
	
	/**
	 * @return list of packets waiting on this request to be resolved
	 */
	public List<Ethernet> getWaitingPackets()
	{ return this.waitingPackets; }
	
	/**
	 * Update the last time an ARP request packet was sent to the current time
	 * (in milliseconds since the epoch) and increment the request packet count.
	 */
	public void incrementSent()
	{
		this.lastTimeSent = System.currentTimeMillis();
		this.sentCount++;
	}
	
	/**
	 * Add a packet to the list of packets waiting on this request to be
	 * resolved. All fields of the packet should be correctly filled in except 
	 * for the destination MAC address in the Ethernet header.
	 * @param etherPacket packet waiting on this request to be resolved
	 */
	public void enqueuePacket(Ethernet etherPacket)
	{ this.waitingPackets.add(etherPacket); }
}
