package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CommandOpenTemplate extends Command 
{
	protected String templateName;
	protected String mVirtualHostId;
	protected List<CommandSrcFilter> srcFilters;
	
	protected CommandOpenTemplate()
	{
		super(Command.VNS_OPEN_TEMPLATE);
		this.mLen = this.getSize();
	}
	
	protected CommandOpenTemplate deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		byte[] tmpBytes = new byte[30];
		buf.get(tmpBytes);
		this.templateName = new String(tmpBytes);
		
		tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mVirtualHostId = new String(tmpBytes);
		
		this.srcFilters = new ArrayList<CommandSrcFilter>(); 
		while (buf.hasRemaining())
		{
			CommandSrcFilter srcFilter = new CommandSrcFilter();
			srcFilter.deserialize(buf);
			this.srcFilters.add(srcFilter);
		}
						
		return this;
	}
	
	protected byte[] serialize()
	{
		byte[] data = new byte[this.getSize()];
        ByteBuffer bb = ByteBuffer.wrap(data);
        
        byte[] parentData = super.serialize();
        
        bb.put(parentData);
        byte[] tmp = new byte[30];
        System.arraycopy(this.templateName.getBytes(), 0, tmp, 0, tmp.length-1);
        bb.put(tmp);
        bb.put(this.mVirtualHostId.getBytes(),0,Command.ID_SIZE);
        
        return data;
	}
	
	protected int getSize()
	{ return super.getSize() + 30 + Command.ID_SIZE; }
}
