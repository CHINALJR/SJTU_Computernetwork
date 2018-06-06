package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandHwRequest extends Command 
{
	public CommandHwRequest()
	{ super(0); }
	
	protected CommandHwRequest deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		return this;
	}
}
