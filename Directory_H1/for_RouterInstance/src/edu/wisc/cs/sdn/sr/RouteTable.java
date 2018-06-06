package edu.wisc.cs.sdn.sr;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Route table for a router.
 * @author Aaron Gember-Jacobson
 */
public class RouteTable 
{
	/** Entries in the route table */
	private List<RouteTableEntry> entries; 
	
	/**
	 * Initialize an empty route table.
	 */
	public RouteTable()
	{ this.entries = new LinkedList<RouteTableEntry>(); }
	
	/**
	 * @return entries in the route table
	 */
	public List<RouteTableEntry> getEntries()
	{ return this.entries; }
	
	/**
	 * Populate the route table from a file.
	 * @param filename name of the file containing the static route table
	 * @return true if route table was successfully loaded, otherwise false
	 */
	public boolean load(String filename)
	{
		// Open the file
		BufferedReader reader;
		try 
		{
			FileReader fileReader = new FileReader(filename);
			reader = new BufferedReader(fileReader);
		}
		catch (FileNotFoundException e) 
		{
			System.err.println(e.toString());
			return false;
		}
		
		boolean clearRoutingTable = true;
		while (true)
		{
			// Read a route entry from the file
			String line = null;
			try 
			{ line = reader.readLine(); }
			catch (IOException e) 
			{
				System.err.println(e.toString());
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			// Stop if we have reached the end of the file
			if (null == line)
			{ break; }
			
			// Parse fields for route entry
			String ipPattern = "(\\d+\\.\\d+\\.\\d+\\.\\d+)";
			String ifacePattern = "([a-zA-Z0-9]+)";
			Pattern pattern = Pattern.compile(String.format(
                        "%s\\s+%s\\s+%s\\s+%s", 
                        ipPattern, ipPattern, ipPattern, ifacePattern));
			Matcher matcher = pattern.matcher(line);
			if (!matcher.matches() || matcher.groupCount() != 4)
			{
				System.err.println("Invalid entry in routing table file");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}

			int dstIp = Util.dottedDecimalToInt(matcher.group(1));
			if (0 == dstIp)
			{
				System.err.println("Error loading routing table, cannot convert " + matcher.group(1) + " to valid IP");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			int gwIp = Util.dottedDecimalToInt(matcher.group(2));
			
			int maskIp = Util.dottedDecimalToInt(matcher.group(3));
			if (0 == maskIp)
			{
				System.err.println("Error loading routing table, cannot convert " + matcher.group(3) + " to valid IP");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			String iface = matcher.group(4).trim();
			
			
			// If we successfully read the first entry from the file, then
			// flush the current route table
			if (clearRoutingTable)
			{
				System.out.println("Loading routing table from server, clear local routing table");
				entries.clear();
				clearRoutingTable = false;
			}
			
			// Add an entry to the route table
			this.addEntry(dstIp, gwIp, maskIp, iface);
		}
	
		// Close the file
		try { reader.close(); } catch (IOException f) {};
		return true;
	}
	
	/**
	 * Add an entry to the route table.
	 * @param dstIp destination IP
	 * @param gwIp gateway IP
	 * @param maskIp subnet mask
	 * @param iface router interface out which to send packets to reach the 
	 *        destination or gateway
	 */
	public void addEntry(int dstIp, int gwIp, int maskIp, String iface)
	{
		RouteTableEntry entry = new RouteTableEntry(dstIp, gwIp, maskIp, iface);
        synchronized(this.entries)
        { 
            this.entries.add(entry);
        }
	}
	
	/**
	 * Remove an entry from the route table.
	 * @param dstIP destination IP of the entry to remove
     * @param maskIp subnet mask of the entry to remove
     * @return true if a matching entry was found and removed, otherwise false
	 */
	public boolean removeEntry(int dstIp, int maskIp)
	{ 
        synchronized(this.entries)
        {
            RouteTableEntry entry = this.findEntry(dstIp, maskIp);
            if (null == entry)
            { return false; }
            this.entries.remove(entry);
        }
        return true;
    }
	
	/**
	 * Update an entry in the route table.
	 * @param dstIP destination IP of the entry to update
     * @param maskIp subnet mask of the entry to update
	 * @param gatewayAddress new gateway IP address for matching entry
	 * @param ifaceName new router interface name for matching entry
     * @return true if a matching entry was found and updated, otherwise false
	 */
	public boolean updateEntry(int dstIp, int maskIp, int gwIp, 
            String ifaceName)
	{
        synchronized(this.entries)
        {
            RouteTableEntry entry = this.findEntry(dstIp, maskIp);
            if (null == entry)
            { return false; }
            entry.setGatewayAddress(gwIp);
            entry.setInterface(ifaceName);
        }
        return true;
	}

    /**
	 * Find an entry in the route table.
	 * @param dstIP destination IP of the entry to find
     * @param maskIp subnet mask of the entry to find
     * @return a matching entry if one was found, otherwise null
	 */
    public RouteTableEntry findEntry(int dstIp, int maskIp)
    {
        synchronized(this.entries)
        {
            for (RouteTableEntry entry : this.entries)
            {
                if ((entry.getDestinationAddress() == dstIp)
                    && (entry.getMaskAddress() == maskIp)) 
                { return entry; }
            }
        }
        return null;
    }

	/**
	 * Verify the interface specified in entries in the route table refer to 
	 * valid router interfaces.
	 * @param interfaces list of router interfaces
	 * @return true if all entries refer to a valid interface, otherwise false
	 */
	public boolean verify(Map<String,Iface> interfaces)
	{
        synchronized(this.entries)
        { 
            for (RouteTableEntry entry : this.entries)
            {
                if (!interfaces.containsKey(entry.getInterface()))
                { return false; }
            }
        }
		return true;
	}
	
	public String toString()
	{
        synchronized(this.entries)
        { 
            if (0 == this.entries.size())
            { return " * warning* Routing table empty"; }
            
            String result = "Destination\tGateway\t\tMask\t\tIface\n";
            for (RouteTableEntry entry : entries)
            { result += entry.toString()+"\n"; }
		    return result;
        }
	}
}
