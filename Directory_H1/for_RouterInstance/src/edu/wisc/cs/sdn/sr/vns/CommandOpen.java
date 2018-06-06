package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandOpen extends Command 
{
	protected short topoId;
	protected short pad;
	protected String mVirtualHostId;
	protected String mUID;
	protected String mPass;
	
	public CommandOpen()
	{
		super(Command.VNS_OPEN);
		this.mLen = this.getSize();
	}
	
	protected CommandOpen deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		this.topoId = buf.getShort();
		this.pad = buf.getShort();
		
		byte[] tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mVirtualHostId = new String(tmpBytes);
		
		tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mUID = new String(tmpBytes);
		
		tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mPass = new String(tmpBytes);
		
		return this;
	}
	
	protected byte[] serialize()
	{
		byte[] data = new byte[this.getSize()];
        ByteBuffer bb = ByteBuffer.wrap(data);
        
        byte[] parentData = super.serialize();
        
        bb.put(parentData);
        bb.putShort(this.topoId);
        bb.putShort(this.pad);
        byte[] tmp = new byte[Command.ID_SIZE];
        System.arraycopy(this.mVirtualHostId.getBytes(), 0, tmp, 0, this.mVirtualHostId.length());
        bb.put(tmp);
        tmp = new byte[Command.ID_SIZE];
        System.arraycopy(this.mUID.getBytes(), 0, tmp, 0, this.mUID.length());
        bb.put(tmp);
        tmp = new byte[Command.ID_SIZE];
        if (this.mPass != null)
        { System.arraycopy(this.mPass.getBytes(), 0, tmp, 0, this.mPass.length()); }
        bb.put(tmp);
        
        return data;
	}
	
	protected int getSize()
	{ return super.getSize() + 2*2 + Command.ID_SIZE*3; }
}
