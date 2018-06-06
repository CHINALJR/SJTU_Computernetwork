package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandSrcFilter 
{
	protected int ip;
	protected short numMaskedBits;
	
	protected CommandSrcFilter deserialize(ByteBuffer buf)
	{
		this.ip = buf.getInt();
		this.numMaskedBits = buf.getShort();		
		return this;
	}
}
