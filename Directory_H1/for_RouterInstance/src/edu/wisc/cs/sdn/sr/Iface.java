package edu.wisc.cs.sdn.sr;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

/**
 * An interface on a router.
 * @author Aaron Gember-Jacobson
 */
public class Iface 
{
	private String name;
	private MACAddress macAddress;
	private int ipAddress;
    private int subnetMask;
	
	public Iface(String name)
	{
		this.name = name;
		this.macAddress = null;
		this.ipAddress = 0;
	}
	
	public String getName()
	{ return this.name; }
	
	public void setMacAddress(MACAddress mac)
	{ this.macAddress = mac; }
	
	public MACAddress getMacAddress()
	{ return this.macAddress; }

	public void setIpAddress(int ip)
	{ this.ipAddress = ip; }
	
	public int getIpAddress()
	{ return this.ipAddress; }
	
    public void setSubnetMask(int subnetMask)
	{ this.subnetMask = subnetMask; }
	
	public int getSubnetMask()
	{ return this.subnetMask; }

	public String toString()
	{
		return String.format("%s\tHWaddr %s\n\tinet addr %s mask %s",
				this.name, this.macAddress.toString(), 
				IPv4.fromIPv4Address(this.ipAddress),
                IPv4.fromIPv4Address(this.subnetMask));
	}
}
