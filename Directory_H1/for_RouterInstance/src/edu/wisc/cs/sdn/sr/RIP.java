package edu.wisc.cs.sdn.sr;
import java.util.*;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.packet.UDP;

/**
  * Implements RIP. 
  * @author Anubhavnidhi Abhashkumar and Aaron Gember-Jacobson
  */
public class RIP implements Runnable
{
    private static final int RIP_MULTICAST_IP = 0xE0000009;
    private static final byte[] BROADCAST_MAC = {(byte)0xFF, (byte)0xFF, 
            (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
    
    /** Send RIP updates every 10 seconds */
    private static final int UPDATE_INTERVAL = 10;

    /** Timeout routes that neighbors last advertised more than 30 seconds ago*/
    private static final int TIMEOUT = 30;

    /** Router whose route table is being managed */
	private Router router;

    /** Thread for periodic tasks */
    private Thread tasksThread;

	public RIP(Router router)
	{ 
        this.router = router; 
        this.tasksThread = new Thread(this);
    }
	private final int RIP_REQUEST = 0;
	private final int RIP_RESPONSE = 1;
	private final int RIP_UNSOL = 2;
	public void init()
	{
        // If we are using static routing, then don't do anything
        if (this.router.getRouteTable().getEntries().size() > 0)
        { return; }

        System.out.println("RIP: Build initial routing table");
        for(Iface iface : this.router.getInterfaces().values())
        {
            this.router.getRouteTable().addEntry(
                    (iface.getIpAddress() & iface.getSubnetMask()),
                    0, // No gateway for subnets this router is connected to
                    iface.getSubnetMask(), iface.getName(),0);
        		this.sendRip(RIP_REQUEST, null, iface);          
        }
        System.out.println("Route Table:\n"+this.router.getRouteTable());

		this.tasksThread.start();

        /*********************************************************************/
        /* TODO: Add other initialization code as necessary                  */

        /*********************************************************************/
	}

    /**
      * Handle a RIP packet received by the router.
      * @param etherPacket the Ethernet packet that was received
      * @param inIface the interface on which the packet was received
      */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
      System.out.println("Handle RIP Packet!");
      // Make sure it is in fact a RIP packet
      
      if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
      { return; } 
			
			IPv4 ipPacket = (IPv4)etherPacket.getPayload();
      if (ipPacket.getProtocol() != IPv4.PROTOCOL_UDP)
      { return; } 
			
			UDP udpPacket = (UDP)ipPacket.getPayload();
      if (udpPacket.getDestinationPort() != UDP.RIP_PORT)
      { return; }
			
			RIPv2 ripPacket = (RIPv2)udpPacket.getPayload();
			byte type = ripPacket.getCommand();
			switch(type)
			{
				case RIPv2.COMMAND_REQUEST:
					System.out.println("Get RIP REQUEST!");
					sendRip(RIP_RESPONSE, etherPacket, inIface);
					break;
				case RIPv2.COMMAND_RESPONSE:

					System.out.println("Handle RIP response from " + IPv4.fromIPv4Address(ipPacket.getSourceAddress()));

					List<RIPv2Entry> entries = ripPacket.getEntries();
					for (RIPv2Entry entry : entries) 
					{
						int ipAddr = entry.getAddress();
						int mask = entry.getSubnetMask();
						int nextHop = ipPacket.getSourceAddress();
						int hoptime = entry.getMetric() + 1;
						if (hoptime >= 17) 
						{ hoptime = 16; }
						int netAddr = ipAddr & mask;
						// i have it already 
						this.router.getRouteTable().updateEntry(ipAddr, mask, nextHop, inIface.getName() ,hoptime);
						
					}
					break;
				default:
					break;
			}
			//System.out.println("Route Table:\n"+this.router.getRouteTable());
			//System.out.println("Handle RIP Packet Over!");
        /*********************************************************************/
        /* TODO: Handle RIP packet                                           */

        /*********************************************************************/
	}
	private void sendRip(int type, Ethernet etherPacket, Iface iface) 
	{
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		UDP udp = new UDP();
		RIPv2 rip = new RIPv2();

		ether.setSourceMACAddress(iface.getMacAddress().toBytes());
		ether.setEtherType(Ethernet.TYPE_IPv4);

		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(iface.getIpAddress());

		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);

		switch(type)
		{
			case RIP_UNSOL:
				//broadcast tell other mine
				rip.setCommand(RIPv2.COMMAND_RESPONSE);
				ether.setDestinationMACAddress(BROADCAST_MAC);
				ip.setDestinationAddress(RIP_MULTICAST_IP);
				break;
			case RIP_REQUEST:
				//broadcast ask other for something
				rip.setCommand(RIPv2.COMMAND_REQUEST);
				ether.setDestinationMACAddress(BROADCAST_MAC);
				ip.setDestinationAddress(RIP_MULTICAST_IP);
				break;
			case RIP_RESPONSE:
				//answer other's ask
				IPv4 ipPacket = (IPv4)etherPacket.getPayload();
				rip.setCommand(RIPv2.COMMAND_RESPONSE);
				ether.setDestinationMACAddress(ether.getSourceMACAddress());
				ip.setDestinationAddress(ipPacket.getSourceAddress());
				break;
			default:
				break;
		}

		List<RIPv2Entry> entries = new ArrayList<RIPv2Entry>();
		for (RouteTableEntry myEntry : this.router.getRouteTable().getEntries())
		{
				RIPv2Entry entry = new RIPv2Entry(myEntry.getDestinationAddress(), myEntry.getMaskAddress(), myEntry.getHopTime());
				entries.add(entry);
		}
		rip.setEntries(entries);		
		udp.setPayload(rip);
		udp.setChecksum((short)(0));
		byte[] serialized = udp.serialize();
		udp.deserialize(serialized, 0, serialized.length);
		
		ip.setPayload(udp);
		ip.setChecksum((short)(0));
		serialized = ip.serialize();
		ip.deserialize(serialized, 0, serialized.length);
		
		ether.setPayload(ip);
		
		//System.out.println(":::::send RIP:::::");
		router.sendPacket(ether, iface);
	}

    
    /**
      * Perform periodic RIP tasks.
      */
	@Override
	public void run() 
    {
    
    while (true)
		{
			// Run every second
			try 
			{ Thread.sleep(UPDATE_INTERVAL*1000); }
			catch (InterruptedException e) 
			{ break; }
			
			
			for (RouteTableEntry entry : this.router.getRouteTable().getEntries())
			{
				if (entry.getTimeAdded() != -1  && (System.currentTimeMillis() - entry.getTimeAdded()) 
						> TIMEOUT*1000)
				{ 
					this.router.getRouteTable().removeEntry(entry.getDestinationAddress(), entry.getMaskAddress());
				}
			}
			for (Iface iface : this.router.getInterfaces().values()){ 
				sendRip(RIP_UNSOL, null, iface); 
			}
		}
        /*********************************************************************/
        /* TODO: Send period updates and time out route table entries        */

        /*********************************************************************/
	}
}
