package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandAuthRequest extends Command
{
	protected String salt;
	
	public CommandAuthRequest()
	{ super(Command.VNS_AUTH_REQUEST); }
	
	protected CommandAuthRequest deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		byte[] tmpBytes = new byte[buf.limit() - buf.position()];
		buf.get(tmpBytes);
		this.salt = new String(tmpBytes);
						
		return this;
	}
}
