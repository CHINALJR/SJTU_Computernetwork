package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandRtable extends Command
{
	protected String mVIrtualHostId;
	protected String rtable;
	
	public CommandRtable()
	{ super(Command.VNS_RTABLE); }
	
	protected CommandRtable deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		byte[] tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mVIrtualHostId = new String(tmpBytes);
		
		tmpBytes = new byte[buf.limit() - buf.position()];
		buf.get(tmpBytes);
		this.rtable = new String(tmpBytes);
						
		return this;
	}
}
