package edu.wisc.cs.sdn.sr;

import edu.wisc.cs.sdn.sr.vns.Command;
import edu.wisc.cs.sdn.sr.vns.VNSComm;

public class Main 
{
	private static final String VERSION_INFO = "Based on VNS sr stub code 0.20";
	private static final short DEFAULT_PORT = 8001;
	private static final short DEFAULT_TOPO = 0;
	private static final String DEFAULT_HOST = "r1";
	private static final String DEFAULT_SERVER = "localhost";
	
	public static void main(String[] args)
	{
		String host = DEFAULT_HOST;
		String user = null;
		String server = DEFAULT_SERVER;
		String routeTableFile = null;
		String logfile = null;
		String template = null;
		short port = DEFAULT_PORT;
		short topo = DEFAULT_TOPO;
		Router router = null;
		VNSComm vnsComm = null;
		
		System.out.println("Using "+VERSION_INFO);
		
		// Parse arguments
		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if (arg.equals("-h"))
			{
				usage();
				System.exit(0);
			}
			else if(arg.equals("-p"))
			{ port = Short.parseShort(args[++i]); }
			else if (arg.equals("-t"))
			{ topo = Short.parseShort(args[++i]); }
			else if (arg.equals("-v"))
			{ host = args[++i]; }
			else if (arg.equals("-u"))
			{ user = args[++i]; }
			else if (arg.equals("-s"))
			{ server = args[++i]; }
			else if (arg.equals("-l"))
			{ logfile = args[++i]; }
			else if (arg.equals("-r"))
			{ routeTableFile = args[++i]; }
			else if (arg.equals("-T"))
			{ template = args[++i]; }
		}
		
		// Create router instance
		router = new Router(topo, host, user, template);
		
		// Load routing table from file
		/*if (null == template)
		{ router.loadRouteTable(rtable); }
		router.setTemplate(template);*/
		
		// Open PCAP dump file for logging packets sent/received by the router
		if (logfile != null)
		{
			router.setLogFile(DumpFile.open(logfile));
			if (null == router.getLogFile())
			{
				System.err.println("Error opening up dump file "+logfile);
				System.exit(1);
			}
		}
		
		// Connect to Virtual Network Simulator server and negotiate session
		System.out.println(String.format("Client %s connecting to server %s:%d", 
				router.getUser(), server, port));
		if (template != null)
		{ System.out.println("Requesting toplogy template "+template); }
		else
		{ System.out.println("Requesting topology "+topo); }
		vnsComm = new VNSComm(router);
		router.setVNSComm(vnsComm);
		if (!vnsComm.connectToServer(port, server))
		{ System.exit(1); }

		if (template != null)
		{
			// We've received the routing table now, so read it in
			System.out.println(String.format(
					"Connected to new instantiation of topology template %s", 
					template));
			router.loadRouteTable("rtable." + host);
		}
		else if (routeTableFile != null)
		{
			// Read from specified routing table
			router.loadRouteTable(routeTableFile);
		}
	
		vnsComm.readFromServerExpect(Command.VNS_HW_INFO);	

		// Call router init (for RIP subsystem, etc.)
		router.init();
		
		// Read messages from the server until the server closes the connection
		while (vnsComm.readFromServer());
		
		// Shutdown the router
		router.destroy();
		
		System.exit(0);
	}
	
	static void usage()
	{
		System.out.println("Simple Router Client");
		System.out.println("Main [-h] [-v host] [-s server] [-p port]");
		System.out.println("     [-T template_name] [-u username]");
		System.out.println("     [-t topo_id] [-r routing_table]");
		System.out.println("     [-l log_file]");
		System.out.println(String.format("  defaults server=%s port=%d host=%s", 
				DEFAULT_SERVER, DEFAULT_PORT, DEFAULT_HOST));
	}

}
