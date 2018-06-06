package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandAuthStatus extends Command 
{
	protected boolean authOk;
	protected String msg;
	
	public CommandAuthStatus()
	{ super(Command.VNS_AUTH_STATUS); }
	
	protected CommandAuthStatus deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		this.authOk = (buf.get() > 0);
		
		byte[] tmpBytes = new byte[buf.limit() - buf.position()];
		buf.get(tmpBytes);
		this.msg = new String(tmpBytes);
						
		return this;
	}
}
