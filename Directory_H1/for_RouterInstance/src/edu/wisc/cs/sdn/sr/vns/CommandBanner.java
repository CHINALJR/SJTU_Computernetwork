package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandBanner extends Command 
{
	protected String mBannerMessage;
	
	public CommandBanner()
	{ super(Command.VNS_BANNER); }
	
	protected CommandBanner deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		byte[] tmpBytes = new byte[256];
		buf.get(tmpBytes);
		this.mBannerMessage = new String(tmpBytes);
		
		return this;
	}
}
