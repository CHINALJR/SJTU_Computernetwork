package edu.wisc.cs.sdn.sr;

/**
 * An entry in a route table.
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteTableEntry 
{
	/** Destination IP address */
	private int destinationAddress;
	
	/** Gateway IP address */
	private int gatewayAddress;
	
	/** Subnet mask */
	private int maskAddress;
	
	/** Name of the router interface out which packets should be sent to reach
	 * the destination or gateway */
	private String interfaceName;
	private int hopTime;
	private long timeAdded;
	//private int timer = 0;
	public int getHopTime()
	{ return this.hopTime; }
	public int setHopTime(int fucktime)
	{ return this.hopTime = fucktime; }
	/**
	 * Create a new route table entry.
	 * @param destinationAddress destination IP address
	 * @param gatewayAddress gateway IP address
	 * @param maskAddress subnet mask
	 * @param ifaceName name of the router interface out which packets should 
	 *        be sent to reach the destination or gateway
	 */
	public RouteTableEntry(int destinationAddress, int gatewayAddress, 
			int maskAddress, String ifaceName,int hopTime)
	{
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.interfaceName = ifaceName;
		this.hopTime = hopTime;
		this.timeAdded = System.currentTimeMillis();
		if (hopTime == 0 ) this.timeAdded = -1;
	}
	
	/**
	 * @return destination IP address
	 */
	public int getDestinationAddress()
	{ return this.destinationAddress; }
	
	/**
	 * @return gateway IP address
	 */
	
	public int getGatewayAddress()
	{ return this.gatewayAddress; }

    public void setGatewayAddress(int gatewayAddress)
    { this.gatewayAddress = gatewayAddress; }
	
	/**
	 * @return subnet mask 
	 */
	public int getMaskAddress()
	{ return this.maskAddress; }
	
	/**
	 * @return name of the router interface out which packets should be sent to 
	 *         reach the destination or gateway
	 */
	public String getInterface()
	{ return this.interfaceName; }

    public void setInterface(String interfaceName)
    { this.interfaceName = interfaceName; }
	
	public String toString()
	{
		String result = "";
		result += Util.intToDottedDecimal(destinationAddress) + "\t";
        String gwString = Util.intToDottedDecimal(gatewayAddress);
		result += gwString + "\t";
        if (gwString.length() < 8)
        { result += "\t"; }
		result += Util.intToDottedDecimal(maskAddress) + "\t";
		result += interfaceName;
		return result;
	}
	public void setTimeAdded(){
		this.timeAdded = System.currentTimeMillis();
	}
  public long getTimeAdded()
	{ return this.timeAdded; }
}
